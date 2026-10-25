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

public class Tokens {

    public enum Type {
        COLON, SEMICOLON, DOT,
        IDENT, NUMBER, PTRVAR, REGVAR, MACRO, STRING, OP,
        LBRACKET, RBRACKET, LPAREN, RPAREN, LBRACE, RBRACE,
        LT, GT, COMMA, ARROW, EOF
    }

    public static class Token {
        public final Type type;
        public final String text;
        public final int line;

        public Token(Type type, String text, int line) {
            this.type = type;
            this.text = text;
            this.line = line;
        }

        @Override
        public String toString() {
            return type + "(" + text + ")@L" + line;
        }
    }

    private static final Set<String> TWO_CHAR_OPS =
        new HashSet<>(Arrays.asList("==", "!=", "<=", ">=", "&&", "||", "++", "--"));

    public static List<Token> tokenize(String src) {

        if (src == null)
            throw new IllegalArgumentException("ASHIR tokenizer: source es null");

        List<Token> out = new ArrayList<>();
        int i = 0, n = src.length(), line = 1;

        while (i < n) {
            char c = src.charAt(i);

            if (c == '\n') { line++; i++; continue; }
            if (Character.isWhitespace(c)) { i++; continue; }

            if (c == '#' && i + 1 < n && src.charAt(i + 1) == '*') {
                int end = src.indexOf("*#", i + 2);

                if (end < 0)
                    throw new IllegalArgumentException("Comentario de bloque sin cerrar (linea " + line + ")");

                for (int k = i; k < end + 2; k++)
                    if (src.charAt(k) == '\n') line++;

                i = end + 2;
                continue;
            }

            if (c == '#') {
                while (i < n && src.charAt(i) != '\n') i++;
                continue;
            }

            if (c == '-' && i + 2 < n && src.charAt(i + 1) == '-' && src.charAt(i + 2) == '>') {
                out.add(new Token(Type.ARROW, "-->", line));
                i += 3;
                continue;
            }

            if (c == '"') {
                int startLine = line;
                StringBuilder value = new StringBuilder();
                i++;
                boolean closed = false, escape = false;

                while (i < n) {
                    char ch = src.charAt(i++);

                    if (ch == '\n') line++;

                    if (escape) {
                        value.append(ch);
                        escape = false;
                        continue;
                    }

                    if (ch == '"') { closed = true; break; }

                    if (ch == '\\') escape = true;

                    value.append(ch);
                }

                if (!closed)
                    throw new IllegalArgumentException("String sin cerrar (linea " + startLine + ")");

                out.add(new Token(Type.STRING, value.toString(), startLine));
                continue;
            }

            Type single = null;

            switch (c) {
                case '[': single = Type.LBRACKET; break;
                case ']': single = Type.RBRACKET; break;
                case '(': single = Type.LPAREN; break;
                case ')': single = Type.RPAREN; break;
                case '{': single = Type.LBRACE; break;
                case '}': single = Type.RBRACE; break;
                case ',': single = Type.COMMA; break;
                case ':': single = Type.COLON; break;
                case ';': single = Type.SEMICOLON; break;
                case '.': single = Type.DOT; break;
                default: break;
            }

            if (single != null) {
                out.add(new Token(single, String.valueOf(c), line));
                i++;
                continue;
            }

            if (i + 1 < n && TWO_CHAR_OPS.contains(src.substring(i, i + 2))) {
                out.add(new Token(Type.OP, src.substring(i, i + 2), line));
                i += 2;
                continue;
            }

            if (c == '<') { out.add(new Token(Type.LT, "<", line)); i++; continue; }
            if (c == '>') { out.add(new Token(Type.GT, ">", line)); i++; continue; }

            if (c == '=' || c == '!' || c == '&' || c == '|' || c == '+' || c == '-' || c == '*' || c == '/') {
                out.add(new Token(Type.OP, String.valueOf(c), line));
                i++;
                continue;
            }

            if (c == '$' || c == '%' || c == '@') {
                int start = i + 1, j = start;

                while (j < n && (Character.isLetterOrDigit(src.charAt(j)) || src.charAt(j) == '_')) j++;

                if (j == start)
                    throw new IllegalArgumentException("Nombre vacio tras '" + c + "' (linea " + line + ")");

                Type type = c == '$' ? Type.PTRVAR : c == '%' ? Type.REGVAR : Type.MACRO;
                out.add(new Token(type, src.substring(start, j), line));
                i = j;
                continue;
            }

            if (Character.isDigit(c)) {
                int start = i, j = i;

                if (c == '0' && j + 1 < n && (src.charAt(j + 1) == 'x' || src.charAt(j + 1) == 'X')) {
                    j += 2;
                    int hexStart = j;

                    while (j < n && isHex(src.charAt(j))) j++;

                    if (j == hexStart)
                        throw new IllegalArgumentException("Literal hexadecimal invalido (linea " + line + ")");
                } else {
                    while (j < n && Character.isDigit(src.charAt(j))) j++;

                    if (j + 1 < n && src.charAt(j) == '.' && Character.isDigit(src.charAt(j + 1))) {
                        j++;
                        while (j < n && Character.isDigit(src.charAt(j))) j++;
                    }

                    if (j < n && (src.charAt(j) == 'e' || src.charAt(j) == 'E')) {
                        int k = j + 1;

                        if (k < n && (src.charAt(k) == '+' || src.charAt(k) == '-')) k++;

                        if (k < n && Character.isDigit(src.charAt(k))) {
                            while (k < n && Character.isDigit(src.charAt(k))) k++;
                            j = k;
                        }
                    }
                }

                out.add(new Token(Type.NUMBER, src.substring(start, j), line));
                i = j;
                continue;
            }

            if (Character.isLetter(c) || c == '_') {
                int start = i, j = i;

                while (j < n && (Character.isLetterOrDigit(src.charAt(j)) || src.charAt(j) == '_')) j++;

                out.add(new Token(Type.IDENT, src.substring(start, j), line));
                i = j;
                continue;
            }

            throw new IllegalArgumentException("Caracter inesperado '" + c + "' (linea " + line + ")");
        }

        out.add(new Token(Type.EOF, "", line));
        return out;
    }

    private static boolean isHex(char c) {
        return Character.isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
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
