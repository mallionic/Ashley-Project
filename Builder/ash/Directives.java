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

package Builder.ash;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Directives {

    private Directives() {
    }

    public static String FORMAT = "ashir";
    public static boolean NO_LIBC = false;
    public static int FORCED_BITS = 0;

    private static final Pattern BITS =
        Pattern.compile("@bits\\s*\\(\\s*(16|32|64)\\s*\\)");

    private static IllegalArgumentException err(int line, String m) {
        return new IllegalArgumentException("line " + line + ": " + m);
    }

    private static final class Frame {
        boolean parentActive;
        boolean taken;
        boolean active;
        boolean sawElse;
        int line;
    }

    private static final Pattern NOLIBC =
        Pattern.compile("@nolibc(?:\\s*\\(\\s*(true|false|1|0|on|off)\\s*\\))?",
            Pattern.CASE_INSENSITIVE);

    public static String process(String src) {

        Matcher nl = NOLIBC.matcher(src);

        if (nl.find()) {
            String v = nl.group(1);
            boolean on = v == null || !(v.equalsIgnoreCase("false") || v.equals("0") || v.equalsIgnoreCase("off"));
            NO_LIBC = on;
            Builder.Parseconv.NO_LIBC = on;
        }

        if (src.indexOf('%') < 0)
            return src;

        boolean any = false;
        for (String l : src.split("\n", -1))
            if (l.trim().startsWith("%")) { any = true; break; }

        if (!any)
            return src;

        Map<String, String> sym = new HashMap<>();
        predefine(sym, src);

        Deque<Frame> stack = new ArrayDeque<>();
        StringBuilder out = new StringBuilder(src.length());

        String[] lines = src.split("\n", -1);

        boolean inBlock = false;
        int blockLine = 0;

        for (int n = 0; n < lines.length; n++) {

            String raw = lines[n];
            String t = raw.trim();
            int ln = n + 1;

            boolean active = stack.isEmpty() || stack.peek().active;

            if (!t.startsWith("%")) {

                if (inBlock && !t.isEmpty() && !t.startsWith("//"))
                    throw err(ln, "only % directives are allowed inside %ash_start / %ash_end");

                out.append(active && !inBlock ? raw : "");
                if (n < lines.length - 1) out.append('\n');
                continue;
            }

            String body = t.substring(1);
            int c = body.indexOf("//");
            if (c >= 0) body = body.substring(0, c);
            body = body.trim();

            String word = body.split("\\s+", 2)[0].toLowerCase();
            String rest = body.length() > word.length() ? body.substring(word.length()).trim() : "";

            switch (word) {

                case "ash_start":
                    if (inBlock) throw err(ln, "%ash_start inside another %ash_start");
                    inBlock = true;
                    blockLine = ln;
                    break;

                case "ash_end": case "ashc_end": case "ash_stop":
                    if (!inBlock) throw err(ln, "%" + word + " without %ash_start");
                    inBlock = false;
                    break;

                case "define":
                    if (active) {
                        String[] p = rest.split("\\s+", 2);
                        if (p[0].isEmpty()) throw err(ln, "%define needs a name");
                        sym.put(p[0], p.length > 1 ? defineValue(p[1], sym, ln) : "1");
                    }
                    break;

                case "undef":
                    if (active) sym.remove(rest);
                    break;

                case "if": case "ifdef": case "ifndef": {
                    Frame f = new Frame();
                    f.parentActive = active;
                    f.line = ln;
                    boolean v = active && cond(word, rest, sym, ln);
                    f.active = v;
                    f.taken = v;
                    stack.push(f);
                    break;
                }

                case "elif": case "elifdef": case "elifndef": {
                    if (stack.isEmpty()) throw err(ln, "%" + word + " without %if");
                    Frame f = stack.peek();
                    if (f.sawElse) throw err(ln, "%" + word + " after %else");
                    boolean v = f.parentActive && !f.taken
                        && cond(word.equals("elif") ? "if" : word.substring(2), rest, sym, ln);
                    f.active = v;
                    f.taken |= v;
                    break;
                }

                case "else": {
                    if (stack.isEmpty()) throw err(ln, "%else without %if");
                    Frame f = stack.peek();
                    if (f.sawElse) throw err(ln, "duplicate %else");
                    f.sawElse = true;
                    f.active = f.parentActive && !f.taken;
                    f.taken = true;
                    break;
                }

                case "endif":
                    if (stack.isEmpty()) throw err(ln, "%endif without %if");
                    stack.pop();
                    break;

                case "error":
                    if (active) throw err(ln, unquote(rest));
                    break;

                case "warning":
                    if (active) arch.Diagnostics.warning("line " + ln + ": " + unquote(rest));
                    break;

                case "tip":
                    if (active) arch.Diagnostics.tip("line " + ln + ": " + unquote(rest));
                    break;

                default:
                    throw err(ln, "unknown directive '%" + word + "'");
            }

            if (n < lines.length - 1) out.append('\n');
        }

        if (!stack.isEmpty())
            throw err(stack.peek().line, "%if without matching %endif");

        if (inBlock)
            throw err(blockLine, "%ash_start without %ash_end");

        return out.toString();
    }

    private static String defineValue(String text, Map<String, String> sym, int ln) {

        String t = text.trim();

        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\""))
            return t.substring(1, t.length() - 1);

        try {
            Eval e = new Eval(t, sym, ln);
            String v = e.parseOr();

            if (e.pos == e.toks.size() && Eval.numOf(v) != null)
                return v;

        } catch (RuntimeException ignored) {

        }

        return t;
    }

    private static String unquote(String s) {
        s = s.trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\""))
            return s.substring(1, s.length() - 1);
        return s;
    }

    private static void predefine(Map<String, String> sym, String src) {

        int bits = FORCED_BITS;

        if (bits == 0) {
            Matcher m = BITS.matcher(src);
            bits = m.find() ? Integer.parseInt(m.group(1)) : 64;
        }

        sym.put("ASH_BITS", String.valueOf(bits));
        sym.put("ASH_ARCH", bits == 16 ? "i8086" : bits == 32 ? "i386" : "x86_64");
        sym.put("ASH_PTR_SIZE", String.valueOf(bits == 16 ? 2 : bits / 8));
        sym.put("ASH_FORMAT", FORMAT);
        sym.put("ASH_OS",
            FORMAT.equals("elf") || FORMAT.equals("exe") ? "linux"
                : FORMAT.equals("coff") ? "windows" : "none");
        sym.put("ASH_NO_LIBC", NO_LIBC ? "1" : "0");
        sym.put("ASH_LIBC", NO_LIBC ? "0" : "1");
        sym.put("ASH_VERSION", "1.0.0");
        sym.put("ASH_ENDIAN", "little");

        if (bits == 64) sym.put("ASH_X86_64", "1");
        if (bits == 32) sym.put("ASH_I386", "1");
        if (bits == 16) sym.put("ASH_I8086", "1");

        sym.put("ASH_TARGET",
            FORMAT.equals("elf") ? "elf"
                : FORMAT.equals("coff") ? "windows"
                : FORMAT.equals("bin") ? "raw"
                : FORMAT);
        if (FORMAT.equals("elf") || FORMAT.equals("exe")) sym.put("ASH_LINUX", "1");
        if (FORMAT.equals("coff")) sym.put("ASH_WINDOWS", "1");
        if (FORMAT.equals("bin")) sym.put("ASH_RAW", "1");

        switch (FORMAT) {
            case "elf":  sym.put("ASH_ELF", "1");  sym.put("ASH_OBJECT", "1"); break;
            case "coff": sym.put("ASH_COFF", "1"); sym.put("ASH_OBJECT", "1"); break;
            case "bin":  sym.put("ASH_BIN", "1");  sym.put("ASH_BAREMETAL", "1"); break;
            case "exe":  sym.put("ASH_EXE", "1");  break;
            default: break;
        }
    }

    private static boolean cond(String kind, String rest, Map<String, String> sym, int ln) {

        if (kind.equals("ifdef")) return sym.containsKey(rest.trim());
        if (kind.equals("ifndef")) return !sym.containsKey(rest.trim());

        if (rest.isEmpty())
            throw err(ln, "%if needs a condition");

        Eval e = new Eval(rest, sym, ln);
        String v = e.parseOr();

        if (e.pos < e.toks.size())
            throw err(ln, "unexpected '" + e.toks.get(e.pos) + "' in condition");

        return truthy(v);
    }

    private static boolean truthy(String v) {
        return v != null && !v.isEmpty() && !v.equals("0");
    }

    private static final class Eval {

        final java.util.List<String> toks = new java.util.ArrayList<>();
        final Map<String, String> sym;
        final int ln;
        int pos = 0;

        Eval(String s, Map<String, String> sym, int ln) {
            this.sym = sym;
            this.ln = ln;

            Matcher m = Pattern.compile(
                "\"[^\"]*\"|0[xX][0-9a-fA-F]+|0[bB][01]+|\\d+|[A-Za-z_]\\w*|&&|\\|\\||==|!=|<=|>=|<<|>>|\\*\\*|[()!<>+\\-*/%&|^~]"
            ).matcher(s);

            int last = 0;

            while (m.find()) {
                if (!s.substring(last, m.start()).trim().isEmpty())
                    throw err(ln, "unexpected text in condition: '" + s.substring(last, m.start()).trim() + "'");
                toks.add(m.group());
                last = m.end();
            }

            if (!s.substring(last).trim().isEmpty())
                throw err(ln, "unexpected text in condition: '" + s.substring(last).trim() + "'");
        }

        boolean at(String t) {
            return pos < toks.size() && toks.get(pos).equals(t);
        }

        String parseOr() {
            String l = parseAnd();
            while (at("||")) {
                pos++;
                String r = parseAnd();
                l = (truthy(l) || truthy(r)) ? "1" : "0";
            }
            return l;
        }

        String parseAnd() {
            String l = parseBitOr();
            while (at("&&")) {
                pos++;
                String r = parseBitOr();
                l = (truthy(l) && truthy(r)) ? "1" : "0";
            }
            return l;
        }

        String parseBitOr() {
            String l = parseBitXor();
            while (at("|")) { pos++; l = Long.toString(need(l) | need(parseBitXor())); }
            return l;
        }

        String parseBitXor() {
            String l = parseBitAnd();
            while (at("^")) { pos++; l = Long.toString(need(l) ^ need(parseBitAnd())); }
            return l;
        }

        String parseBitAnd() {
            String l = parseCmp();
            while (at("&")) { pos++; l = Long.toString(need(l) & need(parseCmp())); }
            return l;
        }

        String parseCmp() {
            String l = parseShift();
            while (pos < toks.size() && java.util.Arrays.asList("==", "!=", "<", "<=", ">", ">=").contains(toks.get(pos))) {
                String op = toks.get(pos++);
                String r = parseShift();
                l = compare(l, op, r) ? "1" : "0";
            }
            return l;
        }

        boolean compare(String a, String op, String b) {
            Long x = num(a), y = num(b);
            int c = (x != null && y != null) ? Long.compare(x, y) : a.compareTo(b);
            switch (op) {
                case "==": return c == 0;
                case "!=": return c != 0;
                case "<":  return c < 0;
                case "<=": return c <= 0;
                case ">":  return c > 0;
                default:   return c >= 0;
            }
        }

        String parseShift() {
            String l = parseAdd();
            while (at("<<") || at(">>")) {
                String op = toks.get(pos++);
                long r = need(parseAdd());
                long v = need(l);
                l = Long.toString(op.equals("<<") ? v << r : v >> r);
            }
            return l;
        }

        String parseAdd() {
            String l = parseMul();
            while (at("+") || at("-")) {
                String op = toks.get(pos++);
                long r = need(parseMul());
                long v = need(l);
                l = Long.toString(op.equals("+") ? v + r : v - r);
            }
            return l;
        }

        String parseMul() {
            String l = parsePow();
            while (at("*") || at("/") || at("%")) {
                String op = toks.get(pos++);
                long r = need(parsePow());
                long v = need(l);
                if (!op.equals("*") && r == 0)
                    throw err(ln, "division by zero in condition");
                l = Long.toString(op.equals("*") ? v * r : op.equals("/") ? v / r : v % r);
            }
            return l;
        }

        String parsePow() {
            String l = parseUnary();
            if (at("**")) {
                pos++;
                long e = need(parsePow());
                long b = need(l);
                if (e < 0) throw err(ln, "negative exponent in '**'");
                l = Long.toString(java.math.BigInteger.valueOf(b).pow((int) Math.min(e, 4096)).longValue());
            }
            return l;
        }

        long need(String v) {
            Long x = num(v);
            if (x == null)
                throw err(ln, "'" + v + "' is not a number (needed for arithmetic)");
            return x;
        }

        static Long numOf(String s) {
            try {
                if (s.startsWith("0x") || s.startsWith("0X")) return Long.parseUnsignedLong(s.substring(2), 16);
                if (s.startsWith("0b") || s.startsWith("0B")) return Long.parseUnsignedLong(s.substring(2), 2);
                return Long.parseLong(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        Long num(String s) {
            return numOf(s);
        }

        String parseUnary() {
            if (at("!")) {
                pos++;
                return truthy(parseUnary()) ? "0" : "1";
            }
            if (at("~")) { pos++; return Long.toString(~need(parseUnary())); }
            if (at("-")) { pos++; return Long.toString(-need(parseUnary())); }
            if (at("+")) { pos++; return Long.toString(need(parseUnary())); }
            return parsePrimary();
        }

        String parsePrimary() {

            if (pos >= toks.size())
                throw err(ln, "incomplete condition");

            String t = toks.get(pos++);

            if (t.equals("(")) {
                String v = parseOr();
                if (!at(")")) throw err(ln, "missing ')' in condition");
                pos++;
                return v;
            }

            if (t.startsWith("\""))
                return t.substring(1, t.length() - 1);

            if (t.equals("defined")) {
                boolean paren = at("(");
                if (paren) pos++;
                if (pos >= toks.size()) throw err(ln, "defined() needs a name");
                String name = toks.get(pos++);
                if (paren) {
                    if (!at(")")) throw err(ln, "missing ')' after defined(");
                    pos++;
                }
                return sym.containsKey(name) ? "1" : "0";
            }

            if (Character.isLetter(t.charAt(0)) || t.charAt(0) == '_')
                return sym.getOrDefault(t, t);

            return t;
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
