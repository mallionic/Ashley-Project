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

package shasm;

import shasm.i18n.I18n;

import java.nio.charset.StandardCharsets;
import java.util.*;

public final class Expr {
    public interface Ctx {

        Long symbol(String name);

        default boolean external(String name) {
            return false;
        }

        long here();
        long sectionStart();
        Reg reg(String name);

        default String relocSection() {
            return null;
        }

        default String symbolTag(String name) {
            return null;
        }
    }

    private final String s;
    private int p;
    private final Ctx ctx;
    public boolean sawUndefined;
    public String undefinedName;
    private final boolean allowRegs;

    private Expr(String s, Ctx ctx, boolean allowRegs) { this.s = s; this.ctx = ctx; this.allowRegs = allowRegs; }

    public static Val eval(String text, Ctx ctx, boolean allowRegs) {
        Expr e = new Expr(text, ctx, allowRegs);
        e.ws();
        Val v = e.cond();
        e.ws();
        if (e.p < e.s.length()) throw new AsmError(I18n.tr("sintaxis de expresion invalida cerca de '%s'", e.s.substring(e.p)));
        if (e.sawUndefined) v.known = false;
        return v;
    }
    public static Val eval(String text, Ctx ctx) { return eval(text, ctx, false); }

    private void ws() { while (p < s.length() && Character.isWhitespace(s.charAt(p))) p++; }
    private boolean peek(String t) { ws(); return s.startsWith(t, p); }
    private boolean eat(String t) { if (peek(t)) { p += t.length(); return true; } return false; }

    private Val bin(Val a, Val b, long r) {
        Val v = new Val(r, a.known && b.known);
        return v;
    }
    private void noRegs(Val a, Val b) {
        if (a.hasRegs() || b.hasRegs()) throw new AsmError(I18n.tr("operacion invalida con registros en la expresion"));
    }

    private Val cond() {
        Val a = lor();
        return a;
    }
    private Val lor() {
        Val a = lxor();
        while (peek("||")) { p += 2; Val b = lxor(); a = bin(a, b, (a.v != 0 || b.v != 0) ? 1 : 0); }
        return a;
    }
    private Val lxor() {
        Val a = land();
        while (peek("^^")) { p += 2; Val b = land(); a = bin(a, b, ((a.v != 0) ^ (b.v != 0)) ? 1 : 0); }
        return a;
    }
    private Val land() {
        Val a = cmp();
        while (peek("&&")) { p += 2; Val b = cmp(); a = bin(a, b, (a.v != 0 && b.v != 0) ? 1 : 0); }
        return a;
    }
    private Val cmp() {
        Val a = bor();
        while (true) {
            ws();
            String op = null;
            for (String c : new String[]{"<=>", "<=", ">=", "==", "!=", "<>", "<", ">", "="}) if (s.startsWith(c, p)) {

                if ((c.equals("<") && s.startsWith("<<", p)) || (c.equals(">") && s.startsWith(">>", p))) continue;
                op = c; break;
            }
            if (op == null) return a;
            p += op.length();
            Val b = bor();
            noRegs(a, b);
            long r = switch (op) {
                case "<=>" -> Long.compare(a.v, b.v);
                case "<" -> a.v < b.v ? 1 : 0; case ">" -> a.v > b.v ? 1 : 0;
                case "<=" -> a.v <= b.v ? 1 : 0; case ">=" -> a.v >= b.v ? 1 : 0;
                case "!=", "<>" -> a.v != b.v ? 1 : 0;
                default -> a.v == b.v ? 1 : 0;
            };
            a = bin(a, b, r);
        }
    }
    private Val bor() {
        Val a = bxor();
        while (true) {
            ws();
            if (p < s.length() && s.charAt(p) == '|' && !s.startsWith("||", p)) { p++; Val b = bxor(); noRegs(a, b); a = bin(a, b, a.v | b.v); }
            else return a;
        }
    }
    private Val bxor() {
        Val a = band();
        while (true) {
            ws();
            if (p < s.length() && s.charAt(p) == '^' && !s.startsWith("^^", p)) { p++; Val b = band(); noRegs(a, b); a = bin(a, b, a.v ^ b.v); }
            else return a;
        }
    }
    private Val band() {
        Val a = shift();
        while (true) {
            ws();
            if (p < s.length() && s.charAt(p) == '&' && !s.startsWith("&&", p)) { p++; Val b = shift(); noRegs(a, b); a = bin(a, b, a.v & b.v); }
            else return a;
        }
    }
    private Val shift() {
        Val a = add();
        while (true) {
            if (peek("<<<")) { p += 3; Val b = add(); noRegs(a, b); a = bin(a, b, a.v << b.v); }
            else if (peek("<<")) { p += 2; Val b = add(); noRegs(a, b); a = bin(a, b, a.v << b.v); }
            else if (peek(">>>")) { p += 3; Val b = add(); noRegs(a, b); a = bin(a, b, a.v >> b.v); }
            else if (peek(">>")) { p += 2; Val b = add(); noRegs(a, b); a = bin(a, b, a.v >>> b.v); }
            else return a;
        }
    }
    private Val add() {
        Val a = mul();
        while (true) {
            ws();
            if (p < s.length() && s.charAt(p) == '+') { p++; Val b = mul(); a = addv(a, b, 1); }
            else if (p < s.length() && s.charAt(p) == '-') { p++; Val b = mul(); a = addv(a, b, -1); }
            else return a;
        }
    }
    private Val addv(Val a, Val b, int sign) {
        Val r = new Val(a.v + sign * b.v, a.known && b.known);
        for (var e : a.regs.entrySet()) r.regs.put(e.getKey(), e.getValue().clone());
        for (var e : b.regs.entrySet()) {
            long[] cur = r.regs.get(e.getKey());
            if (cur == null) r.regs.put(e.getKey(), new long[]{sign * e.getValue()[0], e.getValue()[1]});
            else { cur[0] += sign * e.getValue()[0]; cur[1] |= e.getValue()[1]; }
        }
        return r;
    }
    private Val mul() {
        Val a = unary();
        while (true) {
            ws();
            if (p >= s.length()) return a;
            char c = s.charAt(p);
            if (c == '*') {
                p++; Val b = unary();
                if (a.hasRegs() && b.hasRegs()) throw new AsmError(I18n.tr("no se pueden multiplicar dos registros"));
                if (a.hasRegs() || b.hasRegs()) {
                    Val r = a.hasRegs() ? a : b, k = a.hasRegs() ? b : a;
                    Val o = new Val(r.v * k.v, r.known && k.known);
                    for (var e : r.regs.entrySet()) o.regs.put(e.getKey(), new long[]{e.getValue()[0] * k.v, 1});
                    a = o;
                } else {
                    noRegs(a, b);
                    a = bin(a, b, a.v * b.v);
                }
            } else if (c == '/' && p + 1 < s.length() && s.charAt(p + 1) == '/') {
                p += 2; Val b = unary(); noRegs(a, b);
                a = bin(a, b, b.v == 0 ? zero() : a.v / b.v);
            } else if (c == '/') {
                p++; Val b = unary(); noRegs(a, b);
                a = bin(a, b, b.v == 0 ? zero() : Long.divideUnsigned(a.v, b.v));
            } else if (c == '%' && p + 1 < s.length() && s.charAt(p + 1) == '%') {
                p += 2; Val b = unary(); noRegs(a, b);
                a = bin(a, b, b.v == 0 ? zero() : a.v % b.v);
            } else if (c == '%' ) {
                p++; Val b = unary(); noRegs(a, b);
                a = bin(a, b, b.v == 0 ? zero() : Long.remainderUnsigned(a.v, b.v));
            } else return a;
        }
    }
    private long zero() { if (!sawUndefined) {                                                           } return 0; }

    private Val unary() {
        ws();
        if (p >= s.length()) throw new AsmError(I18n.tr("expresion incompleta"));
        char c = s.charAt(p);
        if (c == '-') { p++; Val a = unary(); Val r = new Val(-a.v, a.known); for (var e : a.regs.entrySet()) r.regs.put(e.getKey(), new long[]{-e.getValue()[0], e.getValue()[1]}); return r; }
        if (c == '+') { p++; return unary(); }
        if (c == '~') { p++; Val a = unary(); noRegs(a, a); return new Val(~a.v, a.known); }
        if (c == '!') { p++; Val a = unary(); noRegs(a, a); return new Val(a.v == 0 ? 1 : 0, a.known); }
        return primary();
    }

    private static boolean idStart(char c) { return Character.isLetter(c) || c == '_' || c == '.' || c == '@' || c == '?'; }
    private static boolean idPart(char c) { return Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '#' || c == '@' || c == '~' || c == '.' || c == '?'; }

    private Val primary() {
        ws();
        char c = s.charAt(p);
        if (c == '(') {
            p++; Val v = cond(); ws();
            if (!eat(")")) throw new AsmError(I18n.tr("falta ')' en la expresion"));
            return v;
        }
        if (c == '$') {
            if (s.startsWith("$$", p)) { p += 2; return new Val(ctx.sectionStart()); }
            if (p + 1 < s.length() && (idStart(s.charAt(p + 1)))) {
                p++; return symbolOrReg(ident(), true);
            }
            p++; return new Val(ctx.here());
        }
        if (c == '\'' || c == '"' || c == '`') return string(c);
        if (Character.isDigit(c)) return number();
        if (idStart(c)) {
            String id = ident();
            String low = id.toLowerCase();
            if (low.equals("seg")) { Val a = unary(); return new Val(0, true); }
            if (low.startsWith("__float") || low.equals("__utf16__") ) throw new AsmError(I18n.tr("funcion no soportada: %s", id));
            return symbolOrReg(id, false);
        }
        throw new AsmError(I18n.tr("sintaxis de expresion invalida cerca de '%s'", s.substring(p)));
    }

    private String ident() {
        int st = p;
        p++;
        while (p < s.length() && idPart(s.charAt(p))) p++;
        return s.substring(st, p);
    }

    private Val symbolOrReg(String id, boolean escaped) {
        if (allowRegs && !escaped) {
            Reg r = ctx.reg(id.toLowerCase());
            if (r != null) {
                Val v = new Val(0);
                v.regs.put(r, new long[]{1, 0});
                return v;
            }
        }
        Long sv = ctx.symbol(id);
        if (sv == null) {
            Reg r = ctx.reg(id.toLowerCase());
            if (r != null && !escaped) throw new AsmError(I18n.tr("registro '%s' no permitido en esta expresion", id));
            sawUndefined = true; if (undefinedName == null) undefinedName = id;
            return new Val(0, false);
        }
        return new Val(sv);
    }

    private Val number() {
        int st = p;
        while (p < s.length() && (Character.isLetterOrDigit(s.charAt(p)) || s.charAt(p) == '_' || s.charAt(p) == '.')) {
            char ch = s.charAt(p);

            p++;
        }
        String t = s.substring(st, p).replace("_", "");
        String lo = t.toLowerCase();
        try {
            if (lo.contains(".") || (lo.matches("[0-9]+e[0-9]*") && !lo.startsWith("0x")) ) {

                if (p < s.length() && (s.charAt(p) == '+' || s.charAt(p) == '-') && lo.endsWith("e")) {
                    int q = p + 1; while (q < s.length() && Character.isDigit(s.charAt(q))) q++;
                    t = s.substring(st, q); p = q;
                }
                Val v = new Val(0); v.isFloat = true; v.dbl = Double.parseDouble(t); return v;
            }
            if (lo.startsWith("0x") || lo.startsWith("0h")) return new Val(Long.parseUnsignedLong(lo.substring(2), 16));
            if (lo.startsWith("0b") || lo.startsWith("0y")) return new Val(Long.parseUnsignedLong(lo.substring(2), 2));
            if (lo.startsWith("0o") || lo.startsWith("0q")) return new Val(Long.parseUnsignedLong(lo.substring(2), 8));
            if (lo.startsWith("0d") || lo.startsWith("0t")) return new Val(Long.parseUnsignedLong(lo.substring(2), 10));
            char last = lo.charAt(lo.length() - 1);
            String body = lo.substring(0, lo.length() - 1);
            if (last == 'h' && body.matches("[0-9a-f]+")) return new Val(Long.parseUnsignedLong(body, 16));
            if ((last == 'b' || last == 'y') && body.matches("[01]+")) return new Val(Long.parseUnsignedLong(body, 2));
            if ((last == 'o' || last == 'q') && body.matches("[0-7]+")) return new Val(Long.parseUnsignedLong(body, 8));
            if (last == 'd' && body.matches("[0-9]+")) return new Val(Long.parseUnsignedLong(body, 10));
            if (lo.matches("[0-9]+")) return new Val(Long.parseUnsignedLong(lo, 10));
        } catch (NumberFormatException e) {                 }
        throw new AsmError(I18n.tr("constante numerica invalida '%s'", t));
    }

    private Val string(char q) {
        p++;
        ByteArrayBuilder b = new ByteArrayBuilder();
        while (true) {
            if (p >= s.length()) throw new AsmError(I18n.tr("cadena sin cerrar"));
            char c = s.charAt(p++);
            if (c == q) break;
            if (q == '`' && c == '\\' && p < s.length()) {
                char e = s.charAt(p++);
                switch (e) {
                    case 'n' -> b.add('\n'); case 't' -> b.add('\t'); case 'r' -> b.add('\r'); case '0' -> b.add(0);
                    case 'a' -> b.add(7); case 'b' -> b.add(8); case 'e' -> b.add(27); case 'f' -> b.add(12); case 'v' -> b.add(11);
                    case '\\' -> b.add('\\'); case '\'' -> b.add('\''); case '"' -> b.add('"'); case '`' -> b.add('`');
                    case 'x' -> { int st = p; while (p < s.length() && p - st < 2 && Character.digit(s.charAt(p), 16) >= 0) p++; b.add(Integer.parseInt(s.substring(st, p), 16)); }
                    default -> b.add(e);
                }
            } else {
                byte[] enc = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
                if (c >= 0x80 && c <= 0xFF && false) b.add(c); else for (byte x : enc) b.add(x & 0xFF);
            }
        }
        byte[] bytes = b.toArray();
        long v = 0;
        for (int i = 0; i < Math.min(8, bytes.length); i++) v |= (long) (bytes[i] & 0xFF) << (8 * i);
        Val r = new Val(v);
        r.isString = true; r.bytes = bytes;
        return r;
    }

    static final class ByteArrayBuilder {
        byte[] a = new byte[16]; int n;
        void add(int b) { if (n == a.length) a = Arrays.copyOf(a, n * 2); a[n++] = (byte) b; }
        byte[] toArray() { return Arrays.copyOf(a, n); }
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
