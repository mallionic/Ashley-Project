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
import static arch.builtin.backd.BackendUtil.decodeRawString;
import static arch.builtin.backd.BackendUtil.isStringLiteral;
import static arch.builtin.backd.BackendUtil.parseNumber;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import arch.builtin.Parsing;

public final class BackendGlobals {

    private final BackendContext ctx;
    private final BackendSymbols symbols;

    public BackendGlobals(BackendContext ctx, BackendSymbols symbols) {
        this.ctx = ctx;
        this.symbols = symbols;
    }

    public void globalString(String symbol, String value) {

        String sym = cleanSymbol(symbol);

        if (sym == null || sym.isEmpty()) {
            throw new IllegalArgumentException("Símbolo de string vacío");
        }

        if (value == null)
            value = "";

        if (
            ctx.generatedFunctions.containsKey(sym) ||
            ctx.generatedLabels.containsKey(sym) ||
            ctx.globalStrings.containsKey(sym) ||
            ctx.pendingGlobalData.containsKey(sym)
        ) {
            throw new IllegalStateException(
                "Símbolo global duplicado: " + sym
            );
        }

        byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);

        byte[] data = new byte[utf8.length + 1];

        System.arraycopy(utf8, 0, data, 0, utf8.length);

        data[data.length - 1] = 0;

        ctx.pendingGlobalData.put(sym, data);

        ctx.globalStringData.put(sym, data.clone());

        ctx.globalStringLengths.put(sym, utf8.length);

        ctx.globalStrings.put(sym, -1);
    }

    public boolean hasGlobalString(String name) {
        return ctx.hasGlobalString(name);
    }

    public Integer globalStringOffset(String name) {

        if (name == null)
            return null;

        return ctx.globalStrings.get(cleanSymbol(name));
    }

    public Integer globalStringLength(String name) {

        if (name == null)
            return null;

        return ctx.globalStringLengths.get(cleanSymbol(name));
    }

    public Integer globalCStringSize(String name) {

        if (name == null)
            return null;

        byte[] data = ctx.globalStringData.get(cleanSymbol(name));

        return data == null ? null : data.length;
    }

    public byte[] globalStringBytes(String name) {

        if (name == null)
            return null;

        byte[] data = ctx.globalStringData.get(cleanSymbol(name));

        return data == null ? null : data.clone();
    }

    public Map<String, Integer> globalStrings() {
        return Collections.unmodifiableMap(ctx.globalStrings);
    }

    public String internStringLiteral(String literal) {

        if (!isStringLiteral(literal)) {
            throw new IllegalArgumentException(
                "Literal de cadena inválido: " + literal
            );
        }

        String decoded = decodeRawString(literal);

        int index = ctx.globalStringData.size();

        String symbol;

        do {

            symbol = "__str" + index++;

        } while (
            ctx.globalStringData.containsKey(symbol) ||
            ctx.pendingGlobalData.containsKey(symbol) ||
            ctx.generatedLabels.containsKey(symbol) ||
            ctx.generatedFunctions.containsKey(symbol) ||
            ctx.externFunctions.containsKey(symbol)
        );

        globalString(symbol, decoded);

        return symbol;
    }

    public void globalBytes(String name, byte[] data) {

        String symbol = cleanSymbol(name);

        if (symbol == null || symbol.isEmpty()) {
            throw new IllegalArgumentException("Array global sin nombre");
        }

        if (data == null) {
            throw new IllegalArgumentException(
                "Array global null: " + symbol
            );
        }

        checkGlobalDataName(symbol);

        ctx.pendingGlobalData.put(symbol, data.clone());

        ctx.generatedLabels.put(symbol, -1);
    }

    public void globalRaw8(String name, long value) {
        defineGlobalRaw(name, value, 1);
    }

    public void globalRaw16(String name, long value) {
        defineGlobalRaw(name, value, 2);
    }

    public void globalRaw32(String name, long value) {
        defineGlobalRaw(name, value, 4);
    }

    public void globalRaw64(String name, long value) {
        defineGlobalRaw(name, value, 8);
    }

    private void defineGlobalRaw(String name, long value, int size) {

        String symbol = cleanSymbol(name);

        checkGlobalDataName(symbol);

        byte[] data = new byte[size];

        for (int i = 0; i < size; i++)
            data[i] = (byte) ((value >>> (i * 8)) & 0xFFL);

        ctx.pendingGlobalData.put(symbol, data);

        ctx.generatedLabels.put(symbol, -1);
    }

    public void globalNull(String name) {
        globalRaw64(name, BackendContext.NULL);
    }

    public void globalInf(String name) {
        globalRaw64(name, BackendContext.INF);
    }

    public void globalNInf(String name) {
        globalRaw64(name, BackendContext.NINF);
    }

    public void globalNan(String name) {
        globalRaw64(name, BackendContext.NAN);
    }

    private void checkGlobalDataName(String symbol) {

        if (symbol == null || symbol.isEmpty()) {
            throw new IllegalArgumentException("Símbolo global vacío");
        }

        if (ctx.generatedFunctions.containsKey(symbol)) {
            throw new IllegalArgumentException(
                "El símbolo '" + symbol + "' ya es una función"
            );
        }

        if (
            ctx.generatedLabels.containsKey(symbol) &&
            !ctx.pendingGlobalData.containsKey(symbol)
        ) {
            throw new IllegalArgumentException(
                "El símbolo global ya existe: " + symbol
            );
        }

        if (ctx.externFunctions.containsKey(symbol)) {
            throw new IllegalArgumentException(
                "El símbolo '" + symbol + "' ya es extern"
            );
        }

        if (ctx.globalStrings.containsKey(symbol)) {
            throw new IllegalArgumentException(
                "El símbolo '" + symbol + "' ya es string"
            );
        }

        if (ctx.pendingGlobalData.containsKey(symbol)) {
            throw new IllegalArgumentException(
                "El símbolo global ya existe: " + symbol
            );
        }
    }

    public void flushGlobalData() {

        if (ctx.pendingGlobalData.isEmpty())
            return;

        for (Map.Entry<String, byte[]> entry :
            ctx.pendingGlobalData.entrySet()) {

            String symbol = entry.getKey();
            byte[] data = entry.getValue();

            if (ctx.globalDataOffsets.containsKey(symbol))
                continue;

            int align = data.length >= 16 ? 16 : 8;
            while ((ctx.builder.size() & (align - 1)) != 0)
                ctx.builder.emit_bytes((byte) 0);

            int bias = ctx.dataBias.getOrDefault(symbol, 0);

            int offset = ctx.builder.size() + bias;

            ctx.globalDataOffsets.put(symbol, offset);

            ctx.globalDataSize += data.length;

            ctx.globalStrings.put(symbol, offset);

            ctx.generatedLabels.put(symbol, offset);

            for (byte value : data)
                ctx.builder.emit_bytes(value);

            symbols.registerGlobalDataSymbol(symbol, offset, data.length - bias);
        }

        ctx.pendingGlobalData.clear();
    }

    public void resolveSymbolReferences() {

        for (Map.Entry<String, List<Integer>> entry :
            ctx.builder.getReferences().entrySet()) {

            String symbol = cleanSymbol(entry.getKey());

            Integer target = ctx.globalDataOffsets.get(symbol);

            if (target == null)
                target = ctx.generatedLabels.get(symbol);

            if (target == null)
                target = ctx.generatedFunctions.get(symbol);

            if (target == null || target < 0)
                continue;

            for (int patchOffset : entry.getValue()) {

                int rel = target - (patchOffset + 4);

                ctx.builder.patchIntLE(patchOffset, rel);

                ctx.builder.noteSymbolFixup(patchOffset, symbol);
            }
        }
    }

    public void prepareGlobalData(Parsing.Program program) {

        if (program == null)
            return;

        if (program.raws == null)
            return;

        for (Parsing.RawStmt raw : program.raws) {

            if (raw == null)
                continue;

            if (raw.directive == null)
                continue;

            String directive = raw.directive.trim().toLowerCase();

            if (directive.equals("string") || directive.equals("cstring"))
                defineRawString(raw);

            if (directive.equals("gvar") || directive.equals("gdata"))
                defineGlobalVariable(raw);

            if (directive.equals("section"))
                arch.Sections.place(cleanSymbol(raw.stringName), raw.stringValue, raw.value);
        }
    }

    public void defineGlobalVariable(Parsing.RawStmt raw) {

        String name = cleanSymbol(raw.stringName);

        if (name == null || name.isEmpty())
            throw new IllegalArgumentException(".gvar sin nombre");

        if (raw.gdata == null && (raw.gvarSize < 1 || raw.gvarSize > 4096))
            throw new IllegalArgumentException(
                ".gvar '" + name + "': tamano invalido " + raw.gvarSize
            );

        if (ctx.pendingGlobalData.containsKey(name))
            return;

        long value = parseNumber(raw.value);

        byte[] data = raw.gdata != null ? raw.gdata.clone() : new byte[raw.gvarSize];

        for (int i = 0; raw.gdata == null && i < data.length && i < 8; i++)
            data[i] = (byte) ((value >>> (i * 8)) & 0xFFL);

        checkGlobalDataName(name);

        ctx.pendingGlobalData.put(name, data);

        if (raw.gdataBias > 0)
            ctx.dataBias.put(name, raw.gdataBias);
        ctx.generatedLabels.put(name, -1);

        arch.formatter.JavaElfOBJ.WRITABLE_TEXT = true;
        arch.formatter.JavaWinOBJ.WRITABLE_TEXT = true;
    }

    public void defineRawString(Parsing.RawStmt raw) {

        if (raw == null)
            return;

        String name = cleanSymbol(raw.stringName);

        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException(".string sin nombre");
        }

        String value = raw.stringValue;

        if (value == null) {
            throw new IllegalArgumentException(
                ".string '" + name + "' sin texto"
            );
        }

        if (hasGlobalString(name))
            return;

        globalString(name, value);
    }

    public void generateRaw(Parsing.RawStmt s) {

        if (s == null)
            return;

        if (s.directive == null || s.directive.isEmpty()) {
            throw new IllegalArgumentException("Directiva RAW sin nombre");
        }

        String directive = s.directive.trim().toLowerCase();

        if (directive.equals("string") || directive.equals("cstring")) {
            defineRawString(s);
            return;
        }

        if (directive.equals("gvar") || directive.equals("gdata")) {
            defineGlobalVariable(s);
            return;
        }

        if (directive.equals("section")) {
            arch.Sections.place(cleanSymbol(s.stringName), s.stringValue, s.value);
            return;
        }

        if (s.value == null || s.value.isEmpty()) {
            throw new IllegalArgumentException(
                "Directiva RAW sin valor: ." + s.directive
            );
        }

        int size;

        switch (directive) {

            case "raw8":  size = 1; break;
            case "raw16": size = 2; break;
            case "raw32": size = 4; break;
            case "raw64": size = 8; break;

            default:
                throw new IllegalArgumentException(
                    "Directiva RAW desconocida: ." + s.directive
                );
        }

        emitRawLittleEndian(parseNumber(s.value), size);
    }

    private void emitRawLittleEndian(long value, int size) {

        for (int i = 0; i < size; i++) {

            ctx.builder.emit_bytes(
                (byte) ((value >>> (i * 8)) & 0xFFL)
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
