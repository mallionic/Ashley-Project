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

import Builder.ash.Generator;
import arch.Diagnostics;
import java.util.Map;

import Builder.Builder;
import arch.Jumpz;
import arch.SymbolTarget;
import arch.builtin.backd.BackendContext;
import arch.builtin.backd.BackendExterns;
import arch.builtin.backd.BackendFunctions;
import arch.builtin.backd.BackendGlobals;
import arch.builtin.backd.BackendRegAssign;
import arch.builtin.backd.BackendStatements;
import arch.builtin.backd.BackendSymbols;
import arch.builtin.backd.ExternFunction;

public class Backend {

    public static final long NULL = BackendContext.NULL;
    public static final long INF  = BackendContext.INF;
    public static final long NINF = BackendContext.NINF;
    public static final long NAN  = BackendContext.NAN;

    private final BackendContext ctx;
    private final BackendExterns externs;
    private final BackendSymbols symbols;
    private final BackendGlobals globals;
    private final BackendRegAssign regAssign;
    private final BackendStatements statements;
    private final BackendFunctions functions;

    public Backend(Builder builder) {
        this(builder, null);
    }

    public Backend(Builder builder, SymbolTarget symbolTarget) {

        this.ctx = new BackendContext(builder, symbolTarget);

        this.externs = new BackendExterns(ctx);
        this.symbols = new BackendSymbols(ctx, externs);
        this.globals = new BackendGlobals(ctx, symbols);
        this.regAssign = new BackendRegAssign(ctx, externs, globals);
        this.statements =
            new BackendStatements(ctx, externs, globals, regAssign);
        this.functions =
            new BackendFunctions(ctx, externs, symbols, globals, statements);
    }

    public void generate(Parsing.Program program) {

        if (
            !Vars.measuring &&
            Vars.calleeMask == null
        ) {
            boolean silent = Diagnostics.SILENT;
            Vars.measuring = true;
            Vars.measured = new java.util.HashMap<>();
            Vars.frameMeasured = new java.util.HashMap<>();

            try {
                Diagnostics.SILENT = true;
                new Backend(new Builder()).generate(program);
                Vars.calleeMask = Vars.measured;
                Vars.frameNeeded = Vars.frameMeasured;
            } catch (Throwable t) {
                Vars.calleeMask = null;
                Vars.frameNeeded = null;
            } finally {
                Vars.measuring = false;
                Vars.measured = null;
                Vars.frameMeasured = null;
                Diagnostics.SILENT = silent;
            }
        }

        functions.generate(program);
    }

    public void extern(String name, String returnType, String... parameters) {
        externs.extern(name, returnType, parameters);
    }

    public void externVariadic(
        String name,
        String returnType,
        String... fixedParameters
    ) {
        externs.externVariadic(name, returnType, fixedParameters);
    }

    public void externExit() {
        externs.externExit();
    }

    public boolean hasExtern(String name) {
        return externs.hasExtern(name);
    }

    public ExternFunction externFunction(String name) {
        return externs.externFunction(name);
    }

    public Map<String, ExternFunction> externFunctions() {
        return externs.externFunctions();
    }

    public void globalString(String symbol, String value) {
        globals.globalString(symbol, value);
    }

    public void GlobalString(String name, String value) {
        globals.globalString(name, value);
    }

    public void cstring(String name, String value) {
        globals.globalString(name, value);
    }

    public void CString(String name, String value) {
        globals.globalString(name, value);
    }

    public boolean hasGlobalString(String name) {
        return globals.hasGlobalString(name);
    }

    public Integer globalStringOffset(String name) {
        return globals.globalStringOffset(name);
    }

    public Integer globalStringLength(String name) {
        return globals.globalStringLength(name);
    }

    public Integer globalCStringSize(String name) {
        return globals.globalCStringSize(name);
    }

    public byte[] globalStringBytes(String name) {
        return globals.globalStringBytes(name);
    }

    public Map<String, Integer> globalStrings() {
        return globals.globalStrings();
    }

    public void globalBytes(String name, byte[] data) {
        globals.globalBytes(name, data);
    }

    public void GlobalBytes(String name, byte[] data) {
        globals.globalBytes(name, data);
    }

    public void globalRaw8(String name, long value) {
        globals.globalRaw8(name, value);
    }

    public void globalRaw16(String name, long value) {
        globals.globalRaw16(name, value);
    }

    public void globalRaw32(String name, long value) {
        globals.globalRaw32(name, value);
    }

    public void globalRaw64(String name, long value) {
        globals.globalRaw64(name, value);
    }

    public void GlobalRaw8(String name, long value) {
        globals.globalRaw8(name, value);
    }

    public void GlobalRaw16(String name, long value) {
        globals.globalRaw16(name, value);
    }

    public void GlobalRaw32(String name, long value) {
        globals.globalRaw32(name, value);
    }

    public void GlobalRaw64(String name, long value) {
        globals.globalRaw64(name, value);
    }

    public void globalNull(String name) {
        globals.globalNull(name);
    }

    public void GlobalNull(String name) {
        globals.globalNull(name);
    }

    public void globalInf(String name) {
        globals.globalInf(name);
    }

    public void GlobalInf(String name) {
        globals.globalInf(name);
    }

    public void globalNInf(String name) {
        globals.globalNInf(name);
    }

    public void GlobalNInf(String name) {
        globals.globalNInf(name);
    }

    public void globalNan(String name) {
        globals.globalNan(name);
    }

    public void GlobalNan(String name) {
        globals.globalNan(name);
    }

    public void attachSymbolTarget(SymbolTarget target) {
        symbols.attachSymbolTarget(target);
    }

    public SymbolTarget symbolTarget() {
        return symbols.symbolTarget();
    }

    public boolean hasSymbolTarget() {
        return symbols.hasSymbolTarget();
    }

    public void globalLabel(String name) {
        symbols.globalLabel(name);
    }

    public void GlobalLabel(String name) {
        symbols.globalLabel(name);
    }

    public void globalLabel(String name, int offset) {
        symbols.globalLabel(name, offset);
    }

    public boolean hasGlobalFunction(String name) {
        return symbols.hasGlobalFunction(name);
    }

    public boolean hasGlobalLabel(String name) {
        return symbols.hasGlobalLabel(name);
    }

    public boolean hasGlobalData(String name) {
        return symbols.hasGlobalData(name);
    }

    public Integer functionOffset(String name) {
        return symbols.functionOffset(name);
    }

    public Integer labelOffset(String name) {
        return symbols.labelOffset(name);
    }

    public Map<String, Integer> globalFunctions() {
        return symbols.globalFunctions();
    }

    public Map<String, Integer> globalLabels() {
        return symbols.globalLabels();
    }

    public void Call(
        String functionName,
        String destination,
        Object... arguments
    ) {
        statements.Call(functionName, destination, arguments);
    }

    public Vars vars() {
        return ctx.vars;
    }

    public Jumpz jumpz() {
        return ctx.jumpz;
    }

    public Builder builder() {
        return ctx.builder;
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
