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

import java.util.Collections;
import java.util.Map;

import arch.SymbolTarget;

public final class BackendSymbols {

    private final BackendContext ctx;
    private final BackendExterns externs;

    public BackendSymbols(
        BackendContext ctx,
        BackendExterns externs
    ) {
        if (ctx == null)
            throw new IllegalArgumentException(
                "BackendContext no puede ser null"
            );

        if (externs == null)
            throw new IllegalArgumentException(
                "BackendExterns no puede ser null"
            );

        this.ctx = ctx;
        this.externs = externs;
    }

    public void attachSymbolTarget(SymbolTarget target) {

        ctx.symbolTarget = target;

        ctx.registeredSymbols.clear();

        if (target == null)
            return;

        for (Map.Entry<String, Integer> entry :
            ctx.generatedFunctions.entrySet()) {

            String name = cleanSymbol(entry.getKey());
            Integer offset = entry.getValue();

            if (name == null || name.isEmpty())
                continue;

            if (offset == null || offset < 0)
                continue;

            registerSymbol(name, offset);
        }

        for (Map.Entry<String, Integer> entry :
            ctx.generatedLabels.entrySet()) {

            String name = cleanSymbol(entry.getKey());
            Integer offset = entry.getValue();

            if (name == null || name.isEmpty())
                continue;

            if (offset == null || offset < 0)
                continue;

            if (ctx.globalStrings.containsKey(name))
                continue;

            registerSymbol(name, offset);
        }

        for (Map.Entry<String, Integer> entry :
            ctx.globalStrings.entrySet()) {

            String name = cleanSymbol(entry.getKey());
            Integer offset = entry.getValue();

            if (name == null || name.isEmpty())
                continue;

            if (offset == null || offset < 0)
                continue;

            byte[] data = ctx.globalStringData.get(name);

            registerGlobalDataSymbol(
                name,
                offset,
                data == null ? 0 : data.length
            );
        }

        for (ExternFunction fn : ctx.externFunctions.values()) {

            if (fn == null)
                continue;

            externs.registerExternSymbol(fn);
        }
    }

    public SymbolTarget symbolTarget() {
        return ctx.symbolTarget;
    }

    public boolean hasSymbolTarget() {
        return ctx.symbolTarget != null;
    }

    public void registerGlobalSymbol(
        String name,
        int offset
    ) {

        String symbol = cleanSymbol(name);

        if (symbol == null || symbol.isEmpty()) {
            throw new IllegalArgumentException(
                "Símbolo global vacío"
            );
        }

        if (offset < 0)
            return;

        if (ctx.symbolTarget == null)
            return;

        registerSymbol(symbol, offset);
    }

    public void registerSymbol(
        String name,
        int offset
    ) {

        if (ctx.symbolTarget == null)
            return;

        String symbol = cleanSymbol(name);

        if (symbol == null || symbol.isEmpty()) {
            throw new IllegalArgumentException(
                "Símbolo global vacío"
            );
        }

        if (offset < 0)
            return;

        if (ctx.registeredSymbols.contains(symbol))
            return;

        ctx.symbolTarget.defineFunctionSymbol(
            symbol,
            offset,
            arch.Visibility.isExported(symbol)
        );

        ctx.registeredSymbols.add(symbol);
    }

    public void registerGlobalDataSymbol(
        String name,
        int offset,
        int size
    ) {

        String symbol = cleanSymbol(name);

        if (symbol == null || symbol.isEmpty()) {
            throw new IllegalArgumentException(
                "Símbolo global vacío"
            );
        }

        if (offset < 0)
            return;

        if (size < 0) {
            throw new IllegalArgumentException(
                "Tamaño de dato global inválido: " + size
            );
        }

        registerGlobalSymbol(symbol, offset);
    }

    public void registerGlobalFunction(
        String name,
        int offset
    ) {

        String symbol = cleanSymbol(name);

        if (symbol == null || symbol.isEmpty()) {
            throw new IllegalArgumentException(
                "No se puede registrar una función sin nombre"
            );
        }

        if (offset < 0) {
            throw new IllegalArgumentException(
                "Offset de función inválido: " + offset
            );
        }

        if (ctx.generatedLabels.containsKey(symbol)) {
            throw new IllegalArgumentException(
                "La función '" + symbol +
                "' entra en conflicto con un label"
            );
        }

        if (ctx.globalStrings.containsKey(symbol)) {
            throw new IllegalArgumentException(
                "La función '" + symbol +
                "' entra en conflicto con un dato global"
            );
        }

        if (ctx.externFunctions.containsKey(symbol)) {
            throw new IllegalArgumentException(
                "La función '" + symbol +
                "' entra en conflicto con un extern"
            );
        }

        Integer previous =
            ctx.generatedFunctions.get(symbol);

        if (previous != null &&
            previous.intValue() != offset) {

            throw new IllegalArgumentException(
                "La función '" + symbol +
                "' ya existe en offset " + previous
            );
        }

        ctx.generatedFunctions.put(
            symbol,
            offset
        );

        registerGlobalSymbol(
            symbol,
            offset
        );
    }

    public void globalLabel(String name) {

        String label = cleanSymbol(name);

        if (label == null || label.isEmpty()) {
            throw new IllegalArgumentException(
                "Label global vacío"
            );
        }

        if (ctx.generatedFunctions.containsKey(label)) {
            throw new IllegalArgumentException(
                "El label global '" + label +
                "' entra en conflicto con una función"
            );
        }

        if (ctx.globalStrings.containsKey(label)) {
            throw new IllegalArgumentException(
                "El label global '" + label +
                "' entra en conflicto con un dato global"
            );
        }

        if (ctx.externFunctions.containsKey(label)) {
            throw new IllegalArgumentException(
                "El label global '" + label +
                "' entra en conflicto con un extern"
            );
        }

        if (ctx.generatedLabels.containsKey(label)) {
            throw new IllegalArgumentException(
                "Label global duplicado: " + label
            );
        }

        int offset = ctx.builder.size();

        ctx.generatedLabels.put(
            label,
            offset
        );

        registerGlobalSymbol(
            label,
            offset
        );
    }

    public void globalLabel(
        String name,
        int offset
    ) {

        String label = cleanSymbol(name);

        if (label == null || label.isEmpty()) {
            throw new IllegalArgumentException(
                "Label global vacío"
            );
        }

        if (offset < 0) {
            throw new IllegalArgumentException(
                "Offset de label inválido: " + offset
            );
        }

        if (ctx.generatedFunctions.containsKey(label)) {
            throw new IllegalArgumentException(
                "El label global '" + label +
                "' entra en conflicto con una función"
            );
        }

        if (ctx.globalStrings.containsKey(label)) {
            throw new IllegalArgumentException(
                "El label global '" + label +
                "' entra en conflicto con un dato global"
            );
        }

        if (ctx.externFunctions.containsKey(label)) {
            throw new IllegalArgumentException(
                "El label global '" + label +
                "' entra en conflicto con un extern"
            );
        }

        Integer previous =
            ctx.generatedLabels.get(label);

        if (previous != null &&
            previous.intValue() != offset) {

            throw new IllegalArgumentException(
                "Label global '" + label +
                "' ya existe en offset " + previous
            );
        }

        ctx.generatedLabels.put(
            label,
            offset
        );

        registerGlobalSymbol(
            label,
            offset
        );
    }

    public boolean hasGlobalFunction(String name) {

        String symbol = cleanSymbol(name);

        if (symbol == null || symbol.isEmpty())
            return false;

        return ctx.generatedFunctions.containsKey(symbol);
    }

    public boolean hasGlobalLabel(String name) {

        String symbol = cleanSymbol(name);

        if (symbol == null || symbol.isEmpty())
            return false;

        return ctx.generatedLabels.containsKey(symbol);
    }

    public boolean hasGlobalData(String name) {

        String symbol = cleanSymbol(name);

        if (symbol == null || symbol.isEmpty())
            return false;

        if (ctx.globalStrings.containsKey(symbol))
            return true;

        return ctx.generatedLabels.containsKey(symbol);
    }

    public boolean hasGlobalSymbol(String name) {

        String symbol = cleanSymbol(name);

        if (symbol == null || symbol.isEmpty())
            return false;

        return
            ctx.generatedFunctions.containsKey(symbol) ||
            ctx.generatedLabels.containsKey(symbol) ||
            ctx.globalStrings.containsKey(symbol) ||
            ctx.externFunctions.containsKey(symbol);
    }

    public Integer functionOffset(String name) {

        String symbol = cleanSymbol(name);

        if (symbol == null || symbol.isEmpty())
            return null;

        return ctx.generatedFunctions.get(symbol);
    }

    public Integer labelOffset(String name) {

        String symbol = cleanSymbol(name);

        if (symbol == null || symbol.isEmpty())
            return null;

        return ctx.generatedLabels.get(symbol);
    }

    public Integer dataOffset(String name) {

        String symbol = cleanSymbol(name);

        if (symbol == null || symbol.isEmpty())
            return null;

        return ctx.globalStrings.get(symbol);
    }

    public Map<String, Integer> globalFunctions() {
        return Collections.unmodifiableMap(
            ctx.generatedFunctions
        );
    }

    public Map<String, Integer> globalLabels() {
        return Collections.unmodifiableMap(
            ctx.generatedLabels
        );
    }

    public Map<String, Integer> globalData() {
        return Collections.unmodifiableMap(
            ctx.globalStrings
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
