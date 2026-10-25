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

import java.util.ArrayList;
import java.util.List;

public final class AsmBlocks {

    private AsmBlocks() {
    }

    public static final class Block {
        public final String text;
        public final boolean isVolatile;

        public final boolean placeholders;

        Block(String text, boolean isVolatile) {
            this(text, isVolatile, false);
        }

        Block(String text, boolean isVolatile, boolean placeholders) {
            this.text = text;
            this.isVolatile = isVolatile;
            this.placeholders = placeholders;
        }
    }

    public static final char PH_OPEN = '\u0001';
    public static final char PH_CLOSE = '\u0002';

    private static final List<Block> BLOCKS = new ArrayList<>();

    public static Block get(int index) {
        return BLOCKS.get(index);
    }

    public static void reset() {
        BLOCKS.clear();
    }

    private static boolean idChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    public static String extract(String src) {

        if (!src.contains("asm"))
            return src;

        StringBuilder out = new StringBuilder(src.length());
        int n = src.length();
        int i = 0;

        while (i < n) {

            char c = src.charAt(i);

            if (c == '"') {
                int j = i + 1;
                while (j < n && src.charAt(j) != '"') {
                    if (src.charAt(j) == '\\') j++;
                    j++;
                }
                j = Math.min(j + 1, n);
                out.append(src, i, j);
                i = j;
                continue;
            }

            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                int j = src.indexOf('\n', i);
                if (j < 0) j = n;
                out.append(src, i, j);
                i = j;
                continue;
            }

            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                int j = src.indexOf("*/", i + 2);
                j = j < 0 ? n : j + 2;
                out.append(src, i, j);
                i = j;
                continue;
            }

            boolean boundary = i == 0 || !idChar(src.charAt(i - 1));

            if (boundary && (src.startsWith("volatile", i) || src.startsWith("asm", i)
                    || src.startsWith("__asm__", i) || src.startsWith("__volatile__", i))) {

                int j = i;
                boolean vol = false;

                int vl = word(src, j, "volatile", "__volatile__");
                if (vl > 0) {
                    vol = true;
                    j = skipWs(src, j + vl);
                }

                int al = word(src, j, "asm", "__asm__");
                if (al > 0) {

                    int k = skipWs(src, j + al);

                    int vl2 = word(src, k, "volatile", "__volatile__");
                    if (vl2 > 0) {
                        vol = true;
                        k = skipWs(src, k + vl2);
                    }

                    if (k < n && src.charAt(k) == '(') {
                        int e = parseStringForm(src, k, vol);
                        if (e > 0) {
                            int lines = 0;
                            for (int q = i; q < e; q++)
                                if (src.charAt(q) == '\n') lines++;
                            out.append("__asm(").append(BLOCKS.size() - 1).append(");");
                            for (int q = 0; q < lines; q++) out.append('\n');
                            i = e;
                            continue;
                        }
                    }

                    if (k < n && src.charAt(k) == '{') {

                        int depth = 0;
                        int e = k;

                        while (e < n) {
                            char d = src.charAt(e);
                            if (d == '{') depth++;
                            else if (d == '}' && --depth == 0) break;
                            e++;
                        }

                        if (e >= n)
                            throw new IllegalArgumentException(
                                "asm block is missing a closing '}'"
                            );

                        String body = src.substring(k + 1, e);

                        BLOCKS.add(new Block(body, vol));

                        int lines = 0;
                        for (int q = i; q <= e; q++)
                            if (src.charAt(q) == '\n') lines++;

                        out.append("__asm(").append(BLOCKS.size() - 1).append(");");
                        for (int q = 0; q < lines; q++) out.append('\n');

                        i = e + 1;
                        continue;
                    }
                }
            }

            out.append(c);
            i++;
        }

        return out.toString();
    }

    private static char charAt(String s, int i) {
        return i < s.length() ? s.charAt(i) : ' ';
    }

    private static int word(String s, int i, String a, String b) {
        if (s.startsWith(b, i) && !idChar(charAt(s, i + b.length()))) return b.length();
        if (s.startsWith(a, i) && !idChar(charAt(s, i + a.length()))) return a.length();
        return 0;
    }

    private static int skipWs(String s, int i) {
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) { i++; continue; }
            if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '/') {
                int j = s.indexOf('\n', i);
                i = j < 0 ? s.length() : j;
                continue;
            }
            if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '*') {
                int j = s.indexOf("*/", i + 2);
                i = j < 0 ? s.length() : j + 2;
                continue;
            }
            break;
        }
        return i;
    }

    private static int readString(String s, int i, StringBuilder sb) {
        int n = s.length();
        i++;
        while (i < n && s.charAt(i) != '"') {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < n) {
                char d = s.charAt(++i);
                switch (d) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': break;
                    case '0': sb.append('\0'); break;
                    default:  sb.append(d);
                }
                i++;
                continue;
            }
            if (c == '\n')
                throw new IllegalArgumentException("asm(\"...\"): unterminated string");
            sb.append(c);
            i++;
        }
        if (i >= n)
            throw new IllegalArgumentException("asm(\"...\"): unterminated string");
        return i + 1;
    }

    private static String operandOf(String s) {
        String t = s.trim();
        if (!t.matches("[A-Za-z_]\\w*(\\s*\\[\\s*([A-Za-z_]\\w*|-?\\d+)\\s*\\])?"))
            throw new IllegalArgumentException(
                "asm operand '(" + t + ")' must be a variable or name[index]");
        return t.replaceAll("\\s+", "");
    }

    private static int parseStringForm(String s, int k, boolean vol) {
        int n = s.length();
        int i = skipWs(s, k + 1);
        if (i >= n || s.charAt(i) != '"')
            return -1;

        StringBuilder tpl = new StringBuilder();
        while (i < n && s.charAt(i) == '"') {
            i = readString(s, i, tpl);
            i = skipWs(s, i);
        }

        List<String> ops = new ArrayList<>();
        java.util.Map<String, String> named = new java.util.HashMap<>();
        int section = 0;

        while (i < n && s.charAt(i) == ':') {
            section++;
            i = skipWs(s, i + 1);
            while (i < n && s.charAt(i) != ':' && s.charAt(i) != ')') {
                String sym = null;
                if (s.charAt(i) == '[') {
                    int e = s.indexOf(']', i);
                    if (e < 0) throw new IllegalArgumentException("asm: missing ']'");
                    sym = s.substring(i + 1, e).trim();
                    i = skipWs(s, e + 1);
                }
                if (i < n && s.charAt(i) == '"') {
                    i = skipWs(s, readString(s, i, new StringBuilder()));
                }
                if (section <= 2) {
                    if (i >= n || s.charAt(i) != '(')
                        throw new IllegalArgumentException("asm: expected '(variable)' after constraint");
                    int depth = 0, e = i;
                    for (; e < n; e++) {
                        if (s.charAt(e) == '(') depth++;
                        else if (s.charAt(e) == ')' && --depth == 0) break;
                    }
                    String op = operandOf(s.substring(i + 1, e));
                    if (sym != null) named.put(sym, op);
                    ops.add(op);
                    i = skipWs(s, e + 1);
                }
                if (i < n && s.charAt(i) == ',') i = skipWs(s, i + 1);
            }
        }

        if (i >= n || s.charAt(i) != ')')
            throw new IllegalArgumentException("asm(\"...\"): expected ')'");
        i = skipWs(s, i + 1);
        if (i < n && s.charAt(i) == ';') i++;

        StringBuilder body = new StringBuilder();
        String t = tpl.toString();
        for (int p = 0; p < t.length(); p++) {
            char c = t.charAt(p);
            if (c == '%' && p + 1 < t.length()) {
                char d = t.charAt(p + 1);
                if (d == '%') { body.append('%'); p++; continue; }
                if (Character.isDigit(d)) {
                    int q = p + 1;
                    while (q < t.length() && Character.isDigit(t.charAt(q))) q++;
                    int idx = Integer.parseInt(t.substring(p + 1, q));
                    if (idx >= ops.size())
                        throw new IllegalArgumentException("asm: operand %" + idx + " does not exist");
                    body.append(PH_OPEN).append(ops.get(idx)).append(PH_CLOSE);
                    p = q - 1;
                    continue;
                }
                if (d == '[') {
                    int q = t.indexOf(']', p);
                    String nm = q < 0 ? "" : t.substring(p + 2, q).trim();
                    String op = named.get(nm);
                    if (op == null)
                        throw new IllegalArgumentException("asm: operand %[" + nm + "] does not exist");
                    body.append(PH_OPEN).append(op).append(PH_CLOSE);
                    p = q;
                    continue;
                }
            }
            if (c == '{') {
                int q = t.indexOf('}', p);
                if (q > 0) {
                    body.append(PH_OPEN).append(operandOf(t.substring(p + 1, q))).append(PH_CLOSE);
                    p = q;
                    continue;
                }
            }
            body.append(c == ';' ? '\n' : c);
        }

        BLOCKS.add(new Block(body.toString(), vol, true));
        return i;
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
