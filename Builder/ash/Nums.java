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

public final class Nums {

    private final Cursor c;

    public Nums(Cursor c) {
        this.c = c;
    }

    public static boolean isType(String type) {
        if (Structs.has(type)) return true;
        switch (type) {
            case "void": case "byte": case "char": case "uint8": case "int8":
            case "short": case "word": case "uint16": case "int16":
            case "int": case "int32": case "uint": case "uint32": case "dword": case "uint32_t":
            case "long": case "ulong": case "int64": case "uint64": case "qword":
            case "float": case "float32": case "double": case "float64":
            case "ptr": case "string": case "bool":
            case "uchar": case "uint8_t": case "int8_t": case "ushort": case "uint16_t": case "int16_t":
            case "uint64_t": case "int64_t": case "int32_t": case "ullong": case "llong": case "size_t":
            case "uintptr_t": case "intptr_t": case "ssize_t": case "ptrdiff_t":
                return true;
            default:
                return false;
        }
    }

    public static String valueType(String type) {
        switch (type) {
            case "int8": case "int8_t": case "schar":
                return "int8";
            case "short": case "int16": case "int16_t":
                return "int16";
            case "int": case "int32": case "int32_t":
                return "int32";
            case "uint64": case "uint64_t": case "ulong": case "ullong":
            case "size_t": case "uintptr_t":
                return "uint64";
            default:
                return normalizeType(type);
        }
    }

    public static String normalizeType(String type) {
        if (type.startsWith("ptr:"))
            return type;
        if (Structs.has(type)) return type;
        switch (type) {
            case "byte": case "char": case "bool": case "uint8": case "int8": case "uchar": case "uint8_t": case "int8_t":
                return "byte";
            case "short": case "word": case "uint16": case "int16": case "ushort": case "uint16_t": case "int16_t":
                return "word";
            case "int": case "int32": case "uint": case "uint32": case "uint32_t": case "dword": case "int32_t":
                return "dword";
            case "long": case "ulong": case "int64": case "uint64": case "qword": case "uint64_t": case "int64_t":
            case "ullong": case "llong": case "size_t": case "uintptr_t": case "intptr_t": case "ssize_t": case "ptrdiff_t":
                return "qword";
            case "float": case "float32":
                return "float32";
            case "double": case "float64":
                return "float64";
            case "string": case "ptr":
                return "ptr";
            case "void":
                return "void";
            default:
                throw new IllegalArgumentException("Tipo ASH desconocido: " + type);
        }
    }

    public static boolean isUnsigned(String type) {
        return type.equals("uint8") || type.equals("uint16") || type.equals("uint")
            || type.equals("uint32") || type.equals("uint64") || type.equals("ulong")
            || type.equals("uint32_t") || type.equals("qword")
            || type.equals("bool") || type.equals("uchar") || type.equals("uint8_t") || type.equals("ushort") || type.equals("uint16_t")
            || type.equals("uint64_t") || type.equals("ullong") || type.equals("size_t") || type.equals("uintptr_t");
    }

    public int typeBits(String type) {
        if (type.startsWith("ptr"))
            return 64;
        switch (normalizeType(type)) {
            case "byte":  return 8;
            case "word":  return 16;
            case "dword": return 32;
            case "qword": return 64;
            default:
                throw c.error("El tipo no es entero: " + type);
        }
    }

    public static long parseIntegerLiteral(String text) {
        String s = text.trim();

        if (s.startsWith("0x") || s.startsWith("0X"))
            return Long.parseUnsignedLong(s.substring(2), 16);

        if (s.startsWith("0b") || s.startsWith("0B"))
            return Long.parseUnsignedLong(s.substring(2), 2);

        if (s.startsWith("0o") || s.startsWith("0O"))
            return Long.parseUnsignedLong(s.substring(2), 8);

        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return Long.parseUnsignedLong(s);
        }
    }

    public static boolean looksNumeric(String text) {
        if (text.isEmpty())
            return false;

        try {
            Long.decode(text);
            return true;
        } catch (NumberFormatException ignored) {}

        try {
            Double.parseDouble(text);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    public void checkIntegerRange(long value, int bits, String type) {
        if (isUnsigned(type)) {
            if (bits == 64)
                return;

            long max = (1L << bits) - 1;

            if (value < 0 || value > max)
                throw c.error("Valor " + value + " fuera del rango de " + type + " [0.." + max + "]");

            return;
        }

        long min = -(1L << (bits - 1));
        long max = (1L << (bits - 1)) - 1;

        if (value < min || value > max)
            throw c.error("Valor " + value + " fuera del rango de " + type + " [" + min + ".." + max + "]");
    }

    public void checkUnsignedRange(long value, int bits, String what) {
        if (bits == 64)
            return;

        long max = (1L << bits) - 1;

        if (value < 0 || value > max)
            throw c.error("Valor " + value + " fuera del rango de " + what + " [0.." + max + "]");
    }

    public long packLittleEndian(String text, int maximumBytes) {
        if (text.length() > maximumBytes)
            throw c.error(
                "La cadena requiere " + text.length() + " bytes, pero el tipo solo admite " + maximumBytes
            );

        long result = 0;

        for (int i = 0; i < text.length(); i++)
            result |= (text.charAt(i) & 0xFFL) << (i * 8);

        return result;
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
