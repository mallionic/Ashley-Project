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

package arch;

import Builder.Builder;
import arch.Registers.Ptr;

public class FloatingPoint {

    private final Builder builder;
    private final Ptr ptr;

    public FloatingPoint(Builder builder) {

        if (builder == null) {
            throw new IllegalArgumentException(
                "Builder no puede ser null"
            );
        }

        this.builder = builder;
        this.ptr = new Ptr(builder);
    }

    private int xmm(String name) {

        if (name == null) {
            throw new IllegalArgumentException(
                "Registro XMM null"
            );
        }

        String r = name.trim().toUpperCase();

        if (!r.startsWith("XMM")) {
            throw new IllegalArgumentException(
                "No es un registro XMM: " + name
            );
        }

        String number = r.substring(3);

        if (number.length() == 0) {
            throw new IllegalArgumentException(
                "Registro XMM inválido: " + name
            );
        }

        int n;

        try {
            n = Integer.parseInt(number);
        } catch (NumberFormatException e) {

            throw new IllegalArgumentException(
                "Registro XMM inválido: " + name
            );
        }

        if (n < 0 || n > 15) {
            throw new IllegalArgumentException(
                "Registro XMM fuera de rango: " + name
            );
        }

        return n;
    }

    private boolean isXMM(String name) {

        if (name == null)
            return false;

        String r = name.trim().toUpperCase();

        if (!r.startsWith("XMM"))
            return false;

        try {

            int n = Integer.parseInt(
                r.substring(3)
            );

            return n >= 0 && n <= 15;

        } catch (Exception e) {

            return false;
        }
    }

    private void checkGeneralRegister(int reg) {

        if (reg < 0 || reg > 15) {
            throw new IllegalArgumentException(
                "Registro entero fuera de rango: " + reg
            );
        }
    }

    private boolean isMemory(String operand) {

        if (operand == null)
            return false;

        String s = operand.trim();

        return s.contains("[")
            && s.contains("]");
    }

    private Ptr.Memory memory(String operand) {

        return ptr.memory(operand);
    }

    private int modrm(
        int mod,
        int reg,
        int rm
    ) {

        return ((mod & 3) << 6)
             | ((reg & 7) << 3)
             | (rm & 7);
    }

    private void emitRex(
        boolean w,
        boolean r,
        boolean x,
        boolean b
    ) {

        int value = 0x40;

        if (w)
            value |= 0x08;

        if (r)
            value |= 0x04;

        if (x)
            value |= 0x02;

        if (b)
            value |= 0x01;

        if (value != 0x40) {
            builder.file_write(value);
        }
    }

    private void emitRexXMM(
        int dst,
        int src
    ) {

        emitRex(
            false,
            dst >= 8,
            false,
            src >= 8
        );
    }

    private void emitRexMemory(
        int xmmReg,
        String operand
    ) {

        ptr.emitRexForMemory(
            xmmReg,
            operand,
            0
        );
    }

    private void emitMemory(
        int regField,
        String operand
    ) {

        ptr.emitModRM(
            regField,
            operand
        );
    }

    public void MOVSD(
        String dst,
        String src
    ) {

        if (isXMM(dst) && isXMM(src)) {

            int d = xmm(dst);
            int s = xmm(src);

            builder.file_write(0xF2);

            emitRexXMM(
                d,
                s
            );

            builder.file_write(0x0F);
            builder.file_write(0x10);

            builder.file_write(
                modrm(
                    3,
                    d,
                    s
                )
            );

            return;
        }

        if (isXMM(dst) && isMemory(src)) {

            int d = xmm(dst);

            builder.file_write(0xF2);

            emitRexMemory(
                d,
                src
            );

            builder.file_write(0x0F);
            builder.file_write(0x10);

            emitMemory(
                d,
                src
            );

            return;
        }

        if (isMemory(dst) && isXMM(src)) {

            int s = xmm(src);

            builder.file_write(0xF2);

            emitRexMemory(
                s,
                dst
            );

            builder.file_write(0x0F);
            builder.file_write(0x11);

            emitMemory(
                s,
                dst
            );

            return;
        }

        throw new IllegalArgumentException(
            "MOVSD inválido: " +
            dst + ", " + src
        );
    }

    public void MOVSS(
        String dst,
        String src
    ) {

        if (isXMM(dst) && isXMM(src)) {

            int d = xmm(dst);
            int s = xmm(src);

            builder.file_write(0xF3);

            emitRexXMM(
                d,
                s
            );

            builder.file_write(0x0F);
            builder.file_write(0x10);

            builder.file_write(
                modrm(
                    3,
                    d,
                    s
                )
            );

            return;
        }

        if (isXMM(dst) && isMemory(src)) {

            int d = xmm(dst);

            builder.file_write(0xF3);

            emitRexMemory(
                d,
                src
            );

            builder.file_write(0x0F);
            builder.file_write(0x10);

            emitMemory(
                d,
                src
            );

            return;
        }

        if (isMemory(dst) && isXMM(src)) {

            int s = xmm(src);

            builder.file_write(0xF3);

            emitRexMemory(
                s,
                dst
            );

            builder.file_write(0x0F);
            builder.file_write(0x11);

            emitMemory(
                s,
                dst
            );

            return;
        }

        throw new IllegalArgumentException(
            "MOVSS inválido: " +
            dst + ", " + src
        );
    }

    public void MOVQ(
        String dst,
        String src
    ) {

        int d = xmm(dst);
        int s = xmm(src);

        builder.file_write(0x66);

        emitRexXMM(
            d,
            s
        );

        builder.file_write(0x0F);
        builder.file_write(0x6F);

        builder.file_write(
            modrm(
                3,
                d,
                s
            )
        );
    }

    private void emitMOVQFromGPR(
        int xmmRegister,
        int generalRegister
    ) {

        checkGeneralRegister(
            generalRegister
        );

        if (xmmRegister < 0 ||
            xmmRegister > 15) {

            throw new IllegalArgumentException(
                "XMM inválido: " +
                xmmRegister
            );
        }

        builder.file_write(0x66);

        emitRex(
            false,
            xmmRegister >= 8,
            false,
            generalRegister >= 8
        );

        builder.file_write(0x0F);
        builder.file_write(0x6E);

        builder.file_write(
            modrm(
                3,
                xmmRegister,
                generalRegister
            )
        );
    }

    public void MOVQ(
        String xmmRegister,
        int generalRegister
    ) {

        emitMOVQFromGPR(
            xmm(xmmRegister),
            generalRegister
        );
    }

    public void MOVQ_TO_REG(
        int generalRegister,
        String xmmRegister
    ) {

        int s = xmm(xmmRegister);

        checkGeneralRegister(
            generalRegister
        );

        builder.file_write(0x66);

        emitRex(
            false,
            s >= 8,
            false,
            generalRegister >= 8
        );

        builder.file_write(0x0F);
        builder.file_write(0x7E);

        builder.file_write(
            modrm(
                3,
                s,
                generalRegister
            )
        );
    }

    public void MOVD(
        String xmmRegister,
        int integerRegister
    ) {

        int d = xmm(xmmRegister);

        checkGeneralRegister(
            integerRegister
        );

        builder.file_write(0x66);

        emitRex(
            false,
            d >= 8,
            false,
            integerRegister >= 8
        );

        builder.file_write(0x0F);
        builder.file_write(0x6E);

        builder.file_write(
            modrm(
                3,
                d,
                integerRegister
            )
        );
    }

    public void MOVD_TO_REG(
        int integerRegister,
        String xmmRegister
    ) {

        int s = xmm(xmmRegister);

        checkGeneralRegister(
            integerRegister
        );

        builder.file_write(0x66);

        emitRex(
            false,
            s >= 8,
            false,
            integerRegister >= 8
        );

        builder.file_write(0x0F);
        builder.file_write(0x7E);

        builder.file_write(
            modrm(
                3,
                s,
                integerRegister
            )
        );
    }

    public void MOV(
        String dst,
        double value
    ) {

        int d = xmm(dst);

        long raw =
            Double.doubleToRawLongBits(
                value
            );

        emitMovRAX(raw);

        emitMOVQFromGPR(
            d,
            0
        );
    }

    public void MOV(
        String dst,
        float value
    ) {

        int d = xmm(dst);

        int raw =
            Float.floatToRawIntBits(
                value
            );

        emitMovEAX(raw);

        MOVD(
            dst,
            0
        );
    }

    private void emitMovRAX(
        long value
    ) {

        builder.file_write(0x48);
        builder.file_write(0xB8);

        emitLE(
            value,
            8
        );
    }

    private void emitMovEAX(
        int value
    ) {

        builder.file_write(0xB8);

        emitLE(
            Integer.toUnsignedLong(value),
            4
        );
    }

    private void binarySD(
        String dst,
        String src,
        int opcode
    ) {

        if (!isXMM(dst)) {

            throw new IllegalArgumentException(
                "Destino debe ser XMM: " +
                dst
            );
        }

        int d = xmm(dst);

        if (isXMM(src)) {

            int s = xmm(src);

            builder.file_write(0xF2);

            emitRexXMM(
                d,
                s
            );

            builder.file_write(0x0F);
            builder.file_write(opcode);

            builder.file_write(
                modrm(
                    3,
                    d,
                    s
                )
            );

            return;
        }

        if (isMemory(src)) {

            builder.file_write(0xF2);

            emitRexMemory(
                d,
                src
            );

            builder.file_write(0x0F);
            builder.file_write(opcode);

            emitMemory(
                d,
                src
            );

            return;
        }

        throw new IllegalArgumentException(
            "Fuente inválida: " +
            src
        );
    }

    public void ADD(
        String dst,
        String src
    ) {

        binarySD(
            dst,
            src,
            0x58
        );
    }

    public void SUB(
        String dst,
        String src
    ) {

        binarySD(
            dst,
            src,
            0x5C
        );
    }

    public void MUL(
        String dst,
        String src
    ) {

        binarySD(
            dst,
            src,
            0x59
        );
    }

    public void DIV(
        String dst,
        String src
    ) {

        binarySD(
            dst,
            src,
            0x5E
        );
    }

    public void MIN(
        String dst,
        String src
    ) {

        binarySD(
            dst,
            src,
            0x5D
        );
    }

    public void MAX(
        String dst,
        String src
    ) {

        binarySD(
            dst,
            src,
            0x5F
        );
    }

    private void binarySS(
        String dst,
        String src,
        int opcode
    ) {

        if (!isXMM(dst)) {

            throw new IllegalArgumentException(
                "Destino debe ser XMM: " +
                dst
            );
        }

        int d = xmm(dst);

        if (isXMM(src)) {

            int s = xmm(src);

            builder.file_write(0xF3);

            emitRexXMM(
                d,
                s
            );

            builder.file_write(0x0F);
            builder.file_write(opcode);

            builder.file_write(
                modrm(
                    3,
                    d,
                    s
                )
            );

            return;
        }

        if (isMemory(src)) {

            builder.file_write(0xF3);

            emitRexMemory(
                d,
                src
            );

            builder.file_write(0x0F);
            builder.file_write(opcode);

            emitMemory(
                d,
                src
            );

            return;
        }

        throw new IllegalArgumentException(
            "Fuente inválida: " +
            src
        );
    }

    public void ADDSS(
        String dst,
        String src
    ) {

        binarySS(
            dst,
            src,
            0x58
        );
    }

    public void SUBSS(
        String dst,
        String src
    ) {

        binarySS(
            dst,
            src,
            0x5C
        );
    }

    public void MULSS(
        String dst,
        String src
    ) {

        binarySS(
            dst,
            src,
            0x59
        );
    }

    public void DIVSS(
        String dst,
        String src
    ) {

        binarySS(
            dst,
            src,
            0x5E
        );
    }

    public void MINSS(
        String dst,
        String src
    ) {

        binarySS(
            dst,
            src,
            0x5D
        );
    }

    public void MAXSS(
        String dst,
        String src
    ) {

        binarySS(
            dst,
            src,
            0x5F
        );
    }

    public void SQRTSD(
        String dst,
        String src
    ) {

        unarySD(
            dst,
            src,
            0x51
        );
    }

    public void SQRTSS(
        String dst,
        String src
    ) {

        unarySS(
            dst,
            src,
            0x51
        );
    }

    private void unarySD(
        String dst,
        String src,
        int opcode
    ) {

        if (!isXMM(dst)) {

            throw new IllegalArgumentException(
                "Destino debe ser XMM: " +
                dst
            );
        }

        int d = xmm(dst);

        if (isXMM(src)) {

            int s = xmm(src);

            builder.file_write(0xF2);

            emitRexXMM(
                d,
                s
            );

            builder.file_write(0x0F);
            builder.file_write(opcode);

            builder.file_write(
                modrm(
                    3,
                    d,
                    s
                )
            );

            return;
        }

        if (isMemory(src)) {

            builder.file_write(0xF2);

            emitRexMemory(
                d,
                src
            );

            builder.file_write(0x0F);
            builder.file_write(opcode);

            emitMemory(
                d,
                src
            );

            return;
        }

        throw new IllegalArgumentException(
            "Fuente inválida: " +
            src
        );
    }

    private void unarySS(
        String dst,
        String src,
        int opcode
    ) {

        if (!isXMM(dst)) {

            throw new IllegalArgumentException(
                "Destino debe ser XMM: " +
                dst
            );
        }

        int d = xmm(dst);

        if (isXMM(src)) {

            int s = xmm(src);

            builder.file_write(0xF3);

            emitRexXMM(
                d,
                s
            );

            builder.file_write(0x0F);
            builder.file_write(opcode);

            builder.file_write(
                modrm(
                    3,
                    d,
                    s
                )
            );

            return;
        }

        if (isMemory(src)) {

            builder.file_write(0xF3);

            emitRexMemory(
                d,
                src
            );

            builder.file_write(0x0F);
            builder.file_write(opcode);

            emitMemory(
                d,
                src
            );

            return;
        }

        throw new IllegalArgumentException(
            "Fuente inválida: " +
            src
        );
    }

    public void UCOMISD(
        String left,
        String right
    ) {

        int a = xmm(left);

        builder.file_write(0x66);

        if (isXMM(right)) {

            int b = xmm(right);

            emitRexXMM(
                a,
                b
            );

            builder.file_write(0x0F);
            builder.file_write(0x2E);

            builder.file_write(
                modrm(
                    3,
                    a,
                    b
                )
            );

            return;
        }

        if (isMemory(right)) {

            emitRexMemory(
                a,
                right
            );

            builder.file_write(0x0F);
            builder.file_write(0x2E);

            emitMemory(
                a,
                right
            );

            return;
        }

        throw new IllegalArgumentException(
            "Operando UCOMISD inválido: " +
            right
        );
    }

    public void UCOMISS(
        String left,
        String right
    ) {

        int a = xmm(left);

        if (isXMM(right)) {

            int b = xmm(right);

            emitRexXMM(
                a,
                b
            );

            builder.file_write(0x0F);
            builder.file_write(0x2E);

            builder.file_write(
                modrm(
                    3,
                    a,
                    b
                )
            );

            return;
        }

        if (isMemory(right)) {

            emitRexMemory(
                a,
                right
            );

            builder.file_write(0x0F);
            builder.file_write(0x2E);

            emitMemory(
                a,
                right
            );

            return;
        }

        throw new IllegalArgumentException(
            "Operando UCOMISS inválido: " +
            right
        );
    }

    public void CVTSI2SD(
        String dst,
        int src
    ) {

        int d = xmm(dst);

        checkGeneralRegister(src);

        builder.file_write(0xF2);

        emitRex(
            true,
            d >= 8,
            false,
            src >= 8
        );

        builder.file_write(0x0F);
        builder.file_write(0x2A);

        builder.file_write(
            modrm(
                3,
                d,
                src
            )
        );
    }

    public void CVTSI2SS(
        String dst,
        int src
    ) {

        int d = xmm(dst);

        checkGeneralRegister(src);

        builder.file_write(0xF3);

        emitRex(
            true,
            d >= 8,
            false,
            src >= 8
        );

        builder.file_write(0x0F);
        builder.file_write(0x2A);

        builder.file_write(
            modrm(
                3,
                d,
                src
            )
        );
    }

    public void CVTTSD2SI(
        int dst,
        String src
    ) {

        int s = xmm(src);

        checkGeneralRegister(dst);

        builder.file_write(0xF2);

        emitRex(
            true,
            dst >= 8,
            false,
            s >= 8
        );

        builder.file_write(0x0F);
        builder.file_write(0x2C);

        builder.file_write(
            modrm(
                3,
                dst,
                s
            )
        );
    }

    public void CVTTSS2SI(
        int dst,
        String src
    ) {

        int s = xmm(src);

        checkGeneralRegister(dst);

        builder.file_write(0xF3);

        emitRex(
            true,
            dst >= 8,
            false,
            s >= 8
        );

        builder.file_write(0x0F);
        builder.file_write(0x2C);

        builder.file_write(
            modrm(
                3,
                dst,
                s
            )
        );
    }

    public void CVTSD2SS(
        String dst,
        String src
    ) {

        convert(
            dst,
            src,
            0xF2
        );
    }

    public void CVTSS2SD(
        String dst,
        String src
    ) {

        convert(
            dst,
            src,
            0xF3
        );
    }

    private void convert(
        String dst,
        String src,
        int prefix
    ) {

        int d = xmm(dst);

        if (isXMM(src)) {

            int s = xmm(src);

            builder.file_write(prefix);

            emitRexXMM(
                d,
                s
            );

            builder.file_write(0x0F);
            builder.file_write(0x5A);

            builder.file_write(
                modrm(
                    3,
                    d,
                    s
                )
            );

            return;
        }

        if (isMemory(src)) {

            builder.file_write(prefix);

            emitRexMemory(
                d,
                src
            );

            builder.file_write(0x0F);
            builder.file_write(0x5A);

            emitMemory(
                d,
                src
            );

            return;
        }

        throw new IllegalArgumentException(
            "Fuente de conversión inválida: " +
            src
        );
    }

    public void MOVSD(
        String memoryDestination,
        double value
    ) {

        if (!isMemory(memoryDestination)) {

            throw new IllegalArgumentException(
                "Destino debe ser memoria: " +
                memoryDestination
            );
        }

        long raw =
            Double.doubleToRawLongBits(
                value
            );

        emitMovRAX(raw);

        MOVSD(
            memoryDestination,
            "XMM0"
        );

    }

    private void emitLE(
        long value,
        int size
    ) {

        for (int i = 0; i < size; i++) {

            builder.file_write(
                (int)(
                    (value >>> (i * 8))
                    & 0xFFL
                )
            );
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
