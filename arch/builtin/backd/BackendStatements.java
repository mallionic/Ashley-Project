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
import static arch.builtin.backd.BackendUtil.isNumber;
import static arch.builtin.backd.BackendUtil.isStringLiteral;
import static arch.builtin.backd.BackendUtil.macroStatements;
import static arch.builtin.backd.BackendUtil.parseNumber;

import java.util.Collections;
import java.util.List;

import arch.builtin.Parsing;
import arch.builtin.Vars;

public final class BackendStatements {

    private final BackendContext ctx;
    private final Vars vars;
    private final BackendExterns externs;
    private final BackendGlobals globals;
    private final BackendRegAssign regAssign;

    public BackendStatements(
        BackendContext ctx,
        BackendExterns externs,
        BackendGlobals globals,
        BackendRegAssign regAssign
    ) {
        this.ctx = ctx;
        this.vars = ctx.vars;
        this.externs = externs;
        this.globals = globals;
        this.regAssign = regAssign;
    }

    private boolean isFloatingNumber(
        String value
    ) {
        if (value == null)
            return false;

        String s = value.trim();

        if (s.isEmpty())
            return false;

        if (
            s.endsWith("f") ||
            s.endsWith("F") ||
            s.endsWith("d") ||
            s.endsWith("D")
        ) {
            return true;
        }

        if (s.indexOf('.') >= 0)
            return true;

        return s.indexOf('e') >= 0 ||
               s.indexOf('E') >= 0;
    }

    private double parseFloatingNumber(
        String value
    ) {
        String s = value.trim();

        if (
            s.endsWith("f") ||
            s.endsWith("F") ||
            s.endsWith("d") ||
            s.endsWith("D")
        ) {
            s = s.substring(
                0,
                s.length() - 1
            );
        }

        return Double.parseDouble(s);
    }

    private Object parseCallNumber(
        String value
    ) {
        if (isFloatingNumber(value)) {
            return Double.valueOf(
                parseFloatingNumber(value)
            );
        }

        return Long.valueOf(
            parseNumber(value)
        );
    }

    private boolean isDoubleBitsLiteral(
        String value
    ) {
        if (value == null)
            return false;

        String s = value.trim();

        if (
            !s.startsWith("0x") &&
            !s.startsWith("0X")
        ) {
            return false;
        }

        String hex =
            s.substring(2);

        if (hex.length() != 16)
            return false;

        try {

            long bits =
                Long.parseUnsignedLong(
                    hex,
                    16
                );

            long exponent =
                (bits >>> 52) & 0x7FFL;

            return exponent == 0x7FFL;

        } catch (NumberFormatException e) {

            return false;
        }
    }

    private double parseDoubleBits(
        String value
    ) {
        String hex =
            value.trim().substring(2);

        long bits =
            Long.parseUnsignedLong(
                hex,
                16
            );

        return Double.longBitsToDouble(bits);
    }

    public void generateStmt(
        Parsing.Stmt stmt
    ) {
        if (stmt == null)
            return;

        if (stmt instanceof Parsing.ExpandedStmt) {

            Parsing.ExpandedStmt e =
                (Parsing.ExpandedStmt) stmt;

            if (e.statements != null) {

                for (
                    Parsing.Stmt child :
                    e.statements
                ) {
                    generateStmt(child);
                }
            }

            return;
        }

        if (stmt instanceof Parsing.TypeStmt) {

            Parsing.TypeStmt typeStmt =
                (Parsing.TypeStmt) stmt;

            if (
                typeStmt.name == null ||
                typeStmt.name.isEmpty()
            ) {
                throw new IllegalArgumentException(
                    ".type sin nombre de variable"
                );
            }

            if (
                typeStmt.type == null ||
                typeStmt.type.isEmpty()
            ) {
                throw new IllegalArgumentException(
                    ".type sin tipo para '" +
                    typeStmt.name +
                    "'"
                );
            }

            ctx.declareType(
                typeStmt.name,
                typeStmt.type
            );

            return;
        }

        if (stmt instanceof Parsing.RawStmt) {

            globals.generateRaw(
                (Parsing.RawStmt) stmt
            );

            return;
        }

        if (stmt instanceof Parsing.PtrDeclStmt) {

            generatePtrDecl(
                (Parsing.PtrDeclStmt) stmt
            );

            return;
        }

        if (stmt instanceof Parsing.RegAssignStmt) {

            regAssign.generateRegAssign(
                (Parsing.RegAssignStmt) stmt
            );

            return;
        }

        if (stmt instanceof Parsing.IncDecStmt) {

            generateIncDec(
                (Parsing.IncDecStmt) stmt
            );

            return;
        }

        if (stmt instanceof Parsing.CallStmt) {

            generateCall(
                (Parsing.CallStmt) stmt
            );

            return;
        }

        if (stmt instanceof Parsing.StoreStmt) {

            generateStore(
                (Parsing.StoreStmt) stmt
            );

            return;
        }

        if (stmt instanceof Parsing.ReturnStmt) {

            generateReturn(
                (Parsing.ReturnStmt) stmt
            );

            return;
        }

        if (stmt instanceof Parsing.MacroBlockStmt) {

            for (
                Parsing.Stmt child :
                macroStatements(
                    (Parsing.MacroBlockStmt) stmt
                )
            ) {
                generateStmt(child);
            }

            return;
        }

        throw new IllegalStateException(
            "Sentencia ASHIR no soportada: " +
            stmt.getClass().getName()
        );
    }

    private void generateIncDec(
        Parsing.IncDecStmt s
    ) {
        if (s == null)
            return;

        if (
            s.operator == null ||
            s.operator.isEmpty()
        ) {
            throw new IllegalArgumentException(
                "inc/dec sin operador"
            );
        }

        if (
            s.variable == null ||
            s.variable.isEmpty()
        ) {
            throw new IllegalArgumentException(
                s.operator +
                " requiere una variable"
            );
        }

        String variable =
            cleanSymbol(s.variable);

        if (
            variable == null ||
            variable.isEmpty()
        ) {
            throw new IllegalArgumentException(
                s.operator +
                " requiere una variable válida"
            );
        }

        ctx.clearConstant(variable);

        switch (s.operator) {

            case "inc":
            case "++":

                vars.Increment(variable);
                return;

            case "dec":
            case "--":

                vars.Decrement(variable);
                return;

            default:

                throw new IllegalArgumentException(
                    "Operador inc/dec desconocido: " +
                    s.operator
                );
        }
    }

    private void generateCall(
        Parsing.CallStmt s
    ) {
        if (s == null)
            return;

        String functionName =
            cleanSymbol(s.function);

        if (
            functionName == null ||
            functionName.isEmpty()
        ) {
            throw new IllegalArgumentException(
                "call sin nombre de función"
            );
        }

        String destination =
            cleanSymbol(s.destination);

        final boolean discardResult =
            destination == null ||
            destination.isEmpty();

        if (discardResult) {
            destination = "__ret";
        }

        List<String> callArgs =
            s.args == null
                ? Collections.<String>emptyList()
                : s.args;

        Object[] arguments =
            new Object[callArgs.size()];

        for (
            int i = 0;
            i < callArgs.size();
            i++
        ) {
            String rawArg =
                callArgs.get(i);

            if (rawArg == null) {
                throw new IllegalArgumentException(
                    "Argumento null en call a '" +
                    functionName +
                    "'"
                );
            }

            rawArg =
                rawArg.trim();

            if (isStringLiteral(rawArg)) {

                arguments[i] =
                    globals.internStringLiteral(
                        rawArg
                    );

                continue;
            }

            String arg =
                cleanSymbol(rawArg);

            if (
                arg == null ||
                arg.isEmpty()
            ) {
                throw new IllegalArgumentException(
                    "Argumento vacío en call a '" +
                    functionName +
                    "'"
                );
            }

            if (isNumber(arg)) {

                arguments[i] =
                    parseCallNumber(arg);

            } else {

                arguments[i] =
                    arg;
            }
        }

        if (externs.hasExtern(functionName)) {

            externs.emitExternalCall(
                externs.externFunction(functionName),
                discardResult ? "" : destination,
                arguments
            );

            return;
        }

        int returnType =
            vars.functionReturnType(
                functionName
            );

        boolean keepExisting = false;

        if (vars.exists(destination)) {

            int existingType = vars.TypeOf(destination);

            boolean bothInteger =
                existingType != Vars.FLOAT32 && existingType != Vars.FLOAT64 &&
                returnType != Vars.FLOAT32 && returnType != Vars.FLOAT64;

            if (bothInteger &&
                existingType != returnType) {

                keepExisting = true;
            }
        }

        int declared = declaredIntegerType(destination);

        if (!keepExisting && declared > 0 &&
            returnType != Vars.FLOAT32 && returnType != Vars.FLOAT64 &&
            !vars.exists(destination)) {
            ctx.ensureTemporary(destination, declared);
            keepExisting = true;
        }

        if (!keepExisting) {

            ctx.ensureTemporary(
                destination,
                returnType
            );
        }

        ctx.clearConstant(
            destination
        );

        Call(
            functionName,
            destination,
            arguments
        );
    }

    private int declaredIntegerType(String name) {
        String t = ctx.typeOf(name);
        if (t == null) return -1;
        switch (t) {
            case "byte": case "int8": case "uint8":    return Vars.BYTE;
            case "word": case "int16": case "uint16":  return Vars.WORD;
            case "dword": case "int32": case "uint32": return Vars.DWORD;
            default: return -1;
        }
    }

    public void Call(
        String functionName,
        String destination,
        Object... arguments
    ) {
        if (
            functionName == null ||
            functionName.isEmpty()
        ) {
            throw new IllegalArgumentException(
                "Call: función vacía"
            );
        }

        if (
            destination == null ||
            destination.isEmpty()
        ) {
            throw new IllegalArgumentException(
                "Call: destino vacío"
            );
        }

        vars.Call(
            cleanSymbol(functionName),
            cleanSymbol(destination),
            arguments
        );
    }

    private void generatePtrDecl(
        Parsing.PtrDeclStmt s
    ) {
        if (s == null)
            return;

        if (
            s.name == null ||
            s.name.isEmpty()
        ) {
            throw new IllegalArgumentException(
                "Declaración ptr sin nombre"
            );
        }

        String name =
            cleanSymbol(s.name);

        ctx.declareLocal(
            name,
            Vars.QWORD,
            0L
        );

        if (
            s.register != null &&
            !s.register.isEmpty()
        ) {
            vars.Set(
                name,
                cleanSymbol(s.register)
            );
        }
    }

    private void generateStore(
        Parsing.StoreStmt s
    ) {
        if (s == null)
            return;

        if (
            s.pointer == null ||
            s.pointer.isEmpty()
        ) {
            throw new IllegalArgumentException(
                "store: puntero vacío"
            );
        }

        if (
            s.value == null ||
            s.value.isEmpty()
        ) {
            throw new IllegalArgumentException(
                "store: valor vacío"
            );
        }

        String pointer =
            cleanSymbol(s.pointer);

        String value =
            cleanSymbol(s.value);

        if (isNumber(value)) {

            if (isFloatingNumber(value)) {

                throw new IllegalArgumentException(
                    "store de floating-point no soportado " +
                    "todavía: " +
                    value
                );
            }

            vars.StorePtr(
                pointer,
                parseNumber(value)
            );

            return;
        }

        vars.StorePtr(
            pointer,
            value
        );
    }

    private void declareReturnPointer(
        String pointer
    ) {
        if (
            !ctx.localVariables.contains(pointer) &&
            !vars.exists(pointer)
        ) {
            ctx.declareLocal(
                pointer,
                Vars.QWORD,
                0L
            );

        } else {

            ctx.localVariables.add(
                pointer
            );
        }
    }

    private void generateReturn(
        Parsing.ReturnStmt s
    ) {
        if (s == null) {
            throw new IllegalArgumentException(
                "return sin sentencia"
            );
        }

        if (
            s.value == null ||
            s.value.toString().trim().isEmpty()
        ) {
            vars.Return();
            return;
        }

        String rawValue =
            s.value.toString().trim();

        String value =
            cleanSymbol(rawValue);

        if (
            value == null ||
            value.isEmpty()
        ) {
            throw new IllegalArgumentException(
                "return sin valor"
            );
        }

        if (
            ctx.knownConstants != null &&
            ctx.knownConstants.contains(value)
        ) {

            Object constant =
                ctx.constantValues == null
                    ? null
                    : ctx.constantValues.get(value);

            if (constant == null) {
                throw new IllegalStateException(
                    "Constante sin valor: " +
                    value
                );
            }

            if (constant instanceof Double) {

                vars.Return(
                    ((Double) constant).doubleValue()
                );

                return;
            }

            if (constant instanceof Float) {

                vars.Return(
                    ((Float) constant).floatValue()
                );

                return;
            }

            if (constant instanceof Number) {

                vars.Return(
                    ((Number) constant).longValue()
                );

                return;
            }

            throw new IllegalArgumentException(
                "Tipo de constante no soportado: " +
                constant.getClass().getName()
            );
        }

        if (isDoubleBitsLiteral(value)) {

            double d =
                parseDoubleBits(value);

            vars.Return(d);

            return;
        }

        if (isNumber(value)) {

            if (isFloatingNumber(value)) {

                double d =
                    parseFloatingNumber(value);

                vars.Return(d);

                return;
            }

            vars.Return(
                parseNumber(value)
            );

            return;
        }

        Object knownValue =
            ctx.constantValues == null
                ? null
                : ctx.constantValues.get(value);

        if (knownValue instanceof Double) {

            vars.Return(
                ((Double) knownValue).doubleValue()
            );

            return;
        }

        if (knownValue instanceof Float) {

            vars.Return(
                ((Float) knownValue).floatValue()
            );

            return;
        }

        if (knownValue instanceof Number) {

            vars.Return(
                ((Number) knownValue).longValue()
            );

            return;
        }

        vars.Return(value);
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
