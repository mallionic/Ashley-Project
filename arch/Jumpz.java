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

import java.util.HashMap;
import java.util.Map;

public class Jumpz {

    private final Builder builder;

    private final Map<String, Integer> labels = new HashMap<>();

    private static class PendingJump {
        final int patchPos;
        final String targetLabel;
        final int instructionEnd;
        final int size;

        PendingJump(
                int patchPos,
                String targetLabel,
                int instructionEnd,
                int size
        ) {
            this.patchPos = patchPos;
            this.targetLabel = targetLabel;
            this.instructionEnd = instructionEnd;
            this.size = size;
        }
    }

    private final Map<String, java.util.List<PendingJump>> pending =
            new HashMap<>();

    public Jumpz(Builder builder) {
        if (builder == null) {
            throw new IllegalArgumentException("Builder no puede ser null");
        }

        this.builder = builder;
    }

    public void label(String name) {

        checkLabelName(name);

        if (labels.containsKey(name)) {
            throw new IllegalArgumentException(
                    "Etiqueta duplicada: " + name
            );
        }

        int address = builder.currentAddress();

        labels.put(name, address);

        java.util.List<PendingJump> list = pending.remove(name);

        if (list != null) {
            for (PendingJump jump : list) {
                patchJump(jump, address);
            }
        }
    }

    public int getLabelAddress(String name) {

        Integer address = labels.get(name);

        if (address == null) {
            throw new IllegalArgumentException(
                    "Etiqueta no definida: " + name
            );
        }

        return address;
    }

    public void resolveAll() {

        if (pending.isEmpty()) {
            return;
        }

        StringBuilder sb = new StringBuilder();

        for (String label : pending.keySet()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }

            sb.append(label);
        }

        throw new IllegalStateException(
                "Hay etiquetas sin resolver: " + sb
        );
    }

    private void checkLabelName(String name) {

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Nombre de etiqueta vacío"
            );
        }
    }

    private void emitShort(int opcode, String label) {

        checkLabelName(label);

        int displacementPos = builder.size();

        builder.file_write(opcode);

        builder.file_write(0);

        int instructionEnd = builder.currentAddress();

        Integer target = labels.get(label);

        if (target != null) {

            patchRelative8(
                    displacementPos + 1,
                    instructionEnd,
                    target
            );

        } else {

            addPending(
                    label,
                    displacementPos + 1,
                    instructionEnd,
                    1
            );
        }
    }

    private void emitNear(int opcode1, int opcode2, String label) {

        checkLabelName(label);

        int displacementPos = builder.size();

        builder.file_write(opcode1, opcode2);

        builder.file_write(0, 0, 0, 0);

        int instructionEnd = builder.currentAddress();

        Integer target = labels.get(label);

        if (target != null) {

            patchRelative32(
                    displacementPos + 2,
                    instructionEnd,
                    target
            );

        } else {

            addPending(
                    label,
                    displacementPos + 2,
                    instructionEnd,
                    4
            );
        }
    }

    private void addPending(
            String label,
            int patchPos,
            int instructionEnd,
            int size
    ) {

        pending
                .computeIfAbsent(
                        label,
                        k -> new java.util.ArrayList<>()
                )
                .add(
                        new PendingJump(
                                patchPos,
                                label,
                                instructionEnd,
                                size
                        )
                );
    }

    private void patchJump(
            PendingJump jump,
            int target
    ) {

        if (jump.size == 1) {

            patchRelative8(
                    jump.patchPos,
                    jump.instructionEnd,
                    target
            );

        } else {

            patchRelative32(
                    jump.patchPos,
                    jump.instructionEnd,
                    target
            );
        }
    }

    private void patchRelative8(
            int patchPos,
            int instructionEnd,
            int target
    ) {

        long relative =
                (long) target - (long) instructionEnd;

        if (relative < -128 || relative > 127) {

            throw new IllegalArgumentException(
                    String.format(
                            "Salto SHORT fuera de rango: target=0x%X end=0x%X rel=%d",
                            target,
                            instructionEnd,
                            relative
                    )
            );
        }

        builder.patchByte(
                patchPos,
                (byte) relative
        );
    }

    private void patchRelative32(
            int patchPos,
            int instructionEnd,
            int target
    ) {

        long relative =
                (long) target - (long) instructionEnd;

        if (relative < Integer.MIN_VALUE ||
            relative > Integer.MAX_VALUE) {

            throw new IllegalArgumentException(
                    String.format(
                            "Salto NEAR fuera de rango: target=0x%X end=0x%X rel=%d",
                            target,
                            instructionEnd,
                            relative
                    )
            );
        }

        int rel = (int) relative;

        builder.patchByte(
                patchPos,
                (byte) (rel & 0xFF)
        );

        builder.patchByte(
                patchPos + 1,
                (byte) ((rel >>> 8) & 0xFF)
        );

        builder.patchByte(
                patchPos + 2,
                (byte) ((rel >>> 16) & 0xFF)
        );

        builder.patchByte(
                patchPos + 3,
                (byte) ((rel >>> 24) & 0xFF)
        );
    }

    public void JA(String label) {
        emitShort(0x77, label);
    }

    public void JAE(String label) {
        emitShort(0x73, label);
    }

    public void JB(String label) {
        emitShort(0x72, label);
    }

    public void JBE(String label) {
        emitShort(0x76, label);
    }

    public void JC(String label) {
        emitShort(0x72, label);
    }

    public void JCXZ(String label) {
        emitShort(0xE3, label);
    }

    public void JECXZ(String label) {
        emitShort(0xE3, label);
    }

    public void JRCXZ(String label) {
        emitShort(0xE3, label);
    }

    public void JE(String label) {
        emitShort(0x74, label);
    }

    public void JZ(String label) {
        emitShort(0x74, label);
    }

    public void JG(String label) {
        emitShort(0x7F, label);
    }

    public void JGE(String label) {
        emitShort(0x7D, label);
    }

    public void JL(String label) {
        emitShort(0x7C, label);
    }

    public void JLE(String label) {
        emitShort(0x7E, label);
    }

    public void JNA(String label) {
        emitShort(0x76, label);
    }

    public void JNAE(String label) {
        emitShort(0x72, label);
    }

    public void JNB(String label) {
        emitShort(0x73, label);
    }

    public void JNBE(String label) {
        emitShort(0x77, label);
    }

    public void JNC(String label) {
        emitShort(0x73, label);
    }

    public void JNE(String label) {
        emitShort(0x75, label);
    }

    public void JNZ(String label) {
        emitShort(0x75, label);
    }

    public void JNG(String label) {
        emitShort(0x7E, label);
    }

    public void JNGE(String label) {
        emitShort(0x7C, label);
    }

    public void JNL(String label) {
        emitShort(0x7D, label);
    }

    public void JNLE(String label) {
        emitShort(0x7F, label);
    }

    public void JNO(String label) {
        emitShort(0x71, label);
    }

    public void JNP(String label) {
        emitShort(0x7B, label);
    }

    public void JNS(String label) {
        emitShort(0x79, label);
    }

    public void JO(String label) {
        emitShort(0x70, label);
    }

    public void JP(String label) {
        emitShort(0x7A, label);
    }

    public void JPE(String label) {
        emitShort(0x7A, label);
    }

    public void JPO(String label) {
        emitShort(0x7B, label);
    }

    public void JS(String label) {
        emitShort(0x78, label);
    }

    public void JMP(String label) {
        emitShort(0xEB, label);
    }

    public void JA_Near(String label) {
        emitNear(0x0F, 0x87, label);
    }

    public void JAE_Near(String label) {
        emitNear(0x0F, 0x83, label);
    }

    public void JB_Near(String label) {
        emitNear(0x0F, 0x82, label);
    }

    public void JBE_Near(String label) {
        emitNear(0x0F, 0x86, label);
    }

    public void JC_Near(String label) {
        emitNear(0x0F, 0x82, label);
    }

    public void JE_Near(String label) {
        emitNear(0x0F, 0x84, label);
    }

    public void JZ_Near(String label) {
        emitNear(0x0F, 0x84, label);
    }

    public void JG_Near(String label) {
        emitNear(0x0F, 0x8F, label);
    }

    public void JGE_Near(String label) {
        emitNear(0x0F, 0x8D, label);
    }

    public void JL_Near(String label) {
        emitNear(0x0F, 0x8C, label);
    }

    public void JLE_Near(String label) {
        emitNear(0x0F, 0x8E, label);
    }

    public void JNA_Near(String label) {
        emitNear(0x0F, 0x86, label);
    }

    public void JNAE_Near(String label) {
        emitNear(0x0F, 0x82, label);
    }

    public void JNB_Near(String label) {
        emitNear(0x0F, 0x83, label);
    }

    public void JNBE_Near(String label) {
        emitNear(0x0F, 0x87, label);
    }

    public void JNC_Near(String label) {
        emitNear(0x0F, 0x83, label);
    }

    public void JNE_Near(String label) {
        emitNear(0x0F, 0x85, label);
    }

    public void JNZ_Near(String label) {
        emitNear(0x0F, 0x85, label);
    }

    public void JNG_Near(String label) {
        emitNear(0x0F, 0x8E, label);
    }

    public void JNGE_Near(String label) {
        emitNear(0x0F, 0x8C, label);
    }

    public void JNL_Near(String label) {
        emitNear(0x0F, 0x8D, label);
    }

    public void JNLE_Near(String label) {
        emitNear(0x0F, 0x8F, label);
    }

    public void JNO_Near(String label) {
        emitNear(0x0F, 0x81, label);
    }

    public void JNP_Near(String label) {
        emitNear(0x0F, 0x8B, label);
    }

    public void JNS_Near(String label) {
        emitNear(0x0F, 0x89, label);
    }

    public void JO_Near(String label) {
        emitNear(0x0F, 0x80, label);
    }

    public void JP_Near(String label) {
        emitNear(0x0F, 0x8A, label);
    }

    public void JPE_Near(String label) {
        emitNear(0x0F, 0x8A, label);
    }

    public void JPO_Near(String label) {
        emitNear(0x0F, 0x8B, label);
    }

    public void JS_Near(String label) {
        emitNear(0x0F, 0x88, label);
    }

    public void JMP_Near(String label) {
        emitNear(0xE9, 0x00, label);
    }

    public void CALL(String label) {

        checkLabelName(label);

        int displacementPos = builder.size();

        builder.file_write(0xE8);

        builder.file_write(0, 0, 0, 0);

        int instructionEnd = builder.currentAddress();

        Integer target = labels.get(label);

        if (target != null) {

            patchRelative32(
                    displacementPos + 1,
                    instructionEnd,
                    target
            );

        } else {

            addPending(
                    label,
                    displacementPos + 1,
                    instructionEnd,
                    4
            );
        }
    }

    public void JZ(int targetAddress) {
        emitShortAddress(0x74, targetAddress);
    }

    public void JNZ(int targetAddress) {
        emitShortAddress(0x75, targetAddress);
    }

    public void JE(int targetAddress) {
        emitShortAddress(0x74, targetAddress);
    }

    public void JNE(int targetAddress) {
        emitShortAddress(0x75, targetAddress);
    }

    public void JMP(int targetAddress) {
        emitShortAddress(0xEB, targetAddress);
    }

    public void JZ_Near(int targetAddress) {
        emitNearAddress(0x0F, 0x84, targetAddress);
    }

    public void JNZ_Near(int targetAddress) {
        emitNearAddress(0x0F, 0x85, targetAddress);
    }

    public void JMP_Near(int targetAddress) {
        emitNearAddress(0xE9, 0x00, targetAddress);
    }

    private void emitShortAddress(
            int opcode,
            int targetAddress
    ) {

        int opcodePos = builder.size();

        builder.file_write(opcode);
        builder.file_write(0);

        int instructionEnd =
                builder.currentAddress();

        patchRelative8(
                opcodePos + 1,
                instructionEnd,
                targetAddress
        );
    }

    private void emitNearAddress(
            int opcode1,
            int opcode2,
            int targetAddress
    ) {

        int opcodePos = builder.size();

        if (opcode1 == 0xE9) {

            builder.file_write(opcode1);

            builder.file_write(
                    0, 0, 0, 0
            );

            int instructionEnd =
                    builder.currentAddress();

            patchRelative32(
                    opcodePos + 1,
                    instructionEnd,
                    targetAddress
            );

        } else {

            builder.file_write(
                    opcode1,
                    opcode2
            );

            builder.file_write(
                    0, 0, 0, 0
            );

            int instructionEnd =
                    builder.currentAddress();

            patchRelative32(
                    opcodePos + 2,
                    instructionEnd,
                    targetAddress
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
