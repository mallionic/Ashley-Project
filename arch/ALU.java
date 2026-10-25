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
import arch.Registers.Ptr;
import Builder.Builder;
import arch.Registers.Movement;

public class ALU {

    private final Builder builder;
    private final Movement mov;
    private final Ptr ptr;

    public ALU(Builder builder) {
        this.builder = builder;
        this.mov = new Movement(builder);
        this.ptr = new Ptr(builder);
    }

    private static int registerCode(String reg) {

        String r = reg.toUpperCase();

        switch (r) {

            case "RAX":
            case "EAX":
            case "AX":
            case "AL":
                return 0;

            case "RCX":
            case "ECX":
            case "CX":
            case "CL":
                return 1;

            case "RDX":
            case "EDX":
            case "DX":
            case "DL":
                return 2;

            case "RBX":
            case "EBX":
            case "BX":
            case "BL":
                return 3;

            case "RSP":
            case "ESP":
            case "SP":
            case "AH":
                return 4;

            case "RBP":
            case "EBP":
            case "BP":
            case "CH":
                return 5;

            case "RSI":
            case "ESI":
            case "SI":
            case "DH":
                return 6;

            case "RDI":
            case "EDI":
            case "DI":
            case "BH":
                return 7;

            case "R8":
            case "R8D":
            case "R8W":
            case "R8B":
                return 8;

            case "R9":
            case "R9D":
            case "R9W":
            case "R9B":
                return 9;

            case "R10":
            case "R10D":
            case "R10W":
            case "R10B":
                return 10;

            case "R11":
            case "R11D":
            case "R11W":
            case "R11B":
                return 11;

            case "R12":
            case "R12D":
            case "R12W":
            case "R12B":
                return 12;

            case "R13":
            case "R13D":
            case "R13W":
            case "R13B":
                return 13;

            case "R14":
            case "R14D":
            case "R14W":
            case "R14B":
                return 14;

            case "R15":
            case "R15D":
            case "R15W":
            case "R15B":
                return 15;

            default:
                throw new IllegalArgumentException(
                    "Registro desconocido: " + reg
                );
        }
    }

    private static int registerBits(String reg) {

        String r = reg.toUpperCase();

        if (r.endsWith("B"))
            return 8;

        if (r.endsWith("W"))
            return 16;

        if (r.endsWith("D"))
            return 32;

        if (r.startsWith("E"))
            return 32;

        if (r.startsWith("R"))
            return 64;

        if (r.equals("AL") ||
            r.equals("CL") ||
            r.equals("DL") ||
            r.equals("BL") ||
            r.equals("AH") ||
            r.equals("CH") ||
            r.equals("DH") ||
            r.equals("BH"))
            return 8;

        if (r.equals("AX") ||
            r.equals("BX") ||
            r.equals("CX") ||
            r.equals("DX") ||
            r.equals("SP") ||
            r.equals("BP") ||
            r.equals("SI") ||
            r.equals("DI"))
            return 16;

        throw new IllegalArgumentException(
            "No se puede determinar tamaño: " + reg
        );
    }

    private static boolean isMemory(String s) {
        return s != null &&
               s.trim().startsWith("[") &&
               s.trim().endsWith("]");
    }

    private static void checkSameSize(String a, String b) {

        int x = registerBits(a);
        int y = registerBits(b);

        if (x != y) {
            throw new IllegalArgumentException(
                "Tamaños incompatibles: " +
                a + " (" + x + ") y " +
                b + " (" + y + ")"
            );
        }
    }
    private int modrm(int mod, int reg, int rm) {

        return ((mod & 3) << 6)
             | ((reg & 7) << 3)
             | (rm & 7);
    }

    private void rex(
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

        if (value != 0x40)
            builder.file_write(value);
    }

    private void rexForRegs(
        int reg,
        int rm,
        int bits
    ) {

        boolean w = bits == 64;
        boolean r = reg >= 8;
        boolean b = rm >= 8;

        if (bits == 8 &&
            (reg >= 4 || rm >= 4)) {

            rex(w, r, false, b);
            return;
        }

        rex(w, r, false, b);
    }

    private void operand16(int bits) {

        if (bits == 16)
            builder.file_write(0x66);
    }

    public void PUSH(String reg) {

        if (!"RBP".equalsIgnoreCase(reg)) arch.builtin.Vars.noteFrameUse();

        int code = registerCode(reg);
        int bits = registerBits(reg);

        if (bits != 16 && bits != 64)
            throw new IllegalArgumentException(
                "PUSH requiere 16 o 64 bits: " + reg
            );

        if (bits == 16)
            builder.file_write(0x66);

        if (code >= 8)
            builder.file_write(0x41);

        builder.file_write(
            0x50 + (code & 7)
        );
    }

    public void POP(String reg) {

        if (!"RBP".equalsIgnoreCase(reg)) arch.builtin.Vars.noteFrameUse();

        int code = registerCode(reg);
        int bits = registerBits(reg);

        if (bits != 16 && bits != 64)
            throw new IllegalArgumentException(
                "POP requiere 16 o 64 bits: " + reg
            );

        if (bits == 16)
            builder.file_write(0x66);

        if (code >= 8)
            builder.file_write(0x41);

        builder.file_write(
            0x58 + (code & 7)
        );
    }

    public void PUSH(long value) {

        arch.builtin.Vars.noteFrameUse();

        if (value >= -128 && value <= 127) {

            builder.file_write(0x6A);
            builder.file_write((int)value & 0xFF);
            return;
        }

        if (value >= Integer.MIN_VALUE &&
            value <= Integer.MAX_VALUE) {

            PUSH32((int)value);
            return;
        }

        throw new IllegalArgumentException(
            "PUSH inmediato fuera de rango: " + value
        );
    }

    public void PUSH8(int value) {

        builder.file_write(
            0x6A,
            value & 0xFF
        );
    }

    public void PUSH32(int value) {

        builder.file_write(0x68);

        builder.file_write(
            value & 0xFF,
            (value >>> 8) & 0xFF,
            (value >>> 16) & 0xFF,
            (value >>> 24) & 0xFF
        );
    }

    public void ADD(String dst, String src) {

        if (isMemory(dst) || isMemory(src)) {

            if (isMemory(dst) && !isMemory(src)) {
                ADD_MEM_REG(dst, src);
                return;
            }

            if (!isMemory(dst) && isMemory(src)) {
                ADD_REG_MEM(dst, src);
                return;
            }

            throw new IllegalArgumentException(
                "ADD memoria <-> memoria no existe directamente"
            );
        }

        checkSameSize(dst, src);

        int d = registerCode(dst);
        int s = registerCode(src);
        int bits = registerBits(dst);

        operand16(bits);

        rexForRegs(s, d, bits);

        builder.file_write(0x01);
        builder.file_write(modrm(3, s, d));
    }

    private void ADD_REG_MEM(
        String dst,
        String memory
    ) {

        int d = registerCode(dst);
        int bits = registerBits(dst);

        operand16(bits);

        ptr.emitRexForMemory(
            d,
            memory,
            bits
        );

        builder.file_write(0x03);

        ptr.emitModRM(
            d,
            memory
        );
    }

    private void ADD_MEM_REG(
        String memory,
        String src
    ) {

        int s = registerCode(src);
        int bits = registerBits(src);

        operand16(bits);

        ptr.emitRexForMemory(
            s,
            memory,
            bits
        );

        builder.file_write(0x01);

        ptr.emitModRM(
            s,
            memory
        );
    }
public void ADD(String reg, long value) {

    if (isMemory(reg))
        throw new IllegalArgumentException(
            "ADD(memory,imm) necesita tamaño explícito"
        );

    int r = registerCode(reg);
    int bits = registerBits(reg);

    operand16(bits);

    if (bits == 8) {

        rexForRegs(0, r, 8);

        builder.file_write(0x80);
        builder.file_write(modrm(3, 0, r));
        builder.file_write((int)value);

        return;
    }

    rex(
        bits == 64,
        false,
        false,
        r >= 8
    );

    if (value >= -128 && value <= 127) {

        builder.file_write(0x83);
        builder.file_write(modrm(3, 0, r));
        builder.file_write((int)value);

        return;
    }

    builder.file_write(0x81);
    builder.file_write(modrm(3, 0, r));

    emitImmediate(value, bits);
}

    public void SUB(String dst, String src) {

        if (isMemory(dst) || isMemory(src)) {

            if (isMemory(dst) && !isMemory(src)) {
                SUB_MEM_REG(dst, src);
                return;
            }

            if (!isMemory(dst) && isMemory(src)) {
                SUB_REG_MEM(dst, src);
                return;
            }

            throw new IllegalArgumentException(
                "SUB memoria <-> memoria no existe directamente"
            );
        }

        checkSameSize(dst, src);

        int d = registerCode(dst);
        int s = registerCode(src);
        int bits = registerBits(dst);

        operand16(bits);
        rexForRegs(s, d, bits);

        builder.file_write(0x29);
        builder.file_write(modrm(3, s, d));
    }

    private void SUB_REG_MEM(
        String dst,
        String memory
    ) {

        int d = registerCode(dst);
        int bits = registerBits(dst);

        operand16(bits);

        ptr.emitRexForMemory(d, memory, bits);

        builder.file_write(0x2B);
        ptr.emitModRM(d, memory);
    }

    private void SUB_MEM_REG(
        String memory,
        String src
    ) {

        int s = registerCode(src);
        int bits = registerBits(src);

        operand16(bits);

        ptr.emitRexForMemory(s, memory, bits);

        builder.file_write(0x29);
        ptr.emitModRM(s, memory);
    }

    public void SUB(String reg, long value) {

        int r = registerCode(reg);
        int bits = registerBits(reg);

        operand16(bits);

        if (bits == 8) {

            rexForRegs(0, r, 8);

            builder.file_write(0x80);
            builder.file_write(modrm(3, 5, r));
            builder.file_write((int)value);

            return;
        }

        rex(
            bits == 64,
            false,
            false,
            r >= 8
        );

        if (value >= -128 && value <= 127) {

            builder.file_write(0x83);
            builder.file_write(modrm(3, 5, r));
            builder.file_write((int)value);

        } else {

            builder.file_write(0x81);
            builder.file_write(modrm(3, 5, r));

            emitImmediate(value, bits);
        }
    }

    public void AND(String dst, String src) {

        if (isMemory(dst) || isMemory(src)) {

            if (isMemory(dst) && !isMemory(src)) {
                AND_MEM_REG(dst, src);
                return;
            }

            if (!isMemory(dst) && isMemory(src)) {
                AND_REG_MEM(dst, src);
                return;
            }

            throw new IllegalArgumentException(
                "AND memoria <-> memoria no existe directamente"
            );
        }

        checkSameSize(dst, src);

        int d = registerCode(dst);
        int s = registerCode(src);
        int bits = registerBits(dst);

        operand16(bits);
        rexForRegs(s, d, bits);

        builder.file_write(0x21);
        builder.file_write(modrm(3, s, d));
    }

    private void AND_REG_MEM(String dst, String mem) {

        int d = registerCode(dst);
        int bits = registerBits(dst);

        operand16(bits);
        ptr.emitRexForMemory(d, mem, bits);

        builder.file_write(0x23);
        ptr.emitModRM(d, mem);
    }

    private void AND_MEM_REG(String mem, String src) {

        int s = registerCode(src);
        int bits = registerBits(src);

        operand16(bits);
        ptr.emitRexForMemory(s, mem, bits);

        builder.file_write(0x21);
        ptr.emitModRM(s, mem);
    }

    public void AND(String reg, long value) {

        immediateALU(reg, value, 4);
    }

    public void OR(String dst, String src) {

        if (isMemory(dst) || isMemory(src)) {

            if (isMemory(dst) && !isMemory(src)) {
                OR_MEM_REG(dst, src);
                return;
            }

            if (!isMemory(dst) && isMemory(src)) {
                OR_REG_MEM(dst, src);
                return;
            }

            throw new IllegalArgumentException(
                "OR memoria <-> memoria no existe directamente"
            );
        }

        checkSameSize(dst, src);

        int d = registerCode(dst);
        int s = registerCode(src);
        int bits = registerBits(dst);

        operand16(bits);
        rexForRegs(s, d, bits);

        builder.file_write(0x09);
        builder.file_write(modrm(3, s, d));
    }

    private void OR_REG_MEM(String dst, String mem) {

        int d = registerCode(dst);
        int bits = registerBits(dst);

        operand16(bits);
        ptr.emitRexForMemory(d, mem, bits);

        builder.file_write(0x0B);
        ptr.emitModRM(d, mem);
    }

    private void OR_MEM_REG(String mem, String src) {

        int s = registerCode(src);
        int bits = registerBits(src);

        operand16(bits);
        ptr.emitRexForMemory(s, mem, bits);

        builder.file_write(0x09);
        ptr.emitModRM(s, mem);
    }

    public void OR(String reg, long value) {

        immediateALU(reg, value, 1);
    }

    public void XOR(String dst, String src) {

        if (isMemory(dst) || isMemory(src)) {

            if (isMemory(dst) && !isMemory(src)) {
                XOR_MEM_REG(dst, src);
                return;
            }

            if (!isMemory(dst) && isMemory(src)) {
                XOR_REG_MEM(dst, src);
                return;
            }

            throw new IllegalArgumentException(
                "XOR memoria <-> memoria no existe directamente"
            );
        }

        checkSameSize(dst, src);

        int d = registerCode(dst);
        int s = registerCode(src);
        int bits = registerBits(dst);

        operand16(bits);
        rexForRegs(s, d, bits);

        builder.file_write(0x31);
        builder.file_write(modrm(3, s, d));
    }

    private void XOR_REG_MEM(String dst, String mem) {

        int d = registerCode(dst);
        int bits = registerBits(dst);

        operand16(bits);

        ptr.emitRexForMemory(
            d,
            mem,
            bits
        );

        builder.file_write(0x33);
        ptr.emitModRM(d, mem);
    }

    private void XOR_MEM_REG(String mem, String src) {

        int s = registerCode(src);
        int bits = registerBits(src);

        operand16(bits);

        ptr.emitRexForMemory(
            s,
            mem,
            bits
        );

        builder.file_write(0x31);
        ptr.emitModRM(s, mem);
    }

    public void XOR(String reg, long value) {

        immediateALU(reg, value, 6);
    }

    public void CMP(String left, String right) {

        if (isMemory(left) || isMemory(right)) {

            if (isMemory(left) && !isMemory(right)) {

                int r = registerCode(right);
                int bits = registerBits(right);

                operand16(bits);

                ptr.emitRexForMemory(r, left, bits);

                builder.file_write(0x39);
                ptr.emitModRM(r, left);

                return;
            }

            if (!isMemory(left) && isMemory(right)) {

                int r = registerCode(left);
                int bits = registerBits(left);

                operand16(bits);

                ptr.emitRexForMemory(r, right, bits);

                builder.file_write(0x3B);
                ptr.emitModRM(r, right);

                return;
            }
        }

        checkSameSize(left, right);

        int l = registerCode(left);
        int r = registerCode(right);
        int bits = registerBits(left);

        operand16(bits);
        rexForRegs(r, l, bits);

        builder.file_write(0x39);
        builder.file_write(modrm(3, r, l));
    }

    public void CMP(String reg, long value) {

        immediateALU(reg, value, 7);
    }

    public void TEST(String left, String right) {

        if (isMemory(left) || isMemory(right)) {

            if (isMemory(left) && !isMemory(right)) {

                int r = registerCode(right);
                int bits = registerBits(right);

                operand16(bits);

                ptr.emitRexForMemory(r, left, bits);

                builder.file_write(0x85);
                ptr.emitModRM(r, left);

                return;
            }

            if (!isMemory(left) && isMemory(right)) {

                int r = registerCode(left);
                int bits = registerBits(left);

                operand16(bits);

                ptr.emitRexForMemory(r, right, bits);

                builder.file_write(0x85);
                ptr.emitModRM(r, right);

                return;
            }
        }

        checkSameSize(left, right);

        int l = registerCode(left);
        int r = registerCode(right);
        int bits = registerBits(left);

        operand16(bits);
        rexForRegs(r, l, bits);

        builder.file_write(0x85);
        builder.file_write(modrm(3, r, l));
    }

    public void TEST(String reg, long value) {

        int r = registerCode(reg);
        int bits = registerBits(reg);

        operand16(bits);

        if (bits == 8) {

            rexForRegs(0, r, 8);

            builder.file_write(0xF6);
            builder.file_write(modrm(3, 0, r));
            builder.file_write((int)value);

            return;
        }

        rex(
            bits == 64,
            false,
            false,
            r >= 8
        );

        builder.file_write(0xF7);
        builder.file_write(modrm(3, 0, r));

        emitImmediate(value, bits);
    }

    public void NEG(String reg) {

        int r = registerCode(reg);
        int bits = registerBits(reg);

        operand16(bits);

        rex(
            bits == 64,
            false,
            false,
            r >= 8
        );

        if (bits == 8) {

            builder.file_write(0xF6);
            builder.file_write(modrm(3, 3, r));

        } else {

            builder.file_write(0xF7);
            builder.file_write(modrm(3, 3, r));
        }
    }

    public void NOT(String reg) {

        int r = registerCode(reg);
        int bits = registerBits(reg);

        operand16(bits);

        rex(
            bits == 64,
            false,
            false,
            r >= 8
        );

        if (bits == 8) {

            builder.file_write(0xF6);
            builder.file_write(modrm(3, 2, r));

        } else {

            builder.file_write(0xF7);
            builder.file_write(modrm(3, 2, r));
        }
    }

    public void INC(String reg) {

        int r = registerCode(reg);
        int bits = registerBits(reg);

        operand16(bits);

        rex(
            bits == 64,
            false,
            false,
            r >= 8
        );

        if (bits == 8) {

            builder.file_write(0xFE);
            builder.file_write(modrm(3, 0, r));

        } else {

            builder.file_write(0xFF);
            builder.file_write(modrm(3, 0, r));
        }
    }

    public void DEC(String reg) {

        int r = registerCode(reg);
        int bits = registerBits(reg);

        operand16(bits);

        rex(
            bits == 64,
            false,
            false,
            r >= 8
        );

        if (bits == 8) {

            builder.file_write(0xFE);
            builder.file_write(modrm(3, 1, r));

        } else {

            builder.file_write(0xFF);
            builder.file_write(modrm(3, 1, r));
        }
    }

    public void SHL(String reg, int count) {

        shift(reg, count, 4);
    }

    public void SAL(String reg, int count) {

        SHL(reg, count);
    }

    public void SHR(String reg, int count) {

        shift(reg, count, 5);
    }

    public void SAR(String reg, int count) {

        shift(reg, count, 7);
    }

    private void shift(
        String reg,
        int count,
        int group
    ) {

        int r = registerCode(reg);
        int bits = registerBits(reg);

        operand16(bits);

        rex(
            bits == 64,
            false,
            false,
            r >= 8
        );

        if (count == 1) {

            if (bits == 8)
                builder.file_write(0xD0);
            else
                builder.file_write(0xD1);

            builder.file_write(
                modrm(3, group, r)
            );

            return;
        }

        if (count < 0 || count > 255)
            throw new IllegalArgumentException(
                "Shift fuera de rango: " + count
            );

        if (bits == 8)
            builder.file_write(0xC0);
        else
            builder.file_write(0xC1);

        builder.file_write(
            modrm(3, group, r)
        );

        builder.file_write(count & 0xFF);
    }

    public void IMUL(String dst, String src) {

        if (isMemory(src)) {

            int d = registerCode(dst);
            int bits = registerBits(dst);

            operand16(bits);

            ptr.emitRexForMemory(
                d,
                src,
                bits
            );

            builder.file_write(
                0x0F,
                0xAF
            );

            ptr.emitModRM(d, src);

            return;
        }

        checkSameSize(dst, src);

        int d = registerCode(dst);
        int s = registerCode(src);
        int bits = registerBits(dst);

        operand16(bits);
        rexForRegs(d, s, bits);

        builder.file_write(
            0x0F,
            0xAF
        );

        builder.file_write(
            modrm(3, d, s)
        );
    }

    public void IMUL(
        String dst,
        String src,
        long immediate
    ) {

        if (isMemory(src))
            throw new IllegalArgumentException(
                "Esta sobrecarga usa src registro"
            );

        checkSameSize(dst, src);

        int d = registerCode(dst);
        int s = registerCode(src);
        int bits = registerBits(dst);

        operand16(bits);
        rexForRegs(d, s, bits);

        if (immediate >= -128 &&
            immediate <= 127) {

            builder.file_write(0x6B);
            builder.file_write(modrm(3, d, s));
            builder.file_write((int)immediate);

        } else {

            builder.file_write(0x69);
            builder.file_write(modrm(3, d, s));

            emitImmediate(immediate, bits);
        }
    }

    public void IMUL(String reg) {

        int r = registerCode(reg);
        int bits = registerBits(reg);

        operand16(bits);

        rex(
            bits == 64,
            false,
            false,
            r >= 8
        );

        if (bits == 8) {

            builder.file_write(0xF6);
            builder.file_write(modrm(3, 5, r));

        } else {

            builder.file_write(0xF7);
            builder.file_write(modrm(3, 5, r));
        }
    }

    public void MUL(String reg) {

        int r = registerCode(reg);
        int bits = registerBits(reg);

        operand16(bits);

        rex(
            bits == 64,
            false,
            false,
            r >= 8
        );

        if (bits == 8) {

            builder.file_write(0xF6);
            builder.file_write(modrm(3, 4, r));

        } else {

            builder.file_write(0xF7);
            builder.file_write(modrm(3, 4, r));
        }
    }

    public void IDIV(String reg) {

        int r = registerCode(reg);
        int bits = registerBits(reg);

        operand16(bits);

        rex(
            bits == 64,
            false,
            false,
            r >= 8
        );

        if (bits == 8) {

            builder.file_write(0xF6);
            builder.file_write(modrm(3, 7, r));

        } else {

            builder.file_write(0xF7);
            builder.file_write(modrm(3, 7, r));
        }
    }

    public void DIV(String reg) {

        int r = registerCode(reg);
        int bits = registerBits(reg);

        operand16(bits);

        rex(
            bits == 64,
            false,
            false,
            r >= 8
        );

        if (bits == 8) {

            builder.file_write(0xF6);
            builder.file_write(modrm(3, 6, r));

        } else {

            builder.file_write(0xF7);
            builder.file_write(modrm(3, 6, r));
        }
    }

    public void LEA(
        String dst,
        String memory
    ) {

        if (!isMemory(memory))
            throw new IllegalArgumentException(
                "LEA necesita un operando memoria"
            );

        int d = registerCode(dst);
        int bits = registerBits(dst);

        if (bits != 16 &&
            bits != 32 &&
            bits != 64) {

            throw new IllegalArgumentException(
                "LEA requiere destino 16/32/64 bits"
            );
        }

        operand16(bits);

        ptr.emitRexForMemory(
            d,
            memory,
            bits
        );

        builder.file_write(0x8D);

        ptr.emitModRM(
            d,
            memory
        );
    }

    public void IDIV(
        String dividend,
        String divisor,
        String output
    ) {

        checkSameSize(dividend, divisor);
        checkSameSize(dividend, output);

        int bits = registerBits(dividend);

        if (bits == 16) {

            saveRegisters("AX", "DX");

            mov.MOV("AX", dividend);

            builder.file_write(0x99);

            IDIV(divisor);

            mov.MOV(output, "AX");

            restoreRegisters("AX", "DX");

            return;
        }

        if (bits == 32) {

            saveRegisters("EAX", "EDX");

            mov.MOV("EAX", dividend);

            builder.file_write(0x99);

            IDIV(divisor);

            mov.MOV(output, "EAX");

            restoreRegisters("EAX", "EDX");

            return;
        }

        if (bits == 64) {

            saveRegisters("RAX", "RDX");

            mov.MOV("RAX", dividend);

            builder.file_write(
                0x48,
                0x99
            );

            IDIV(divisor);

            mov.MOV(output, "RAX");

            restoreRegisters("RAX", "RDX");

            return;
        }

        throw new IllegalArgumentException(
            "Tamaño no soportado: " + bits
        );
    }

    public void IMUL(
        String left,
        String right,
        String output
    ) {

        checkSameSize(left, right);
        checkSameSize(left, output);

        mov.MOV(output, left);
        IMUL(output, right);
    }

    public void saveRegisters(
        String... registers
    ) {

        for (String r : registers)
            PUSH(r);
    }

    public void restoreRegisters(
        String... registers
    ) {

        for (int i = registers.length - 1;
             i >= 0;
             i--) {

            POP(registers[i]);
        }
    }

    private void immediateALU(
        String reg,
        long value,
        int group
    ) {

        int r = registerCode(reg);
        int bits = registerBits(reg);

        operand16(bits);

        if (bits == 8) {

            rex(
                false,
                false,
                false,
                r >= 8
            );

            builder.file_write(0x80);

            builder.file_write(
                modrm(3, group, r)
            );

            builder.file_write(
                (int)value
            );

            return;
        }

        rex(
            bits == 64,
            false,
            false,
            r >= 8
        );

        if (value >= -128 &&
            value <= 127) {

            builder.file_write(0x83);

            builder.file_write(
                modrm(3, group, r)
            );

            builder.file_write(
                (int)value
            );

            return;
        }

        builder.file_write(0x81);

        builder.file_write(
            modrm(3, group, r)
        );

        emitImmediate(value, bits);
    }

    private void emitImmediate(
        long value,
        int bits
    ) {

        if (bits == 8) {

            builder.file_write(
                (int)value & 0xFF
            );

            return;
        }

        if (bits == 16) {

            int v = (int)value;

            builder.file_write(
                v & 0xFF,
                (v >>> 8) & 0xFF
            );

            return;
        }

        if (bits == 32 ||
            bits == 64) {

            int v = (int)value;

            builder.file_write(
                v & 0xFF,
                (v >>> 8) & 0xFF,
                (v >>> 16) & 0xFF,
                (v >>> 24) & 0xFF
            );

            return;
        }

        throw new IllegalArgumentException(
            "Tamaño inmediato no soportado: " + bits
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
