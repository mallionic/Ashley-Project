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

package arch.builtin.backd;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import arch.builtin.Parsing;
import arch.builtin.Vars;

public final class BackendUtil {

    private BackendUtil() {
    }

    public static String cleanSymbol(String symbol) {

        if (symbol == null)
            return null;

        String s = symbol.trim();

        while (s.startsWith("$") || s.startsWith("%"))
            s = s.substring(1);

        return s;
    }

    public static boolean isCompilerTemporary(String name) {

        if (name == null)
            return false;

        String n = cleanSymbol(name);

        return
            n.equals("__ret") ||
            n.startsWith("__arg") ||
            n.startsWith("__tmp") ||
            n.startsWith("__strptr_") ||
            n.startsWith("__ptr_") ||
            n.equals("__retstr");
    }

    public static boolean isNumber(String text) {

        if (text == null)
            return false;

        String s = text.trim();

        if (s.isEmpty())
            return false;

        if (s.startsWith("+") ||
            s.startsWith("-")) {

            s = s.substring(1);

            if (s.isEmpty())
                return false;
        }

        if (s.startsWith("0x") ||
            s.startsWith("0X")) {

            if (s.length() <= 2)
                return false;

            for (int i = 2; i < s.length(); i++) {

                if (Character.digit(
                        s.charAt(i),
                        16
                    ) < 0) {

                    return false;
                }
            }

            return true;
        }

        if (s.startsWith("0b") ||
            s.startsWith("0B")) {

            if (s.length() <= 2)
                return false;

            for (int i = 2; i < s.length(); i++) {

                char c = s.charAt(i);

                if (c != '0' && c != '1')
                    return false;
            }

            return true;
        }

        boolean digit = false;
        boolean dot = false;
        boolean exponent = false;
        boolean exponentDigit = false;
        boolean exponentSignAllowed = false;

        int end = s.length();

        char last = s.charAt(end - 1);

        if (last == 'f' ||
            last == 'F' ||
            last == 'd' ||
            last == 'D') {

            end--;
        }

        if (end <= 0)
            return false;

        for (int i = 0; i < end; i++) {

            char c = s.charAt(i);

            if (Character.isDigit(c)) {

                digit = true;

                if (exponent)
                    exponentDigit = true;

                exponentSignAllowed = false;

                continue;
            }

            if (c == '.') {

                if (dot || exponent)
                    return false;

                dot = true;
                continue;
            }

            if (c == 'e' || c == 'E') {

                if (exponent || !digit)
                    return false;

                exponent = true;
                exponentDigit = false;
                exponentSignAllowed = true;

                continue;
            }

            if ((c == '+' || c == '-') &&
                exponent &&
                exponentSignAllowed) {

                exponentSignAllowed = false;
                continue;
            }

            return false;
        }

        if (!digit)
            return false;

        if (exponent &&
            !exponentDigit) {

            return false;
        }

        return true;
    }

public static long parseNumber(String text) {

    if (text == null)
        throw new IllegalArgumentException(
            "Número null"
        );

    String s = text.trim();

    if (s.isEmpty())
        throw new NumberFormatException(
            "Número vacío"
        );

    boolean negative = s.startsWith("-");
    boolean positive = s.startsWith("+");

    if (negative || positive)
        s = s.substring(1);

    if (s.isEmpty())
        throw new NumberFormatException(
            "Número inválido: " + text
        );

    if (s.startsWith("0x") ||
        s.startsWith("0X")) {

        long value = Long.parseUnsignedLong(
            s.substring(2),
            16
        );

        return negative ? -value : value;
    }

    if (s.startsWith("0b") ||
        s.startsWith("0B")) {

        long value = Long.parseUnsignedLong(
            s.substring(2),
            2
        );

        return negative ? -value : value;
    }

    if (s.indexOf('.') >= 0 ||
        s.indexOf('e') >= 0 ||
        s.indexOf('E') >= 0 ||
        s.endsWith("f") ||
        s.endsWith("F") ||
        s.endsWith("d") ||
        s.endsWith("D")) {

        throw new NumberFormatException(
            "El literal es de punto flotante: " + text
        );
    }

    long value = new java.math.BigInteger(s).longValue();

    return negative ? -value : value;
}

    public static double parseDouble(String text) {

        if (text == null)
            throw new IllegalArgumentException(
                "Número floating-point null"
            );

        String s = text.trim();

        if (s.isEmpty())
            throw new NumberFormatException(
                "Número floating-point vacío"
            );

        return Double.parseDouble(s);
    }

    public static float parseFloat(String text) {

        if (text == null)
            throw new IllegalArgumentException(
                "Número FLOAT32 null"
            );

        String s = text.trim();

        if (s.isEmpty())
            throw new NumberFormatException(
                "Número FLOAT32 vacío"
            );

        return Float.parseFloat(s);
    }

    public static long doubleRawBits(String text) {

        return Double.doubleToRawLongBits(
            parseDouble(text)
        );
    }

    public static int floatRawBits(String text) {

        return Float.floatToRawIntBits(
            parseFloat(text)
        );
    }

    public static boolean isFloatingNumber(String text) {

        if (text == null)
            return false;

        String s = text.trim();

        if (s.isEmpty())
            return false;

        if (s.startsWith("+") ||
            s.startsWith("-")) {

            s = s.substring(1);
        }

        return
            s.indexOf('.') >= 0 ||
            s.indexOf('e') >= 0 ||
            s.indexOf('E') >= 0 ||
            s.endsWith("f") ||
            s.endsWith("F") ||
            s.endsWith("d") ||
            s.endsWith("D");
    }

    public static boolean isStringLiteral(String value) {

        if (value == null)
            return false;

        String s = value.trim();

        return
            s.length() >= 2 &&
            s.charAt(0) == '"' &&
            s.charAt(s.length() - 1) == '"';
    }

    public static int findFirstWhitespaceOutsideQuotes(String text) {

        boolean quoted = false;
        boolean escaped = false;

        for (int i = 0; i < text.length(); i++) {

            char c = text.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }

            if (c == '\\' && quoted) {
                escaped = true;
                continue;
            }

            if (c == '"') {
                quoted = !quoted;
                continue;
            }

            if (!quoted && Character.isWhitespace(c))
                return i;
        }

        return -1;
    }

    public static String decodeRawString(String value) {

        if (value == null) {
            throw new IllegalArgumentException("String null");
        }

        String text = value.trim();

        if (
            text.length() >= 2 &&
            text.charAt(0) == '"' &&
            text.charAt(text.length() - 1) == '"'
        ) {
            text = text.substring(1, text.length() - 1);
        }

        StringBuilder out =
            new StringBuilder(text.length());

        boolean escaped = false;

        for (int i = 0; i < text.length(); i++) {

            char c = text.charAt(i);

            if (!escaped) {

                if (c == '\\')
                    escaped = true;
                else
                    out.append(c);

                continue;
            }

            escaped = false;

            switch (c) {

                case 'n':
                    out.append('\n');
                    break;

                case 'r':
                    out.append('\r');
                    break;

                case 't':
                    out.append('\t');
                    break;

                case '0':
                    out.append('\0');
                    break;

                case 'b':
                    out.append('\b');
                    break;

                case 'f':
                    out.append('\f');
                    break;

                case '\\':
                    out.append('\\');
                    break;

                case '"':
                    out.append('"');
                    break;

                case '\'':
                    out.append('\'');
                    break;

                default:

                    out.append('\\');
                    out.append(c);
                    break;
            }
        }

        if (escaped)
            out.append('\\');

        return out.toString();
    }

    public static int typeOf(String type) {

        if (type == null)
            return Vars.QWORD;

        String t = type.trim().toLowerCase();

        if (t.startsWith("ptr"))
            return Vars.QWORD;

        switch (t) {

            case "void":
                return Vars.QWORD;

            case "bool":
            case "boolean":
            case "byte":
            case "int8":
            case "uint8":
                return Vars.BYTE;

            case "word":
            case "int16":
            case "uint16":
                return Vars.WORD;

            case "dword":
            case "int32":
            case "uint32":
                return Vars.DWORD;

            case "qword":
            case "int64":
            case "uint64":
                return Vars.QWORD;

            case "float32":
            case "float":
                return Vars.FLOAT32;

            case "float64":
            case "double":
                return Vars.FLOAT64;

            case "string":
            case "cstring":
            case "char*":
            case "char *":
                return Vars.QWORD;

            default:
                throw new IllegalArgumentException(
                    "Tipo ASHIR desconocido: " + type
                );
        }
    }

    public static int typeOf(Object type) {

        if (type == null)
            return Vars.QWORD;

        if (type instanceof Number)
            return ((Number) type).intValue();

        return typeOf(String.valueOf(type));
    }

    public static Iterable<Parsing.Stmt> macroStatements(
        Parsing.MacroBlockStmt macro
    ) {

        if (macro == null)
            return Collections.emptyList();

        String[] fields = {
            "body",
            "statements",
            "stmts",
            "items",
            "children",
            "nodes"
        };

        Class<?> current = macro.getClass();

        while (current != null &&
               current != Object.class) {

            for (String fieldName : fields) {

                try {

                    java.lang.reflect.Field field =
                        current.getDeclaredField(fieldName);

                    field.setAccessible(true);

                    Object value = field.get(macro);

                    if (value instanceof Iterable<?>) {

                        List<Parsing.Stmt> result =
                            new ArrayList<>();

                        for (Object item :
                             (Iterable<?>) value) {

                            if (item instanceof Parsing.Stmt)
                                result.add(
                                    (Parsing.Stmt) item
                                );
                        }

                        return result;
                    }

                    if (value instanceof Parsing.Stmt) {

                        return Collections.singletonList(
                            (Parsing.Stmt) value
                        );
                    }

                } catch (
                    NoSuchFieldException |
                    IllegalAccessException ignored
                ) {
                }
            }

            current = current.getSuperclass();
        }

        return Collections.emptyList();
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
