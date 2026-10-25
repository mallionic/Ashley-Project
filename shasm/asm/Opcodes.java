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

package shasm.asm;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.regex.*;
import shasm.*;
import shasm.i18n.I18n;

public abstract class Opcodes extends Directives {

    protected byte[] externalCall(String name) {
        return new byte[] {
            (byte) 0xE8,
            0, 0, 0, 0
        };
    }

    protected boolean isExternalName(String name) {
        if (name == null) return false;
        String q = qualify(name);
        return externSymbols.contains(q) || externSymbols.contains(name);
    }

    protected String externalName(String name) {
        if (name == null) return null;
        String q = qualify(name);
        if (externSymbols.contains(q)) return q;
        if (externSymbols.contains(name)) return name;
        return null;
    }

    protected Opcodes(InsnDb db, Map<String, Reg> regs, List<Preproc.Line> lines,
                      int defaultBits, List<Path> includeDirs) {
        super(db, regs, lines, defaultBits, includeDirs);
    }

    protected void captureReloc(Op o) {
        if (!objMode || refs.size() != 1 || o.text.indexOf('$') >= 0) return;

        String n = refs.iterator().next();

        RelocInfo ri = new RelocInfo();

        if (externSymbols.contains(n)) {
            ri.ext = n;
        } else {
            String sn = labelSecName(n);
            if (sn == null) return;

            ri.tsec = sn;
            ri.base = secStartPrev.getOrDefault(sn, 0L);
        }

        relocInfo.put(o, ri);
    }

    protected boolean farReloc(Op o) {
        RelocInfo r = relocInfo.get(o);
        return r != null && (r.ext != null || !r.tsec.equals(cur.name));
    }

    protected void value(List<Integer> body, List<Fix> fixes, Op o, long v,
                         boolean known, int n, boolean rel, boolean plt,
                         boolean signed) {
        Fix f = new Fix();
        f.off = body.size();
        f.size = n;
        f.target = v;
        f.rel = rel;
        f.known = known;
        f.plt = plt;
        f.signed = signed;

        RelocInfo ri = o == null ? null : relocInfo.get(o);

        if (ri != null && !(rel && ri.ext == null && ri.tsec.equals(cur.name))) {
            f.ext = ri.ext;
            f.tsec = ri.tsec;
            f.tbase = ri.base;
        }

        if (!rel && f.ext == null && f.tsec == null) {
            put(body, v, n);
            return;
        }

        for (int i = 0; i < n; i++) body.add(0);
        fixes.add(f);
    }

    protected static final Map<String, Integer> SIZEKW = Map.of(
        "byte", 8,
        "word", 16,
        "dword", 32,
        "qword", 64,
        "tword", 80,
        "oword", 128,
        "yword", 256,
        "zword", 512
    );

    protected Op parseOp(String s) {
        Op o = new Op();
        o.text = s;

        String t = s.strip();

        while (true) {
            Matcher m = Pattern.compile(
                "^(byte|word|dword|qword|tword|oword|yword|zword|short|near|far|to|strict|nosplit|ptr)\\b\\s*(.*)$",
                Pattern.CASE_INSENSITIVE
            ).matcher(t);

            if (!m.matches()) break;

            String kw = m.group(1).toLowerCase();
            String remain = m.group(2);

            if (remain.isEmpty()
                    || remain.startsWith(",")
                    || remain.startsWith("+")
                    || remain.startsWith("-") && false) {
                break;
            }

            if (SIZEKW.containsKey(kw)) {
                o.size = SIZEKW.get(kw);
            } else {
                switch (kw) {
                    case "short" -> o.short_ = true;
                    case "near" -> o.near = true;
                    case "far" -> o.far = true;
                    case "to" -> o.to = true;
                    case "strict" -> o.strict = true;
                    default -> { }
                }
            }

            t = remain;
        }

        Matcher sm = Pattern.compile("^st\\((\\d)\\)$",
                Pattern.CASE_INSENSITIVE).matcher(t);

        if (sm.matches()) t = "st" + sm.group(1);

        String lt = t.toLowerCase();
        Reg r = regs.get(lt);

        if (r != null) {
            o.kind = 'R';
            o.reg = r;
            return o;
        }

        Matcher segm = Pattern.compile(
            "^(es|cs|ss|ds|fs|gs)\\s*:\\s*(.*)$",
            Pattern.CASE_INSENSITIVE
        ).matcher(t);

        if (segm.matches() && segm.group(2).startsWith("[")) {
            Op m = parseMem(segm.group(2), o);
            m.seg = regs.get(segm.group(1).toLowerCase());
            return m;
        }

        if (t.startsWith("[") && t.endsWith("]") && matchingBracket(t))
            return parseMem(t, o);

        o.kind = 'I';

        refs.clear();
        undefinedSeen = null;

        o.val = evalVal(t, false);

        captureReloc(o);

        if (!objMode && !o.val.known && lastPass
                && undefinedSeen != null
                && externSymbols.contains(undefinedSeen)) {
            throw new AsmError(I18n.tr(
                "simbolo no definido: %s",
                undefinedSeen
            ));
        }

        return o;
    }

    protected static boolean matchingBracket(String t) {
        int d = 0;
        char q = 0;

        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);

            if (q != 0) {
                if (c == q) q = 0;
                continue;
            }

            if (c == '\'' || c == '"') q = c;
            else if (c == '[') d++;
            else if (c == ']') {
                d--;
                if (d == 0 && i != t.length() - 1) return false;
            }
        }

        return d == 0;
    }

    protected Op parseMem(String t, Op o) {
        o.kind = 'M';

        String in = t.substring(1, t.length() - 1).strip();

        while (true) {
            Matcher m = Pattern.compile(
                "^(rel|abs|nosplit|bnd|byte|word|dword|qword)\\b\\s+(.*)$",
                Pattern.CASE_INSENSITIVE
            ).matcher(in);

            if (!m.matches()) break;

            String kw = m.group(1).toLowerCase();

            if (kw.equals("rel")) o.rel = true;
            else if (kw.equals("abs")) o.abs = true;
            else if (SIZEKW.containsKey(kw)) o.size = SIZEKW.get(kw);

            in = m.group(2);
        }

        Matcher segm = Pattern.compile(
            "^(es|cs|ss|ds|fs|gs)\\s*:\\s*(.*)$",
            Pattern.CASE_INSENSITIVE
        ).matcher(in);

        if (segm.matches()) {
            o.seg = regs.get(segm.group(1).toLowerCase());
            in = segm.group(2);
        }

        refs.clear();
        undefinedSeen = null;

        Val v = evalVal(in, true);

        captureReloc(o);

        if (!objMode && !v.known && lastPass
                && undefinedSeen != null
                && externSymbols.contains(undefinedSeen)) {
            throw new AsmError(I18n.tr(
                "simbolo no definido: %s",
                undefinedSeen
            ));
        }

        List<Map.Entry<Reg, long[]>> e = new ArrayList<>();

        for (var en : v.regs.entrySet()) {
            if (en.getValue()[0] != 0) e.add(en);
        }

        Val d = new Val(v.v, v.known);
        o.disp = d;

        if (e.isEmpty()) return o;

        int ab = 0;

        for (var en : e) {
            Reg r = en.getKey();

            if (!r.cls().matches("reg(16|32|64)")) {
                throw new AsmError(I18n.tr(
                    "registro invalido en la direccion: %s",
                    r.name
                ));
            }

            if (ab == 0) ab = r.bits;
            else if (ab != r.bits) {
                throw new AsmError(I18n.tr(
                    "tamanos de registro mezclados en la direccion"
                ));
            }
        }

        o.addrBits = ab;

        if (e.size() == 1) {
            long c = e.get(0).getValue()[0];
            Reg r = e.get(0).getKey();

            if (c == 1) {
                o.base = r;
            } else if (c == 2 || c == 4 || c == 8) {
                o.index = r;
                o.scale = (int) c;
            } else if ((c == 3 || c == 5 || c == 9) && ab != 16) {
                o.base = r;
                o.index = r;
                o.scale = (int) c - 1;
            } else {
                throw new AsmError(I18n.tr("factor de escala invalido"));
            }
        } else if (e.size() == 2) {
            var a = e.get(0);
            var b = e.get(1);

            long ca = a.getValue()[0];
            long cb = b.getValue()[0];

            if (ca != 1 && cb != 1 && !(ab == 16)) {
                throw new AsmError(I18n.tr("direccion invalida"));
            }

            if (ab == 16) {
                if (ca != 1 || cb != 1) {
                    throw new AsmError(I18n.tr(
                        "direccionamiento de 16 bits invalido"
                    ));
                }

                o.base = a.getKey();
                o.index = b.getKey();
            } else {
                Reg base, idx;
                long sc;

                if (ca == 1 && cb == 1) {
                    if (b.getValue()[1] == 1) {
                        base = a.getKey();
                        idx = b.getKey();
                    } else if (a.getValue()[1] == 1) {
                        base = b.getKey();
                        idx = a.getKey();
                    } else {
                        base = a.getKey();
                        idx = b.getKey();
                    }

                    sc = 1;
                } else if (ca == 1) {
                    base = a.getKey();
                    idx = b.getKey();
                    sc = cb;
                } else {
                    base = b.getKey();
                    idx = a.getKey();
                    sc = ca;
                }

                if (sc != 1 && sc != 2 && sc != 4 && sc != 8) {
                    throw new AsmError(I18n.tr(
                        "factor de escala invalido"
                    ));
                }

                if (idx.num == 4 && idx.name.matches("e?r?sp")) {
                    if (sc != 1) {
                        throw new AsmError(I18n.tr(
                            "ESP no puede ser indice"
                        ));
                    }

                    Reg tmp = base;
                    base = idx;
                    idx = tmp;
                }

                o.base = base;
                o.index = idx;
                o.scale = (int) sc;
            }
        } else {
            throw new AsmError(I18n.tr(
                "demasiados registros en la direccion"
            ));
        }

        return o;
    }

    protected Op[] parseOperands(String args) {
        if (args.isBlank()) return new Op[0];

        List<Op> ops = new ArrayList<>();

        for (String a : splitArgs(args)) {

            int ci = topColon(a);

            if (ci > 0) {
                Op s = parseOp(a.substring(0, ci));
                Op f = parseOp(a.substring(ci + 1));

                s.colonAfter = true;

                if (s.far) {
                    f.far = true;
                    s.far = false;
                }

                ops.add(s);
                ops.add(f);
            } else {
                ops.add(parseOp(a));
            }
        }

        return ops.toArray(new Op[0]);
    }

    protected int topColon(String a) {
        int d = 0;
        char q = 0;

        for (int i = 0; i < a.length(); i++) {
            char c = a.charAt(i);

            if (q != 0) {
                if (c == q) q = 0;
                continue;
            }

            if (c == '\'' || c == '"') q = c;
            else if (c == '[' || c == '(') d++;
            else if (c == ']' || c == ')') d--;
            else if (c == ':' && d == 0) {
                String l = a.substring(0, i).strip().toLowerCase();

                if (l.matches("(es|cs|ss|ds|fs|gs)"))
                    return -1;

                if (l.matches(
                        "(byte|word|dword|qword|tword|oword|yword|zword|short|near|far)\\s+.*")
                        && false) {
                    return -1;
                }

                return i;
            }
        }

        return -1;
    }

    protected void instruction(String mn, String args, Pfx pf) {
        List<Tpl> cands = db.get(mn);

        if (cands == null) {
            throw new AsmError(I18n.tr(
                "instruccion desconocida '%s'",
                mn
            ));
        }

        Op[] ops = parseOperands(args);

        String site = "L" + curLineIdx + "#" + (siteSeq++ % 1);
        site = "L" + curLineIdx;

        Tpl chosen = null;

        outer:
        for (int pass = 0; pass < 2; pass++) {
            boolean strict = pass == 0;

            for (Tpl t : cands) {
                if (t.has("PSEUDO")) continue;
                if (banned.contains(site + ":" + t.id)) continue;

                if (matchTpl(t, ops, strict, pf)) {
                    chosen = t;
                    break outer;
                }
            }
        }

        if (chosen == null) {
            throw new AsmError(I18n.tr(
                "combinacion invalida de codigo de operacion y operandos (%s%s)",
                mn.toUpperCase(),
                args.isEmpty() ? "" : " " + args
            ));
        }

        emit(chosen, ops, pf, site);
    }

    protected boolean matchTpl(Tpl t, Op[] ops, boolean strict, Pfx pf) {
        if (t.ops.length != ops.length) return false;

        if (t.has("LONG") && bits != 64) return false;
        if (t.has("NOLONG") && bits == 64) return false;

        if (t.has("OSIZE")) {
            int w = t.tok("o16") ? 16
                    : t.tok("o32") ? 32
                    : t.tok("o64") ? 64
                    : 0;

            int want = pf.osz != 0 ? pf.osz : bits;

            boolean explicitSize = false;

            for (Op o : ops) {
                if (o.size != 0 && (o.kind == 'M' || o.kind == 'I'))
                    explicitSize = true;
            }

            if (w != 0 && !explicitSize && w != want)
                return false;
        }

        if (pf.osz != 0 && t.tok("odf"))
            return false;

        for (int i = 0; i < ops.length; i++) {
            if (t.colon[i] != ops[i].colonAfter)
                return false;

            if (!matchOp(t.ops[i], ops[i], strict, t, ops, i))
                return false;
        }

        if (t.tok("jcc8") || t.tok("jmp8")) {
            Op o = ops[0];

            if (!o.val.known || farReloc(o))
                return false;

            long end = here() + 2;
            long delta = o.val.v - end;

            if (delta < -128 || delta > 127)
                return false;
        }

        return true;
    }

    protected static boolean fitsSigned(long v, int bits) {
        if (bits >= 64) return true;
        if (bits <= 0) return false;

        long min = -(1L << (bits - 1));
        long max = (1L << (bits - 1)) - 1;

        return v >= min && v <= max;
    }

    protected static boolean fitsAny(long v, int bits) {
        if (bits >= 64) return true;
        if (bits <= 0) return false;

        long signedMin = -(1L << (bits - 1));
        long unsignedMax = (1L << bits) - 1;

        return v >= signedMin && v <= unsignedMax;
    }

    protected boolean matchOp(String tt, Op o, boolean strict,
                              Tpl t, Op[] ops, int idx) {
        if (tt == null || o == null) return false;

        String[] parts = tt.split("\\|");
        String base = normalizeOperandToken(parts[0], o);

        Set<String> mods = new HashSet<>();

        for (int i = 1; i < parts.length; i++) {
            String mod = parts[i].strip().toLowerCase(Locale.ROOT);
            if (!mod.isEmpty()) mods.add(mod);
        }

        if (mods.contains("to") != o.to) return false;
        if (mods.contains("far") != o.far) return false;
        if (mods.contains("short") != o.short_) return false;
        if (mods.contains("near") != o.near) return false;

        switch (base) {
            case "imm":
                return o.kind == 'I' && o.val != null;

            case "imm8":
            case "imm16":
            case "imm32":
            case "imm64": {
                if (o.kind != 'I' || o.val == null) return false;

                int n = Integer.parseInt(base.substring(3));

                if (n == 8 && relocInfo.containsKey(o))
                    return false;

                if (o.size != 0 && o.size != n) return false;

                if (!o.val.known)
                    return !strict;

                return fitsAny(o.val.v, n);
            }

            case "sbyteword16":
            case "sbytedword32":
            case "sbytedword64": {
                if (relocInfo.containsKey(o))
                    return false;

                if (o.kind != 'I' || o.val == null || o.strict)
                    return false;

                if (o.size != 0 && o.size != 8)
                    return false;

                if (!o.val.known)
                    return !strict;

                long v = o.val.v;

                if (v >= -128 && v <= 127)
                    return true;

                return switch (base) {
                    case "sbyteword16" ->
                        v >= 0xFF80L && v <= 0xFFFFL;

                    case "sbytedword32" ->
                        v >= 0xFFFFFF80L && v <= 0xFFFFFFFFL;

                    case "sbytedword64" ->
                        false;

                    default ->
                        false;
                };
            }

            case "udword64":
                if (o.kind != 'I' || o.val == null)
                    return false;

                if (o.size != 0 || o.strict)
                    return false;

                if (!o.val.known)
                    return !strict;

                return o.val.v >= 0 && o.val.v <= 0xFFFFFFFFL;

            case "sdword64":
                if (o.kind != 'I' || o.val == null)
                    return false;

                if (o.size != 0 && o.size != 32)
                    return false;

                if (!o.val.known)
                    return !strict;

                return fitsSigned(o.val.v, 32);

            case "unity":
            case "one":
                return o.kind == 'I'
                        && o.val != null
                        && o.val.known
                        && o.val.v == 1
                        && o.size == 0;

            case "mem":
                return o.kind == 'M';

            case "mem_offs":
                if (o.kind != 'M')
                    return false;

                if (!o.noRegsMem())
                    return false;

                if (bits == 64 || o.rel || o.addrBits != 0)
                    return false;

                if (ops != null && ops.length == 2) {
                    int j = idx == 0 ? 1 : 0;

                    if (ops[j].kind == 'R'
                            && ops[j].reg != null
                            && o.size != 0
                            && o.size != ops[j].reg.bits) {
                        return false;
                    }
                }

                return true;

            case "mem8":
            case "mem16":
            case "mem32":
            case "mem64":
            case "mem80":
            case "mem128":
            case "mem256":
            case "mem512": {
                if (o.kind != 'M')
                    return false;

                int n = Integer.parseInt(base.substring(3));

                if (o.size != 0)
                    return o.size == n;

                return otherFixesSize(o, n, t, ops, idx);
            }

            case "rm8":
            case "rm16":
            case "rm32":
            case "rm64": {
                int n = Integer.parseInt(base.substring(2));

                if (o.kind == 'R') {
                    return o.reg != null && o.reg.gprBits() == n;
                }

                if (o.kind != 'M')
                    return false;

                if (o.size != 0)
                    return o.size == n;

                return otherFixesSize(o, n, t, ops, idx);
            }

            case "rm_sel":
                if (o.kind == 'R')
                    return o.reg != null && o.reg.gprBits() == 16;

                return o.kind == 'M'
                        && (o.size == 0 || o.size == 16);

            case "reg8":
            case "reg16":
            case "reg32":
            case "reg64": {
                int n = Integer.parseInt(base.substring(3));

                return o.kind == 'R'
                        && o.reg != null
                        && o.reg.gprBits() == n;
            }

            case "reg32na":
                return o.kind == 'R'
                        && o.reg != null
                        && o.reg.bits == 32
                        && o.reg.num != 0;

            case "reg_sreg":
                return o.kind == 'R'
                        && o.reg != null
                        && "sreg".equalsIgnoreCase(o.reg.cls());

            case "reg_creg":
                return o.kind == 'R'
                        && o.reg != null
                        && "creg".equalsIgnoreCase(o.reg.cls());

            case "reg_dreg":
                return o.kind == 'R'
                        && o.reg != null
                        && "dreg".equalsIgnoreCase(o.reg.cls());

            case "reg_treg":
                return o.kind == 'R'
                        && o.reg != null
                        && "treg".equalsIgnoreCase(o.reg.cls());

            case "fpureg":
                return o.kind == 'R'
                        && o.reg != null
                        && "fpureg".equalsIgnoreCase(o.reg.cls());

            case "fpu0":
                return o.kind == 'R'
                        && o.reg != null
                        && "st0".equalsIgnoreCase(o.reg.name);

            case "mmxreg":
                return o.kind == 'R'
                        && o.reg != null
                        && "mmxreg".equalsIgnoreCase(o.reg.cls());

            case "xmmreg":
                return o.kind == 'R'
                        && o.reg != null
                        && "xmmreg".equalsIgnoreCase(o.reg.cls())
                        && o.reg.num < 16;

            case "xmm0":
                return o.kind == 'R'
                        && o.reg != null
                        && "xmm0".equalsIgnoreCase(o.reg.name);

            case "bndreg":
                return o.kind == 'R'
                        && o.reg != null
                        && "bndreg".equalsIgnoreCase(o.reg.cls());

            case "mmxrm":
            case "mmxrm64":
                if (o.kind == 'R') {
                    return o.reg != null
                            && "mmxreg".equalsIgnoreCase(o.reg.cls());
                }

                return o.kind == 'M'
                        && (o.size == 0 || o.size == 64);

            case "xmmrm":
            case "xmmrm128":
            case "xmmrm64":
            case "xmmrm32":
            case "xmmrm16": {
                int n = base.equals("xmmrm")
                        ? 128
                        : Integer.parseInt(base.substring(5));

                if (o.kind == 'R') {
                    return o.reg != null
                            && "xmmreg".equalsIgnoreCase(o.reg.cls())
                            && o.reg.num < 16;
                }

                return o.kind == 'M'
                        && (o.size == 0 || o.size == n);
            }

            default:
                if (base.startsWith("reg_")) {
                    String name = base.substring(4);

                    return o.kind == 'R'
                            && o.reg != null
                            && name.equalsIgnoreCase(o.reg.name);
                }

                return false;
        }
    }

    protected static String normalizeOperandToken(String t, Op o) {
        if (t == null)
            return "";

        t = t.trim().toLowerCase(Locale.ROOT);

        if (o == null)
            return t;

        int size = o.size;

        if (size == 0 && o.reg != null)
            size = o.reg.bits;

        return switch (t) {
            case "rm#" ->
                size > 0 ? "rm" + size : "rm#";

            case "reg#" ->
                size > 0 ? "reg" + size : "reg#";

            case "imm#" ->
                size > 0 ? "imm" + size : "imm#";

            case "imm##" ->
                size > 0 ? "imm" + size : "imm##";

            case "mem#" ->
                size > 0 ? "mem" + size : "mem#";

            case "m#" ->
                size > 0 ? "m" + size : "m#";

            default ->
                t;
        };
    }

    protected static String normalizeOperandToken(String t) {
        return t == null
                ? ""
                : t.trim().toLowerCase(Locale.ROOT);
    }

    protected boolean otherFixesSize(Op o, int n, Tpl t, Op[] ops, int idx) {
        if (t.tok("jmp8")) return true;

        for (int j = 0; j < ops.length; j++) {
            if (j == idx) continue;

            if (ops[j].kind == 'R'
                    && ops[j].reg.gprBits() == n
                    && t.ops[j].split("\\|")[0].matches(
                        "reg(8|16|32|64)|reg_(al|ax|eax|rax)|rm(8|16|32|64)")) {
                return true;
            }

            if (ops[j].size == n)
                return true;
        }

        return false;
    }

    protected int bitsOf(int n) {
        return n;
    }

    protected void emit(Tpl t, Op[] ops, Pfx pf, String site) {
        List<Integer> body = new ArrayList<>();
        List<Fix> fixes = new ArrayList<>();

        int rexPos = -1;
        int osz = 0;

        boolean nw = t.tok("nw") || t.tok("o64nw");

        boolean rexW = false;
        boolean rexR = false;
        boolean rexX = false;
        boolean rexB = false;
        boolean needRex = false;
        boolean noRex = false;

        boolean emit66 = false;
        boolean emit67 = false;

        int segPrefix = -1;
        int immNo = 0;
        Op relOp = null;

        for (String tk : t.tokens) {
            switch (tk) {
                case "o8" -> osz = 8;

                case "o16" -> {
                    osz = 16;
                    if (bits != 16) emit66 = true;
                }

                case "o32" -> {
                    osz = 32;
                    if (bits == 16) emit66 = true;
                    if (bits == 64 && nw) osz = 64;
                }

                case "o64" -> {
                    osz = 64;
                    if (!nw) rexW = true;
                }

                case "o64nw" -> osz = 64;

                case "nw" -> { }

                case "osz" -> {
                    osz = pf.osz != 0
                            ? pf.osz
                            : (bits == 16 ? 16 : 32);

                    if (pf.osz == 16 && bits != 16)
                        emit66 = true;

                    if (pf.osz == 32 && bits == 16)
                        emit66 = true;

                    if (pf.osz == 64)
                        rexW = true;
                }

                case "osm" -> {
                    osz = bits;
                    if (bits == 64 && !nw)
                        rexW = true;
                }

                case "odf" ->
                    osz = bits == 16 ? 16 : 32;

                case "a16" -> {
                    if (bits != 16) emit67 = true;
                }

                case "a32" -> {
                    if (bits != 32) emit67 = true;
                }

                case "a64", "asm", "adf" -> { }

                case "wait" ->
                    body.add(0x9B);

                case "rex.l" -> {

                    boolean hiCr = false;
                    for (Op o : ops) {
                        if (o != null && o.reg != null
                                && "creg".equalsIgnoreCase(o.reg.cls())
                                && o.reg.num >= 8)
                            hiCr = true;
                    }
                    if (hiCr) body.add(0xF0);
                }

                case "jlen" ->
                    body.add(bits == 16 ? 3 : 5);

                case "np", "norexb", "norexw", "nof3",
                     "norep", "nohi", "!osp", "repe", "m0",
                     "hle", "hlexr", "hlenl", "hlex", "optw",
                     "optd", "optq", "jcc8", "jmp8", "norexx",
                     "norexr" -> { }

                case "0f38" -> {
                    if (rexPos < 0) rexPos = body.size();
                    body.add(0x0F);
                    body.add(0x38);
                }

                case "0f3a" -> {
                    if (rexPos < 0) rexPos = body.size();
                    body.add(0x0F);
                    body.add(0x3A);
                }

                case "/r" -> {
                    Op r = byLetter(t, ops, 'r');
                    Op m = byLetter(t, ops, 'm');

                    if (m == null)
                        m = byLetter(t, ops, 'b');

                    if (r == null || m == null) {
                        throw new AsmError(I18n.tr(
                            "plantilla interna invalida (/r): %s",
                            t.src
                        ));
                    }

                    int[] fl = new int[4];

                    regField(r);

                    int regv = r.reg.num;

                    if (regv >= 16) {
                        throw new AsmError(I18n.tr(
                            "registro no soportado (APX): %s",
                            r.reg.name
                        ));
                    }

                    if (regv >= 8)
                        rexR = true;

                    byteRegFlags(r, fl);

                    if (fl[0] == 1) needRex = true;
                    if (fl[1] == 1) noRex = true;

                    ModRM mr = modrm(
                        regv & 7,
                        m,
                        pf,
                        body,
                        fixes,
                        t
                    );

                    rexX |= mr.x;
                    rexB |= mr.b;
                    needRex |= mr.needRex;
                    noRex |= mr.noRex;

                    if (mr.addr67) emit67 = true;
                    if (mr.seg >= 0) segPrefix = mr.seg;
                }

                default -> {
                    if (tk.matches("/[0-7]")) {
                        Op m = byLetter(t, ops, 'm');

                        if (m == null)
                            m = byLetter(t, ops, 'b');

                        if (m == null) {
                            throw new AsmError(I18n.tr(
                                "plantilla interna invalida (/n): %s",
                                t.src
                            ));
                        }

                        ModRM mr = modrm(
                            tk.charAt(1) - '0',
                            m,
                            pf,
                            body,
                            fixes,
                            t
                        );

                        rexX |= mr.x;
                        rexB |= mr.b;
                        needRex |= mr.needRex;
                        noRex |= mr.noRex;

                        if (mr.addr67) emit67 = true;
                        if (mr.seg >= 0) segPrefix = mr.seg;

                    } else if (tk.matches("[0-9a-f]{2}\\+r")) {
                        Op r = byLetter(t, ops, 'r');

                        if (r == null || r.kind != 'R') {
                            throw new AsmError(I18n.tr(
                                "plantilla interna invalida (+r): %s",
                                t.src
                            ));
                        }

                        if (r.reg.num >= 16) {
                            throw new AsmError(I18n.tr(
                                "registro no soportado (APX)"
                            ));
                        }

                        if (rexPos < 0)
                            rexPos = body.size();

                        body.add(
                            Integer.parseInt(tk.substring(0, 2), 16)
                            | (r.reg.num & 7)
                        );

                        if (r.reg.num >= 8)
                            rexB = true;

                        int[] fl = new int[4];
                        byteRegFlags(r, fl);

                        if (fl[0] == 1) needRex = true;
                        if (fl[1] == 1) noRex = true;

                    } else if (tk.matches("[0-9a-f]{2}")) {
                        int b = Integer.parseInt(tk, 16);

                        boolean pre = rexPos < 0
                                && (b == 0x66
                                || b == 0x67
                                || b == 0xF2
                                || b == 0xF3);

                        if (!pre && rexPos < 0)
                            rexPos = body.size();

                        body.add(b);

                    } else if (tk.equals("f3i")) {
                        body.add(0xF3);

                    } else if (tk.matches(
                            "i[bwdq](,[su])?|iwd|iwdq|ibn|rel8|rel|rel16|rel32|seg")) {

                        immToken(
                            tk,
                            t,
                            ops,
                            body,
                            fixes,
                            osz,
                            pf,
                            immNo
                        );

                        if (!tk.equals("seg"))
                            immNo++;

                        if (tk.startsWith("rel"))
                            relOp = byLetter(t, ops, 'i');

                    } else if (tk.matches("ib\\^[0-9a-f]{2}")) {
                        Op i = immOp(t, ops, immNo++);

                        body.add(
                            (int) (
                                i.val.v
                                ^ Integer.parseInt(
                                    tk.substring(3),
                                    16
                                )
                            ) & 0xFF
                        );

                    } else {
                        throw new AsmError(I18n.tr(
                            "token de codificacion no soportado '%s' en %s",
                            tk,
                            t.src
                        ));
                    }
                }
            }
        }

        if (segPrefix < 0) {
            for (Op o : ops) {
                if (o.kind == 'M' && o.seg != null) {
                    segPrefix = switch (o.seg.name) {
                        case "es" -> 0x26;
                        case "cs" -> 0x2E;
                        case "ss" -> 0x36;
                        case "ds" -> 0x3E;
                        case "fs" -> 0x64;
                        case "gs" -> 0x65;
                        default -> -1;
                    };
                }
            }
        }

        if (bits == 64) {
            int rex = 0x40
                    | (rexW ? 8 : 0)
                    | (rexR ? 4 : 0)
                    | (rexX ? 2 : 0)
                    | (rexB ? 1 : 0);

            if (rex != 0x40 || needRex) {
                if (noRex) {
                    throw new AsmError(I18n.tr(
                        "no se puede usar AH/BH/CH/DH con un prefijo REX"
                    ));
                }

                if (rexPos < 0)
                    rexPos = 0;

                body.add(rexPos, rex);

                for (Fix f : fixes) {
                    if (f.off >= rexPos)
                        f.off++;
                }
            }
        } else if (rexW || rexR || rexX || rexB) {
            throw new AsmError(I18n.tr(
                "registro o instruccion de 64 bits solo valido en modo BITS 64"
            ));
        }

        ByteArrayOutputStream pre = new ByteArrayOutputStream();

        byte[] ub = pf.bytes.toByteArray();
        pre.write(ub, 0, ub.length);

        if (segPrefix >= 0)
            pre.write(segPrefix);

        if (emit67
                || (pf.asz != 0
                && pf.asz != bits
                && !hasMem(ops)
                && !t.tok("a16")
                && !t.tok("a32")
                && !t.tok("a64"))) {
            pre.write(0x67);
        }

        if (emit66) {
            pre.write(0x66);
        } else if (pf.osz == 16
                && bits != 16
                && osz != 16
                && !t.tok("osz")) {
            pre.write(0x66);
        } else if (pf.osz == 32
                && bits == 16
                && osz != 32
                && !t.tok("osz")) {
            pre.write(0x66);
        }

        byte[] prefix = pre.toByteArray();

        int total = prefix.length + body.size();
        long end = here() + total;

        byte[] outb = new byte[total];

        for (int i = 0; i < prefix.length; i++)
            outb[i] = prefix[i];

        for (int i = 0; i < body.size(); i++)
            outb[prefix.length + i] = (byte) (int) body.get(i);

        for (Fix f : fixes) {
            int off = f.off + prefix.length;

            if (f.ext != null || f.tsec != null) {
                long p = here() + off;
                long tgt = f.tsec != null
                        ? f.target - f.tbase
                        : f.target;

                long addend = f.rel
                        ? tgt - (end - p)
                        : tgt;

                int trail = f.rel
                        ? (int) (end - p) - f.size
                        : 0;

                recordReloc(
                    f,
                    cur.size() + off,
                    addend,
                    trail
                );

                continue;
            }

            long v = f.rel
                    ? f.target - end
                    : f.target;

            if (f.rel && f.size == 1) {
                if (f.known && (v < -128 || v > 127)) {
                    boolean explicitShort =
                            ops.length > 0 && ops[0].short_;

                    if (lastPass) {
                        throw new AsmError(I18n.tr(
                            "salto corto fuera de rango (%s bytes)",
                            v
                        ));
                    }
                }

                final Op ro = relOp;
                final long here0 = here();
                final int tlen = total;

                if (!t.tok("jcc8") && !t.tok("jmp8")) {

                }
            }

            for (int i = 0; i < f.size; i++)
                outb[off + i] = (byte) (v >>> (8 * i));
        }

        if (t.tok("jcc8") || t.tok("jmp8")) {
            final Op ro = ops[0];
            final long here0 = here();

            checks.add(new Check(
                site + ":" + t.id,
                () -> {
                    Long d = reevalTarget(ro);

                    if (d == null) return true;

                    long delta = d - (here0 + 2);

                    return delta >= -128 && delta <= 127;
                }
            ));
        }

        for (int i = 0; i < ops.length; i++) {
            String b0 = t.ops[i].split("\\|")[0];

            if (ops[i].kind == 'I'
                    && ops[i].val != null
                    && ops[i].val.known
                    && ops[i].size == 0
                    && b0.matches("sbyte.*|imm8|udword64|sdword64")) {

                final Op ro = ops[i];
                final String b = b0;

                checks.add(new Check(
                    site + ":" + t.id,
                    () -> {
                        Long d = reevalTarget(ro);

                        if (d == null) return true;

                        long v = d;

                        return switch (b) {
                            case "imm8" ->
                                fitsAny(v, 8);

                            case "udword64" ->
                                v >= 0 && v <= 0xFFFFFFFFL;

                            case "sdword64" ->
                                fitsSigned(v, 32);

                            default ->
                                (v >= -128 && v <= 127)
                                || (b.equals("sbyteword16")
                                    && v >= 0xFF80L
                                    && v <= 0xFFFFL)
                                || (b.equals("sbytedword32")
                                    && v >= 0xFFFFFF80L
                                    && v <= 0xFFFFFFFFL)
                                || (b.equals("sbytedword64")
                                    && v < 0
                                    && v >= -128);
                        };
                    }
                ));
            }
        }

        cur.buf.write(outb, 0, outb.length);

        if (cur.nobits) {
            throw new AsmError(I18n.tr(
                "no se puede emitir codigo en una seccion NOBITS"
            ));
        }
    }

    protected boolean hasMem(Op[] ops) {
        for (Op o : ops)
            if (o.kind == 'M') return true;

        return false;
    }

    protected Long reevalTarget(Op o) {
        try {
            Val v = Expr.eval(
                o.text.replaceAll(
                    "^(?i)(byte|word|dword|qword|short|near|far|strict|to)\\s+",
                    ""
                ),
                this,
                false
            );

            return v.known ? v.v : null;
        } catch (AsmError e) {
            return null;
        }
    }

    protected void regField(Op r) {
        if (r.kind != 'R') {
            throw new AsmError(I18n.tr(
                "operando de registro esperado"
            ));
        }
    }

    protected void byteRegFlags(Op r, int[] fl) {
        if (r.kind == 'R' && r.reg.cls().equals("reg8")) {
            if (r.reg.rexOnly) fl[0] = 1;
            if (r.reg.high) fl[1] = 1;
        }
    }

    protected Op byLetter(Tpl t, Op[] ops, char c) {
        for (int i = 0; i < ops.length; i++) {
            if (t.slots[i].indexOf(c) >= 0)
                return ops[i];
        }

        if (t.dropLetter == c) {
            for (int i = 0; i < ops.length; i++) {
                if (t.slots[i].indexOf(t.srcLetter) >= 0)
                    return ops[i];
            }
        }

        return null;
    }

    protected Op immOp(Tpl t, Op[] ops, int n) {
        Op o = byLetter(
            t,
            ops,
            n == 0 ? 'i' : 'j'
        );

        if (o == null)
            o = byLetter(t, ops, 'i');

        if (o == null) {
            throw new AsmError(I18n.tr(
                "plantilla interna: sin operando inmediato: %s",
                t.src
            ));
        }

        return o;
    }

    protected void immToken(String tk, Tpl t, Op[] ops,
                            List<Integer> body, List<Fix> fixes,
                            int osz, Pfx pf, int immNo) {

        if (tk.equals("seg")) {
            Op j = byLetter(t, ops, 'j');

            if (j == null) {
                throw new AsmError(I18n.tr(
                    "plantilla interna: seg sin operando"
                ));
            }

            put(body, j.val.v, 2);
            return;
        }

        Op o = immOp(t, ops, immNo);

        long v = o.kind == 'M'
                ? (o.disp == null ? 0 : o.disp.v)
                : o.val.v;

        boolean known = o.kind == 'M'
                ? (o.disp == null || o.disp.known)
                : o.val.known;

        switch (tk) {
            case "ib", "ib,s", "ib,u", "ibn" -> {
                checkImm(v, known, 8);
                put(body, v, 1);
            }

            case "iw" -> {
                checkImm(v, known, 16);
                value(
                    body,
                    fixes,
                    o,
                    v,
                    known,
                    2,
                    false,
                    false,
                    false
                );
            }

            case "id", "id,s", "id,u" -> {
                checkImm(v, known, 32);

                value(
                    body,
                    fixes,
                    o,
                    v,
                    known,
                    4,
                    false,
                    false,
                    bits == 64 && (osz == 64 || tk.equals("id,s"))
                );
            }

            case "iq" ->
                value(
                    body,
                    fixes,
                    o,
                    v,
                    known,
                    8,
                    false,
                    false,
                    false
                );

            case "iwd" -> {
                int n = (osz == 0 ? bits : osz) == 16
                        ? 2
                        : 4;

                value(
                    body,
                    fixes,
                    o,
                    v,
                    known,
                    n,
                    false,
                    false,
                    false
                );
            }

            case "iwdq" -> {
                int ab = pf.asz != 0
                        ? pf.asz
                        : bits;

                value(
                    body,
                    fixes,
                    o,
                    v,
                    known,
                    ab / 8,
                    false,
                    false,
                    false
                );
            }

            case "rel8", "rel", "rel16", "rel32" -> {
                int n = tk.equals("rel8")
                        ? 1
                        : tk.equals("rel16")
                            ? 2
                            : tk.equals("rel32")
                                ? 4
                                : ((osz == 0 ? bits : osz) == 16
                                    ? 2
                                    : 4);

                value(
                    body,
                    fixes,
                    o,
                    v,
                    known,
                    n,
                    true,
                    true,
                    false
                );
            }

            default ->
                throw new AsmError(I18n.tr(
                    "token inmediato desconocido %s",
                    tk
                ));
        }
    }

    protected void checkImm(long v, boolean known, int bitsN) {
        if (!lastPass || !known) return;

        if (!fitsAny(v, bitsN)) {
            warnings.add(I18n.tr(
                "%s:%s: aviso: el valor %s excede los limites de %s bits",
                curLine.file,
                curLine.no,
                v,
                bitsN
            ));
        }
    }

    protected static void put(List<Integer> b, long v, int n) {
        for (int i = 0; i < n; i++)
            b.add((int) (v >>> (8 * i)) & 0xFF);
    }

    static final class ModRM {
        boolean x;
        boolean b;
        boolean needRex;
        boolean noRex;
        boolean addr67;
        int seg = -1;
    }

    protected ModRM modrm(int regField, Op m, Pfx pf,
                          List<Integer> body, List<Fix> fixes, Tpl t) {

        ModRM r = new ModRM();

        if (m.kind == 'R') {
            if (m.reg.num >= 16) {
                throw new AsmError(I18n.tr(
                    "registro no soportado (APX): %s",
                    m.reg.name
                ));
            }

            body.add(
                0xC0
                | (regField << 3)
                | (m.reg.num & 7)
            );

            if (m.reg.num >= 8)
                r.b = true;

            int[] fl = new int[4];
            byteRegFlags(m, fl);

            r.needRex = fl[0] == 1;
            r.noRex = fl[1] == 1;

            return r;
        }

        RelocInfo ri0 = relocInfo.get(m);

        if (m.kind == 'I') {

            Op mm = new Op();
            mm.kind = 'M';
            mm.disp = m.val;
            mm.size = m.size;
            m = mm;

            if (ri0 != null)
                relocInfo.put(mm, ri0);
        }

        if (m.seg != null) {
            r.seg = switch (m.seg.name) {
                case "es" -> 0x26;
                case "cs" -> 0x2E;
                case "ss" -> 0x36;
                case "ds" -> 0x3E;
                case "fs" -> 0x64;
                case "gs" -> 0x65;
                default -> -1;
            };
        }

        int ab = m.addrBits != 0
                ? m.addrBits
                : (pf.asz != 0 ? pf.asz : bits);

        if (m.addrBits != 0
                && pf.asz != 0
                && pf.asz != m.addrBits) {
            throw new AsmError(I18n.tr(
                "el prefijo de tamano de direccion no coincide con los registros"
            ));
        }

        if (ab == 16 && bits == 64) {
            throw new AsmError(I18n.tr(
                "direccionamiento de 16 bits no valido en modo 64 bits"
            ));
        }

        if (ab != bits)
            r.addr67 = true;

        long disp = m.disp == null ? 0 : m.disp.v;
        boolean dknown = m.disp == null || m.disp.known;

        if (relocInfo.containsKey(m))
            dknown = false;

        final String dkey = "L" + curLineIdx + ":d8";

        if (ab == 16) {
            int rm;

            Reg b = m.base;
            Reg i = m.index;

            if (b == null && i == null) {
                body.add((regField << 3) | 6);
                put(body, disp, 2);
                return r;
            }

            String bn = b == null ? "" : b.name;
            String in = i == null ? "" : i.name;

            String key = bn + "+" + in;

            rm = switch (key) {
                case "bx+si", "si+bx" -> 0;
                case "bx+di", "di+bx" -> 1;
                case "bp+si", "si+bp" -> 2;
                case "bp+di", "di+bp" -> 3;
                case "si+" -> 4;
                case "di+" -> 5;
                case "bp+" -> 6;
                case "bx+" -> 7;
                default -> -1;
            };

            if (rm < 0 && b != null && i == null) {

            }

            if (rm < 0) {
                throw new AsmError(I18n.tr(
                    "combinacion de registros de direccion de 16 bits invalida"
                ));
            }

            boolean useD8 =
                    dknown
                    && disp >= -128
                    && disp <= 127
                    && !banned.contains(dkey)
                    && m.size >= 0;

            if (disp == 0 && dknown && rm != 6) {
                body.add((regField << 3) | rm);
            } else if (useD8) {
                body.add(
                    0x40
                    | (regField << 3)
                    | rm
                );

                put(body, disp, 1);
                addDispCheck(m, dkey);
            } else {
                body.add(
                    0x80
                    | (regField << 3)
                    | rm
                );

                put(body, disp, 2);
            }

            return r;
        }

        Reg b = m.base;
        Reg i = m.index;

        boolean rip = false;

        if (b == null && i == null) {
            if (bits == 64 && (m.rel || (defaultRel && !m.abs)))
                rip = true;
        }

        if (rip) {
            body.add((regField << 3) | 5);

            value(
                body,
                fixes,
                m,
                disp,
                dknown,
                4,
                true,
                false,
                false
            );

            return r;
        }

        if (b == null && i == null) {
            if (bits == 64) {
                body.add((regField << 3) | 4);
                body.add(0x25);

                value(
                    body,
                    fixes,
                    m,
                    disp,
                    dknown,
                    4,
                    false,
                    false,
                    true
                );
            } else {
                body.add((regField << 3) | 5);

                value(
                    body,
                    fixes,
                    m,
                    disp,
                    dknown,
                    4,
                    false,
                    false,
                    false
                );
            }

            return r;
        }

        if ((b != null && b.num >= 16)
                || (i != null && i.num >= 16)) {
            throw new AsmError(I18n.tr(
                "registro no soportado (APX)"
            ));
        }

        boolean needSib =
                i != null
                || (b != null && (b.num & 7) == 4);

        boolean noBase = b == null;

        int mod;

        boolean d8 =
                dknown
                && disp >= -128
                && disp <= 127
                && !banned.contains(dkey);

        if (noBase)
            mod = 0;
        else if (disp == 0 && dknown && (b.num & 7) != 5)
            mod = 0;
        else if (d8)
            mod = 1;
        else
            mod = 2;

        int rmField = needSib
                ? 4
                : (b.num & 7);

        body.add(
            (mod << 6)
            | (regField << 3)
            | rmField
        );

        if (needSib) {
            int ss = switch (m.scale) {
                case 1 -> 0;
                case 2 -> 1;
                case 4 -> 2;
                case 8 -> 3;
                default -> 0;
            };

            int idx = i == null
                    ? 4
                    : (i.num & 7);

            int bs = noBase
                    ? 5
                    : (b.num & 7);

            body.add(
                (ss << 6)
                | (idx << 3)
                | bs
            );

            if (i != null && i.num >= 8)
                r.x = true;
        }

        if (b != null && b.num >= 8)
            r.b = true;

        if (noBase) {
            value(
                body,
                fixes,
                m,
                disp,
                dknown,
                4,
                false,
                false,
                bits == 64
            );
        } else if (mod == 1) {
            put(body, disp, 1);
            addDispCheck(m, dkey);
        } else if (mod == 2) {
            value(
                body,
                fixes,
                m,
                disp,
                dknown,
                4,
                false,
                false,
                bits == 64
            );
        }

        return r;
    }

    protected void addDispCheck(Op m, String dkey) {
        if (m.disp == null || m.disp.regs == null)
            return;

        final String txt = m.text;

        checks.add(new Check(
            dkey,
            () -> {
                try {
                    String in = txt.substring(
                        txt.indexOf('[') + 1,
                        txt.lastIndexOf(']')
                    )
                    .replaceAll(
                        "^(?i)\\s*(rel|abs|nosplit|bnd)\\s+",
                        ""
                    )
                    .replaceAll(
                        "^(?i)\\s*(es|cs|ss|ds|fs|gs)\\s*:",
                        ""
                    );

                    Val v = Expr.eval(in, this, true);

                    return !v.known
                            || (v.v >= -128 && v.v <= 127);

                } catch (AsmError e) {
                    return true;
                }
            }
        ));
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
