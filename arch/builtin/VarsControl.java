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

package arch.builtin;

import java.util.*;
import Builder.Builder;

final class VarsControl {

    private final Vars core;

    VarsControl(Vars core) { this.core = core; }

    static final class Lbl {
        int pos = -1;
        final List<Integer> fix = new ArrayList<>();
    }

    private Builder b() { return core.builder; }

    Lbl newLabel() { return new Lbl(); }

    void bind(Lbl l) {
        if (l.pos >= 0) throw new IllegalStateException("Label ya definido");
        l.pos = b().size();
        for (int f : l.fix) b().patchIntLE(f, l.pos - (f + 4));
        l.fix.clear();
    }

    private void ref(Lbl l) {
        int at = b().size();
        b().file_write(0, 0, 0, 0);
        if (l.pos >= 0) b().patchIntLE(at, l.pos - (at + 4));
        else l.fix.add(at);
    }

    void jmp(Lbl l) { b().file_write(0xE9); ref(l); }

    void jcc(int cc, Lbl l) { b().file_write(0x0F, cc); ref(l); }

    private final Map<String, Lbl> named = new HashMap<>();

    private Lbl named(String n) {
        return named.computeIfAbsent(core.cleanName(n), k -> new Lbl());
    }

    void labelNamed(String n) { bind(named(n)); }

    void jmpNamed(String n) { jmp(named(n)); }

    void jccNamed(String cond, String label) {
        jcc(ccByName(cond), named(label));
    }

    private int ccByName(String c) {
        switch (c.toLowerCase(Locale.ROOT)) {
            case "je": case "jz": return 0x84;
            case "jne": case "jnz": return 0x85;
            case "jl": return 0x8C;
            case "jge": return 0x8D;
            case "jle": return 0x8E;
            case "jg": return 0x8F;
            case "jb": return 0x82;
            case "jae": return 0x83;
            case "jbe": return 0x86;
            case "ja": return 0x87;
            default: throw new IllegalArgumentException("Condicion JCC no soportada: " + c);
        }
    }

    void endFunction() {
        for (Map.Entry<String, Lbl> e : named.entrySet())
            if (e.getValue().pos < 0 && !e.getValue().fix.isEmpty())
                throw new IllegalStateException("Label sin definir: " + e.getKey());
        if (!stack.isEmpty())
            throw new IllegalStateException("Bloque sin cerrar: " + stack.peek().kind);
        named.clear();
    }

    private static final class Frame {
        final String kind;
        Lbl top, cont, end, next;
        boolean hasElse;
        String var;
        double step;
        Frame(String k) { kind = k; }
    }

    private final Deque<Frame> stack = new ArrayDeque<>();

    private Frame top(String kind) {
        Frame f = stack.peek();
        if (f == null || !f.kind.equals(kind))
            throw new IllegalStateException("'end" + kind + "' / else sin '" + kind + "' abierto");
        return f;
    }

    void If(String cond) {
        Frame f = new Frame("if");
        f.end = newLabel();
        f.next = newLabel();
        jump(parse(cond), false, f.next);
        stack.push(f);
    }

    void ElseIf(String cond) {
        Frame f = top("if");
        if (f.hasElse) throw new IllegalStateException("elseif despues de else");
        jmp(f.end);
        bind(f.next);
        f.next = newLabel();
        jump(parse(cond), false, f.next);
    }

    void Else() {
        Frame f = top("if");
        if (f.hasElse) throw new IllegalStateException("else duplicado");
        jmp(f.end);
        bind(f.next);
        f.next = null;
        f.hasElse = true;
    }

    void EndIf() {
        Frame f = top("if");
        if (f.next != null) bind(f.next);
        bind(f.end);
        stack.pop();
    }

    void While(String cond) {
        Frame f = new Frame("while");
        f.top = newLabel();
        f.end = newLabel();
        bind(f.top);
        jump(parse(cond), false, f.end);
        stack.push(f);
    }

    void EndWhile() {
        Frame f = top("while");
        jmp(f.top);
        bind(f.end);
        stack.pop();
    }

    void For(String var, String start, String end, String step) {
        double st = step == null ? 1 : Double.parseDouble(step);
        setStart(var, start);
        Frame f = new Frame("for");
        f.top = newLabel();
        f.cont = newLabel();
        f.end = newLabel();
        f.var = var;
        f.step = st;
        bind(f.top);
        Node c = new Node();
        c.k = "CMP"; c.l = var; c.op = st < 0 ? ">" : "<"; c.r = end;
        jump(c, false, f.end);
        stack.push(f);
    }

    void EndFor() {
        Frame f = top("for");
        bind(f.cont);
        Vars.Variable v = core.variable(f.var);
        if (v.isFloat()) {
            if (v.type == Vars.FLOAT32) core.Add(f.var, (float) f.step);
            else core.Add(f.var, (double) f.step);
        } else if (f.step == 1) core.Increment(f.var);
        else if (f.step == -1) core.Decrement(f.var);
        else core.Add(f.var, (long) f.step);
        jmp(f.top);
        bind(f.end);
        stack.pop();
    }

    private void setStart(String var, String start) {
        Vars.Variable v = core.variable(var);
        if (isSym(start)) { core.Set(var, start); return; }
        if (v.isFloat()) {
            double d = Double.parseDouble(start);
            if (v.type == Vars.FLOAT32) core.Set(var, (float) d);
            else core.Set(var, d);
        } else {
            Long n = num(start);
            if (n == null) throw new IllegalArgumentException("for: inicio invalido: " + start);
            core.Set(var, n.longValue());
        }
    }

    private Frame loop() {
        for (Frame f : stack)
            if (f.kind.equals("while") || f.kind.equals("for")) return f;
        throw new IllegalStateException("break/continue fuera de un loop");
    }

    void Break() { jmp(loop().end); }

    void Continue() {
        Frame f = loop();
        jmp(f.kind.equals("for") ? f.cont : f.top);
    }

    static final class Node {
        String k;
        Node a, b;
        String op, l, r;
    }

    private List<String> tokenize(String s) {
        List<String> t = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c) || c == ',') { i++; continue; }
            if (c == '(' || c == ')') { t.add("" + c); i++; continue; }
            String two = i + 1 < s.length() ? s.substring(i, i + 2) : "";
            if (two.equals("&&") || two.equals("||") || two.equals("==")
                || two.equals("!=") || two.equals("<=") || two.equals(">=")) {
                t.add(two); i += 2; continue;
            }
            if (c == '<' || c == '>' || c == '!') { t.add("" + c); i++; continue; }
            if (c == '=') { t.add("=="); i++; continue; }
            if (c == '&' || c == '|')
                throw new IllegalArgumentException("Operador invalido: " + c + " (usa && o ||)");
            int j = i;
            while (j < s.length()) {
                char d = s.charAt(j);
                if (Character.isWhitespace(d) || ",()&|=<>!".indexOf(d) >= 0) break;
                j++;
            }
            String w = s.substring(i, j);
            i = j;
            if (w.equals("and")) t.add("&&");
            else if (w.equals("or")) t.add("||");
            else if (w.equals("not")) t.add("!");
            else t.add(w);
        }
        return t;
    }

    private final class P {
        final List<String> t;
        int i = 0;
        P(List<String> t) { this.t = t; }
        String peek() { return i < t.size() ? t.get(i) : null; }

        Node or() {
            Node n = and();
            while ("||".equals(peek())) {
                i++;
                Node x = new Node(); x.k = "OR"; x.a = n; x.b = and(); n = x;
            }
            return n;
        }

        Node and() {
            Node n = not();
            while ("&&".equals(peek())) {
                i++;
                Node x = new Node(); x.k = "AND"; x.a = n; x.b = not(); n = x;
            }
            return n;
        }

        Node not() {
            if ("!".equals(peek())) {
                i++;
                Node x = new Node(); x.k = "NOT"; x.a = not(); return x;
            }
            return primary();
        }

        boolean isCmp(String s) {
            return "==".equals(s) || "!=".equals(s) || "<".equals(s)
                || "<=".equals(s) || ">".equals(s) || ">=".equals(s);
        }

        Node primary() {
            String s = peek();
            if (s == null) throw new IllegalArgumentException("Condicion incompleta");
            if (s.equals("(")) {
                i++;
                Node n = or();
                if (!")".equals(peek())) throw new IllegalArgumentException("Falta ')'");
                i++;
                return n;
            }
            i++;
            Node n = new Node();
            if (isCmp(peek())) {
                n.k = "CMP"; n.l = s; n.op = t.get(i++);
                if (peek() == null) throw new IllegalArgumentException("Falta operando derecho");
                n.r = t.get(i++);
            } else {
                n.k = "CMP"; n.l = s; n.op = "!="; n.r = "0";
            }
            return n;
        }
    }

    private Node parse(String cond) {
        P p = new P(tokenize(cond));
        Node n = p.or();
        if (p.peek() != null) throw new IllegalArgumentException("Sobra en condicion: " + p.peek());
        return n;
    }

    private void jump(Node n, boolean jumpWhen, Lbl target) {
        switch (n.k) {
            case "NOT":
                jump(n.a, !jumpWhen, target);
                return;
            case "AND":
                if (!jumpWhen) {
                    jump(n.a, false, target);
                    jump(n.b, false, target);
                } else {
                    Lbl skip = newLabel();
                    jump(n.a, false, skip);
                    jump(n.b, true, target);
                    bind(skip);
                }
                return;
            case "OR":
                if (jumpWhen) {
                    jump(n.a, true, target);
                    jump(n.b, true, target);
                } else {
                    Lbl ok = newLabel();
                    jump(n.a, true, ok);
                    jump(n.b, false, target);
                    bind(ok);
                }
                return;
            default: {
                boolean f = compare(n.l, n.r);
                int cc = cc(n.op, f);
                jcc(jumpWhen ? cc : inverse(cc), target);
            }
        }
    }

    private int cc(String op, boolean f) {
        switch (op) {
            case "==": return 0x84;
            case "!=": return 0x85;
            case "<":  return f ? 0x82 : 0x8C;
            case "<=": return f ? 0x86 : 0x8E;
            case ">":  return f ? 0x87 : 0x8F;
            case ">=": return f ? 0x83 : 0x8D;
            default: throw new IllegalArgumentException("Operador: " + op);
        }
    }

    private int inverse(int cc) {
        switch (cc) {
            case 0x84: return 0x85; case 0x85: return 0x84;
            case 0x8C: return 0x8D; case 0x8D: return 0x8C;
            case 0x8E: return 0x8F; case 0x8F: return 0x8E;
            case 0x82: return 0x83; case 0x83: return 0x82;
            case 0x86: return 0x87; case 0x87: return 0x86;
            default: throw new IllegalArgumentException("cc " + cc);
        }
    }

    void branch(String l, String op, String r, String label, boolean jumpWhen) {
        String o;
        switch (op) {
            case "eq": o = "=="; break;
            case "ne": o = "!="; break;
            case "lt": o = "<";  break;
            case "le": o = "<="; break;
            case "gt": o = ">";  break;
            case "ge": o = ">="; break;
            default: throw new IllegalArgumentException("Operador de salto: " + op);
        }
        boolean f = compare(l, r);
        int c = cc(o, f);
        jcc(jumpWhen ? c : inverse(c), named(label));
    }

    private int typeOfSym(String tok) {
        Vars.Variable v = core.localVariable(tok);
        if (v != null) return v.type;
        Vars.Parameter p = core.parameter(tok);
        if (p != null) return p.type;
        return -1;
    }

    private boolean isSym(String tok) { return typeOfSym(tok) >= 0; }

    private boolean compare(String l, String r) {
        int t = typeOfSym(l);
        if (t < 0) t = typeOfSym(r);
        if (t < 0) t = (l.contains(".") || r.contains(".")) ? Vars.FLOAT64 : Vars.QWORD;

        if (Vars.isFloatType(t)) {
            loadF(l, 0, t);
            loadF(r, 1, t);
            if (t == Vars.FLOAT64) b().file_write(0x66);
            b().file_write(0x0F, 0x2E, 0xC1);
            return true;
        }

        if (t == Vars.BYTE || t == Vars.WORD)
            t = Vars.DWORD;

        String a = core.temporaryInteger(t);
        String c = core.temporaryInteger2(t);
        loadI(l, a);
        loadI(r, c);
        core.alu.CMP(a, c);

        return isUnsignedOperand(l, t) || isUnsignedOperand(r, t);
    }

    private boolean isUnsignedOperand(String tok, int width) {
        if (!core.unsignedLookup.test(tok))
            return false;
        int ot = typeOfSym(tok);
        return ot == width && width >= Vars.DWORD;
    }

    private void loadI(String tok, String reg) {
        Long n = num(tok);
        if (n != null) { core.mov.MOV(reg, n.longValue()); return; }
        Vars.Variable v = core.localVariable(tok);
        if (v != null) { core.loadInteger(v, reg); return; }
        Vars.Parameter p = core.parameter(tok);
        if (p != null) { core.loadParameterInteger(p, reg); return; }
        throw new IllegalArgumentException("Operando desconocido: " + tok);
    }

    private void loadF(String tok, int xmm, int type) {
        Vars.Variable v = core.localVariable(tok);
        if (v != null) { core.loadFloat(v, "XMM" + xmm); return; }
        Vars.Parameter p = core.parameter(tok);
        if (p != null) { core.loadParameterFloat(p, "XMM" + xmm); return; }
        double d;
        try { d = Double.parseDouble(tok); }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException("Operando desconocido: " + tok);
        }
        if (type == Vars.FLOAT32) {
            core.mov.MOV("EAX", Float.floatToRawIntBits((float) d) & 0xFFFFFFFFL);
            core.emitMovD_XmmFromEax(xmm);
        } else {
            core.mov.MOV("RAX", Double.doubleToRawLongBits(d));
            core.emitMovQ_XmmFromRax(xmm);
        }
    }

    static Long num(String s) {
        try {
            s = s.trim();
            if (s.equals("true")) return 1L;
            if (s.equals("false")) return 0L;
            boolean neg = s.startsWith("-");
            String t = (neg || s.startsWith("+")) ? s.substring(1) : s;
            long v;
            if (t.startsWith("0x") || t.startsWith("0X")) v = Long.parseUnsignedLong(t.substring(2), 16);
            else if (t.startsWith("0b") || t.startsWith("0B")) v = Long.parseUnsignedLong(t.substring(2), 2);
            else v = Long.parseLong(t);
            return neg ? -v : v;
        } catch (NumberFormatException e) {
            return null;
        }
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
