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
import static arch.builtin.backd.BackendUtil.macroStatements;
import static arch.builtin.backd.BackendUtil.typeOf;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import arch.builtin.Parsing;
import arch.builtin.Vars;

public final class BackendFunctions {

    private final BackendContext ctx;
    private final Vars vars;
    private final BackendExterns externs;
    private final BackendSymbols symbols;
    private final BackendGlobals globals;
    private final BackendStatements statements;

    public BackendFunctions(
        BackendContext ctx,
        BackendExterns externs,
        BackendSymbols symbols,
        BackendGlobals globals,
        BackendStatements statements
    ) {
        this.ctx = ctx;
        this.vars = ctx.vars;
        this.externs = externs;
        this.symbols = symbols;
        this.globals = globals;
        this.statements = statements;
    }

    public void generate(Parsing.Program program) {

        if (program == null) {
            throw new IllegalArgumentException(
                "Program no puede ser null"
            );
        }

        globals.prepareGlobalData(program);

        if (program.externs != null) {

            Set<String> internal = new HashSet<>();

            if (program.functions != null)
                for (Parsing.FunctionDecl f : program.functions)
                    if (f != null && f.name != null)
                        internal.add(f.name);

            for (Parsing.ExternDecl d : program.externs)
                externs.declare(d, internal);
        }

        if (program.functions == null ||
            program.functions.isEmpty()) {

            globals.flushGlobalData();
            globals.resolveSymbolReferences();
            return;
        }

        Parsing.FunctionDecl entry = null;

        for (Parsing.FunctionDecl fn : program.functions) {

            if (fn == null)
                continue;

            if (fn.entry) {

                if (entry != null) {
                    throw new IllegalArgumentException(
                        "ASHIR: existen múltiples funciones [entry]"
                    );
                }

                entry = fn;
            }
        }

        Set<String> generated = new HashSet<>();

        if (entry != null) {

            generateFunctionDependencies(
                entry,
                program,
                generated,
                new HashSet<>()
            );
        }

        for (Parsing.FunctionDecl fn : program.functions) {

            if (fn == null)
                continue;

            generateFunctionDependencies(
                fn,
                program,
                generated,
                new HashSet<>()
            );
        }

        globals.flushGlobalData();

        globals.resolveSymbolReferences();

        publishLayout();
    }

    private void publishLayout() {

        if (!(ctx.symbolTarget instanceof arch.formatter.JavaElfOBJ))
            return;

        java.util.Map<String, Integer> units = new java.util.HashMap<>();
        units.putAll(ctx.globalStrings);
        units.putAll(ctx.globalDataOffsets);
        units.putAll(ctx.generatedFunctions);
        units.values().removeIf(v -> v == null || v < 0);

        java.util.Map<String, Integer> starts = new java.util.HashMap<>(units);
        for (java.util.Map.Entry<String, Integer> e : ctx.dataBias.entrySet())
            if (starts.containsKey(e.getKey()))
                starts.put(e.getKey(), starts.get(e.getKey()) - e.getValue());

        ((arch.formatter.JavaElfOBJ) ctx.symbolTarget).setLayoutUnits(
            starts, units, ctx.generatedFunctions.keySet()
        );
    }

    private void generateFunctionDependencies(
        Parsing.FunctionDecl fn,
        Parsing.Program program,
        Set<String> generated,
        Set<String> visiting
    ) {

        if (fn == null || fn.name == null)
            return;

        String functionName =
            cleanSymbol(fn.name);

        if (
            functionName == null ||
            functionName.isEmpty()
        ) {
            throw new IllegalArgumentException(
                "Función sin nombre"
            );
        }

        if (generated.contains(functionName))
            return;

        if (!visiting.add(functionName))
            return;

        if (fn.body != null) {

            for (Parsing.Stmt stmt : fn.body) {

                generateDependenciesFromStmt(
                    stmt,
                    program,
                    generated,
                    visiting
                );
            }
        }

        visiting.remove(functionName);

        if (!generated.contains(functionName)) {

            generateFunction(fn);

            generated.add(functionName);
        }
    }

    private Parsing.FunctionDecl findFunction(
        Parsing.Program program,
        String functionName
    ) {

        if (program == null ||
            program.functions == null) {

            return null;
        }

        String wanted =
            cleanSymbol(functionName);

        if (
            wanted == null ||
            wanted.isEmpty()
        ) {
            return null;
        }

        for (
            Parsing.FunctionDecl candidate :
            program.functions
        ) {

            if (
                candidate == null ||
                candidate.name == null
            ) {
                continue;
            }

            String candidateName =
                cleanSymbol(candidate.name);

            if (wanted.equals(candidateName))
                return candidate;
        }

        return null;
    }

    private void dependOnCall(
        String functionName,
        Parsing.Program program,
        Set<String> generated,
        Set<String> visiting
    ) {

        String name =
            cleanSymbol(functionName);

        if (
            name == null ||
            name.isEmpty()
        ) {
            throw new IllegalArgumentException(
                "call sin nombre de función"
            );
        }

        if (externs.hasExtern(name))
            return;

        Parsing.FunctionDecl target =
            findFunction(
                program,
                name
            );

        if (target != null) {

            generateFunctionDependencies(
                target,
                program,
                generated,
                visiting
            );

            return;
        }

        externs.ensureImplicitExtern(name);
    }

    private void generateDependenciesFromStmt(
        Parsing.Stmt stmt,
        Parsing.Program program,
        Set<String> generated,
        Set<String> visiting
    ) {

        if (stmt == null)
            return;

        if (stmt instanceof Parsing.ExpandedStmt) {

            Parsing.ExpandedStmt expanded =
                (Parsing.ExpandedStmt) stmt;

            if (expanded.statements != null) {

                for (
                    Parsing.Stmt child :
                    expanded.statements
                ) {

                    generateDependenciesFromStmt(
                        child,
                        program,
                        generated,
                        visiting
                    );
                }
            }

            return;
        }

        if (stmt instanceof Parsing.MacroBlockStmt) {

            for (
                Parsing.Stmt child :
                macroStatements(
                    (Parsing.MacroBlockStmt) stmt
                )
            ) {

                generateDependenciesFromStmt(
                    child,
                    program,
                    generated,
                    visiting
                );
            }

            return;
        }

        if (stmt instanceof Parsing.RawStmt)
            return;

        if (stmt instanceof Parsing.CallStmt) {

            Parsing.CallStmt call =
                (Parsing.CallStmt) stmt;

            String functionName =
                cleanSymbol(call.function);

            if (
                functionName == null ||
                functionName.isEmpty()
            ) {
                throw new IllegalArgumentException(
                    "call sin nombre de función"
                );
            }

            dependOnCall(
                functionName,
                program,
                generated,
                visiting
            );

            return;
        }

        if (stmt instanceof Parsing.RegAssignStmt) {

            Parsing.RegAssignStmt s =
                (Parsing.RegAssignStmt) stmt;

            if (
                s.opcode == null ||
                !s.opcode.equalsIgnoreCase("call")
            ) {
                return;
            }

            if (
                s.args == null ||
                s.args.isEmpty()
            ) {
                throw new IllegalArgumentException(
                    "call sin nombre de función"
                );
            }

            String functionName =
                cleanSymbol(
                    s.args.get(0)
                );

            if (
                functionName == null ||
                functionName.isEmpty()
            ) {
                throw new IllegalArgumentException(
                    "call sin nombre de función"
                );
            }

            dependOnCall(
                functionName,
                program,
                generated,
                visiting
            );
        }
    }

    private void generateFunction(
        Parsing.FunctionDecl fn
    ) {

        if (fn == null)
            return;

        String functionName =
            cleanSymbol(fn.name);

        if (
            functionName == null ||
            functionName.isEmpty()
        ) {
            throw new IllegalArgumentException(
                "Función sin nombre"
            );
        }

        if (
            ctx.generatedFunctions.containsKey(
                functionName
            )
        ) {
            return;
        }

        if (fn.naked) {

            int at = ctx.builder.size();

            ctx.generatedFunctions.put(functionName, at);

            java.util.List<Vars.Parameter> np = new java.util.ArrayList<>();
            if (fn.params != null)
                for (Parsing.Param p : fn.params)
                    np.add(Vars.Parameter(cleanSymbol(p.name), typeOf(p.type)));

            vars.DeclareNaked(functionName, typeOf(fn.returnType), np.toArray(new Vars.Parameter[0]));

            for (Parsing.Stmt st : fn.body == null ? java.util.List.<Parsing.Stmt>of() : fn.body) {

                if (st instanceof Parsing.RawStmt) {
                    globals.generateRaw((Parsing.RawStmt) st);
                    continue;
                }

                if (
                    st instanceof Parsing.RegAssignStmt &&
                    "asm".equalsIgnoreCase(((Parsing.RegAssignStmt) st).opcode)
                ) {
                    String hex = ((Parsing.RegAssignStmt) st).args.get(0).trim().substring(1);
                    byte[] raw = new byte[hex.length() / 2];
                    for (int k = 0; k < raw.length; k++)
                        raw[k] = (byte) Integer.parseInt(hex.substring(2 * k, 2 * k + 2), 16);
                    String text = new String(raw, java.nio.charset.StandardCharsets.UTF_8);
                    if (text.contains("@var:"))
                        throw new IllegalArgumentException(
                            "naked function '" + functionName + "': asm cannot use variables");
                    for (byte x : arch.AsmBridge.assemble(text, 64, 0))
                        ctx.builder.file_write(x & 0xFF);
                    continue;
                }

                if (st instanceof Parsing.TypeStmt)
                    continue;

                throw new IllegalArgumentException(
                    "naked function '" + functionName + "' can only contain asm and emit");
            }

            symbols.registerGlobalFunction(functionName, at);
            return;
        }

        ctx.localVariables =
            new HashSet<>();

        ctx.constantValues =
            new HashMap<>();

        ctx.knownConstants =
            new HashSet<>();

        ctx.addressTaken.clear();

        ctx.currentReturnType =
            typeOf(fn.returnType);

        int parameterCount =
            fn.params == null
                ? 0
                : fn.params.size();

        Vars.Parameter[] params =
            new Vars.Parameter[
                parameterCount
            ];

        for (
            int i = 0;
            i < parameterCount;
            i++
        ) {

            Parsing.Param p =
                fn.params.get(i);

            if (p == null) {
                throw new IllegalArgumentException(
                    "Parámetro null en función: " +
                    fn.name
                );
            }

            int type =
                typeOf(p.type);

            String parameterName =
                cleanSymbol(p.name);

            if (
                parameterName == null ||
                parameterName.isEmpty()
            ) {
                throw new IllegalArgumentException(
                    "Parámetro sin nombre en función: " +
                    fn.name
                );
            }

            params[i] =
                Vars.Parameter(
                    parameterName,
                    type
                );

            if (p.type != null && ctx.typeOf(parameterName) == null)
                ctx.declareType(parameterName, p.type);

            ctx.localVariables.add(
                parameterName
            );
        }

        int functionOffset =
            ctx.builder.size();

        ctx.generatedFunctions.put(
            functionName,
            functionOffset
        );

        Vars.leafFunction =
            fn.body != null && isLeaf(fn.body);

        Vars.msAbiFunction = fn.efiapi;

        vars.Function(
            functionName,
            ctx.currentReturnType,
            params
        );

        if (fn.body != null) {

            for (
                Parsing.Stmt stmt :
                fn.body
            ) {

                statements.generateStmt(
                    stmt
                );
            }
        }

        vars.EndFunction();

        Vars.leafFunction = false;
        Vars.msAbiFunction = false;

        symbols.registerGlobalFunction(
            functionName,
            functionOffset
        );
    }

    private static boolean isLeaf(java.util.List<Parsing.Stmt> body) {

        for (Parsing.Stmt st : body) {

            if (st == null)
                continue;

            if (
                st instanceof Parsing.CallStmt ||
                st instanceof Parsing.RawStmt ||
                st instanceof Parsing.PtrDeclStmt
            )
                return false;

            if (st instanceof Parsing.RegAssignStmt) {
                String op = ((Parsing.RegAssignStmt) st).opcode;
                if (op == null)
                    return false;
                switch (op.toLowerCase()) {
                    case "call": case "asm": case "faddr":
                        return false;
                    default:
                        break;
                }
            }

            if (
                st instanceof Parsing.ExpandedStmt &&
                ((Parsing.ExpandedStmt) st).statements != null &&
                !isLeaf(((Parsing.ExpandedStmt) st).statements)
            )
                return false;

            if (st instanceof Parsing.MacroBlockStmt)
                return false;
        }

        return true;
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
