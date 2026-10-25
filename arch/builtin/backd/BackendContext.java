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

import static arch.builtin.backd.BackendUtil.cleanSymbol;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import Builder.Builder;
import arch.Jumpz;
import arch.SymbolTarget;
import arch.builtin.Vars;
import arch.builtin.VarsBridge;

public final class BackendContext {

    public static final long NULL = 0x0000000000000000L;

    public static final long INF  = 0x7FF0000000000000L;
    public static final long NINF = 0xFFF0000000000000L;
    public static final long NAN  = 0x7FF8000000000000L;

    public final Builder builder;
    public final Vars vars;
    public final VarsBridge bridge;
    public final Jumpz jumpz;

    public SymbolTarget symbolTarget;

    public int currentReturnType;

    public final java.util.Set<String> addressTaken =
        new java.util.HashSet<>();

    public final Map<String, Integer> dataBias =
        new java.util.HashMap<>();

    public final Map<String, byte[]> pendingGlobalData =
        new LinkedHashMap<>();

    public final Map<String, Integer> globalDataOffsets =
        new LinkedHashMap<>();

    public int globalDataSize = 0;

    public final Map<String, Integer> generatedFunctions =
        new LinkedHashMap<>();

    public final Map<String, Integer> generatedLabels =
        new LinkedHashMap<>();

    public final Map<String, ExternFunction> externFunctions =
        new LinkedHashMap<>();

    public final Map<String, Integer> globalStrings =
        new LinkedHashMap<>();

    public final Map<String, byte[]> globalStringData =
        new LinkedHashMap<>();

    public final Map<String, Integer> globalStringLengths =
        new LinkedHashMap<>();

    public final Set<String> registeredSymbols =
        new HashSet<>();

    public Set<String> localVariables;

    public Map<String, Object> constantValues;

    public Set<String> knownConstants;

    public final Map<String, String> variableTypes =
        new LinkedHashMap<>();

    public BackendContext(
        Builder builder,
        SymbolTarget symbolTarget
    ) {

        if (builder == null) {
            throw new IllegalArgumentException(
                "Backend: Builder no puede ser null"
            );
        }

        this.builder = builder;
        this.symbolTarget = symbolTarget;

        this.vars = new Vars(builder);
        this.vars.signedLookup = n -> {
            String t = typeOf(n);
            return t != null && (t.equals("int8") || t.equals("int16") || t.equals("int32"));
        };
        this.vars.unsignedLookup = n -> {
            String t = typeOf(n);
            return t != null && (t.equals("uint64") || t.equals("uint32") || t.equals("dword")
                || t.equals("uint16") || t.equals("word") || t.equals("uint8") || t.equals("byte"));
        };
        this.bridge = new VarsBridge(vars);
        this.jumpz = new Jumpz(builder);
    }

    public boolean hasGlobalString(String name) {

        if (name == null)
            return false;

        return globalStrings.containsKey(
            cleanSymbol(name)
        );
    }

    public boolean hasGlobalLabel(String name) {

        if (name == null)
            return false;

        return generatedLabels.containsKey(
            cleanSymbol(name)
        );
    }

    public boolean hasGlobalFunction(String name) {

        if (name == null)
            return false;

        return generatedFunctions.containsKey(
            cleanSymbol(name)
        );
    }
public void declareLocal(
    String name,
    int type,
    long value
) {
    String n = cleanSymbol(name);

    if (localVariables.contains(n)) {
        throw new IllegalArgumentException(
            "Variable ya declarada: " + n
        );
    }

    if (
        type == Vars.FLOAT32 ||
        type == Vars.FLOAT64
    ) {
        double d;

        if (type == Vars.FLOAT64) {
            long bits = value;

            long exponent =
                (bits >>> 52) & 0x7FFL;

            if (
                exponent == 0x7FFL &&
                (value < 0L || value >= 0L)
            ) {
                d =
                    Double.longBitsToDouble(bits);
            } else {
                d =
                    (double) value;
            }

        } else {
            d = (double) value;
        }

        vars.Let(
            n,
            type,
            d
        );

        if (constantValues != null) {
            constantValues.put(
                n,
                type == Vars.FLOAT32
                    ? Float.valueOf((float) d)
                    : Double.valueOf(d)
            );
        }

        if (knownConstants != null) {
            if (!addressTaken.contains(n))                knownConstants.add(n);
        }

        localVariables.add(n);
        return;
    }

    vars.Let(
        n,
        type,
        value
    );

    if (constantValues != null) {
        constantValues.put(
            n,
            Long.valueOf(value)
        );
    }

    if (knownConstants != null) {
        if (!addressTaken.contains(n))            knownConstants.add(n);
    }

    localVariables.add(n);
}
public void declareLocal(
    String name,
    int type,
    double value
) {
    String n = cleanSymbol(name);

    if (localVariables.contains(n)) {
        throw new IllegalArgumentException(
            "Variable ya declarada: " + n
        );
    }

    if (
        type != Vars.FLOAT32 &&
        type != Vars.FLOAT64
    ) {
        throw new IllegalArgumentException(
            "Valor double usado con tipo no flotante: " +
            typeName(type)
        );
    }

    vars.Let(
        n,
        type,
        value
    );

    if (constantValues != null) {
        if (type == Vars.FLOAT32) {
            constantValues.put(
                n,
                Float.valueOf((float) value)
            );
        } else {
            constantValues.put(
                n,
                Double.valueOf(value)
            );
        }
    }

    if (knownConstants != null) {
        if (!addressTaken.contains(n))            knownConstants.add(n);
    }

    localVariables.add(n);
}

    public void declareLocal(
        String name,
        int type,
        String source
    ) {

        String n = cleanSymbol(name);
        String s = cleanSymbol(source);

        if (localVariables.contains(n)) {
            throw new IllegalArgumentException(
                "Variable ya declarada: " + n
            );
        }

        vars.Let(
            n,
            type,
            s
        );

        localVariables.add(n);
    }

    public void declareType(
        String name,
        String type
    ) {

        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException(
                ".type sin nombre de variable"
            );
        }

        if (type == null || type.trim().isEmpty()) {
            throw new IllegalArgumentException(
                ".type sin tipo para '" + name + "'"
            );
        }

        String n = cleanSymbol(name);
        String t = type.trim().toLowerCase();

        variableTypes.put(
            n,
            t
        );
    }

    public String typeOf(
        String name
    ) {

        if (name == null)
            return null;

        return variableTypes.get(
            cleanSymbol(name)
        );
    }

    public boolean hasType(
        String name
    ) {

        if (name == null)
            return false;

        return variableTypes.containsKey(
            cleanSymbol(name)
        );
    }

    public int varsTypeFor(
        String type
    ) {

        if (type == null ||
            type.trim().isEmpty()) {

            throw new IllegalArgumentException(
                "Tipo vacío"
            );
        }

        String t =
            type.trim().toLowerCase();

        switch (t) {

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
                return Vars.FLOAT32;

            case "float64":
                return Vars.FLOAT64;

            default:
                throw new IllegalArgumentException(
                    "Tipo ASHIR no soportado: " + type
                );
        }
    }

    public int varsTypeOf(
        String name
    ) {

        String type = typeOf(name);

        if (type == null)
            return Vars.QWORD;

        return varsTypeFor(type);
    }

    public boolean isUnsignedType(
        String type
    ) {

        if (type == null)
            return false;

        switch (type.trim().toLowerCase()) {

            case "uint8":
            case "uint16":
            case "uint32":
            case "uint64":
                return true;

            default:
                return false;
        }
    }

    public boolean isUnsigned(
        String name
    ) {

        return isUnsignedType(
            typeOf(name)
        );
    }

    public boolean isSignedType(
        String type
    ) {

        if (type == null)
            return false;

        switch (type.trim().toLowerCase()) {

            case "int8":
            case "int16":
            case "int32":
            case "int64":
                return true;

            default:
                return false;
        }
    }

    public boolean isSigned(
        String name
    ) {

        return isSignedType(
            typeOf(name)
        );
    }

    public boolean isFloatType(
        int type
    ) {

        return type == Vars.FLOAT32 ||
               type == Vars.FLOAT64;
    }

    public boolean isFloat(
        String name
    ) {

        if (name == null)
            return false;

        String n = cleanSymbol(name);

        if (vars.exists(n))
            return isFloatType(
                vars.TypeOf(n)
            );

        if (hasType(n))
            return isFloatType(
                varsTypeOf(n)
            );

        return false;
    }

    public int bitsForType(
        String type
    ) {

        int varsType =
            varsTypeFor(type);

        switch (varsType) {

            case Vars.BYTE:
                return 8;

            case Vars.WORD:
                return 16;

            case Vars.DWORD:
                return 32;

            case Vars.QWORD:
                return 64;

            case Vars.FLOAT32:
                return 32;

            case Vars.FLOAT64:
                return 64;

            default:
                throw new IllegalArgumentException(
                    "Tipo sin tamaño conocido: " +
                    type
                );
        }
    }

    public int bitsOf(
        String name
    ) {

        String type =
            typeOf(name);

        if (type == null) {

            if (vars.exists(
                cleanSymbol(name)
            )) {

                return bitsOfVarsType(
                    vars.TypeOf(
                        cleanSymbol(name)
                    )
                );
            }

            return 64;
        }

        return bitsForType(type);
    }

    public int bitsOfVarsType(
        int type
    ) {

        switch (type) {

            case Vars.BYTE:
                return 8;

            case Vars.WORD:
                return 16;

            case Vars.DWORD:
                return 32;

            case Vars.QWORD:
                return 64;

            case Vars.FLOAT32:
                return 32;

            case Vars.FLOAT64:
                return 64;

            default:
                throw new IllegalArgumentException(
                    "Tipo Vars desconocido: " +
                    type
                );
        }
    }

    public int typeForBits(
        int bits
    ) {

        switch (bits) {

            case 8:
                return Vars.BYTE;

            case 16:
                return Vars.WORD;

            case 32:
                return Vars.DWORD;

            case 64:
                return Vars.QWORD;

            default:
                throw new IllegalArgumentException(
                    "Tamaño entero inválido: " +
                    bits
                );
        }
    }

    public void clearConstant(
        String name
    ) {

        if (name == null)
            return;

        String n =
            cleanSymbol(name);

        constantValues.remove(n);
        knownConstants.remove(n);
    }
public int typeForTemporary(
    String name
) {

    String n =
        cleanSymbol(name);

    if ("__ret".equals(n)) {
        if (vars.exists(n)) {
            return vars.TypeOf(n);
        }

        return Vars.QWORD;
    }

    if (vars.exists(n))
        return vars.TypeOf(n);

    String declaredType =
        typeOf(n);

    if (declaredType != null)
        return varsTypeFor(declaredType);

    return Vars.QWORD;
}

    public boolean isCompilerTemporary(
        String name
    ) {

        if (name == null)
            return false;

        return cleanSymbol(name)
            .startsWith("__tmp");
    }

    public void ensureTemporary(
        String name
    ) {

        String n =
            cleanSymbol(name);

        if (n == null ||
            n.isEmpty()) {

            throw new IllegalArgumentException(
                "Temporal vacío"
            );
        }

        if (localVariables.contains(n))
            return;

        if (vars.exists(n)) {
            localVariables.add(n);
            return;
        }

        int temporaryType =
            typeForTemporary(n);

        if (temporaryType == Vars.FLOAT32 ||
            temporaryType == Vars.FLOAT64) {

            declareLocal(
                n,
                temporaryType,
                0.0
            );

        } else {

            declareLocal(
                n,
                temporaryType,
                0L
            );
        }
    }

    public void ensureTemporary(
        String name,
        int type
    ) {

        String n =
            cleanSymbol(name);

        if (n == null ||
            n.isEmpty()) {

            throw new IllegalArgumentException(
                "Temporal vacío"
            );
        }

        if (vars.exists(n)) {

            int existingType =
                vars.TypeOf(n);

            if (existingType != type) {

                boolean bothInteger =
                    existingType != Vars.FLOAT32 && existingType != Vars.FLOAT64 &&
                    type != Vars.FLOAT32 && type != Vars.FLOAT64;

                if (isCompilerTemporary(n) && bothInteger) {

                    vars.Delete(n);
                    localVariables.remove(n);

                } else {

                    throw new IllegalArgumentException(
                        "'" + n + "' already exists as " +
                        typeName(existingType) + " but " +
                        typeName(type) + " is needed"
                    );
                }
            }

            if (vars.exists(n)) {
                localVariables.add(n);
                return;
            }

        }

        if (type == Vars.FLOAT32 ||
            type == Vars.FLOAT64) {

            declareLocal(
                n,
                type,
                0.0
            );

        } else {

            declareLocal(
                n,
                type,
                0L
            );
        }
    }

    public void ensureTemporaryBits(
        String name,
        int bits
    ) {

        ensureTemporary(
            name,
            typeForBits(bits)
        );
    }

    public String typeName(
        int type
    ) {

        switch (type) {

            case Vars.BYTE:
                return "BYTE";

            case Vars.WORD:
                return "WORD";

            case Vars.DWORD:
                return "DWORD";

            case Vars.QWORD:
                return "QWORD";

            case Vars.FLOAT32:
                return "FLOAT32";

            case Vars.FLOAT64:
                return "FLOAT64";

            default:
                return "TYPE(" +
                    type +
                    ")";
        }
    }

    public void ensureTemporaryLike(
        String temporary,
        String source
    ) {

        String t =
            cleanSymbol(temporary);

        String s =
            cleanSymbol(source);

        if (t == null ||
            t.isEmpty()) {

            throw new IllegalArgumentException(
                "Temporal vacío"
            );
        }

        if (s == null ||
            s.isEmpty()) {

            throw new IllegalArgumentException(
                "Fuente vacía para temporal '" +
                t +
                "'"
            );
        }

        int sourceType;

        if (vars.exists(s)) {

            sourceType =
                vars.TypeOf(s);

        } else if (hasType(s)) {

            sourceType =
                varsTypeOf(s);

        } else {

            sourceType =
                Vars.QWORD;
        }

        ensureTemporary(
            t,
            sourceType
        );
    }

    public int ensureTemporaryLikeType(
        String temporary,
        String source
    ) {

        ensureTemporaryLike(
            temporary,
            source
        );

        return vars.TypeOf(
            cleanSymbol(temporary)
        );
    }

    public void clearVariableTypes() {

        variableTypes.clear();
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
