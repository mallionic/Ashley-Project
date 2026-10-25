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

package arch.Registers;

import Builder.Builder;

public class Ptr {

    private final Builder builder;

    public Ptr(Builder builder) {

        if (builder == null) {
            throw new IllegalArgumentException(
                "Builder no puede ser null"
            );
        }

        this.builder = builder;
    }

    public static class Register {

        public final int code;
        public final int bits;
        public final int low;
        public final boolean extended;

        public final boolean highByte;

        public Register(
            int code,
            int bits
        ) {

            this(
                code,
                bits,
                false
            );
        }

        public Register(
            int code,
            int bits,
            boolean highByte
        ) {

            this.code = code;
            this.bits = bits;
            this.low = code & 7;
            this.extended = code >= 8;
            this.highByte = highByte;
        }
    }

    public static Register register(String name) {

        if (name == null) {
            throw new IllegalArgumentException(
                "Registro null"
            );
        }

        String r = name.trim().toUpperCase();

        switch (r) {

            case "RAX":
                return new Register(0, 64);

            case "RCX":
                return new Register(1, 64);

            case "RDX":
                return new Register(2, 64);

            case "RBX":
                return new Register(3, 64);

            case "RSP":
                return new Register(4, 64);

            case "RBP":
                return new Register(5, 64);

            case "RSI":
                return new Register(6, 64);

            case "RDI":
                return new Register(7, 64);

            case "R8":
                return new Register(8, 64);

            case "R9":
                return new Register(9, 64);

            case "R10":
                return new Register(10, 64);

            case "R11":
                return new Register(11, 64);

            case "R12":
                return new Register(12, 64);

            case "R13":
                return new Register(13, 64);

            case "R14":
                return new Register(14, 64);

            case "R15":
                return new Register(15, 64);

            case "EAX":
                return new Register(0, 32);

            case "ECX":
                return new Register(1, 32);

            case "EDX":
                return new Register(2, 32);

            case "EBX":
                return new Register(3, 32);

            case "ESP":
                return new Register(4, 32);

            case "EBP":
                return new Register(5, 32);

            case "ESI":
                return new Register(6, 32);

            case "EDI":
                return new Register(7, 32);

            case "R8D":
                return new Register(8, 32);

            case "R9D":
                return new Register(9, 32);

            case "R10D":
                return new Register(10, 32);

            case "R11D":
                return new Register(11, 32);

            case "R12D":
                return new Register(12, 32);

            case "R13D":
                return new Register(13, 32);

            case "R14D":
                return new Register(14, 32);

            case "R15D":
                return new Register(15, 32);

            case "AX":
                return new Register(0, 16);

            case "CX":
                return new Register(1, 16);

            case "DX":
                return new Register(2, 16);

            case "BX":
                return new Register(3, 16);

            case "SP":
                return new Register(4, 16);

            case "BP":
                return new Register(5, 16);

            case "SI":
                return new Register(6, 16);

            case "DI":
                return new Register(7, 16);

            case "R8W":
                return new Register(8, 16);

            case "R9W":
                return new Register(9, 16);

            case "R10W":
                return new Register(10, 16);

            case "R11W":
                return new Register(11, 16);

            case "R12W":
                return new Register(12, 16);

            case "R13W":
                return new Register(13, 16);

            case "R14W":
                return new Register(14, 16);

            case "R15W":
                return new Register(15, 16);

            case "AL":
                return new Register(0, 8);

            case "CL":
                return new Register(1, 8);

            case "DL":
                return new Register(2, 8);

            case "BL":
                return new Register(3, 8);

            case "AH":
                return new Register(4, 8, true);

            case "CH":
                return new Register(5, 8, true);

            case "DH":
                return new Register(6, 8, true);

            case "BH":
                return new Register(7, 8, true);

            case "SPL":
                return new Register(4, 8);

            case "BPL":
                return new Register(5, 8);

            case "SIL":
                return new Register(6, 8);

            case "DIL":
                return new Register(7, 8);

            case "R8B":
                return new Register(8, 8);

            case "R9B":
                return new Register(9, 8);

            case "R10B":
                return new Register(10, 8);

            case "R11B":
                return new Register(11, 8);

            case "R12B":
                return new Register(12, 8);

            case "R13B":
                return new Register(13, 8);

            case "R14B":
                return new Register(14, 8);

            case "R15B":
                return new Register(15, 8);

            default:
                throw new IllegalArgumentException(
                    "Registro desconocido en puntero: " + name
                );
        }
    }

    public static class Memory {

        public Register base;
        public Register index;

        public long displacement;

        public int scale = 1;

        public int bits = 0;

        public boolean explicitSize = false;

        public boolean absolute = false;

        public Memory() {
        }
    }

    private static int parseSizePrefix(
        String operand
    ) {

        String s = operand.trim().toLowerCase();

        if (s.startsWith("byte ptr "))
            return 8;

        if (s.startsWith("word ptr "))
            return 16;

        if (s.startsWith("dword ptr "))
            return 32;

        if (s.startsWith("qword ptr "))
            return 64;

        if (s.startsWith("byte "))
            return 8;

        if (s.startsWith("word "))
            return 16;

        if (s.startsWith("dword "))
            return 32;

        if (s.startsWith("qword "))
            return 64;

        return 0;
    }

    private static String removeSizePrefix(
        String operand
    ) {

        String s = operand.trim();

        String lower = s.toLowerCase();

        String[] prefixes = {
            "byte ptr ",
            "word ptr ",
            "dword ptr ",
            "qword ptr ",
            "byte ",
            "word ",
            "dword ",
            "qword "
        };

        for (String prefix : prefixes) {

            if (lower.startsWith(prefix)) {

                return s
                    .substring(prefix.length())
                    .trim();
            }
        }

        return s;
    }

    public static int inferBits(
        String operand
    ) {

        return parseSizePrefix(operand);
    }

    private static long parseNumber(
        String input
    ) {

        String s = input.trim();

        if (s.isEmpty()) {
            throw new IllegalArgumentException(
                "Número vacío"
            );
        }

        boolean negative = false;

        if (s.startsWith("+")) {
            s = s.substring(1).trim();
        }

        if (s.startsWith("-")) {

            negative = true;

            s = s
                .substring(1)
                .trim();
        }

        long value;

        if (s.startsWith("0x")
            || s.startsWith("0X")) {

            value = Long.parseUnsignedLong(
                s.substring(2),
                16
            );

        } else if (
            s.endsWith("h")
            || s.endsWith("H")
        ) {

            value = Long.parseUnsignedLong(
                s.substring(
                    0,
                    s.length() - 1
                ),
                16
            );

        } else {

            value = new java.math.BigInteger(s).longValue();
        }

        return negative
            ? -value
            : value;
    }

    private static boolean isNumber(
        String s
    ) {

        try {

            parseNumber(s);

            return true;

        } catch (Exception e) {

            return false;
        }
    }

    private static long evaluateConstant(
        String expression
    ) {

        String s = expression
            .replace(" ", "")
            .replace("\t", "");

        if (s.isEmpty())
            return 0;

        return new ConstantParser(s).parse();
    }

    private static class ConstantParser {

        private final String s;

        private int pos;

        ConstantParser(String s) {

            this.s = s;
            this.pos = 0;
        }

        long parse() {

            long result = parseExpression();

            if (pos != s.length()) {

                throw new IllegalArgumentException(
                    "Expresión constante inválida: "
                    + s
                );
            }

            return result;
        }

        private long parseExpression() {

            long value = parseTerm();

            while (pos < s.length()) {

                char c = s.charAt(pos);

                if (c == '+') {

                    pos++;

                    value += parseTerm();

                } else if (c == '-') {

                    pos++;

                    value -= parseTerm();

                } else {

                    break;
                }
            }

            return value;
        }

        private long parseTerm() {

            long value = parseFactor();

            while (pos < s.length()) {

                char c = s.charAt(pos);

                if (c == '*') {

                    pos++;

                    value *= parseFactor();

                } else if (c == '/') {

                    pos++;

                    long divisor = parseFactor();

                    if (divisor == 0) {

                        throw new ArithmeticException(
                            "División por cero en dirección"
                        );
                    }

                    value /= divisor;

                } else {

                    break;
                }
            }

            return value;
        }

        private long parseFactor() {

            if (pos >= s.length()) {

                throw new IllegalArgumentException(
                    "Factor incompleto: " + s
                );
            }

            char c = s.charAt(pos);

            if (c == '+') {

                pos++;

                return parseFactor();
            }

            if (c == '-') {

                pos++;

                return -parseFactor();
            }

            if (c == '(') {

                pos++;

                long value = parseExpression();

                if (
                    pos >= s.length()
                    || s.charAt(pos) != ')'
                ) {

                    throw new IllegalArgumentException(
                        "Falta ')' en: " + s
                    );
                }

                pos++;

                return value;
            }

            int start = pos;

            if (
                c == '0'
                && pos + 1 < s.length()
                && (
                    s.charAt(pos + 1) == 'x'
                    || s.charAt(pos + 1) == 'X'
                )
            ) {

                pos += 2;

                while (
                    pos < s.length()
                    && isHexDigit(s.charAt(pos))
                ) {
                    pos++;
                }

            } else {

                while (
                    pos < s.length()
                    && (
                        Character.isDigit(
                            s.charAt(pos)
                        )
                        || (
                            pos > start
                            && isHexDigit(
                                s.charAt(pos)
                            )
                        )
                    )
                ) {
                    pos++;
                }

                if (
                    pos < s.length()
                    && (
                        s.charAt(pos) == 'h'
                        || s.charAt(pos) == 'H'
                    )
                ) {
                    pos++;
                }
            }

            if (start == pos) {

                throw new IllegalArgumentException(
                    "Número inválido en: " + s
                );
            }

            return parseNumber(
                s.substring(start, pos)
            );
        }

        private static boolean isHexDigit(
            char c
        ) {

            return Character.isDigit(c)
                || (
                    c >= 'a'
                    && c <= 'f'
                )
                || (
                    c >= 'A'
                    && c <= 'F'
                );
        }
    }

    private static boolean isRegister(
        String s
    ) {

        try {

            register(s);

            return true;

        } catch (Exception e) {

            return false;
        }
    }

    private static boolean containsRegister(
        String s
    ) {

        try {

            register(s);

            return true;

        } catch (Exception ignored) {
        }

        String lower =
            s.toLowerCase();

        String[] names = {

            "r15","r14","r13","r12",
            "r11","r10","r9","r8",

            "rax","rcx","rdx","rbx",
            "rsp","rbp","rsi","rdi",

            "r15d","r14d","r13d","r12d",
            "r11d","r10d","r9d","r8d",

            "eax","ecx","edx","ebx",
            "esp","ebp","esi","edi",

            "r15w","r14w","r13w","r12w",
            "r11w","r10w","r9w","r8w",

            "ax","cx","dx","bx",
            "sp","bp","si","di",

            "r15b","r14b","r13b","r12b",
            "r11b","r10b","r9b","r8b",

            "al","cl","dl","bl",
            "ah","ch","dh","bh",
            "spl","bpl","sil","dil"
        };

        for (String name : names) {

            if (lower.contains(name))
                return true;
        }

        return false;
    }

    public static Memory parse(
        String operand
    ) {

        if (operand == null) {

            throw new IllegalArgumentException(
                "Operando null"
            );
        }

        Memory mem = new Memory();

        mem.bits =
            parseSizePrefix(operand);

        mem.explicitSize =
            mem.bits != 0;

        String s =
            removeSizePrefix(operand);

        int open =
            s.indexOf('[');

        int close =
            s.lastIndexOf(']');

        if (
            open < 0
            || close < open
        ) {

            throw new IllegalArgumentException(
                "Puntero inválido: " + operand
            );
        }

        String expression =
            s.substring(
                open + 1,
                close
            )
            .replace(" ", "")
            .replace("\t", "");

        if (expression.isEmpty()) {

            throw new IllegalArgumentException(
                "Puntero vacío: " + operand
            );
        }

        java.util.List<String> terms =
            splitAddressTerms(expression);

        for (String raw : terms) {

            if (raw == null
                || raw.isEmpty())
                continue;

            String term = raw;

            boolean negative = false;

            if (term.charAt(0) == '-') {

                negative = true;

                term =
                    term.substring(1);
            }

            if (term.isEmpty())
                continue;

            if (containsTopLevelMultiply(term)) {

                String[] parts =
                    splitFirstMultiply(term);

                String left =
                    parts[0];

                String right =
                    parts[1];

                if (
                    isRegister(left)
                    && isNumber(right)
                ) {

                    Register r =
                        register(left);

                    int scale =
                        (int)evaluateConstant(right);

                    if (
                        scale != 1
                        && scale != 2
                        && scale != 4
                        && scale != 8
                    ) {

                        throw new IllegalArgumentException(
                            "Escala x86 debe ser 1, 2, 4 u 8: "
                            + scale
                        );
                    }

                    if (negative) {

                        throw new IllegalArgumentException(
                            "No se puede negar el index: "
                            + term
                        );
                    }

                    if (mem.index != null) {

                        throw new IllegalArgumentException(
                            "Solo puede existir un index"
                        );
                    }

                    if (r.bits != 64) {

                        throw new IllegalArgumentException(
                            "El registro de index debe ser de 64 bits: "
                            + left
                        );
                    }

                    mem.index = r;
                    mem.scale = scale;

                    continue;
                }

                if (
                    isNumber(left)
                    && isRegister(right)
                ) {

                    int scale =
                        (int)evaluateConstant(left);

                    if (
                        scale != 1
                        && scale != 2
                        && scale != 4
                        && scale != 8
                    ) {

                        throw new IllegalArgumentException(
                            "Escala x86 inválida: "
                            + scale
                        );
                    }

                    Register r =
                        register(right);

                    if (negative) {

                        throw new IllegalArgumentException(
                            "No se puede negar el index: "
                            + term
                        );
                    }

                    if (mem.index != null) {

                        throw new IllegalArgumentException(
                            "Solo puede existir un index"
                        );
                    }

                    if (r.bits != 64) {

                        throw new IllegalArgumentException(
                            "El registro de index debe ser de 64 bits: "
                            + right
                        );
                    }

                    mem.index = r;
                    mem.scale = scale;

                    continue;
                }
            }

            if (isRegister(term)) {

                if (negative) {

                    throw new IllegalArgumentException(
                        "No se puede negar un registro en dirección: "
                        + term
                    );
                }

                Register r =
                    register(term);

                if (r.bits != 64) {

                    throw new IllegalArgumentException(
                        "Registro inválido para dirección: "
                        + term
                    );
                }

                if (mem.base == null) {

                    mem.base = r;

                } else if (
                    mem.index == null
                ) {

                    mem.index = r;
                    mem.scale = 1;

                } else {

                    throw new IllegalArgumentException(
                        "Demasiados registros en dirección: "
                        + expression
                    );
                }

                continue;
            }

            if (!containsRegister(term)) {

                long value =
                    evaluateConstant(term);

                if (negative)
                    value = -value;

                mem.displacement += value;

                continue;
            }

            throw new IllegalArgumentException(
                "Término inválido en puntero: "
                + term
            );
        }

        if (
            mem.base == null
            && mem.index == null
        ) {

            mem.absolute = true;
        }

        return mem;
    }

    private static java.util.List<String>
    splitAddressTerms(
        String expression
    ) {

        java.util.List<String> result =
            new java.util.ArrayList<>();

        StringBuilder current =
            new StringBuilder();

        int depth = 0;

        for (int i = 0;
             i < expression.length();
             i++) {

            char c =
                expression.charAt(i);

            if (c == '(')
                depth++;

            if (c == ')')
                depth--;

            if (
                depth == 0
                && (
                    c == '+'
                    || c == '-'
                )
                && current.length() > 0
            ) {

                result.add(
                    current.toString()
                );

                current.setLength(0);

                if (c == '-') {

                    current.append('-');
                }

                continue;
            }

            current.append(c);
        }

        if (current.length() > 0) {

            result.add(
                current.toString()
            );
        }

        return result;
    }

    private static boolean containsTopLevelMultiply(
        String s
    ) {

        int depth = 0;

        for (int i = 0;
             i < s.length();
             i++) {

            char c = s.charAt(i);

            if (c == '(')
                depth++;

            else if (c == ')')
                depth--;

            else if (
                c == '*'
                && depth == 0
            )
                return true;
        }

        return false;
    }

    private static String[] splitFirstMultiply(
        String s
    ) {

        int depth = 0;

        for (int i = 0;
             i < s.length();
             i++) {

            char c = s.charAt(i);

            if (c == '(')
                depth++;

            else if (c == ')')
                depth--;

            else if (
                c == '*'
                && depth == 0
            ) {

                return new String[] {

                    s.substring(0, i),

                    s.substring(i + 1)
                };
            }
        }

        throw new IllegalArgumentException(
            "Multiplicación inválida: " + s
        );
    }

    private static int modrm(
        int mod,
        int reg,
        int rm
    ) {

        return
            ((mod & 3) << 6)
            | ((reg & 7) << 3)
            | (rm & 7);
    }

    private static int sib(
        int scale,
        int index,
        int base
    ) {

        int scaleBits;

        switch (scale) {

            case 1:
                scaleBits = 0;
                break;

            case 2:
                scaleBits = 1;
                break;

            case 4:
                scaleBits = 2;
                break;

            case 8:
                scaleBits = 3;
                break;

            default:

                throw new IllegalArgumentException(
                    "Escala inválida: "
                    + scale
                );
        }

        return
            (scaleBits << 6)
            | ((index & 7) << 3)
            | (base & 7);
    }

    private void emit8(
        long value
    ) {

        builder.file_write(
            (int)(value & 0xFF)
        );
    }

    private void emit32(
        long value
    ) {

        int v = (int)value;

        builder.file_write(

            v & 0xFF,

            (v >>> 8) & 0xFF,

            (v >>> 16) & 0xFF,

            (v >>> 24) & 0xFF
        );
    }

    private void emitRex(
        boolean w,
        boolean r,
        boolean x,
        boolean b
    ) {

        int rex = 0x40;

        if (w)
            rex |= 0x08;

        if (r)
            rex |= 0x04;

        if (x)
            rex |= 0x02;

        if (b)
            rex |= 0x01;

        if (rex != 0x40) {

            builder.file_write(rex);
        }
    }

    public void emitRexForMemory(
        int regField,
        String operand,
        int bits
    ) {

        Memory mem =
            parse(operand);

        boolean w =
            bits == 64;

        boolean r =
            (regField & 8) != 0;

        boolean x =
            mem.index != null
            && mem.index.extended;

        boolean b =
            mem.base != null
            && mem.base.extended;

        boolean rexRequired =
            w
            || r
            || x
            || b;

        if (
            bits == 8
            && (regField & 7) >= 4
        ) {

            rexRequired = true;
        }

        if (!rexRequired)
            return;

        emitRex(
            w,
            r,
            x,
            b
        );
    }

    public void emitModRM(
        int regField,
        String operand
    ) {

        Memory mem =
            parse(operand);

        emitMemoryNoRex(
            mem,
            regField
        );
    }

    public void emitMemory(
        Memory mem,
        int regField,
        boolean rexW,
        boolean rexR
    ) {

        if (mem == null) {

            throw new IllegalArgumentException(
                "Memory null"
            );
        }

        emitRex(
            rexW,
            rexR,
            mem.index != null
                && mem.index.extended,
            mem.base != null
                && mem.base.extended
        );

        emitMemoryNoRex(
            mem,
            regField
        );
    }

    private void emitMemoryNoRex(
        Memory mem,
        int regField
    ) {

        if (mem == null) {

            throw new IllegalArgumentException(
                "Memory null"
            );
        }

        int reg =
            regField & 7;

        if (mem.absolute) {

            builder.file_write(
                modrm(
                    0,
                    reg,
                    4
                )
            );

            builder.file_write(
                sib(
                    1,
                    4,
                    5
                )
            );

            emit32(
                mem.displacement
            );

            return;
        }

        Register base =
            mem.base;

        Register index =
            mem.index;

        if (
            base == null
            && index != null
        ) {

            builder.file_write(
                modrm(
                    0,
                    reg,
                    4
                )
            );

            builder.file_write(
                sib(
                    mem.scale,
                    index.low,
                    5
                )
            );

            emit32(
                mem.displacement
            );

            return;
        }

        if (base == null) {

            throw new IllegalArgumentException(
                "Dirección sin base/index"
            );
        }

        boolean hasSib =
            index != null
            || base.low == 4;

        boolean zeroDisp =
            mem.displacement == 0
            && base.low != 5;

        boolean disp8 =
            mem.displacement >= -128
            && mem.displacement <= 127;

        int mod;

        if (zeroDisp) {

            mod = 0;

        } else if (disp8) {

            mod = 1;

        } else {

            mod = 2;
        }

        if (
            base.low == 5
            && mod == 0
        ) {

            mod = 1;
        }

        if (!hasSib) {

            builder.file_write(
                modrm(
                    mod,
                    reg,
                    base.low
                )
            );

        } else {

            builder.file_write(
                modrm(
                    mod,
                    reg,
                    4
                )
            );

            int indexCode =
                index == null
                ? 4
                : index.low;

            builder.file_write(
                sib(
                    mem.scale,
                    indexCode,
                    base.low
                )
            );
        }

        if (mod == 1) {

            if (
                base.low == 5
                && mem.displacement == 0
            ) {

                emit8(0);

            } else {

                emit8(
                    mem.displacement
                );
            }

        } else if (mod == 2) {

            emit32(
                mem.displacement
            );
        }
    }

    public Memory memory(
        String expression
    ) {

        return parse(expression);
    }

    public void LEA(
        String dst,
        String address
    ) {

        Register d =
            register(dst);

        if (
            d.bits != 16
            && d.bits != 32
            && d.bits != 64
        ) {

            throw new IllegalArgumentException(
                "LEA requiere registro 16/32/64"
            );
        }

        Memory mem =
            parse(address);

        if (d.bits == 16) {

            builder.file_write(0x66);
        }

        emitRex(
            d.bits == 64,
            d.extended,
            mem.index != null
                && mem.index.extended,
            mem.base != null
                && mem.base.extended
        );

        builder.file_write(
            0x8D
        );

        emitMemoryNoRex(
            mem,
            d.low
        );
    }

    public void LEA_REAL(
        String dst,
        String address
    ) {

        LEA(
            dst,
            address
        );
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
