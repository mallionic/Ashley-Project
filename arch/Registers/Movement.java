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
import arch.X86;

public class Movement {

    private final Builder builder;

    public Movement(Builder builder) {
        if (builder == null) {
            throw new IllegalArgumentException("Builder no puede ser null");
        }

        this.builder = builder;
    }

    private static class Register {
        final int code;
        final int low;
        final int bits;
        final boolean extended;

        Register(int code, int bits) {
            this.code = code;
            this.low = code & 7;
            this.bits = bits;
            this.extended = code >= 8;
        }
    }

    private Register register(String name) {
        switch (name.toUpperCase()) {

            case "AL":  return new Register(0, 8);
            case "CL":  return new Register(1, 8);
            case "DL":  return new Register(2, 8);
            case "BL":  return new Register(3, 8);
            case "SPL": return new Register(4, 8);
            case "BPL": return new Register(5, 8);
            case "SIL": return new Register(6, 8);
            case "DIL": return new Register(7, 8);

            case "R8B":  return new Register(8, 8);
            case "R9B":  return new Register(9, 8);
            case "R10B": return new Register(10, 8);
            case "R11B": return new Register(11, 8);
            case "R12B": return new Register(12, 8);
            case "R13B": return new Register(13, 8);
            case "R14B": return new Register(14, 8);
            case "R15B": return new Register(15, 8);

            case "AX": return new Register(0, 16);
            case "CX": return new Register(1, 16);
            case "DX": return new Register(2, 16);
            case "BX": return new Register(3, 16);
            case "SP": return new Register(4, 16);
            case "BP": return new Register(5, 16);
            case "SI": return new Register(6, 16);
            case "DI": return new Register(7, 16);

            case "R8W":  return new Register(8, 16);
            case "R9W":  return new Register(9, 16);
            case "R10W": return new Register(10, 16);
            case "R11W": return new Register(11, 16);
            case "R12W": return new Register(12, 16);
            case "R13W": return new Register(13, 16);
            case "R14W": return new Register(14, 16);
            case "R15W": return new Register(15, 16);

            case "EAX": return new Register(0, 32);
            case "ECX": return new Register(1, 32);
            case "EDX": return new Register(2, 32);
            case "EBX": return new Register(3, 32);
            case "ESP": return new Register(4, 32);
            case "EBP": return new Register(5, 32);
            case "ESI": return new Register(6, 32);
            case "EDI": return new Register(7, 32);

            case "R8D":  return new Register(8, 32);
            case "R9D":  return new Register(9, 32);
            case "R10D": return new Register(10, 32);
            case "R11D": return new Register(11, 32);
            case "R12D": return new Register(12, 32);
            case "R13D": return new Register(13, 32);
            case "R14D": return new Register(14, 32);
            case "R15D": return new Register(15, 32);

            case "RAX": return new Register(0, 64);
            case "RCX": return new Register(1, 64);
            case "RDX": return new Register(2, 64);
            case "RBX": return new Register(3, 64);
            case "RSP": return new Register(4, 64);
            case "RBP": return new Register(5, 64);
            case "RSI": return new Register(6, 64);
            case "RDI": return new Register(7, 64);

            case "R8":  return new Register(8, 64);
            case "R9":  return new Register(9, 64);
            case "R10": return new Register(10, 64);
            case "R11": return new Register(11, 64);
            case "R12": return new Register(12, 64);
            case "R13": return new Register(13, 64);
            case "R14": return new Register(14, 64);
            case "R15": return new Register(15, 64);

            default:
                throw new IllegalArgumentException(
                    "Registro desconocido: " + name
                );
        }
    }

    public void MOV(String dst, int value) {
        Register d = register(dst);

        long unsignedValue = Integer.toUnsignedLong(value);

        if (d.bits == 64) {
            builder.file_write(X86.MOV_EAX[0] + d.low);
            emitLE(unsignedValue, 4);
            return;
        }

        MOV(dst, unsignedValue);
    }

    public void MOV(String dst, long value) {
        Register d = register(dst);

        if (d.bits == 64) {

            if (value >= 0L && value <= 0xFFFFFFFFL) {
                builder.file_write(X86.MOV_EAX[0] + d.low);
                emitLE(value, 4);
                return;
            }

            emitRex(true, false, false, d.extended, false);

            builder.file_write(X86.MOV_RAX[0] + d.low);
            emitLE(value, 8);
            return;
        }

        if (d.bits == 32) {
            builder.file_write(X86.MOV_EAX[0] + d.low);
            emitLE(value, 4);
            return;
        }

        if (d.bits == 16) {
            builder.file_write(X86.OVERRIDE[0]);
            builder.file_write(X86.MOV_AX[0] + d.low);
            emitLE(value, 2);
            return;
        }

        if (d.bits == 8) {
            emitRex(false, false, false, d.extended, d.code >= 4);
            builder.file_write(0xB0 + d.low);
            emitLE(value, 1);
            return;
        }

        throw new IllegalArgumentException(
            "Tamaño de registro no soportado"
        );
    }

    public void MOV(String dst, String src) {
        Register d = register(dst);
        Register s = register(src);

        if (d.bits != s.bits) {

            if (d.bits < s.bits) {
                MOV(dst, subName(s.code, d.bits));
            } else {
                zeroExtend(d, s);
            }
            return;
        }

        if (d.bits == 8) {

            emitRex(
                false,
                s.extended,
                false,
                d.extended,
                d.code >= 4 || s.code >= 4
            );

            builder.file_write(0x88);

        } else {

            if (d.bits == 16) {
                builder.file_write(X86.OVERRIDE[0]);
            }

            emitRex(
                d.bits == 64,
                s.extended,
                false,
                d.extended,
                false
            );

            builder.file_write(0x89);
        }

        builder.file_write(modrm(
            3,
            s.low,
            d.low
        ));
    }

    private static String subName(int code, int bits) {
        final String[] b8  = {"AL","CL","DL","BL","SPL","BPL","SIL","DIL"};
        final String[] b16 = {"AX","CX","DX","BX","SP","BP","SI","DI"};
        final String[] b32 = {"EAX","ECX","EDX","EBX","ESP","EBP","ESI","EDI"};
        final String[] b64 = {"RAX","RCX","RDX","RBX","RSP","RBP","RSI","RDI"};
        if (code < 8) {
            switch (bits) {
                case 8:  return b8[code];
                case 16: return b16[code];
                case 32: return b32[code];
                default: return b64[code];
            }
        }
        switch (bits) {
            case 8:  return "R" + code + "B";
            case 16: return "R" + code + "W";
            case 32: return "R" + code + "D";
            default: return "R" + code;
        }
    }

    private void zeroExtend(Register d, Register s) {

        if (s.bits == 32) {

            MOV(subName(d.code, 32), subName(s.code, 32));
            return;
        }

        if (d.bits == 16)
            builder.file_write(X86.OVERRIDE[0]);

        boolean force = s.bits == 8 && s.code >= 4;

        emitRex(
            d.bits == 64,
            d.extended,
            false,
            s.extended,
            force
        );

        builder.file_write(0x0F);
        builder.file_write(s.bits == 8 ? 0xB6 : 0xB7);
        builder.file_write(modrm(3, d.low, s.low));
    }

    private int modrm(int mod, int reg, int rm) {
        return ((mod & 3) << 6)
             | ((reg & 7) << 3)
             | (rm & 7);
    }

    private void emitRex(
        boolean w,
        boolean r,
        boolean x,
        boolean b,
        boolean force
    ) {
        int rex = X86.REX[0] & 0xFF;

        if (w) {
            rex |= X86.REX_W[0] & 0x0F;
        }

        if (r) {
            rex |= X86.REX_R[0] & 0x0F;
        }

        if (x) {
            rex |= X86.REX_X[0] & 0x0F;
        }

        if (b) {
            rex |= X86.REX_B[0] & 0x0F;
        }

        if (rex != (X86.REX[0] & 0xFF) || force) {
            builder.file_write(rex);
        }
    }

    private void emitLE(long value, int size) {
        for (int i = 0; i < size; i++) {
            builder.file_write(
                (int) ((value >>> (i * 8)) & 0xFFL)
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
