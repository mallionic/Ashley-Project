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

final class VarsArithmetic {

    private final Vars core;

    VarsArithmetic(Vars core) {
        this.core = core;
    }

    private void aluBit(String op, String a, String b) {
        switch (op) {
            case "AND": core.alu.AND(a, b); break;
            case "OR":  core.alu.OR(a, b);  break;
            default:    core.alu.XOR(a, b); break;
        }
    }

    private void aluBit(String op, String a, long v) {
        switch (op) {
            case "AND": core.alu.AND(a, v); break;
            case "OR":  core.alu.OR(a, v);  break;
            default:    core.alu.XOR(a, v); break;
        }
    }

    public void Bitwise(String op, String name, String source) {

        Vars.Variable dst = core.variable(name);

        core.ensureMutable(dst);
        core.ensureInteger(dst);

        Vars.Variable rhs = core.localVariable(source);

        if (rhs != null) {

            core.ensureSameType(dst, rhs);

            int bits = dst.bits();
            String a = core.temporaryInteger(bits);
            String b = core.temporaryInteger2(bits);

            core.loadInteger(dst, a);
            core.loadInteger(rhs, b);
            aluBit(op, a, b);
            core.storeInteger(dst, a);

            return;
        }

        Vars.Parameter p = core.parameter(source);

        if (p != null) {

            if (p.type != dst.type)
                throw new IllegalArgumentException(
                    "Tipos incompatibles: " + name + " y " + source
                );

            int bits = dst.bits();
            String a = core.temporaryInteger(bits);
            String b = core.temporaryInteger2(bits);

            core.loadInteger(dst, a);
            core.loadIntegerParameter(p, b);
            aluBit(op, a, b);
            core.storeInteger(dst, a);

            return;
        }

        throw new IllegalArgumentException(
            "Variable/parámetro no existe: " + source
        );
    }

    public void Bitwise(String op, String name, long value) {

        Vars.Variable dst = core.variable(name);

        core.ensureMutable(dst);
        core.ensureInteger(dst);

        core.checkIntegerRange(dst.bits(), value);

        String a = core.temporaryInteger(dst.bits());

        core.loadInteger(dst, a);
        aluBit(op, a, value);
        core.storeInteger(dst, a);
    }

    public void Add(
        String name,
        String source
    ) {

        Vars.Variable dst =
            core.variable(name);

        core.ensureMutable(dst);

        Vars.Variable rhs =
            core.localVariable(source);

        if (rhs != null) {

            core.ensureSameType(
                dst,
                rhs
            );

            if (dst.isFloat()) {

                floatBinary(
                    dst,
                    rhs,
                    "ADD"
                );

                return;
            }

            core.ensureInteger(dst);

            int bits =
                dst.bits();

            String a =
                core.temporaryInteger(bits);

            String b =
                core.temporaryInteger2(bits);

            core.loadInteger(
                dst,
                a
            );

            core.loadInteger(
                rhs,
                b
            );

            core.alu.ADD(
                a,
                b
            );

            core.storeInteger(
                dst,
                a
            );

            return;
        }

        Vars.Parameter p =
            core.parameter(source);

        if (p != null) {

            if (p.type != dst.type) {

                throw new IllegalArgumentException(
                    "Tipos incompatibles: " +
                    name +
                    " y " +
                    source
                );
            }

            if (dst.isFloat()) {

                throw new IllegalArgumentException(
                    "ADD FLOAT con parámetro no soportado: " +
                    source
                );
            }

            core.ensureInteger(dst);

            int bits =
                dst.bits();

            String a =
                core.temporaryInteger(bits);

            String b =
                core.temporaryInteger2(bits);

            core.loadInteger(
                dst,
                a
            );

            core.loadIntegerParameter(
                p,
                b
            );

            core.alu.ADD(
                a,
                b
            );

            core.storeInteger(
                dst,
                a
            );

            return;
        }

        throw new IllegalArgumentException(
            "Variable/parámetro no existe: " +
            source
        );
    }

    public void Add(
        String name,
        long value
    ) {

        Vars.Variable dst =
            core.variable(name);

        core.ensureMutable(dst);
        core.ensureInteger(dst);

        core.checkIntegerRange(
            dst.bits(),
            value
        );

        String a =
            core.temporaryInteger(
                dst.bits()
            );

        core.loadInteger(
            dst,
            a
        );

        core.alu.ADD(
            a,
            value
        );

        core.storeInteger(
            dst,
            a
        );
    }

    public void Add(
        String name,
        float value
    ) {

        Vars.Variable dst =
            core.variable(name);

        core.ensureMutable(dst);

        if (dst.type != Vars.FLOAT32) {

            throw new IllegalArgumentException(
                "FLOAT32 requerido"
            );
        }

        floatImmediateBinary(
            dst,
            value,
            "ADD"
        );
    }

    public void Add(
        String name,
        double value
    ) {

        Vars.Variable dst =
            core.variable(name);

        core.ensureMutable(dst);

        if (dst.type != Vars.FLOAT64) {

            throw new IllegalArgumentException(
                "FLOAT64 requerido"
            );
        }

        floatImmediateBinary(
            dst,
            value,
            "ADD"
        );
    }

    public void Sub(
        String name,
        String source
    ) {

        Vars.Variable dst =
            core.variable(name);

        core.ensureMutable(dst);

        Vars.Variable rhs =
            core.localVariable(source);

        if (rhs != null) {

            core.ensureSameType(
                dst,
                rhs
            );

            if (dst.isFloat()) {

                floatBinary(
                    dst,
                    rhs,
                    "SUB"
                );

                return;
            }

            core.ensureInteger(dst);

            int bits =
                dst.bits();

            String a =
                core.temporaryInteger(bits);

            String b =
                core.temporaryInteger2(bits);

            core.loadInteger(
                dst,
                a
            );

            core.loadInteger(
                rhs,
                b
            );

            core.alu.SUB(
                a,
                b
            );

            core.storeInteger(
                dst,
                a
            );

            return;
        }

        Vars.Parameter p =
            core.parameter(source);

        if (p != null) {

            if (p.type != dst.type) {

                throw new IllegalArgumentException(
                    "Tipos incompatibles: " +
                    name +
                    " y " +
                    source
                );
            }

            if (dst.isFloat()) {

                throw new IllegalArgumentException(
                    "SUB FLOAT con parámetro no soportado: " +
                    source
                );
            }

            core.ensureInteger(dst);

            int bits =
                dst.bits();

            String a =
                core.temporaryInteger(bits);

            String b =
                core.temporaryInteger2(bits);

            core.loadInteger(
                dst,
                a
            );

            core.loadIntegerParameter(
                p,
                b
            );

            core.alu.SUB(
                a,
                b
            );

            core.storeInteger(
                dst,
                a
            );

            return;
        }

        throw new IllegalArgumentException(
            "Variable/parámetro no existe: " +
            source
        );
    }

    public void Sub(
        String name,
        long value
    ) {

        Vars.Variable dst =
            core.variable(name);

        core.ensureMutable(dst);
        core.ensureInteger(dst);

        core.checkIntegerRange(
            dst.bits(),
            value
        );

        String a =
            core.temporaryInteger(
                dst.bits()
            );

        core.loadInteger(
            dst,
            a
        );

        core.alu.SUB(
            a,
            value
        );

        core.storeInteger(
            dst,
            a
        );
    }

    public void Sub(
        String name,
        float value
    ) {

        Vars.Variable dst =
            core.variable(name);

        core.ensureMutable(dst);

        if (dst.type != Vars.FLOAT32) {

            throw new IllegalArgumentException(
                "FLOAT32 requerido"
            );
        }

        floatImmediateBinary(
            dst,
            value,
            "SUB"
        );
    }

    public void Sub(
        String name,
        double value
    ) {

        Vars.Variable dst =
            core.variable(name);

        core.ensureMutable(dst);

        if (dst.type != Vars.FLOAT64) {

            throw new IllegalArgumentException(
                "FLOAT64 requerido"
            );
        }

        floatImmediateBinary(
            dst,
            value,
            "SUB"
        );
    }

    public void Multiply(
        String name,
        String source
    ) {

        Vars.Variable dst =
            core.variable(name);

        core.ensureMutable(dst);

        Vars.Variable rhs =
            core.localVariable(source);

        Vars.Parameter rhsParameter =
            core.parameter(source);

        if (rhs == null &&
            rhsParameter == null) {

            throw new IllegalArgumentException(
                "Variable/parámetro no existe: " +
                source
            );
        }

        if (dst.isFloat()) {

            if (rhsParameter != null) {

                if (!rhsParameter.isFloat()) {

                    throw new IllegalArgumentException(
                        "Tipos incompatibles: " +
                        dst.name +
                        " y " +
                        source
                    );
                }

                if (dst.type != rhsParameter.type) {

                    throw new IllegalArgumentException(
                        "Tipos floating incompatibles: " +
                        dst.name +
                        " y " +
                        source
                    );
                }

                core.loadFloat(
                    dst,
                    "XMM0"
                );

                core.loadFloatParameter(
                    rhsParameter,
                    "XMM1"
                );

                core.emitSseArithmetic(
                    "MUL",
                    0,
                    1,
                    dst.type
                );

                core.storeFloat(
                    dst,
                    "XMM0"
                );

                return;
            }

            core.ensureSameType(
                dst,
                rhs
            );

            floatBinary(
                dst,
                rhs,
                "MUL"
            );

            return;
        }

        core.ensureInteger(dst);

        if (rhsParameter != null) {

            if (!rhsParameter.isInteger()) {

                throw new IllegalArgumentException(
                    "El parámetro no es entero: " +
                    source
                );
            }

            if (dst.type != rhsParameter.type) {

                throw new IllegalArgumentException(
                    "Tipos incompatibles: " +
                    dst.name +
                    " y " +
                    source
                );
            }

            int bits =
                dst.bits();

            String a =
                core.temporaryInteger(bits);

            String b =
                core.temporaryInteger2(bits);

            core.loadInteger(
                dst,
                a
            );

            core.loadIntegerParameter(
                rhsParameter,
                b
            );

            core.alu.IMUL(
                a,
                b
            );

            core.storeInteger(
                dst,
                a
            );

            return;
        }

        core.ensureSameType(
            dst,
            rhs
        );

        int bits =
            dst.bits();

        String a =
            core.temporaryInteger(bits);

        String b =
            core.temporaryInteger2(bits);

        core.loadInteger(
            dst,
            a
        );

        core.loadInteger(
            rhs,
            b
        );

        core.alu.IMUL(
            a,
            b
        );

        core.storeInteger(
            dst,
            a
        );
    }

    public void ShiftLeft(String name, int count) {
        shiftImmediate(name, count, true);
    }

    public void ShiftRight(String name, int count) {
        shiftImmediate(name, count, false);
    }

    private void shiftImmediate(
        String name,
        int count,
        boolean left
    ) {

        if (count == 0)
            return;

        Vars.Variable dst =
            core.variable(name);

        core.ensureMutable(dst);
        core.ensureInteger(dst);

        String a =
            core.temporaryInteger(
                dst.bits()
            );

        core.loadInteger(dst, a);

        if (left)
            core.alu.SHL(a, count);
        else if (core.unsignedOp)
            core.alu.SHR(a, count);
        else
            core.alu.SAR(a, count);

        core.storeInteger(dst, a);
    }

    public void Multiply(
        String name,
        long value
    ) {

        Vars.Variable dst =
            core.variable(name);

        core.ensureMutable(dst);
        core.ensureInteger(dst);

        core.checkIntegerRange(
            dst.bits(),
            value
        );

        String a =
            core.temporaryInteger(
                dst.bits()
            );

        core.loadInteger(
            dst,
            a
        );

        core.alu.IMUL(
            a,
            a,
            value
        );

        core.storeInteger(
            dst,
            a
        );
    }

    public void Multiply(
        String name,
        float value
    ) {

        Vars.Variable dst =
            core.variable(name);

        core.ensureMutable(dst);

        if (dst.type != Vars.FLOAT32) {

            throw new IllegalArgumentException(
                "FLOAT32 requerido"
            );
        }

        floatImmediateBinary(
            dst,
            value,
            "MUL"
        );
    }

    public void Multiply(
        String name,
        double value
    ) {

        Vars.Variable dst =
            core.variable(name);

        core.ensureMutable(dst);

        if (dst.type != Vars.FLOAT64) {

            throw new IllegalArgumentException(
                "FLOAT64 requerido"
            );
        }

        floatImmediateBinary(
            dst,
            value,
            "MUL"
        );
    }

    private void floatBinary(
        Vars.Variable dst,
        Vars.Variable rhs,
        String op
    ) {

        core.ensureFloat(dst);
        core.ensureFloat(rhs);
        core.ensureSameType(dst, rhs);

        core.loadFloat(
            dst,
            "XMM0"
        );

        core.loadFloat(
            rhs,
            "XMM1"
        );

        core.emitSseArithmetic(
            op,
            0,
            1,
            dst.type
        );

        core.storeFloat(
            dst,
            "XMM0"
        );
    }

    private void floatImmediateBinary(
        Vars.Variable dst,
        double value,
        String op
    ) {

        core.ensureFloat(dst);

        core.loadFloat(
            dst,
            "XMM0"
        );

        if (dst.type == Vars.FLOAT32) {

            int bits =
                Float.floatToRawIntBits(
                    (float) value
                );

            core.mov.MOV(
                "EAX",
                bits & 0xFFFFFFFFL
            );

            core.emitMovD_XmmFromEax(
                1
            );

        } else {

            long bits =
                Double.doubleToRawLongBits(
                    value
                );

            core.mov.MOV(
                "RAX",
                bits
            );

            core.emitMovQ_XmmFromRax(
                1
            );
        }

        core.emitSseArithmetic(
            op,
            0,
            1,
            dst.type
        );

        core.storeFloat(
            dst,
            "XMM0"
        );
    }

    public void Divide(
        String name,
        String source
    ) {

        Vars.Variable dst =
            core.variable(name);

        core.ensureMutable(dst);

        Vars.Variable rhs =
            core.localVariable(source);

        if (rhs != null) {

            core.ensureSameType(
                dst,
                rhs
            );

            if (dst.isFloat()) {

                floatBinary(
                    dst,
                    rhs,
                    "DIV"
                );

                return;
            }

            core.ensureInteger(dst);

            divideInteger(
                dst,
                rhs
            );

            return;
        }

        Vars.Parameter parameter =
            core.parameter(source);

        if (parameter != null) {

            if (parameter.type != dst.type) {

                throw new IllegalArgumentException(
                    "Tipos incompatibles: " +
                    name +
                    " y " +
                    source
                );
            }

            if (dst.isFloat()) {

                throw new IllegalArgumentException(
                    "DIV FLOAT con parámetro no soportado: " +
                    source
                );
            }

            core.ensureInteger(dst);

            int bits =
                dst.bits();

            String divisor =
                core.temporaryInteger2(bits);

            core.loadIntegerParameter(
                parameter,
                divisor
            );

            divideIntegerWithRegister(
                dst,
                divisor
            );

            return;
        }

        throw new IllegalArgumentException(
            "Variable/parámetro no existe: " +
            source
        );
    }

    public void Divide(
        String name,
        long divisor
    ) {

        Vars.Variable dst =
            core.variable(name);

        core.ensureMutable(dst);
        core.ensureInteger(dst);

        String d =
            core.temporaryInteger2(
                dst.bits()
            );

        core.mov.MOV(
            d,
            divisor
        );

        divideIntegerWithRegister(
            dst,
            d
        );
    }

    public void Divide(
        String name,
        float divisor
    ) {

        Vars.Variable dst =
            core.variable(name);

        core.ensureMutable(dst);

        if (dst.type != Vars.FLOAT32) {

            throw new IllegalArgumentException(
                "FLOAT32 requerido"
            );
        }

        floatImmediateBinary(
            dst,
            divisor,
            "DIV"
        );
    }

    public void Divide(
        String name,
        double divisor
    ) {

        Vars.Variable dst =
            core.variable(name);

        core.ensureMutable(dst);

        if (dst.type != Vars.FLOAT64) {

            throw new IllegalArgumentException(
                "FLOAT64 requerido"
            );
        }

        floatImmediateBinary(
            dst,
            divisor,
            "DIV"
        );
    }

    private void divideInteger(
        Vars.Variable dst,
        Vars.Variable rhs
    ) {

        String dividend =
            core.temporaryInteger(
                dst.bits()
            );

        String divisor =
            core.temporaryInteger2(
                dst.bits()
            );

        core.loadInteger(
            dst,
            dividend
        );

        core.loadInteger(
            rhs,
            divisor
        );

        divideIntegerWithRegister(
            dst,
            divisor
        );
    }

    private void divideIntegerWithRegister(
        Vars.Variable dst,
        String divisor
    ) {

        int bits =
            dst.bits();

        String dividend =
            core.temporaryInteger(bits);

        core.loadInteger(dst, dividend);

        if (bits == 64) {

            if (!dividend.equals("RAX")) {

                core.mov.MOV(
                    "RAX",
                    dividend
                );
            }

            core.builder.file_write(
                0x48,
                0x99
            );

            core.emitIDIV(
                divisor,
                64
            );

            if (!dividend.equals("RAX")) {

                core.mov.MOV(
                    dividend,
                    "RAX"
                );
            }

            core.storeInteger(
                dst,
                dividend
            );

            return;
        }

        if (bits == 32) {

            if (!dividend.equals("EAX")) {

                core.mov.MOV(
                    "EAX",
                    dividend
                );
            }

            core.builder.file_write(
                0x99
            );

            core.emitIDIV(
                divisor,
                32
            );

            if (!dividend.equals("EAX")) {

                core.mov.MOV(
                    dividend,
                    "EAX"
                );
            }

            core.storeInteger(
                dst,
                dividend
            );

            return;
        }

        if (bits == 16) {

            if (!dividend.equals("AX")) {

                core.mov.MOV(
                    "AX",
                    dividend
                );
            }

            core.builder.file_write(
                0x66,
                0x99
            );

            core.emitIDIV(
                divisor,
                16
            );

            if (!dividend.equals("AX")) {

                core.mov.MOV(
                    dividend,
                    "AX"
                );
            }

            core.storeInteger(
                dst,
                dividend
            );

            return;
        }

        if (bits == 8) {

            if (!dividend.equals("AL")) {

                core.mov.MOV(
                    "AL",
                    dividend
                );
            }

            core.builder.file_write(0x66, 0x98);

            core.emitIDIV(
                divisor,
                8
            );

            if (!dividend.equals("AL")) {

                core.mov.MOV(
                    dividend,
                    "AL"
                );
            }

            core.storeInteger(
                dst,
                dividend
            );

            return;
        }

        throw new IllegalArgumentException(
            "DIV inválido"
        );
    }

    public void Mod(
        String name,
        String source
    ) {

        Vars.Variable dst =
            core.variable(name);

        core.ensureMutable(dst);
        core.ensureInteger(dst);

        Vars.Variable rhs =
            core.localVariable(source);

        if (rhs != null) {

            core.ensureInteger(rhs);
            core.ensureSameType(
                dst,
                rhs
            );

            String divisor =
                core.temporaryInteger2(
                    dst.bits()
                );

            core.loadInteger(
                rhs,
                divisor
            );

            modInteger(
                dst,
                divisor
            );

            return;
        }

        Vars.Parameter parameter =
            core.parameter(source);

        if (parameter != null) {

            if (!parameter.isInteger()) {

                throw new IllegalArgumentException(
                    "El parámetro no es entero: " +
                    source
                );
            }

            if (parameter.type != dst.type) {

                throw new IllegalArgumentException(
                    "Tipos incompatibles: " +
                    dst.name +
                    " y " +
                    source
                );
            }

            String divisor =
                core.temporaryInteger2(
                    dst.bits()
                );

            core.loadIntegerParameter(
                parameter,
                divisor
            );

            modInteger(
                dst,
                divisor
            );

            return;
        }

        throw new IllegalArgumentException(
            "Variable/parámetro no existe: " +
            source
        );
    }

    public void Mod(
        String name,
        long divisor
    ) {

        Vars.Variable dst =
            core.variable(name);

        core.ensureMutable(dst);
        core.ensureInteger(dst);

        String d =
            core.temporaryInteger2(
                dst.bits()
            );

        core.mov.MOV(
            d,
            divisor
        );

        modInteger(
            dst,
            d
        );
    }

    private void modInteger(
        Vars.Variable dst,
        String divisor
    ) {

        int bits =
            dst.bits();

        if (bits == 64) {

            core.loadInteger(
                dst,
                "RAX"
            );

            core.builder.file_write(
                0x48,
                0x99
            );

            core.emitIDIV(
                divisor,
                64
            );

            core.storeInteger(
                dst,
                "RDX"
            );

            return;
        }

        if (bits == 32) {

            core.loadInteger(
                dst,
                "EAX"
            );

            core.builder.file_write(
                0x99
            );

            core.emitIDIV(
                divisor,
                32
            );

            core.storeInteger(
                dst,
                "EDX"
            );

            return;
        }

        if (bits == 16) {

            core.loadInteger(
                dst,
                "AX"
            );

            core.builder.file_write(
                0x66,
                0x99
            );

            core.emitIDIV(
                divisor,
                16
            );

            core.storeInteger(
                dst,
                "DX"
            );

            return;
        }

        if (bits == 8) {

            core.loadInteger(
                dst,
                "AL"
            );

            core.builder.file_write(0x66, 0x98);

            core.emitIDIV(
                divisor,
                8
            );

            core.storeInteger(
                dst,
                "AH"
            );

            return;
        }

        throw new IllegalArgumentException(
            "MOD inválido"
        );
    }

    public void Negate(
        String name
    ) {

        Vars.Variable v =
            core.variable(name);

        core.ensureMutable(v);

        if (v.isFloat()) {

            if (v.type == Vars.FLOAT32) {

                core.loadFloatConstant(
                    core.newConstantFloat(
                        Vars.FLOAT32,
                        0.0
                    ),
                    0
                );

            } else {

                core.loadFloatConstant(
                    core.newConstantFloat(
                        Vars.FLOAT64,
                        0.0
                    ),
                    0
                );
            }

            core.loadFloat(
                v,
                "XMM1"
            );

            core.emitSseArithmetic(
                "SUB",
                0,
                1,
                v.type
            );

            core.storeFloat(
                v,
                "XMM0"
            );

            return;
        }

        String r =
            core.temporaryInteger(
                v.bits()
            );

        core.loadInteger(
            v,
            r
        );

        core.alu.NEG(
            r
        );

        core.storeInteger(
            v,
            r
        );
    }

    public void Increment(
        String name
    ) {

        Vars.Variable v =
            core.variable(name);

        core.ensureMutable(v);

        if (v.isFloat()) {

            if (v.type == Vars.FLOAT32) {

                Add(
                    name,
                    1.0f
                );

            } else {

                Add(
                    name,
                    1.0
                );
            }

            return;
        }

        String r =
            core.temporaryInteger(
                v.bits()
            );

        core.loadInteger(
            v,
            r
        );

        core.alu.INC(
            r
        );

        core.storeInteger(
            v,
            r
        );
    }

    public void Decrement(
        String name
    ) {

        Vars.Variable v =
            core.variable(name);

        core.ensureMutable(v);

        if (v.isFloat()) {

            if (v.type == Vars.FLOAT32) {

                Sub(
                    name,
                    1.0f
                );

            } else {

                Sub(
                    name,
                    1.0
                );
            }

            return;
        }

        String r =
            core.temporaryInteger(
                v.bits()
            );

        core.loadInteger(
            v,
            r
        );

        core.alu.DEC(
            r
        );

        core.storeInteger(
            v,
            r
        );
    }

public void Jcc(
    String condition,
    String label
) {
    core.control.jccNamed(condition, label);
}

    public void Cmp(
        String name,
        String source
    ) {

        Vars.Variable lhs =
            core.variable(name);

        core.ensureInteger(lhs);

        Vars.Variable rhs =
            core.localVariable(source);

        if (rhs != null) {

            core.ensureInteger(rhs);

            core.ensureSameType(
                lhs,
                rhs
            );

            int bits =
                lhs.bits();

            String a =
                core.temporaryInteger(
                    bits
                );

            String b =
                core.temporaryInteger2(
                    bits
                );

            core.loadInteger(
                lhs,
                a
            );

            core.loadInteger(
                rhs,
                b
            );

            core.alu.CMP(
                a,
                b
            );

            return;
        }

        Vars.Parameter parameter =
            core.parameter(source);

        if (parameter != null) {

            if (!parameter.isInteger()) {

                throw new IllegalArgumentException(
                    "El parámetro no es entero: " +
                    source
                );
            }

            if (lhs.type != parameter.type) {

                throw new IllegalArgumentException(
                    "Tipos incompatibles: " +
                    lhs.name +
                    " y " +
                    source
                );
            }

            int bits =
                lhs.bits();

            String a =
                core.temporaryInteger(
                    bits
                );

            String b =
                core.temporaryInteger2(
                    bits
                );

            core.loadInteger(
                lhs,
                a
            );

            core.loadIntegerParameter(
                parameter,
                b
            );

            core.alu.CMP(
                a,
                b
            );

            return;
        }

        throw new IllegalArgumentException(
            "Variable/parámetro no existe: " +
            source
        );
    }

    public void Cmp(
        String name,
        long value
    ) {

        Vars.Variable lhs =
            core.variable(name);

        core.ensureInteger(lhs);

        int bits =
            lhs.bits();

        String a =
            core.temporaryInteger(
                bits
            );

        core.loadInteger(
            lhs,
            a
        );

        core.alu.CMP(
            a,
            value
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
