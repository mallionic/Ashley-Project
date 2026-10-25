/*
 * ============================================================================
 * Ashley Project — ASH Compiler and Toolchain
 * Java Source File
 * ============================================================================
 *
 * Copyright (C) 2026 Mallionic Inc.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * This file is part of the Ashley Project.
 *
 * The Ashley Project is free software: you can redistribute it and/or modify
 * it under the terms of version 3 of the GNU General Public License only,
 * as published by the Free Software Foundation.
 *
 * The Ashley Project is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * See the GNU General Public License for more details.
 *
 * A complete copy of the GNU GPL version 3 should be provided in the
 * project's COPYING file.
 *
 * Project: Ashley Project
 * Component: ASH Compiler and Toolchain
 * Copyright holder: Mallionic Inc.
 * Script update date: 2026-10-25
 * File processing date: 2026-10-25
 *
 * ============================================================================
 */

/*
 * Ashley Project — ASH Compiler and Toolchain
 * Copyright (C) 2026 Mallionic Inc.
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * This file is part of Ashley Project.
 * Licensed under GNU GPL version 3 only.
 * See COPYING for the full license text.
 */

package arch;

import arch.builtin.Parsing;
import arch.builtin.Parsing.*;

import java.io.ByteArrayOutputStream;
import java.util.*;

public final class Backend32 {

    private final Program program;
    private final long org;

    private byte[] buf = new byte[4096];
    private int len = 0;

    private final Map<String, Integer> labels = new HashMap<>();
    private final Map<String, Integer> funcOffset = new HashMap<>();
    private final Map<String, Integer> dataOffset = new LinkedHashMap<>();
    private final List<Object[]> relFix = new ArrayList<>();
    private final List<Object[]> absFix = new ArrayList<>();

    private final Map<String, FunctionDecl> funcs = new LinkedHashMap<>();
    private final Map<String, byte[]> strings = new LinkedHashMap<>();

    private final Map<String, Integer> dataBias = new HashMap<>();
    private final Set<String> alignedData = new HashSet<>();
    private final Set<String> builtinsUsed = new LinkedHashSet<>();

    private int heapPtrPos = -1;
    boolean bits16 = false;

    public int HEAP_BYTES = 65536;
    private int W = 4;

    public void set16(boolean v) { bits16 = v; W = v ? 2 : 4; }

    public boolean linuxExit = false;

    public final List<String> warnings = new ArrayList<>();

    public boolean objectMode = false;

    public boolean noLibc = false;
    public boolean windows = false;
    public int dataSize = 0;
    public int bssSize = 0;

    public final List<Obj32.Reloc> relocs = new ArrayList<>();
    public final List<Obj32.Sym> symbols = new ArrayList<>();
    private final Set<String> externs = new LinkedHashSet<>();

    private FunctionDecl cur;
    private Map<String, Integer> slot;
    private Map<String, String> vtype;
    private int frame;

    public Backend32(Program program, long org) {
        this.program = program;
        this.org = org;
    }

    private static IllegalArgumentException err(String m) {
        return new IllegalArgumentException(m);
    }

    private void b(int... v) {
        for (int x : v) {
            if (len == buf.length)
                buf = Arrays.copyOf(buf, len * 2);
            buf[len++] = (byte) x;
        }
    }

    private void i32(long v) {
        b((int) v & 0xFF, (int) (v >> 8) & 0xFF);
        if (W == 4)
            b((int) (v >> 16) & 0xFF, (int) (v >> 24) & 0xFF);
    }

    private void patch32(int pos, long v) {
        buf[pos] = (byte) v;
        buf[pos + 1] = (byte) (v >> 8);
        if (W == 4) {
            buf[pos + 2] = (byte) (v >> 16);
            buf[pos + 3] = (byte) (v >> 24);
        }
    }

    private static boolean isNumber(String s) {
        if (s == null || s.isEmpty()) return false;
        return s.matches("-?(0[xX][0-9a-fA-F]+|[0-9]+)");
    }

    private static long num(String s) {
        boolean neg = s.startsWith("-");
        String t = neg ? s.substring(1) : s;
        long v = t.startsWith("0x") || t.startsWith("0X")
            ? Long.parseUnsignedLong(t.substring(2), 16)
            : Long.parseUnsignedLong(t);
        return neg ? -v : v;
    }

    private static final int EAX = 0, ECX = 1, EDX = 2;

    private int bitsOf(String type) {
        if (type == null) return 32;
        String t = type.toLowerCase();
        if (t.contains("float") || t.equals("double"))
            throw err("floating point is not supported in 32-bit mode yet (type '" + type + "')");
        if (t.equals("byte") || t.contains("8")) return 8;
        if (t.equals("word") || t.contains("16")) return 16;
        return bits16 ? 16 : 32;
    }

    private boolean unsigned(String type) {
        if (type == null) return false;
        String t = type.toLowerCase();

        return t.startsWith("uint") || t.equals("byte") || t.equals("ptr");
    }

    private String typeOf(String name) {
        return vtype.get(name);
    }

    private void ebpOperand(int opcode, int reg, int disp) {
        b(opcode);
        int rm = bits16 ? 0x46 : 0x45;

        if (disp >= -128 && disp <= 127)
            b(rm | (reg << 3), disp & 0xFF);
        else {
            b((rm + 0x40) | (reg << 3));
            i32(disp);
        }
    }

    private void ebpOperand2(int op1, int op2, int reg, int disp) {
        b(op1);
        ebpOperand(op2, reg, disp);
    }

    private void load(int reg, String op) {

        if (isNumber(op)) {
            b(0xB8 + reg);
            i32(num(op));
            return;
        }

        Integer d = slot.get(op);

        if (d != null) {
            ebpOperand(0x8B, reg, d);
            return;
        }

        if (strings.containsKey(op) || funcs.containsKey(op)) {
            b(0xB8 + reg);
            absFix.add(new Object[]{len, op});
            i32(0);
            return;
        }

        throw err("unknown symbol '" + op + "' in function '" + cur.name + "'");
    }

    private void storeVar(String name, int reg) {

        Integer d = slot.get(name);

        if (d == null)
            throw err("unknown variable '" + name + "'");

        String t = typeOf(name);
        int bits = bitsOf(t);

        if (bits == 8) {
            if (unsigned(t) || t == null) b(0x0F, 0xB6, 0xC0 | (reg << 3) | reg);
            else b(0x0F, 0xBE, 0xC0 | (reg << 3) | reg);
        } else if (bits == 16 && bits16) {

        } else if (bits == 16 && bits16) {

        } else if (bits == 16) {
            if (unsigned(t)) b(0x0F, 0xB7, 0xC0 | (reg << 3) | reg);
            else b(0x0F, 0xBF, 0xC0 | (reg << 3) | reg);
        }

        ebpOperand(0x89, reg, d);
    }

    private static String normOp(String op) {
        switch (op) {
            case "==": return "eq";
            case "!=": return "ne";
            case "<":  return "lt";
            case "<=": return "le";
            case ">":  return "gt";
            case ">=": return "ge";
            default:   return op;
        }
    }

    private static String invert(String op) {
        switch (op) {
            case "eq": return "ne";
            case "ne": return "eq";
            case "lt": return "ge";
            case "ge": return "lt";
            case "gt": return "le";
            case "le": return "gt";
            default: throw err("unknown comparison '" + op + "'");
        }
    }

    private int jcc(String op, boolean uns) {
        switch (op) {
            case "eq": return 0x84;
            case "ne": return 0x85;
            case "lt": return uns ? 0x82 : 0x8C;
            case "ge": return uns ? 0x83 : 0x8D;
            case "le": return uns ? 0x86 : 0x8E;
            case "gt": return uns ? 0x87 : 0x8F;
            default: throw err("unknown comparison '" + op + "'");
        }
    }

    private int setcc(String op, boolean uns) {
        return jcc(op, uns) - 0x10;
    }

    private boolean unsignedCompare(String a) {
        return !isNumber(a) && slot.containsKey(a) && unsigned(typeOf(a))
           ;
    }

    private void collectVars(List<Stmt> body, Set<String> names, Map<String, String> types) {

        for (Stmt s : body) {

            if (s instanceof ExpandedStmt) {
                collectVars(((ExpandedStmt) s).statements, names, types);

            } else if (s instanceof TypeStmt) {
                TypeStmt t = (TypeStmt) s;
                names.add(t.name);
                types.put(t.name, t.type);

            } else if (s instanceof PtrDeclStmt) {
                PtrDeclStmt p = (PtrDeclStmt) s;
                names.add(p.name);
                types.put(p.name, "ptr");

            } else if (s instanceof RegAssignStmt) {
                String n = ((RegAssignStmt) s).name;
                if (n != null && !n.equals("_")) names.add(n);

            } else if (s instanceof CallStmt) {
                CallStmt c = (CallStmt) s;
                if (c.destination != null && !c.destination.isEmpty() && !c.destination.equals("_"))
                    names.add(c.destination);

            } else if (s instanceof MacroBlockStmt) {
                throw err("macro blocks are not supported in 32-bit mode");
            }
        }
    }

    private void emitBody(List<Stmt> body) {
        for (Stmt s : body)
            emitStmt(s);
    }

    private void emitStmt(Stmt s) {

        if (s instanceof ExpandedStmt) {
            emitBody(((ExpandedStmt) s).statements);
            return;
        }

        if (s instanceof RawStmt) {
            emitRaw((RawStmt) s);
            return;
        }

        if (s instanceof TypeStmt || s instanceof PtrDeclStmt)
            return;

        if (s instanceof ReturnStmt) {
            ReturnStmt r = (ReturnStmt) s;
            if (r.value != null && !r.value.isEmpty())
                load(EAX, r.value);
            epilogue();
            return;
        }

        if (s instanceof IncDecStmt) {
            IncDecStmt i = (IncDecStmt) s;
            Integer d = slot.get(i.variable);
            if (d == null) throw err("unknown variable '" + i.variable + "'");
            load(EAX, i.variable);
            b(i.operator.contains("+") || i.operator.equals("inc") ? 0x40 : 0x48);
            storeVar(i.variable, EAX);
            return;
        }

        if (s instanceof StoreStmt) {
            StoreStmt st = (StoreStmt) s;
            int pr = bits16 ? 3 : ECX;
            int rm = bits16 ? 0x07 : 0x01;
            load(pr, st.pointer);
            load(EAX, st.value);
            int bits = isNumber(st.value) ? (bits16 ? 16 : 32) : bitsOf(typeOf(st.value));
            if (bits == 8) b(0x88, rm);
            else if (bits == 16 && !bits16) b(0x66, 0x89, rm);
            else b(0x89, rm);
            return;
        }

        if (s instanceof CallStmt) {
            CallStmt c = (CallStmt) s;
            emitCall(c.function, c.args, c.destination);
            return;
        }

        if (s instanceof RegAssignStmt) {
            emitAssign((RegAssignStmt) s);
            return;
        }

        throw err("unsupported statement in 32-bit mode: " + s.getClass().getSimpleName());
    }

    private String lbl(String name) {
        return cur.name + "::" + name;
    }

    private void emitCall(String fn, List<String> args, String dest) {

        String target = fn;

        boolean external = false;

        boolean libcRuntime =
            fn.equals("calloc") || fn.equals("free") || fn.equals("exit");

        if (!funcs.containsKey(fn) && objectMode && !(noLibc && libcRuntime)) {
            external = true;
            externs.add(fn);
        } else if (!funcs.containsKey(fn)) {
            switch (fn) {
                case "calloc": target = "__b_calloc"; break;
                case "free":   target = "__b_free";   break;
                case "exit":   target = "__b_exit";   break;
                default:
                    throw err(
                        "extern '" + fn + "' is not available in 32-bit flat mode"
                    );
            }
            builtinsUsed.add(target);
        }

        for (int i = args.size() - 1; i >= 0; i--) {
            load(EAX, args.get(i));
            b(0x50);
        }

        b(0xE8);
        if (external) {
            relocs.add(new Obj32.Reloc(len, 1, fn));
            i32(-4);
        } else {
            relFix.add(new Object[]{len, target, false});
            i32(0);
        }

        if (!args.isEmpty()) {
            b(0x81, 0xC4);
            i32((long) W * args.size());
        }

        if (dest != null && !dest.isEmpty() && !dest.equals("_") && slot.containsKey(dest))
            storeVar(dest, EAX);
    }

    private void emitAssign(RegAssignStmt s) {

        String d = s.name;
        String op = s.opcode == null ? "" : s.opcode.toLowerCase();
        List<String> a = s.args;

        switch (op) {

            case "label":
                labels.put(lbl(a.get(0)), len);
                return;

            case "asm": {
                String hex = a.get(0).substring(1);
                byte[] raw = new byte[hex.length() / 2];
                for (int i = 0; i < raw.length; i++)
                    raw[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
                String text = new String(raw, java.nio.charset.StandardCharsets.UTF_8);

                java.util.regex.Matcher m =
                    java.util.regex.Pattern.compile("@var:(\\w+)").matcher(text);
                StringBuffer sb = new StringBuffer();

                while (m.find()) {
                    Integer disp = slot.get(m.group(1));
                    if (disp == null)
                        throw err("unknown variable '" + m.group(1) + "' in asm block");
                    String mem = (bits16 ? "word [bp" : "dword [ebp")
                        + (disp < 0 ? "-" + (-disp) : "+" + disp) + "]";
                    m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(mem));
                }
                m.appendTail(sb);

                byte[] code = AsmBridge.assemble(sb.toString(), bits16 ? 16 : 32, org + len);
                for (byte x : code) b(x);
                return;
            }

            case "jmp":
            case "goto":
                b(0xE9);
                relFix.add(new Object[]{len, lbl(a.get(0)), true});
                i32(0);
                return;

            case "brf":
            case "brt": {
                if (a.size() != 4)
                    throw err(op + " expects 4 arguments");
                String cond = normOp(a.get(1));
                if (op.equals("brf")) cond = invert(cond);
                load(EAX, a.get(0));
                load(ECX, a.get(2));
                b(0x39, 0xC8);
                b(0x0F, jcc(cond, unsignedCompare(a.get(0))));
                relFix.add(new Object[]{len, lbl(a.get(3)), true});
                i32(0);
                return;
            }

            case "call":
                emitCall(a.get(0), a.subList(1, a.size()), d);
                return;

            case "mov":
                load(EAX, a.get(0));
                storeVar(d, EAX);
                return;

            case "addr": {
                String sym = a.get(0);
                if (slot.containsKey(sym)) {
                    ebpOperand(0x8D, EAX, slot.get(sym));
                } else
                    load(EAX, sym);
                storeVar(d, EAX);
                return;
            }

            case "load": {
                int pr = bits16 ? 3 : EAX;
                int rm = bits16 ? 0x07 : 0x00;
                load(pr, a.get(0));
                int bits = bitsOf(typeOf(d));
                if (bits == 8) b(0x0F, 0xB6, rm);
                else if (bits == 16 && !bits16) b(0x0F, 0xB7, rm);
                else b(0x8B, rm);
                storeVar(d, EAX);
                return;
            }

            case "cmp": {
                if (a.size() != 3) throw err("cmp expects 3 arguments");
                String cond = normOp(a.get(1));
                load(EAX, a.get(0));
                load(ECX, a.get(2));
                b(0x39, 0xC8);
                b(0x0F, setcc(cond, unsignedCompare(a.get(0))), 0xC0);
                b(0x0F, 0xB6, 0xC0);
                storeVar(d, EAX);
                return;
            }

            case "add": case "sub": case "mul": case "imul": case "multiply":
                load(EAX, d);
                load(ECX, a.get(0));
                if (op.equals("add")) b(0x01, 0xC8);
                else if (op.equals("sub")) b(0x29, 0xC8);
                else b(0x0F, 0xAF, 0xC1);
                storeVar(d, EAX);
                return;

            case "and": case "or": case "xor":
                load(EAX, d);
                load(ECX, a.get(0));
                if (op.equals("and")) b(0x21, 0xC8);
                else if (op.equals("or")) b(0x09, 0xC8);
                else b(0x31, 0xC8);
                storeVar(d, EAX);
                return;

            case "div": case "mod": {

                if (op.equals("mod") && isNumber(a.get(0))) {
                    long m = num(a.get(0));
                    if (m >= (1L << (8 * W)) && Long.bitCount(m) == 1) return;
                    if (m > 0 && Long.bitCount(m) == 1 && (unsigned(typeOf(d)) || m == 256 || m == 65536)) {
                        load(EAX, d);
                        b(0x25);
                        i32(m - 1);
                        storeVar(d, EAX);
                        return;
                    }
                }

                load(EAX, d);
                load(ECX, a.get(0));

                if (unsigned(typeOf(d))) {
                    b(0x31, 0xD2);
                    b(0xF7, 0xF1);
                } else {
                    b(0x99);
                    b(0xF7, 0xF9);
                }

                if (op.equals("mod")) b(0x89, 0xD0);
                storeVar(d, EAX);
                return;
            }

            case "shl": case "shr": case "sar": {

                if (!isNumber(a.get(0)))
                    throw err("shift count must be a constant in 32-bit mode");

                long n = num(a.get(0));

                if (n < 0 || n > 31)
                    throw err("shift count " + n + " is out of range (0-31)");

                load(EAX, d);

                boolean left = op.equals("shl");
                boolean logical = op.equals("shr") && unsigned(typeOf(d))
                   ;

                b(0xC1, left ? 0xE0 : logical ? 0xE8 : 0xF8, (int) n);
                storeVar(d, EAX);
                return;
            }

            default:
                throw err("unsupported operation '" + op + "' in 32-bit mode");
        }
    }

    public static boolean emitFrames = false;
    private boolean hasFrame = true;

    private void epilogue() {
        if (hasFrame) {
            b(0x89, 0xEC);
            b(0x5D);
        }
        b(0xC3);
    }

    private void emitRaw(RawStmt r) {
        String d = r.directive == null ? "" : r.directive.trim().toLowerCase();
        int size;
        switch (d) {
            case "raw8":  size = 1; break;
            case "raw16": size = 2; break;
            case "raw32": size = 4; break;
            case "raw64": size = 8; break;
            default: return;
        }
        long v = num(r.value.trim());
        for (int i = 0; i < size; i++)
            b((int) (v >>> (8 * i)) & 0xFF);
    }

    private void emitFunction(FunctionDecl f) {

        cur = f;
        funcOffset.put(f.name, len);

        if (f.naked) {
            slot = new LinkedHashMap<>();
            vtype = new HashMap<>();
            for (Stmt s : f.body) {
                if (s instanceof RawStmt) { emitRaw((RawStmt) s); continue; }
                if (s instanceof TypeStmt) continue;
                if (s instanceof RegAssignStmt && "asm".equalsIgnoreCase(((RegAssignStmt) s).opcode)) {
                    emitStmt(s);
                    continue;
                }
                throw err("naked function '" + f.name + "' can only contain asm and emit");
            }
            return;
        }

        slot = new LinkedHashMap<>();
        vtype = new HashMap<>();

        int i = 0;

        for (Param p : f.params) {
            slot.put(p.name, 2 * W + W * i++);
            vtype.put(p.name, p.type);
        }

        Set<String> names = new LinkedHashSet<>();
        collectVars(f.body, names, vtype);

        int k = 0;

        for (String n : names) {
            if (slot.containsKey(n)) continue;
            slot.put(n, -W * (++k));
            if (vtype.get(n) != null && vtype.get(n).toLowerCase().matches("(u?int64|qword)"))
                {                                                   }
        }

        frame = (W * k + 15) & ~15;

        hasFrame = emitFrames || !slot.isEmpty();

        if (hasFrame) {
        b(0x55);
        b(0x89, 0xE5);
        }

        if (frame > 0) {
            b(0x81, 0xEC);
            i32(frame);
        }

        emitBody(f.body);

        b(0x31, 0xC0);
        epilogue();
    }

    private void emitBuiltins() {

        if (builtinsUsed.contains("__b_calloc") && bits16) {

            funcOffset.put("__b_calloc", len);

            b(0x55, 0x89, 0xE5, 0x57);
            b(0x8B, 0x46, 0x04);
            b(0x0F, 0xAF, 0x46, 0x06);
            b(0x05, 0x01, 0x00);
            b(0x25, 0xFE, 0xFF);
            b(0x89, 0xC1);
            b(0xA1); absFix.add(new Object[]{len, "__heap_ptr"}); i32(0);
            b(0x89, 0xC7);
            b(0x89, 0xC2);
            b(0x01, 0xCA);
            b(0x89, 0x16); absFix.add(new Object[]{len, "__heap_ptr"}); i32(0);
            b(0x50);
            b(0x31, 0xC0);
            b(0xF3, 0xAA);
            b(0x58, 0x5F, 0x5D, 0xC3);

        } else if (builtinsUsed.contains("__b_calloc")) {

            funcOffset.put("__b_calloc", len);

            b(0x55, 0x89, 0xE5, 0x57);
            b(0x8B, 0x45, 0x08);
            b(0x0F, 0xAF, 0x45, 0x0C);
            b(0x83, 0xC0, 0x07);
            b(0x83, 0xE0, 0xF8);
            b(0x89, 0xC1);
            b(0xA1); absFix.add(new Object[]{len, "__heap_ptr"}); i32(0);
            b(0x89, 0xC7);
            b(0x8D, 0x14, 0x08);
            b(0x89, 0x15); absFix.add(new Object[]{len, "__heap_ptr"}); i32(0);
            b(0x50);
            b(0x31, 0xC0);
            b(0xF3, 0xAA);
            b(0x58, 0x5F, 0x5D, 0xC3);
        }

        if (builtinsUsed.contains("__b_free")) {
            funcOffset.put("__b_free", len);
            b(0x31, 0xC0, 0xC3);
        }

        if (builtinsUsed.contains("__b_exit")) {
            funcOffset.put("__b_exit", len);
            if (bits16 && org == 0x100) {

                b(0x89, 0xE3);
                b(0x8A, 0x47, 0x02);
                b(0xB4, 0x4C);
                b(0xCD, 0x21);
            } else if (bits16) {
                b(0xFA, 0xF4, 0xEB, 0xFD);
            } else if (!objectMode && org == 0x1000) {

                b(0x8B, 0x44, 0x24, 0x04);
                b(0xB4, 0x4C);
                b(0xCD, 0x21);
            } else if (linuxExit) {
                b(0x8B, 0x5C, 0x24, 0x04);
                b(0xB8); i32(1);
                b(0xCD, 0x80);
            } else
                b(0xFA, 0xF4, 0xEB, 0xFD);
        }
    }

    private void reachable(String name, Set<String> seen) {

        FunctionDecl f = funcs.get(name);

        if (f == null || !seen.add(name))
            return;

        walk(f.body, seen);
    }

    private void walk(List<Stmt> body, Set<String> seen) {

        for (Stmt s : body) {

            if (s instanceof ExpandedStmt)
                walk(((ExpandedStmt) s).statements, seen);

            else if (s instanceof CallStmt)
                reachable(((CallStmt) s).function, seen);

            else if (s instanceof RegAssignStmt) {
                RegAssignStmt r = (RegAssignStmt) s;
                if ("call".equalsIgnoreCase(r.opcode) && !r.args.isEmpty())
                    reachable(r.args.get(0), seen);
                if ("addr".equalsIgnoreCase(r.opcode) && !r.args.isEmpty())
                    reachable(r.args.get(0), seen);
                if ("mov".equalsIgnoreCase(r.opcode) && !r.args.isEmpty())
                    reachable(r.args.get(0), seen);
            }
        }
    }

    public byte[] compile() {

        FunctionDecl entry = null;

        for (FunctionDecl f : program.functions) {
            funcs.put(f.name, f);
            if (f.entry) entry = f;
        }

        if (entry == null && funcs.containsKey("_start"))
            entry = funcs.get("_start");

        if (entry == null)
            throw err("no entry point: mark a function with @Entry or name it _start");

        for (RawStmt r : program.raws) {
            if ("string".equals(r.directive) && r.stringName != null) {
                byte[] u = r.stringValue.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                byte[] z = Arrays.copyOf(u, u.length + 1);
                strings.put(r.stringName, z);
            }

            if ("gvar".equals(r.directive) && r.stringName != null) {
                byte[] d = new byte[Math.max(1, r.gvarSize)];
                long v = isNumber(r.value) ? num(r.value) : 0;
                for (int i = 0; i < d.length && i < 8; i++) d[i] = (byte) (v >>> (8 * i));
                strings.put(r.stringName, d);
                alignedData.add(r.stringName);
            }

            if ("gdata".equals(r.directive) && r.stringName != null && r.gdata != null) {
                strings.put(r.stringName, r.gdata);
                alignedData.add(r.stringName);
                if (r.gdataBias > 0) dataBias.put(r.stringName, r.gdataBias);
            }
        }

        Set<String> live = new LinkedHashSet<>();
        reachable(entry.name, live);

        if (objectMode)
            for (String fnName : funcs.keySet())
                if (Visibility.isExported(fnName))
                    reachable(fnName, live);

        emitFunction(entry);

        for (String n : live) {
            if (!n.equals(entry.name))
                emitFunction(funcs.get(n));
        }

        emitBuiltins();

        for (Map.Entry<String, byte[]> e : strings.entrySet()) {
            if (alignedData.contains(e.getKey()))
                while ((len % W) != 0) b(0);
            dataOffset.put(e.getKey(), len + dataBias.getOrDefault(e.getKey(), 0));
            for (byte x : e.getValue()) b(x);
        }

        while ((len & 3) != 0) b(0);

        dataOffset.put("__heap_ptr", len);
        heapPtrPos = len;
        i32(0);

        if (objectMode && !windows && builtinsUsed.contains("__b_calloc")) {

            dataSize = 4;
            bssSize = HEAP_BYTES;

        } else if (objectMode && builtinsUsed.contains("__b_calloc")) {

            int bufOff = len;

            for (int q = 0; q < HEAP_BYTES; q++) b(0);

            patch32(heapPtrPos, bufOff);
            relocs.add(new Obj32.Reloc(heapPtrPos, 0, null));

        } else {
            long heapStart = org + len;
            patch32(heapPtrPos, heapStart);
        }

        for (Object[] f : relFix) {
            int pos = (Integer) f[0];
            String name = (String) f[1];
            boolean isLabel = (Boolean) f[2];
            Integer target = isLabel ? labels.get(name) : funcOffset.get(name);
            if (target == null)
                throw err("undefined " + (isLabel ? "label" : "function") + " '" + name + "'");
            patch32(pos, target - (pos + W));
        }

        for (Object[] f : absFix) {
            int pos = (Integer) f[0];
            String name = (String) f[1];
            Integer target = dataOffset.get(name);
            if (target == null) target = funcOffset.get(name);
            if (target == null)
                throw err("undefined symbol '" + name + "'");
            if (objectMode && name.equals("__heap_ptr") && dataSize > 0) {
                patch32(pos, 0);
                relocs.add(new Obj32.Reloc(pos, 2, null));
            } else if (objectMode) {
                patch32(pos, target);
                relocs.add(new Obj32.Reloc(pos, 0, null));
            } else
                patch32(pos, org + target);
        }

        for (String fnName : funcOffset.keySet()) {
            if (fnName.startsWith("__b_"))
                continue;
            symbols.add(new Obj32.Sym(
                fnName, funcOffset.get(fnName),
                Visibility.isExported(fnName) || fnName.equals(entry.name)
            ));
        }

        for (String ex : externs)
            symbols.add(new Obj32.Sym(ex, -1, true));

        return Arrays.copyOf(buf, len);
    }
}

/*
 * ============================================================================
 * END OF FILE LICENSE NOTICE
 *
 * Ashley Project — ASH Compiler and Toolchain
 * Copyright (C) 2026 Mallionic Inc.
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * Licensed under GNU GPL version 3 only.
 * Refer to COPYING in the project root for the complete license text.
 *
 * END OF NOTICE
 * ============================================================================
 */
