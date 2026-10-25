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

import Builder.ash.Ast.Expression;
import Builder.ash.Ast.ValueExpr;

public final class StringConv {

    private final Cursor c;
    private final Nums nums;

    public StringConv(Cursor c, Nums nums) {
        this.c = c;
        this.nums = nums;
    }

    public static String stripQuotes(String text) {
        if (text == null || text.length() < 2)
            return text;

        char a = text.charAt(0);
        char b = text.charAt(text.length() - 1);

        if ((a == '"' && b == '"') || (a == '\'' && b == '\''))
            return text.substring(1, text.length() - 1);

        return text;
    }

    public static String decodeEscapes(String text) {
        if (text == null || text.isEmpty())
            return text == null ? "" : text;

        StringBuilder out = new StringBuilder(text.length());

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);

            if (ch != '\\') {
                out.append(ch);
                continue;
            }

            if (i + 1 >= text.length()) {
                out.append('\\');
                break;
            }

            char esc = text.charAt(++i);

            switch (esc) {

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

                    if (i + 1 < text.length()
                        && isOctalDigit(text.charAt(i + 1))) {

                        int value = 0;
                        int count = 0;

                        while (i + 1 < text.length()
                            && count < 3
                            && isOctalDigit(text.charAt(i + 1))) {

                            i++;

                            value =
                                (value << 3)
                                | octalValue(text.charAt(i));

                            count++;
                        }

                        appendCodePoint(out, value);
                    } else {
                        out.append('\0');
                    }
                    break;

                case 'a':
                    out.append('\u0007');
                    break;

                case 'b':
                    out.append('\b');
                    break;

                case 'f':
                    out.append('\f');
                    break;

                case 'v':
                    out.append('\u000B');
                    break;

                case '\\':
                    out.append('\\');
                    break;

                case '\'':
                    out.append('\'');
                    break;

                case '"':
                    out.append('"');
                    break;

                case 'x': {

                    if (i + 2 >= text.length()) {
                        throw new IllegalArgumentException(
                            "Escape hexadecimal incompleto: \\x"
                        );
                    }

                    char h1 = text.charAt(i + 1);
                    char h2 = text.charAt(i + 2);

                    int hi = hexValue(h1);
                    int lo = hexValue(h2);

                    if (hi < 0 || lo < 0) {
                        throw new IllegalArgumentException(
                            "Escape hexadecimal invalido: \\x"
                            + h1
                            + h2
                        );
                    }

                    int value = (hi << 4) | lo;

                    out.append((char) value);

                    i += 2;
                    break;
                }

                case 'u': {

                    if (i + 4 >= text.length()) {
                        throw new IllegalArgumentException(
                            "Escape Unicode incompleto: \\u"
                        );
                    }

                    int value = 0;

                    for (int j = 1; j <= 4; j++) {
                        char h = text.charAt(i + j);
                        int digit = hexValue(h);

                        if (digit < 0) {
                            throw new IllegalArgumentException(
                                "Escape Unicode invalido: \\u"
                                + text.substring(i + 1, i + 5)
                            );
                        }

                        value = (value << 4) | digit;
                    }

                    out.append((char) value);

                    i += 4;
                    break;
                }

                case 'U': {

                    if (i + 8 >= text.length()) {
                        throw new IllegalArgumentException(
                            "Escape Unicode incompleto: \\U"
                        );
                    }

                    long value = 0;

                    for (int j = 1; j <= 8; j++) {
                        char h = text.charAt(i + j);
                        int digit = hexValue(h);

                        if (digit < 0) {
                            throw new IllegalArgumentException(
                                "Escape Unicode invalido: \\U"
                                + text.substring(i + 1, i + 9)
                            );
                        }

                        value = (value << 4) | digit;
                    }

                    if (value > 0x10FFFFL) {
                        throw new IllegalArgumentException(
                            "Code point Unicode fuera de rango: 0x"
                            + Long.toHexString(value)
                        );
                    }

                    if (value >= 0xD800L && value <= 0xDFFFL) {
                        throw new IllegalArgumentException(
                            "Code point Unicode invalido: 0x"
                            + Long.toHexString(value)
                        );
                    }

                    appendCodePoint(out, (int) value);

                    i += 8;
                    break;
                }

                default:

                    if (isOctalDigit(esc)) {

                        int value = octalValue(esc);
                        int count = 1;

                        while (i + 1 < text.length()
                            && count < 3
                            && isOctalDigit(text.charAt(i + 1))) {

                            i++;

                            value =
                                (value << 3)
                                | octalValue(text.charAt(i));

                            count++;
                        }

                        appendCodePoint(out, value);
                    } else {
                        out.append(esc);
                    }

                    break;
            }
        }

        return out.toString();
    }

    private static int hexValue(char ch) {
        if (ch >= '0' && ch <= '9')
            return ch - '0';

        if (ch >= 'a' && ch <= 'f')
            return ch - 'a' + 10;

        if (ch >= 'A' && ch <= 'F')
            return ch - 'A' + 10;

        return -1;
    }

    private static boolean isOctalDigit(char ch) {
        return ch >= '0' && ch <= '7';
    }

    private static int octalValue(char ch) {
        return ch - '0';
    }

    private static void appendCodePoint(
        StringBuilder out,
        int codePoint
    ) {
        if (codePoint < 0 || codePoint > 0x10FFFF) {
            throw new IllegalArgumentException(
                "Code point Unicode fuera de rango: 0x"
                + Integer.toHexString(codePoint)
            );
        }

        if (codePoint >= 0xD800 && codePoint <= 0xDFFF) {
            throw new IllegalArgumentException(
                "Code point Unicode invalido: 0x"
                + Integer.toHexString(codePoint)
            );
        }

        if (codePoint <= 0xFFFF) {
            out.append((char) codePoint);
            return;
        }

        int adjusted = codePoint - 0x10000;

        char high =
            (char) (
                0xD800
                + (adjusted >> 10)
            );

        char low =
            (char) (
                0xDC00
                + (adjusted & 0x3FF)
            );

        out.append(high);
        out.append(low);
    }

    public void validateConversion(
        String destinationType,
        Expression expression
    ) {
        if (!(expression instanceof ValueExpr))
            return;

        ValueExpr value = (ValueExpr) expression;
        String type = Nums.normalizeType(destinationType);

        if (value.type.equals("string")) {

            if (type.equals("ptr") || type.startsWith("ptr:"))
                return;

            if (type.equals("void")) {
                throw c.error(
                    "No se puede convertir string a void"
                );
            }

            if (isIntType(type)) {
                convertStringToInteger(value, type);
                return;
            }

            if (type.equals("float32")
                || type.equals("float64")) {

                convertStringToFloat(value, type);
                return;
            }

            throw c.error(
                "Conversion no soportada de string a " + type
            );
        }

        if (value.type.equals("char")) {

            if (type.equals("ptr")) {
                throw c.error(
                    "No se puede convertir char a ptr"
                );
            }

            if (type.equals("void")) {
                throw c.error(
                    "No se puede convertir char a void"
                );
            }

            if (isIntType(type)) {
                convertStringToInteger(value, type);
                return;
            }

            if (type.equals("float32")
                || type.equals("float64")) {

                convertStringToFloat(value, type);
                return;
            }

            throw c.error(
                "Conversion no soportada de char a " + type
            );
        }
    }

    private static boolean isIntType(String type) {
        return type.equals("byte")
            || type.equals("word")
            || type.equals("dword")
            || type.equals("qword");
    }

    private void convertStringToInteger(
        ValueExpr value,
        String destination
    ) {
        int bits = nums.typeBits(destination);

        String decoded = decodeEscapes(
            stripQuotes(value.value)
        );

        if (value.type.equals("char")) {

            if (decoded.length() != 1) {
                throw c.error(
                    "Un char debe contener exactamente un caracter"
                );
            }

            long n = decoded.charAt(0);

            nums.checkIntegerRange(
                n,
                bits,
                destination
            );

            value.value = Long.toString(n);
            value.type = "integer";

            return;
        }

        if (decoded.isEmpty()) {
            value.value = "0";
            value.type = "integer";

            return;
        }

        if (decoded.length() == 1) {
            long n = decoded.charAt(0);

            nums.checkIntegerRange(
                n,
                bits,
                destination
            );

            value.value = Long.toString(n);
            value.type = "integer";

            return;
        }

        if (Nums.looksNumeric(decoded)) {

            long n;

            try {
                n = Long.decode(decoded);

            } catch (NumberFormatException e) {

                try {
                    n = new java.math.BigInteger(decoded).longValue();

                } catch (NumberFormatException ex) {

                    throw c.error(
                        "No se puede convertir \""
                        + decoded
                        + "\" a "
                        + destination
                    );
                }
            }

            nums.checkIntegerRange(
                n,
                bits,
                destination
            );

            value.value = Long.toString(n);
            value.type = "integer";

            return;
        }

        if (decoded.length() <= bits / 8) {

            long packed = nums.packLittleEndian(
                decoded,
                bits / 8
            );

            nums.checkIntegerRange(
                packed,
                bits,
                destination
            );

            value.value =
                "0x"
                + Long.toUnsignedString(
                    packed,
                    16
                );

            value.type = "integer";

            return;
        }

        throw c.error(
            "La cadena \""
            + decoded
            + "\" ocupa "
            + decoded.length()
            + " bytes, pero "
            + destination
            + " permite "
            + (bits / 8)
            + " bytes"
        );
    }

    private void convertStringToFloat(
        ValueExpr value,
        String destination
    ) {
        String decoded = decodeEscapes(
            stripQuotes(value.value)
        );

        double n;

        if (value.type.equals("char")) {

            if (decoded.length() != 1) {
                throw c.error(
                    "Un char debe contener exactamente un caracter"
                );
            }

            n = decoded.charAt(0);

        } else {

            try {
                n = Double.parseDouble(decoded);

            } catch (NumberFormatException e) {

                if (decoded.length() == 1) {
                    n = decoded.charAt(0);

                } else {
                    throw c.error(
                        "No se puede convertir \""
                        + decoded
                        + "\" a "
                        + destination
                    );
                }
            }
        }

        if (destination.equals("float32")) {

            float f = (float) n;

            if (Float.isInfinite(f)
                && !Double.isInfinite(n)) {

                throw c.error(
                    "Valor fuera del rango de float32: "
                    + decoded
                );
            }

            value.value = Float.toString(f);

        } else {

            if (Double.isInfinite(n)) {

                throw c.error(
                    "Valor fuera del rango de float64"
                );
            }

            value.value = Double.toString(n);
        }

        value.type = "float";
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
