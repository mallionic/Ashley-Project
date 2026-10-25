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

package arch.formatter;

import Builder.Builder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

import arch.SymbolTarget;

public class JavaWinOBJ implements SymbolTarget {

    public static final int FILE_HEADER_SIZE = 20;
    public static final int SECTION_HEADER_SIZE = 40;
    public static final int SYMBOL_SIZE = 18;
    public static final int RELOCATION_SIZE = 10;

    public static final int IMAGE_FILE_MACHINE_I386  = 0x014C;
    public static final int IMAGE_FILE_MACHINE_AMD64 = 0x8664;

    public static final int IMAGE_SCN_CNT_CODE               = 0x00000020;
    public static final int IMAGE_SCN_CNT_INITIALIZED_DATA   = 0x00000040;
    public static final int IMAGE_SCN_CNT_UNINITIALIZED_DATA = 0x00000080;

    public static final int IMAGE_SCN_ALIGN_1BYTES  = 0x00100000;
    public static final int IMAGE_SCN_ALIGN_2BYTES  = 0x00200000;
    public static final int IMAGE_SCN_ALIGN_4BYTES  = 0x00300000;
    public static final int IMAGE_SCN_ALIGN_8BYTES  = 0x00400000;
    public static final int IMAGE_SCN_ALIGN_16BYTES = 0x00500000;
    public static final int IMAGE_SCN_ALIGN_32BYTES = 0x00600000;

    public static final int IMAGE_SCN_MEM_EXECUTE = 0x20000000;
    public static final int IMAGE_SCN_MEM_READ    = 0x40000000;
    public static final int IMAGE_SCN_MEM_WRITE   = 0x80000000;

    public static boolean WRITABLE_TEXT = false;

    public static final int SCN_DATA =
        IMAGE_SCN_CNT_INITIALIZED_DATA |
        IMAGE_SCN_ALIGN_4BYTES |
        IMAGE_SCN_MEM_READ |
        IMAGE_SCN_MEM_WRITE;

    public static final int SCN_BSS =
        IMAGE_SCN_CNT_UNINITIALIZED_DATA |
        IMAGE_SCN_ALIGN_4BYTES |
        IMAGE_SCN_MEM_READ |
        IMAGE_SCN_MEM_WRITE;

    public static final int SCN_RDATA =
        IMAGE_SCN_CNT_INITIALIZED_DATA |
        IMAGE_SCN_ALIGN_4BYTES |
        IMAGE_SCN_MEM_READ;

    public static final int SCN_CODE =
        IMAGE_SCN_CNT_CODE |
        IMAGE_SCN_ALIGN_16BYTES |
        IMAGE_SCN_MEM_EXECUTE |
        IMAGE_SCN_MEM_READ;

    public static final int IMAGE_REL_AMD64_ABSOLUTE = 0x0000;
    public static final int IMAGE_REL_AMD64_ADDR64   = 0x0001;
    public static final int IMAGE_REL_AMD64_ADDR32   = 0x0002;
    public static final int IMAGE_REL_AMD64_ADDR32NB = 0x0003;
    public static final int IMAGE_REL_AMD64_REL32    = 0x0004;
    public static final int IMAGE_REL_AMD64_REL32_1  = 0x0005;
    public static final int IMAGE_REL_AMD64_REL32_2  = 0x0006;
    public static final int IMAGE_REL_AMD64_REL32_3  = 0x0007;
    public static final int IMAGE_REL_AMD64_REL32_4  = 0x0008;
    public static final int IMAGE_REL_AMD64_REL32_5  = 0x0009;

    public static final int IMAGE_SYM_CLASS_EXTERNAL = 2;
    public static final int IMAGE_SYM_CLASS_STATIC   = 3;

    public static final int IMAGE_SYM_CLASS_FILE = 0x67;

    public static final int IMAGE_SYM_DTYPE_FUNCTION = 0x20;

    private static class Section {

        final String name;
        final int characteristics;

        int headerPos = -1;
        int rawDataStart = -1;
        int rawDataEnd = -1;
        int sectionIndex = -1;

        int pointerToRelocations = 0;

        final List<Reloc> relocs =
            new ArrayList<>();

        Section(
            String name,
            int characteristics
        ) {
            this.name = name;
            this.characteristics = characteristics;
        }

        int size() {
            if (
                rawDataStart < 0 ||
                rawDataEnd < 0
            ) {
                return 0;
            }

            return rawDataEnd - rawDataStart;
        }
    }

    private static class Reloc {

        final int offsetInSection;
        final String symbolName;
        final int type;

        Reloc(
            int offsetInSection,
            String symbolName,
            int type
        ) {
            this.offsetInSection = offsetInSection;
            this.symbolName = symbolName;
            this.type = type;
        }
    }

    private static class Sym {

        final String name;

        int value = 0;
        int sectionNumber = 0;

        int storageClass =
            IMAGE_SYM_CLASS_STATIC;

        int type = 0;

        int symbolIndex = -1;

        Sym(String name) {
            this.name = name;
        }
    }

    private final Builder b;
    private final int machine;

    private final List<Section> sections =
        new ArrayList<>();

    private final Map<String, Section> sectionByName =
        new LinkedHashMap<>();

    private Section current;

    private boolean began = false;

    private final Map<String, Sym> symbols =
        new LinkedHashMap<>();

    private final Set<String> pendingGlobal =
        new HashSet<>();

    private int fileHeaderPos = -1;

    public JavaWinOBJ(Builder b) {
        this(
            b,
            IMAGE_FILE_MACHINE_AMD64
        );
    }

    public JavaWinOBJ(
        Builder b,
        int machine
    ) {
        if (b == null) {
            throw new IllegalArgumentException(
                "Builder no puede ser null"
            );
        }

        this.b = b;
        this.machine = machine;
    }

    public JavaWinOBJ addSection(
        String name,
        int characteristics
    ) {

        if (began) {
            throw new IllegalStateException(
                "No se pueden declarar secciones tras begin()"
            );
        }

        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException(
                "Nombre de sección inválido"
            );
        }

        if (sectionByName.containsKey(name)) {
            throw new IllegalArgumentException(
                "Sección duplicada: " + name
            );
        }

        Section s =
            new Section(
                name,
                characteristics
            );

        s.sectionIndex =
            sections.size() + 1;

        sections.add(s);
        sectionByName.put(name, s);

        return this;
    }

    public JavaWinOBJ begin() {

        if (began) {
            throw new IllegalStateException(
                "begin() ya fue llamado en este JavaOBJ"
            );
        }

        if (sections.isEmpty()) {
            throw new IllegalStateException(
                "Declara al menos una sección con addSection() antes de begin()"
            );
        }

        fileHeaderPos =
            b.offset();

        b.fill_with_until(
            (byte) 0x00,
            fileHeaderPos + FILE_HEADER_SIZE
        );

        for (Section s : sections) {

            s.headerPos =
                b.offset();

            b.fill_with_until(
                (byte) 0x00,
                s.headerPos + SECTION_HEADER_SIZE
            );
        }

        began = true;

        arch.Diagnostics.log(
            "JavaOBJ.begin(): fileHeader@" +
            fileHeaderPos +
            " " +
            sections.size() +
            " secciones reservadas"
        );

        return this;
    }

    public JavaWinOBJ section(
        String name
    ) {

        if (!began) {
            throw new IllegalStateException(
                "Llama a begin() antes de escribir secciones"
            );
        }

        Section s =
            sectionByName.get(name);

        if (s == null) {
            throw new IllegalArgumentException(
                "Sección no declarada con addSection(): " +
                name
            );
        }

        if (
            current != null &&
            current != s
        ) {
            closeCurrentSection();
        }

        if (s.rawDataEnd != -1) {
            throw new IllegalStateException(
                "La sección '" +
                name +
                "' ya está cerrada"
            );
        }

        if (s.rawDataStart == -1) {
            s.rawDataStart =
                b.offset();
        }

        current = s;

        return this;
    }

    private void closeCurrentSection() {

        if (
            current != null &&
            current.rawDataEnd == -1
        ) {

            current.rawDataEnd =
                b.offset();

            arch.Diagnostics.log(
                "JavaOBJ: sección '" +
                current.name +
                "' cerrada, tamaño=" +
                current.size()
            );
        }
    }

    private void requireCurrentSection() {

        if (current == null) {
            throw new IllegalStateException(
                "Llama primero a section(nombre)"
            );
        }
    }

    private Section ensureBackendTextSection() {

        if (!began) {
            throw new IllegalStateException(
                "JavaWinOBJ.begin() debe ejecutarse antes del Backend"
            );
        }

        Section text =
            sectionByName.get(".text");

        if (text == null) {
            throw new IllegalStateException(
                "El Backend necesita una sección .text"
            );
        }

        int expectedTextStart =
            fileHeaderPos +
            FILE_HEADER_SIZE +
            (
                sections.size() *
                SECTION_HEADER_SIZE
            );

        if (text.rawDataStart == -1) {
            text.rawDataStart =
                expectedTextStart;
        }

        current = text;

        return text;
    }

    public JavaWinOBJ bytes(
        byte[] data
    ) {

        requireCurrentSection();

        b.file_write(data);

        return this;
    }

    public JavaWinOBJ byte_(int v) {

        requireCurrentSection();

        b.file_write(v);

        return this;
    }

    public JavaWinOBJ word(int v) {

        requireCurrentSection();

        b.file_write_shortLE(
            (short) v
        );

        return this;
    }

    public JavaWinOBJ dword(int v) {

        requireCurrentSection();

        b.file_write_intLE(v);

        return this;
    }

    public JavaWinOBJ qword(long v) {

        requireCurrentSection();

        b.file_write(
            u64(v)
        );

        return this;
    }

    public JavaWinOBJ zero(int n) {

        requireCurrentSection();

        if (n < 0) {
            throw new IllegalArgumentException(
                "n no puede ser negativo"
            );
        }

        b.fill_with_until(
            (byte) 0x00,
            b.offset() + n
        );

        return this;
    }

    public JavaWinOBJ asciiz(
        String s
    ) {

        requireCurrentSection();

        if (s == null) {
            s = "";
        }

        b.file_write(
            s.getBytes(
                StandardCharsets.US_ASCII
            )
        );

        b.file_write(0x00);

        return this;
    }

    public JavaWinOBJ global(
        String name
    ) {

        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException(
                "Nombre de símbolo inválido"
            );
        }

        pendingGlobal.add(name);

        Sym existing =
            symbols.get(name);

        if (existing != null) {
            existing.storageClass =
                IMAGE_SYM_CLASS_EXTERNAL;
        }

        return this;
    }

    public JavaWinOBJ extern(
        String name
    ) {

        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException(
                "Nombre de símbolo inválido"
            );
        }

        Sym s =
            symbols.computeIfAbsent(
                name,
                Sym::new
            );

        s.sectionNumber = 0;
        s.value = 0;

        s.storageClass =
            IMAGE_SYM_CLASS_EXTERNAL;

        return this;
    }

    public JavaWinOBJ addFileMetadata(
        Path ashSource
    ) {

        if (ashSource == null) {
            throw new IllegalArgumentException(
                "ashSource no puede ser null"
            );
        }

        Sym fileSym =
            new Sym(".file");

        fileSym.storageClass =
            IMAGE_SYM_CLASS_FILE;

        fileSym.type = 0;
        fileSym.sectionNumber = -2;
        fileSym.value = 0;

        symbols.put(
            ".file",
            fileSym
        );

        Path absolute =
            ashSource
                .toAbsolutePath()
                .normalize();

        Path directory =
            absolute.getParent();

        String source =
            absolute.getFileName()
                .toString();

        String directoryName =
            directory != null
                ? directory.toString()
                : ".";

        String osName =
            System.getProperty(
                "os.name",
                "unknown"
            );

        String osVersion =
            System.getProperty(
                "os.version",
                "unknown"
            );

        String osArch =
            System.getProperty(
                "os.arch",
                "unknown"
            );

        String userName =
            System.getProperty(
                "user.name",
                "unknown"
            );

        String userHome =
            System.getProperty(
                "user.home",
                "unknown"
            );

        String userDirectory =
            System.getProperty(
                "user.dir",
                "unknown"
            );

        String fileSeparator =
            System.getProperty(
                "file.separator",
                "/"
            );

        String pathSeparator =
            System.getProperty(
                "path.separator",
                ":"
            );

        String lineSeparator =
            System.getProperty(
                "line.separator",
                "\n"
            );

        addMetadataSymbol(source);
        addMetadataSymbol(absolute.toString());
        addMetadataSymbol(directoryName);

        addMetadataSymbol(osName);
        addMetadataSymbol(osVersion);
        addMetadataSymbol(osArch);

        addMetadataSymbol(userName);
        addMetadataSymbol(userHome);
        addMetadataSymbol(userDirectory);

        addMetadataSymbol(fileSeparator);
        addMetadataSymbol(pathSeparator);

        addMetadataSymbol(
            escapeMetadata(
                lineSeparator
            )
        );

        addMetadataSymbol(
            "ASH       "
        );

        addMetadataSymbol(
            "ASH_WINPE_OBJECT"
        );

        return this;
    }

    private void addMetadataSymbol(
        String metadata
    ) {

        if (metadata == null) {
            return;
        }

        Sym sym =
            new Sym(metadata);

        sym.storageClass =
            IMAGE_SYM_CLASS_FILE;

        sym.type = 0;
        sym.sectionNumber = -2;
        sym.value = 0;

        if (!symbols.containsKey(metadata)) {
            symbols.put(
                metadata,
                sym
            );
        }
    }

    private String escapeMetadata(
        String value
    ) {

        if (value == null) {
            return "";
        }

        return value
            .replace(
                "\\",
                "\\\\"
            )
            .replace(
                "\r",
                "\\r"
            )
            .replace(
                "\n",
                "\\n"
            )
            .replace(
                "\t",
                "\\t"
            );
    }

    public JavaWinOBJ label(
        String name
    ) {

        requireCurrentSection();

        return labelAt(
            name,
            b.offset()
        );
    }

    public JavaWinOBJ labelAt(
        String name,
        int absoluteFileOffset
    ) {

        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException(
                "Nombre de label inválido"
            );
        }

        if (current == null) {
            throw new IllegalStateException(
                "No hay sección activa para el símbolo '" +
                name +
                "'"
            );
        }

        if (current.rawDataStart < 0) {
            current.rawDataStart =
                absoluteFileOffset;
        }

        Sym s =
            symbols.get(name);

        if (s == null) {

            s =
                new Sym(name);

            symbols.put(
                name,
                s
            );
        }

        s.value =
            absoluteFileOffset -
            current.rawDataStart;

        if (s.value < 0) {
            throw new IllegalStateException(
                "Offset inválido para '" +
                name +
                "': 0x" +
                Integer.toHexString(
                    absoluteFileOffset
                ) +
                " < rawDataStart 0x" +
                Integer.toHexString(
                    current.rawDataStart
                )
            );
        }

        s.sectionNumber =
            current.sectionIndex;

        s.storageClass =
            pendingGlobal.contains(name)
                ? IMAGE_SYM_CLASS_EXTERNAL
                : IMAGE_SYM_CLASS_STATIC;

        return this;
    }

    public JavaWinOBJ markFunction(
        String name
    ) {

        Sym s =
            symbols.get(name);

        if (s == null) {
            throw new IllegalArgumentException(
                "Símbolo no definido: " +
                name
            );
        }

        s.type =
            IMAGE_SYM_DTYPE_FUNCTION;

        s.storageClass =
            IMAGE_SYM_CLASS_EXTERNAL;

        return this;
    }

    public JavaWinOBJ ripRel32(
        String symbolName
    ) {

        return ripRel32(
            symbolName,
            IMAGE_REL_AMD64_REL32
        );
    }

    public JavaWinOBJ ripRel32(
        String symbolName,
        int relocType
    ) {

        requireCurrentSection();

        int off =
            b.offset() -
            current.rawDataStart;

        current.relocs.add(
            new Reloc(
                off,
                symbolName,
                relocType
            )
        );

        b.file_write_intLE(0);

        return this;
    }

    public JavaWinOBJ abs32(
        String symbolName
    ) {

        requireCurrentSection();

        int off =
            b.offset() -
            current.rawDataStart;

        current.relocs.add(
            new Reloc(
                off,
                symbolName,
                IMAGE_REL_AMD64_ADDR32
            )
        );

        b.file_write_intLE(0);

        return this;
    }

    public JavaWinOBJ abs64(
        String symbolName
    ) {

        requireCurrentSection();

        int off =
            b.offset() -
            current.rawDataStart;

        current.relocs.add(
            new Reloc(
                off,
                symbolName,
                IMAGE_REL_AMD64_ADDR64
            )
        );

        b.file_write(
            u64(0)
        );

        return this;
    }

    public JavaWinOBJ callExtern(
        String symbolName
    ) {

        if (current == null)
            ensureBackendTextSection();

        requireCurrentSection();

        b.file_write(0xE8);

        return ripRel32(
            symbolName,
            IMAGE_REL_AMD64_REL32
        );
    }

    public JavaWinOBJ callOrJmpRel32(
        String symbolName
    ) {

        return ripRel32(
            symbolName,
            IMAGE_REL_AMD64_REL32
        );
    }

    @Override
    public void defineFunctionSymbol(
        String name,
        int fileOffset,
        boolean exported
    ) {

        Section text =
            ensureBackendTextSection();

        if (fileOffset < text.rawDataStart) {
            throw new IllegalStateException(
                "Offset de función inválido para '" +
                name +
                "': 0x" +
                Integer.toHexString(fileOffset) +
                " < .text.rawDataStart 0x" +
                Integer.toHexString(text.rawDataStart)
            );
        }

        if (exported) {
            global(name);
        }

        Sym s =
            symbols.get(name);

        if (s == null) {
            s =
                new Sym(name);

            symbols.put(
                name,
                s
            );
        }

        s.value =
            fileOffset -
            text.rawDataStart;

        s.sectionNumber =
            text.sectionIndex;

        s.type =
            IMAGE_SYM_DTYPE_FUNCTION;

        s.storageClass =
            exported
                ? IMAGE_SYM_CLASS_EXTERNAL
                : IMAGE_SYM_CLASS_STATIC;

        arch.Diagnostics.log(
            "[JavaWinOBJ] FUNCTION '" +
            name +
            "'" +
            " absolute=0x" +
            Integer.toHexString(fileOffset) +
            " value=0x" +
            Integer.toHexString(s.value) +
            " section=" +
            s.sectionNumber +
            " class=" +
            s.storageClass
        );
    }

    public String end(
        String outputDir,
        String filename
    ) {

        if (!began) {
            throw new IllegalStateException(
                "Llama a begin() antes de end()"
            );
        }

        Section text =
            sectionByName.get(".text");

        if (
            text != null &&
            text.rawDataStart == -1
        ) {
            text.rawDataStart =
                fileHeaderPos +
                FILE_HEADER_SIZE +
                (
                    sections.size() *
                    SECTION_HEADER_SIZE
                );
        }

        closeCurrentSection();

        for (Section s : sections) {

            if (s.rawDataStart == -1) {

                s.rawDataStart = 0;
                s.rawDataEnd = 0;
            }

            if (s.rawDataEnd == -1) {

                if (s == text) {
                    s.rawDataEnd =
                        b.offset();
                } else {
                    s.rawDataEnd =
                        s.rawDataStart;
                }
            }
        }

        int idxAssign = 0;

        for (Sym s : symbols.values()) {

            s.symbolIndex =
                idxAssign++;
        }

        for (Section s : sections) {

            for (Reloc r : s.relocs) {

                if (
                    !symbols.containsKey(
                        r.symbolName
                    )
                ) {

                    throw new IllegalStateException(
                        "Símbolo no definido: '" +
                        r.symbolName +
                        "' (referenciado desde la sección '" +
                        s.name +
                        "')"
                    );
                }
            }
        }

        Map<String, Integer> longNameOffsets =
            new LinkedHashMap<>();

        java.io.ByteArrayOutputStream strtab =
            new java.io.ByteArrayOutputStream();

        int strtabCursor = 4;

        for (Sym s : symbols.values()) {

            if (
                s.name.getBytes(
                    StandardCharsets.US_ASCII
                ).length > 8
            ) {

                longNameOffsets.put(
                    s.name,
                    strtabCursor
                );

                byte[] nameBytes =
                    s.name.getBytes(
                        StandardCharsets.US_ASCII
                    );

                strtab.write(
                    nameBytes,
                    0,
                    nameBytes.length
                );

                strtab.write(0);

                strtabCursor +=
                    nameBytes.length + 1;
            }
        }

        int strtabTotalSize =
            4 + strtab.size();

        for (Section s : sections) {

            if (s.relocs.isEmpty()) {
                continue;
            }

            s.pointerToRelocations =
                b.offset();

            for (Reloc r : s.relocs) {

                Sym sym =
                    symbols.get(
                        r.symbolName
                    );

                writeRelocation(
                    r.offsetInSection,
                    sym,
                    r.type
                );
            }
        }

        int symbolTablePos =
            b.offset();

        for (Sym s : symbols.values()) {

            writeSymbol(
                s,
                longNameOffsets
            );
        }

        b.file_write(
            u32(strtabTotalSize)
        );

        if (strtab.size() > 0) {

            b.file_write(
                strtab.toByteArray()
            );
        }

        patchFileHeader(
            symbolTablePos,
            symbols.size()
        );

        for (Section s : sections) {

            patchSectionHeader(s);
        }

        arch.Diagnostics.log(
            "JavaOBJ.end(): " +
            sections.size() +
            " secciones, " +
            symbols.size() +
            " símbolos, symtab@" +
            symbolTablePos
        );

        return b.BUILD(
            outputDir,
            filename
        );
    }

    private void writeRelocation(
        int virtualAddress,
        Sym sym,
        int type
    ) {

        if (sym == null) {
            throw new IllegalStateException(
                "Relocación apunta a símbolo null"
            );
        }

        b.file_write(
            u32(virtualAddress)
        );

        b.file_write(
            u32(sym.symbolIndex)
        );

        b.file_write(
            u16(type)
        );
    }

    private void writeSymbol(
        Sym s,
        Map<String, Integer> longNameOffsets
    ) {

        byte[] nameField =
            new byte[8];

        byte[] rawName =
            s.name.getBytes(
                StandardCharsets.US_ASCII
            );

        if (rawName.length <= 8) {

            System.arraycopy(
                rawName,
                0,
                nameField,
                0,
                rawName.length
            );

        } else {

            Integer off =
                longNameOffsets.get(
                    s.name
                );

            if (off == null) {
                throw new IllegalStateException(
                    "No existe offset en string table para '" +
                    s.name +
                    "'"
                );
            }

            byte[] offBytes =
                u32(off);

            System.arraycopy(
                offBytes,
                0,
                nameField,
                4,
                4
            );
        }

        b.file_write(nameField);

        b.file_write(
            u32(s.value)
        );

        b.file_write(
            u16(s.sectionNumber)
        );

        b.file_write(
            u16(s.type)
        );

        b.file_write(
            s.storageClass
        );

        b.file_write(
            0x00
        );
    }

    private void patchFileHeader(
        int symbolTablePos,
        int numberOfSymbols
    ) {

        int p =
            fileHeaderPos;

        b.patchBytes(
            p,
            u16(machine)
        );

        b.patchBytes(
            p + 2,
            u16(sections.size())
        );

        b.patchBytes(
            p + 4,
            u32(
                (int)
                (
                    System.currentTimeMillis()
                    / 1000L
                )
            )
        );

        b.patchBytes(
            p + 8,
            u32(symbolTablePos)
        );

        b.patchBytes(
            p + 12,
            u32(numberOfSymbols)
        );

        b.patchBytes(
            p + 16,
            u16(0)
        );

        b.patchBytes(
            p + 18,
            u16(0)
        );
    }

    private void patchSectionHeader(
        Section s
    ) {

        int p =
            s.headerPos;

        byte[] nameField =
            new byte[8];

        byte[] raw =
            s.name.getBytes(
                StandardCharsets.US_ASCII
            );

        System.arraycopy(
            raw,
            0,
            nameField,
            0,
            Math.min(
                raw.length,
                8
            )
        );

        b.patchBytes(
            p,
            nameField
        );

        b.patchBytes(
            p + 8,
            u32(0)
        );

        b.patchBytes(
            p + 12,
            u32(0)
        );

        b.patchBytes(
            p + 16,
            u32(s.size())
        );

        b.patchBytes(
            p + 20,
            u32(
                s.size() == 0
                    ? 0
                    : s.rawDataStart
            )
        );

        b.patchBytes(
            p + 24,
            u32(
                s.pointerToRelocations
            )
        );

        b.patchBytes(
            p + 28,
            u32(0)
        );

        b.patchBytes(
            p + 32,
            u16(
                s.relocs.size()
            )
        );

        b.patchBytes(
            p + 34,
            u16(0)
        );

        b.patchBytes(
            p + 36,
            u32(
                (WRITABLE_TEXT && ".text".equals(s.name))
                    ? (s.characteristics | IMAGE_SCN_MEM_WRITE)
                    : s.characteristics
            )
        );
    }

    private static byte[] u16(
        int v
    ) {

        return new byte[] {
            (byte) (v & 0xFF),
            (byte) ((v >> 8) & 0xFF)
        };
    }

    private static byte[] u32(
        long v
    ) {

        return new byte[] {
            (byte) (v & 0xFF),
            (byte) ((v >> 8) & 0xFF),
            (byte) ((v >> 16) & 0xFF),
            (byte) ((v >> 24) & 0xFF)
        };
    }

    private static byte[] u64(
        long v
    ) {

        byte[] out =
            new byte[8];

        for (int i = 0; i < 8; i++) {

            out[i] =
                (byte)
                (
                    (v >> (8 * i))
                    & 0xFF
                );
        }

        return out;
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
