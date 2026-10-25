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

package arch.Func;

import Builder.Builder;
import arch.SymbolTarget;
import arch.builtin.Env;

import java.util.*;

public class Functions {

    private final Builder b;

    private final Map<String, Integer> funcAddr =
        new LinkedHashMap<>();

    private final Map<String, Runnable> funcBodies =
        new LinkedHashMap<>();

    private final Map<String, Integer> labelAddr =
        new LinkedHashMap<>();

    private final Set<String> exportedNames =
        new LinkedHashSet<>();

    private final Set<String> exportedLabels =
        new LinkedHashSet<>();

    private SymbolTarget symbolTarget;

    private boolean functionsEmitted = false;

    private enum PatchType {
        NEAR,
        SHORT
    }

    private static final class Patch {

        final int pos;
        final PatchType type;

        Patch(
            int pos,
            PatchType type
        ) {

            this.pos = pos;
            this.type = type;
        }
    }

    private final Map<String, List<Patch>> pending =
        new LinkedHashMap<>();

    public Functions(
        Builder b
    ) {

        if (b == null) {

            throw new IllegalArgumentException(
                "Builder null"
            );
        }

        this.b = b;
    }

    public Functions(
        Builder b,
        SymbolTarget symbolTarget
    ) {

        if (b == null) {

            throw new IllegalArgumentException(
                "Builder null"
            );
        }

        this.b = b;
        this.symbolTarget = symbolTarget;

        registerExistingSymbols();
    }

    public void attachSymbolTarget(
        SymbolTarget symbolTarget
    ) {

        this.symbolTarget =
            symbolTarget;

        registerExistingSymbols();
    }

    private void registerExistingSymbols() {

        if (
            symbolTarget == null
        ) {

            return;
        }

        for (
            Map.Entry<String, Integer> entry :
            funcAddr.entrySet()
        ) {

            registerFunctionSymbol(
                entry.getKey(),
                entry.getValue()
            );
        }

        for (
            Map.Entry<String, Integer> entry :
            labelAddr.entrySet()
        ) {

            registerLabelSymbol(
                entry.getKey(),
                entry.getValue()
            );
        }
    }

    private boolean usesRealSymbols() {

        return symbolTarget != null;
    }

    private int getBits() {

        int bits =
            Env.GET_BITS();

        if (
            bits != 16 &&
            bits != 32 &&
            bits != 64
        ) {

            throw new IllegalStateException(
                "Bits no soportados en Env: "
                + bits
            );
        }

        return bits;
    }

    private boolean usesRel16() {

        return getBits() == 16;
    }

    private boolean usesRel32() {

        return getBits() == 32 ||
               getBits() == 64;
    }

    public void FUNCTION(
        String name,
        Runnable body
    ) {

        validateFunctionName(
            name
        );

        if (
            body == null
        ) {

            throw new IllegalArgumentException(
                "body null"
            );
        }

        if (
            functionsEmitted
        ) {

            throw new IllegalStateException(
                "No se puede registrar la función '"
                + name
                + "' después de emitAllFunctions()"
            );
        }

        if (
            funcBodies.containsKey(name) ||
            funcAddr.containsKey(name)
        ) {

            throw new IllegalArgumentException(
                "Duplicated Function : "
                + name
            );
        }

        if (
            labelAddr.containsKey(name)
        ) {

            throw new IllegalArgumentException(
                "El nombre '"
                + name
                + "' ya pertenece a un label"
            );
        }

        funcBodies.put(
            name,
            body
        );
    }

    public void EXPORT_FUNCTION(
        String name
    ) {

        validateFunctionName(
            name
        );

        exportedNames.add(
            name
        );

        Integer addr =
            funcAddr.get(name);

        if (
            addr != null
        ) {

            registerFunctionSymbol(
                name,
                addr
            );
        }
    }

    private void registerFunctionSymbol(
        String name,
        int addr
    ) {

        validateSymbolName(
            name
        );

        validateAddress(
            addr
        );

        if (
            !usesRealSymbols()
        ) {

            return;
        }

        symbolTarget.defineFunctionSymbol(
            name,
            addr,
            arch.Visibility.isExported(name)
        );

        arch.Diagnostics.log(
            "[Functions] GLOBAL FUNCTION '"
            + name
            + "' offset="
            + addr
        );
    }

    private void registerLabelSymbol(
        String name,
        int addr
    ) {

        validateSymbolName(
            name
        );

        validateAddress(
            addr
        );

        if (
            !usesRealSymbols()
        ) {

            return;
        }

        symbolTarget.defineFunctionSymbol(
            name,
            addr,
            arch.Visibility.isExported(name)
        );

        arch.Diagnostics.log(
            "[Functions] GLOBAL LABEL '"
            + name
            + "' offset="
            + addr
        );
    }

    public void emitAllFunctions() {

        if (
            functionsEmitted
        ) {

            arch.Diagnostics.log(
                "[Functions] emitAllFunctions() ya fue ejecutado."
            );

            return;
        }

        functionsEmitted = true;

        if (
            funcBodies.isEmpty()
        ) {

            arch.Diagnostics.log(
                "[Functions] No function bodies to emit."
            );

            resolveRemainingPatches();

            return;
        }

        arch.Diagnostics.log(
            "[Functions] emitAllFunctions:"
            + " bits="
            + getBits()
            + " realSymbols="
            + usesRealSymbols()
            + " functions="
            + funcBodies.keySet()
        );

        for (
            Map.Entry<String, Runnable> entry :
            funcBodies.entrySet()
        ) {

            String name =
                entry.getKey();

            Runnable body =
                entry.getValue();

            Integer previous =
                funcAddr.get(name);

            int addr;

            if (
                previous == null
            ) {

                addr =
                    b.markLabel();

                funcAddr.put(
                    name,
                    addr
                );

            } else {

                addr =
                    previous;

                if (
                    addr != b.size()
                ) {

                    throw new IllegalStateException(
                        "La función '"
                        + name
                        + "' fue registrada en "
                        + addr
                        + " pero el Builder está en "
                        + b.size()
                    );
                }
            }

            exportedNames.add(
                name
            );

            arch.Diagnostics.log(
                "[Functions] GLOBAL FUNCTION '"
                + name
                + "' at offset "
                + addr
            );

            registerFunctionSymbol(
                name,
                addr
            );

            resolvePatchesFor(
                name,
                addr
            );

            body.run();

            b.file_write(
                0xC3
            );
        }

        resolveRemainingPatches();

        funcBodies.clear();

        arch.Diagnostics.log(
            "[Functions] emitAllFunctions complete."
            + " funcAddr="
            + funcAddr
            + " labelAddr="
            + labelAddr
            + " builderSize="
            + b.size()
        );
    }

    public void CALL_FUNCTION(
        String name
    ) {

        validateSymbolName(
            name
        );

        Integer addr =
            getSymbolAddress(
                name
            );

        if (
            addr != null
        ) {

            int operandPos =
                emit_call_placeholder();

            b.noteSymbolFixup(operandPos, name);

            arch.Diagnostics.log(
                "[Functions] CALL immediate"
                + " name='"
                + name
                + "' operandPos="
                + operandPos
                + " addr="
                + addr
            );

            patchNear(
                operandPos,
                addr
            );

            return;
        }

        int operandPos =
            emit_call_placeholder();

        b.noteSymbolFixup(operandPos, name);

            arch.Diagnostics.log(
            "[Functions] CALL pending"
            + " name='"
            + name
            + "' operandPos="
            + operandPos
        );

        pending
            .computeIfAbsent(
                name,
                k -> new ArrayList<>()
            )
            .add(
                new Patch(
                    operandPos,
                    PatchType.NEAR
                )
            );
    }

    public void JUMP_TO_FUNCTION(
        String name
    ) {

        validateSymbolName(
            name
        );

        Integer addr =
            getSymbolAddress(
                name
            );

        if (
            addr != null
        ) {

            int current =
                b.size();

            int rel8 =
                addr -
                (
                    current + 2
                );

            if (
                rel8 >= -128 &&
                rel8 <= 127
            ) {

                int operandPos =
                    emit_jmp_short_placeholder();

                arch.Diagnostics.log(
                    "[Functions] JUMP short"
                    + " name='"
                    + name
                    + "' operandPos="
                    + operandPos
                    + " addr="
                    + addr
                    + " rel8="
                    + rel8
                );

                b.patchByte(
                    operandPos,
                    (byte) rel8
                );

                return;
            }

            int operandPos =
                emit_jmp_near_placeholder();

            b.noteSymbolFixup(operandPos, name);

            arch.Diagnostics.log(
                "[Functions] JUMP near"
                + " name='"
                + name
                + "' operandPos="
                + operandPos
                + " addr="
                + addr
            );

            patchNear(
                operandPos,
                addr
            );

            return;
        }

        int operandPos =
            emit_jmp_near_placeholder();

        b.noteSymbolFixup(operandPos, name);

            arch.Diagnostics.log(
            "[Functions] JUMP pending"
            + " name='"
            + name
            + "' operandPos="
            + operandPos
        );

        pending
            .computeIfAbsent(
                name,
                k -> new ArrayList<>()
            )
            .add(
                new Patch(
                    operandPos,
                    PatchType.NEAR
                )
            );
    }

    public void JUMP_TO_LABEL(
        String name
    ) {

        JUMP_TO_FUNCTION(
            name
        );
    }

    public void defineFunctionAt(
        String name,
        int address
    ) {

        validateFunctionName(
            name
        );

        validateAddress(
            address
        );

        if (
            labelAddr.containsKey(name)
        ) {

            throw new IllegalStateException(
                "El símbolo '"
                + name
                + "' ya está definido como label"
            );
        }

        Integer previous =
            funcAddr.get(name);

        if (
            previous != null &&
            previous != address
        ) {

            throw new IllegalStateException(
                "Function '"
                + name
                + "' already defined at "
                + previous
                + ", cannot redefine at "
                + address
            );
        }

        funcAddr.put(
            name,
            address
        );

        exportedNames.add(
            name
        );

        arch.Diagnostics.log(
            "[Functions] defineFunctionAt '"
            + name
            + "' = "
            + address
        );

        registerFunctionSymbol(
            name,
            address
        );

        resolvePatchesFor(
            name,
            address
        );
    }

    public int LABEL(
        String name
    ) {

        validateLabelName(
            name
        );

        if (
            funcAddr.containsKey(name)
        ) {

            throw new IllegalArgumentException(
                "El símbolo '"
                + name
                + "' ya pertenece a una función"
            );
        }

        if (
            labelAddr.containsKey(name)
        ) {

            throw new IllegalArgumentException(
                "Duplicated Label : "
                + name
            );
        }

        int address =
            b.markLabel();

        labelAddr.put(
            name,
            address
        );

        exportedLabels.add(
            name
        );

        arch.Diagnostics.log(
            "[Functions] GLOBAL LABEL '"
            + name
            + "' at offset "
            + address
        );

        registerLabelSymbol(
            name,
            address
        );

        resolvePatchesFor(
            name,
            address
        );

        return address;
    }

    public int GLOBAL_LABEL(
        String name
    ) {

        return LABEL(
            name
        );
    }

    public int globalLabel(
        String name
    ) {

        return LABEL(
            name
        );
    }

    public int defineLabelAt(
        String name,
        int address
    ) {

        validateLabelName(
            name
        );

        validateAddress(
            address
        );

        if (
            funcAddr.containsKey(name)
        ) {

            throw new IllegalStateException(
                "El símbolo '"
                + name
                + "' ya está definido como función"
            );
        }

        Integer previous =
            labelAddr.get(name);

        if (
            previous != null &&
            previous != address
        ) {

            throw new IllegalStateException(
                "Label '"
                + name
                + "' already defined at "
                + previous
                + ", cannot redefine at "
                + address
            );
        }

        labelAddr.put(
            name,
            address
        );

        exportedLabels.add(
            name
        );

        arch.Diagnostics.log(
            "[Functions] defineLabelAt '"
            + name
            + "' = "
            + address
        );

        registerLabelSymbol(
            name,
            address
        );

        resolvePatchesFor(
            name,
            address
        );

        return address;
    }

    public void EXPORT_LABEL(
        String name
    ) {

        validateLabelName(
            name
        );

        exportedLabels.add(
            name
        );

        Integer addr =
            labelAddr.get(name);

        if (
            addr != null
        ) {

            registerLabelSymbol(
                name,
                addr
            );
        }
    }

    public Integer getSymbolOffset(
        String name
    ) {

        return getSymbolAddress(
            name
        );
    }

    public Integer getFunctionOffset(
        String name
    ) {

        return funcAddr.get(
            name
        );
    }

    public Integer getLabelOffset(
        String name
    ) {

        return labelAddr.get(
            name
        );
    }

    public boolean isFunctionDefined(
        String name
    ) {

        return name != null &&
               funcAddr.containsKey(name);
    }

    public boolean isLabelDefined(
        String name
    ) {

        return name != null &&
               labelAddr.containsKey(name);
    }

    public boolean isSymbolDefined(
        String name
    ) {

        return name != null &&
               (
                   funcAddr.containsKey(name) ||
                   labelAddr.containsKey(name)
               );
    }

    public boolean isFunctionExported(
        String name
    ) {

        return name != null &&
               exportedNames.contains(name);
    }

    public boolean isLabelExported(
        String name
    ) {

        return name != null &&
               exportedLabels.contains(name);
    }

    public Map<String, Integer> functions() {

        return Collections.unmodifiableMap(
            funcAddr
        );
    }

    public Map<String, Integer> labels() {

        return Collections.unmodifiableMap(
            labelAddr
        );
    }

    public Map<String, Integer> symbols() {

        Map<String, Integer> result =
            new LinkedHashMap<>();

        result.putAll(
            funcAddr
        );

        result.putAll(
            labelAddr
        );

        return Collections.unmodifiableMap(
            result
        );
    }

    private Integer getSymbolAddress(
        String name
    ) {

        Integer addr =
            funcAddr.get(name);

        if (
            addr != null
        ) {

            return addr;
        }

        return labelAddr.get(
            name
        );
    }

    private void resolvePatchesFor(
        String name,
        int addr
    ) {

        List<Patch> list =
            pending.remove(name);

        if (
            list == null ||
            list.isEmpty()
        ) {

            arch.Diagnostics.log(
                "[Functions] pending patches for '"
                + name
                + "': 0"
            );

            return;
        }

        arch.Diagnostics.log(
            "[Functions] resolving "
            + list.size()
            + " patch(es) for '"
            + name
            + "' -> "
            + addr
        );

        for (
            Patch p :
            list
        ) {

            if (
                p == null
            ) {

                continue;
            }

            try {

                switch (
                    p.type
                ) {

                    case NEAR:

                        patchNear(
                            p.pos,
                            addr
                        );

                        break;

                    case SHORT:

                        patchShort(
                            p.pos,
                            addr
                        );

                        break;

                    default:

                        throw new IllegalStateException(
                            "Unknown patch type: "
                            + p.type
                        );
                }

            } catch (
                RuntimeException ex
            ) {

                throw new IllegalStateException(
                    "[Functions] Error patching '"
                    + name
                    + "' at operandPos="
                    + p.pos,
                    ex
                );
            }
        }
    }

    private void resolveRemainingPatches() {

        if (
            pending.isEmpty()
        ) {

            return;
        }

        StringBuilder unresolved =
            new StringBuilder();

        for (
            Map.Entry<String, List<Patch>> entry :
            pending.entrySet()
        ) {

            if (
                entry.getValue() == null ||
                entry.getValue().isEmpty()
            ) {

                continue;
            }

            if (
                unresolved.length() > 0
            ) {

                unresolved.append(
                    ", "
                );
            }

            unresolved
                .append(
                    entry.getKey()
                )
                .append(
                    " ("
                )
                .append(
                    entry.getValue().size()
                )
                .append(
                    " patch(es))"
                );
        }

        if (
            unresolved.length() == 0
        ) {

            pending.clear();

            return;
        }

        throw new IllegalStateException(
            "[Functions] Unresolved function/label "
            + "reference(s): "
            + unresolved
            + "."
        );
    }

    private void patchNear(
        int operandPos,
        int addr
    ) {

        if (
            operandPos < 0
        ) {

            throw new IllegalArgumentException(
                "Invalid operandPos: "
                + operandPos
            );
        }

        validateAddress(
            addr
        );

        if (
            usesRel16()
        ) {

            int rel16 =
                addr -
                (
                    operandPos + 2
                );

            if (
                rel16 < Short.MIN_VALUE ||
                rel16 > Short.MAX_VALUE
            ) {

                throw new IllegalStateException(
                    "rel16 fuera de rango: "
                    + rel16
                    + " operandPos="
                    + operandPos
                    + " addr="
                    + addr
                );
            }

            arch.Diagnostics.log(
                "[Functions] patchNear rel16"
                + " operandPos="
                + operandPos
                + " addr="
                + addr
                + " rel="
                + rel16
            );

            b.patchBytes(
                operandPos,
                new byte[] {
                    (byte) (
                        rel16 & 0xFF
                    ),
                    (byte) (
                        (rel16 >> 8) & 0xFF
                    )
                }
            );

            return;
        }

        if (
            usesRel32()
        ) {

            long rel32 =
                (long) addr -
                (long) (
                    operandPos + 4
                );

            if (
                rel32 < Integer.MIN_VALUE ||
                rel32 > Integer.MAX_VALUE
            ) {

                throw new IllegalStateException(
                    "rel32 fuera de rango: "
                    + rel32
                    + " operandPos="
                    + operandPos
                    + " addr="
                    + addr
                );
            }

            int value =
                (int) rel32;

            arch.Diagnostics.log(
                "[Functions] patchNear rel32"
                + " operandPos="
                + operandPos
                + " addr="
                + addr
                + " rel="
                + value
            );

            b.patchBytes(
                operandPos,
                new byte[] {
                    (byte) (
                        value & 0xFF
                    ),
                    (byte) (
                        (value >> 8) & 0xFF
                    ),
                    (byte) (
                        (value >> 16) & 0xFF
                    ),
                    (byte) (
                        (value >> 24) & 0xFF
                    )
                }
            );

            return;
        }

        throw new IllegalStateException(
            "Modo de bits desconocido: "
            + getBits()
        );
    }

    private void patchShort(
        int operandPos,
        int addr
    ) {

        int rel8 =
            addr -
            (
                operandPos + 1
            );

        if (
            rel8 < -128 ||
            rel8 > 127
        ) {

            throw new IllegalStateException(
                "rel8 fuera de rango: "
                + rel8
                + " operandPos="
                + operandPos
                + " addr="
                + addr
            );
        }

        arch.Diagnostics.log(
            "[Functions] patchShort"
            + " operandPos="
            + operandPos
            + " addr="
            + addr
            + " rel="
            + rel8
        );

        b.patchByte(
            operandPos,
            (byte) rel8
        );
    }

    private int emit_call_placeholder() {

        int opcodePos =
            b.size();

        b.file_write(
            0xE8
        );

        int operandPos =
            b.size();

        if (
            usesRel16()
        ) {

            b.file_write_shortLE(
                (short) 0
            );

        } else {

            b.file_write_intLE(
                0
            );
        }

        arch.Diagnostics.log(
            "[Functions] emit CALL"
            + " opcodePos="
            + opcodePos
            + " operandPos="
            + operandPos
            + " operandSize="
            + (
                usesRel16()
                    ? 2
                    : 4
            )
        );

        return operandPos;
    }

    private int emit_jmp_near_placeholder() {

        b.file_write(
            0xE9
        );

        int operandPos =
            b.size();

        if (
            usesRel16()
        ) {

            b.file_write_shortLE(
                (short) 0
            );

        } else {

            b.file_write_intLE(
                0
            );
        }

        return operandPos;
    }

    private int emit_jmp_short_placeholder() {

        b.file_write(
            0xEB
        );

        int operandPos =
            b.size();

        b.file_write(
            0x00
        );

        return operandPos;
    }

    private void validateFunctionName(
        String name
    ) {

        validateSymbolName(
            name
        );
    }

    private void validateLabelName(
        String name
    ) {

        validateSymbolName(
            name
        );
    }

    private void validateSymbolName(
        String name
    ) {

        if (
            name == null
        ) {

            throw new IllegalArgumentException(
                "Symbol name null"
            );
        }

        if (
            name.isEmpty()
        ) {

            throw new IllegalArgumentException(
                "Symbol name empty"
            );
        }
    }

    private void validateAddress(
        int address
    ) {

        if (
            address < 0
        ) {

            throw new IllegalArgumentException(
                "Invalid address: "
                + address
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
