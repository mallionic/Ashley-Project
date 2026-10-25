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

import java.io.ByteArrayOutputStream;

import java.nio.ByteBuffer;

import java.nio.ByteOrder;

import java.nio.charset.StandardCharsets;

import java.nio.file.Path;

import java.util.*;

import arch.SymbolTarget;

public class JavaElfOBJ implements SymbolTarget {

    public static final int EHDR_SIZE = 64;

    public static final int SHDR_SIZE = 64;

    public static final int SYM_SIZE  = 24;

    public static final int RELA_SIZE = 24;

    public static final int ET_REL    = 1;

    public static final int EM_X86_64 = 0x3E;

    public static final int SHT_PROGBITS = 1;

    public static final int SHT_SYMTAB   = 2;

    public static final int SHT_STRTAB   = 3;

    public static final int SHT_RELA     = 4;

    public static final int SHT_NOBITS   = 8;

    public static final long SHF_WRITE     = 0x1;

    public static boolean WRITABLE_TEXT = false;

    private static String outName(String n) {
        return (WRITABLE_TEXT && ".text".equals(n)) ? ".ashtext" : n;
    }

    public static final long SHF_ALLOC     = 0x2;

    public static final long SHF_EXECINSTR = 0x4;

    public static final long SHF_MERGE     = 0x10;

    public static final long SHF_STRINGS   = 0x20;

    public static final long SHF_INFO_LINK = 0x40;

    public static final int STB_LOCAL  = 0;

    public static final int STB_GLOBAL = 1;

    public static final int STB_WEAK   = 2;

    public static final int STT_NOTYPE  = 0;

    public static final int STT_OBJECT  = 1;

    public static final int STT_FUNC    = 2;

    public static final int STT_SECTION = 3;

    public static final int STT_FILE    = 4;

    public static final int SHN_UNDEF = 0;

    public static final int SHN_ABS   = 0xFFF1;

    public static final int R_X86_64_NONE     = 0;

    public static final int R_X86_64_64       = 1;

    public static final int R_X86_64_PC32     = 2;

    public static final int R_X86_64_GOT32    = 3;

    public static final int R_X86_64_PLT32    = 4;

    public static final int R_X86_64_GOTPCREL = 9;

    public static final int R_X86_64_32       = 10;

    public static final int R_X86_64_32S      = 11;

    public static final class SectionKind {

        public final int type;

        public final long flags;

        public final long align;

        public SectionKind(int type, long flags, long align) {

            this.type = type;

            this.flags = flags;

            this.align = align;

        }

    }

    public static final SectionKind SCN_TEXT =

        new SectionKind(

            SHT_PROGBITS,

            SHF_ALLOC | SHF_EXECINSTR,

            16

        );

    public static final SectionKind SCN_RODATA =

        new SectionKind(

            SHT_PROGBITS,

            SHF_ALLOC,

            16

        );

    public static final SectionKind SCN_DATA =

        new SectionKind(

            SHT_PROGBITS,

            SHF_ALLOC | SHF_WRITE,

            8

        );

    public static final SectionKind SCN_BSS =

        new SectionKind(

            SHT_NOBITS,

            SHF_ALLOC | SHF_WRITE,

            8

        );

    public enum ExternType {

        VOID  ("void",   0, false, ""),

        BOOL  ("i1",     1, false, "zeroext"),

        BYTE  ("i8",     1, false, "signext"),

        UBYTE ("i8",     1, false, "zeroext"),

        SHORT ("i16",    2, false, "signext"),

        USHORT("i16",    2, false, "zeroext"),

        INT   ("i32",    4, false, ""),

        UINT  ("i32",    4, false, ""),

        LONG  ("i64",    8, false, ""),

        ULONG ("i64",    8, false, ""),

        FLOAT ("float",  4, true,  ""),

        DOUBLE("double", 8, true,  ""),

        STRING("ptr",    8, false, ""),

        PTR   ("ptr",    8, false, "");

        public final String llvm;

        public final int size;

        public final boolean sse;

        public final String attr;

        ExternType(

            String llvm,

            int size,

            boolean sse,

            String attr

        ) {

            this.llvm = llvm;

            this.size = size;

            this.sse = sse;

            this.attr = attr;

        }

        public static ExternType parse(String s) {

            if (s == null) {

                throw new IllegalArgumentException("Tipo null");

            }

            switch (s.trim().toLowerCase(Locale.ROOT)) {

                case "void":

                    return VOID;

                case "bool":

                case "boolean":

                    return BOOL;

                case "byte":

                case "i8":

                case "char":

                    return BYTE;

                case "ubyte":

                case "u8":

                    return UBYTE;

                case "short":

                case "i16":

                    return SHORT;

                case "ushort":

                case "u16":

                    return USHORT;

                case "int":

                case "i32":

                    return INT;

                case "uint":

                case "u32":

                    return UINT;

                case "long":

                case "i64":

                    return LONG;

                case "ulong":

                case "u64":

                    return ULONG;

                case "float":

                case "f32":

                    return FLOAT;

                case "double":

                case "f64":

                    return DOUBLE;

                case "string":

                case "str":

                    return STRING;

                case "ptr":

                case "pointer":

                    return PTR;

                default:

                    throw new IllegalArgumentException(

                        "Tipo extern desconocido: " + s

                    );

            }

        }

    }

    public static final class ExternSig {

        public final String name;

        public final ExternType ret;

        public final List<ExternType> params;

        public final boolean variadic;

        ExternSig(

            String name,

            ExternType ret,

            List<ExternType> params,

            boolean variadic

        ) {

            this.name = name;

            this.ret = ret;

            this.params =

                Collections.unmodifiableList(params);

            this.variadic = variadic;

        }

        private static String llvmName(String n) {

            return n.matches("[A-Za-z_.$][A-Za-z0-9_.$]*")

                ? "@" + n

                : "@\"" + n + "\"";

        }

        private static String withAttr(ExternType t) {

            return t.attr.isEmpty()

                ? t.llvm

                : t.llvm + " " + t.attr;

        }

        public String toLLVM() {

            StringBuilder sb =

                new StringBuilder("declare ");

            if (

                ret != ExternType.VOID &&

                !ret.attr.isEmpty()

            ) {

                sb.append(ret.attr).append(' ');

            }

            sb.append(ret.llvm)

              .append(' ')

              .append(llvmName(name))

              .append('(');

            for (int i = 0; i < params.size(); i++) {

                if (i > 0) {

                    sb.append(", ");

                }

                sb.append(withAttr(params.get(i)));

            }

            if (variadic) {

                if (!params.isEmpty()) {

                    sb.append(", ");

                }

                sb.append("...");

            }

            return sb.append(')').toString();

        }

    }

    private static class Section {

        final String name;

        final int type;

        final long flags;

        final long align;

        int sectionIndex = -1;

        int rawDataStart = -1;

        int rawDataEnd = -1;

        long bssSize = 0;

        final List<Reloc> relocs =

            new ArrayList<>();

        Section(

            String name,

            int type,

            long flags,

            long align

        ) {

            this.name = name;

            this.type = type;

            this.flags = flags;

            this.align = align;

        }

        boolean nobits() {

            return type == SHT_NOBITS;

        }

        boolean exec() {

            return (flags & SHF_EXECINSTR) != 0;

        }

        long size() {

            if (nobits()) {

                return bssSize;

            }

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

        final long offsetInSection;

        final String symbolName;

        final int type;

        final long addend;

        Reloc(

            long offsetInSection,

            String symbolName,

            int type,

            long addend

        ) {

            this.offsetInSection = offsetInSection;

            this.symbolName = symbolName;

            this.type = type;

            this.addend = addend;

        }

    }

    private static class Sym {

        final String name;

        long value = 0;

        long size = 0;

        int shndx = SHN_UNDEF;

        int bind = STB_LOCAL;

        int type = STT_NOTYPE;

        int index = -1;

        Sym(String name) {

            this.name = name;

        }

    }

    private final Builder b;

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

    private final Set<String> pendingWeak =

        new HashSet<>();

    private final Map<String, ExternSig> externSigs =

        new LinkedHashMap<>();

    private final Map<String, ExternType> externVarTypes =

        new LinkedHashMap<>();

    private final List<String> fileSymbols =

        new ArrayList<>();

    private final List<String> commentStrings =

        new ArrayList<>();

    private int fileHeaderPos = -1;

    private ByteArrayOutputStream endBuffer;
    private int endBaseOffset;
    private final byte[] symbolScratch = new byte[SYM_SIZE];

    public JavaElfOBJ(Builder b) {

        if (b == null) {

            throw new IllegalArgumentException(

                "Builder no puede ser null"

            );

        }

        this.b = b;

    }

    public JavaElfOBJ addSection(

        String name,

        SectionKind kind

    ) {

        return addSection(

            name,

            kind.type,

            kind.flags,

            kind.align

        );

    }

    public JavaElfOBJ addSection(

        String name,

        int type,

        long flags,

        long align

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

        if (

            align < 1 ||

            (align & (align - 1)) != 0

        ) {

            throw new IllegalArgumentException(

                "Alineación debe ser potencia de 2: " +

                align

            );

        }

        Section s =

            new Section(

                name,

                type,

                flags,

                align

            );

        s.sectionIndex =

            sections.size() + 1;

        sections.add(s);

        sectionByName.put(name, s);

        return this;

    }

    public JavaElfOBJ begin() {

        if (began) {

            throw new IllegalStateException(

                "begin() ya fue llamado en este JavaElfOBJ"

            );

        }

        if (sections.isEmpty()) {

            throw new IllegalStateException(

                "Declara al menos una sección con addSection() antes de begin()"

            );

        }

        fileHeaderPos = b.offset();

        if (fileHeaderPos != 0) {

            throw new IllegalStateException(

                "El Builder debe estar en offset 0 al llamar begin() " +

                "(offset=" + fileHeaderPos + ")"

            );

        }

        b.fill_with_until(

            (byte) 0x00,

            fileHeaderPos + EHDR_SIZE

        );

        began = true;

        arch.Diagnostics.log(

            "JavaElfOBJ.begin(): ehdr@" +

            fileHeaderPos +

            " " +

            sections.size() +

            " secciones declaradas"

        );

        return this;

    }

    private int expectedTextStart() {

        return fileHeaderPos + EHDR_SIZE;

    }

    public JavaElfOBJ section(String name) {

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

        Section text =

            sectionByName.get(".text");

        if (

            text != null &&

            text != s &&

            current == null &&

            text.rawDataStart == -1 &&

            b.offset() > expectedTextStart()

        ) {

            text.rawDataStart =

                expectedTextStart();

            current = text;

        }

        if (

            current != null &&

            current != s

        ) {

            closeCurrentSection();

        }

        if (

            !s.nobits() &&

            s.rawDataEnd != -1

        ) {

            throw new IllegalStateException(

                "La sección '" +

                name +

                "' ya está cerrada"

            );

        }

        if (s.nobits()) {

            s.rawDataStart = 0;

        } else if (s.rawDataStart == -1) {

            int aligned =

                alignUp(

                    b.offset(),

                    (int) s.align

                );

            if (aligned > b.offset()) {

                b.fill_with_until(

                    (byte) 0x00,

                    aligned

                );

            }

            s.rawDataStart = aligned;

        }

        current = s;

        return this;

    }

    private void closeCurrentSection() {

        if (

            current != null &&

            !current.nobits() &&

            current.rawDataEnd == -1

        ) {

            current.rawDataEnd =

                b.offset();

            arch.Diagnostics.log(

                "JavaElfOBJ: sección '" +

                current.name +

                "' cerrada, tamaño=" +

                current.size()

            );

        }

    }

    private void requireCurrentSection() {

        if (current == null) {

            Section text =

                sectionByName.get(".text");

            if (

                began &&

                text != null &&

                text.rawDataStart == -1

            ) {

                ensureBackendTextSection();

            } else {

                throw new IllegalStateException(

                    "Llama primero a section(nombre)"

                );

            }

        }

    }

    private void requireFileSection() {

        requireCurrentSection();

        if (current.nobits()) {

            throw new IllegalStateException(

                "La sección '" +

                current.name +

                "' es NOBITS"

            );

        }

    }

    private Section ensureBackendTextSection() {

        if (!began) {

            throw new IllegalStateException(

                "JavaElfOBJ.begin() debe ejecutarse antes del Backend"

            );

        }

        Section text =

            sectionByName.get(".text");

        if (text == null) {

            throw new IllegalStateException(

                "El Backend necesita una sección .text"

            );

        }

        if (text.rawDataStart == -1) {

            text.rawDataStart =

                expectedTextStart();

        }

        if (

            current == null ||

            current == text

        ) {

            current = text;

        }

        return text;

    }

    private static int alignUp(int v, int a) {

        return (v + a - 1) & ~(a - 1);

    }

    public JavaElfOBJ bytes(byte[] data) {

        requireFileSection();

        b.file_write(data);

        return this;

    }

    public JavaElfOBJ byte_(int v) {

        requireFileSection();

        b.file_write(v);

        return this;

    }

    public JavaElfOBJ word(int v) {

        requireFileSection();

        b.file_write_shortLE((short) v);

        return this;

    }

    public JavaElfOBJ dword(int v) {

        requireFileSection();

        b.file_write_intLE(v);

        return this;

    }

    public JavaElfOBJ qword(long v) {

        requireFileSection();

        b.file_write(u64(v));

        return this;

    }

    public JavaElfOBJ float32(float v) {

        return dword(

            Float.floatToIntBits(v)

        );

    }

    public JavaElfOBJ float64(double v) {

        return qword(

            Double.doubleToLongBits(v)

        );

    }

    public JavaElfOBJ zero(int n) {

        requireFileSection();

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

    public JavaElfOBJ resb(int n) {

        requireCurrentSection();

        if (n < 0) {

            throw new IllegalArgumentException(

                "n no puede ser negativo"

            );

        }

        if (current.nobits()) {

            current.bssSize += n;

            return this;

        }

        return zero(n);

    }

    public JavaElfOBJ align(int n) {

        requireCurrentSection();

        if (

            n < 1 ||

            (n & (n - 1)) != 0

        ) {

            throw new IllegalArgumentException(

                "Alineación debe ser potencia de 2"

            );

        }

        if (current.nobits()) {

            current.bssSize =

                (current.bssSize + n - 1) &

                ~((long) n - 1);

            return this;

        }

        int aligned =

            alignUp(

                b.offset(),

                n

            );

        if (aligned > b.offset()) {

            b.fill_with_until(

                (byte) 0x00,

                aligned

            );

        }

        return this;

    }

    public JavaElfOBJ asciiz(String s) {

        requireFileSection();

        if (s == null) {

            s = "";

        }

        b.file_write(

            s.getBytes(

                StandardCharsets.UTF_8

            )

        );

        b.file_write(0x00);

        return this;

    }

    public JavaElfOBJ global(String name) {

        checkName(name);

        pendingGlobal.add(name);

        Sym existing =

            symbols.get(name);

        if (existing != null) {

            existing.bind =

                STB_GLOBAL;

        }

        return this;

    }

    public JavaElfOBJ weak(String name) {

        checkName(name);

        pendingWeak.add(name);

        Sym existing =

            symbols.get(name);

        if (existing != null) {

            existing.bind =

                STB_WEAK;

        }

        return this;

    }

    public JavaElfOBJ extern(String name) {

        checkName(name);

        Sym s =

            symbols.computeIfAbsent(

                name,

                Sym::new

            );

        s.shndx = SHN_UNDEF;

        s.value = 0;

        s.bind = STB_GLOBAL;

        return this;

    }

    public JavaElfOBJ externFunc(

        String name,

        ExternType ret,

        ExternType... params

    ) {

        return declareExternFunc(

            name,

            ret,

            Arrays.asList(params),

            false

        );

    }

    public JavaElfOBJ externVariadic(

        String name,

        ExternType ret,

        ExternType... fixedParams

    ) {

        return declareExternFunc(

            name,

            ret,

            Arrays.asList(fixedParams),

            true

        );

    }

    public JavaElfOBJ externFunc(

        String name,

        String ret,

        String... params

    ) {

        List<ExternType> ps =

            new ArrayList<>();

        for (String p : params) {

            ps.add(

                ExternType.parse(p)

            );

        }

        return declareExternFunc(

            name,

            ExternType.parse(ret),

            ps,

            false

        );

    }

    public JavaElfOBJ externVariadic(

        String name,

        String ret,

        String... fixedParams

    ) {

        List<ExternType> ps =

            new ArrayList<>();

        for (String p : fixedParams) {

            ps.add(

                ExternType.parse(p)

            );

        }

        return declareExternFunc(

            name,

            ExternType.parse(ret),

            ps,

            true

        );

    }

    private JavaElfOBJ declareExternFunc(

        String name,

        ExternType ret,

        List<ExternType> params,

        boolean variadic

    ) {

        checkName(name);

        if (ret == null) {

            throw new IllegalArgumentException(

                "Tipo de retorno null"

            );

        }

        for (ExternType p : params) {

            if (

                p == null ||

                p == ExternType.VOID

            ) {

                throw new IllegalArgumentException(

                    "Parámetro inválido en extern '" +

                    name +

                    "'"

                );

            }

        }

        externSigs.put(

            name,

            new ExternSig(

                name,

                ret,

                new ArrayList<>(params),

                variadic

            )

        );

        Sym s =

            symbols.computeIfAbsent(

                name,

                Sym::new

            );

        s.shndx = SHN_UNDEF;

        s.value = 0;

        s.bind =

            pendingWeak.contains(name)

                ? STB_WEAK

                : STB_GLOBAL;

        s.type = STT_FUNC;

        return this;

    }

    public JavaElfOBJ externVar(

        String name,

        ExternType type

    ) {

        checkName(name);

        if (

            type == null ||

            type == ExternType.VOID

        ) {

            throw new IllegalArgumentException(

                "Tipo de variable extern inválido"

            );

        }

        Sym s =

            symbols.computeIfAbsent(

                name,

                Sym::new

            );

        s.shndx = SHN_UNDEF;

        s.value = 0;

        s.size = type.size;

        s.bind =

            pendingWeak.contains(name)

                ? STB_WEAK

                : STB_GLOBAL;

        s.type = STT_OBJECT;

        externVarTypes.put(

            name,

            type

        );

        return this;

    }

    public ExternSig externSig(String name) {

        return externSigs.get(name);

    }

    private static final String[] INT_ARG_REGS = {

        "rdi",

        "rsi",

        "rdx",

        "rcx",

        "r8",

        "r9"

    };

    public static List<String> abiArgs(

        List<ExternType> params

    ) {

        List<String> out =

            new ArrayList<>();

        int ints = 0;

        int sse = 0;

        int stack = 0;

        for (ExternType t : params) {

            if (t.sse) {

                if (sse < 8) {

                    out.add(

                        "xmm" + sse++

                    );

                } else {

                    out.add(

                        "stack[" +

                        (stack++ * 8) +

                        "]"

                    );

                }

            } else {

                if (

                    ints <

                    INT_ARG_REGS.length

                ) {

                    out.add(

                        INT_ARG_REGS[ints++]

                    );

                } else {

                    out.add(

                        "stack[" +

                        (stack++ * 8) +

                        "]"

                    );

                }

            }

        }

        return out;

    }

    public List<String> abiArgs(

        String externName

    ) {

        ExternSig sig =

            externSigs.get(externName);

        if (sig == null) {

            throw new IllegalArgumentException(

                "Extern sin firma: " +

                externName

            );

        }

        return abiArgs(sig.params);

    }

    public static String abiReturn(

        ExternType t

    ) {

        if (t == ExternType.VOID) {

            return "none";

        }

        if (t.sse) {

            return "xmm0";

        }

        return "rax";

    }

    public static int variadicVectorCount(

        List<ExternType> actualArgs

    ) {

        int n = 0;

        for (ExternType t : actualArgs) {

            if (t.sse) {

                n++;

            }

        }

        return Math.min(n, 8);

    }

    public String llvmDeclare(

        String name

    ) {

        ExternSig sig =

            externSigs.get(name);

        if (sig == null) {

            throw new IllegalArgumentException(

                "Extern sin firma: " +

                name

            );

        }

        return sig.toLLVM();

    }

    public String llvmDeclarations() {

        StringBuilder sb =

            new StringBuilder();

        for (

            ExternSig sig :

            externSigs.values()

        ) {

            sb.append(

                sig.toLLVM()

            ).append('\n');

        }

        for (

            Map.Entry<String, ExternType> e :

            externVarTypes.entrySet()

        ) {

            String n =

                e.getKey();

            String ref =

                n.matches(

                    "[A-Za-z_.$][A-Za-z0-9_.$]*"

                )

                ? "@" + n

                : "@\"" + n + "\"";

            sb.append(ref)

              .append(" = external global ")

              .append(e.getValue().llvm)

              .append('\n');

        }

        return sb.toString();

    }

    public JavaElfOBJ addFileMetadata(

        Path ashSource

    ) {

        if (ashSource == null) {

            throw new IllegalArgumentException(

                "ashSource no puede ser null"

            );

        }

        Path absolute =

            ashSource

                .toAbsolutePath()

                .normalize();

        Path directory =

            absolute.getParent();

        String source =

            absolute

                .getFileName()

                .toString();

        String directoryName =

            directory != null

                ? directory.toString()

                : ".";

        addMetadataSymbol(source);

        addMetadataSymbol(

            absolute.toString()

        );

        addMetadataSymbol(

            directoryName

        );

        addMetadataSymbol(

            System.getProperty(

                "os.name",

                "unknown"

            )

        );

        addMetadataSymbol(

            System.getProperty(

                "os.version",

                "unknown"

            )

        );

        addMetadataSymbol(

            System.getProperty(

                "os.arch",

                "unknown"

            )

        );

        addMetadataSymbol(

            System.getProperty(

                "user.name",

                "unknown"

            )

        );

        addMetadataSymbol(

            System.getProperty(

                "user.home",

                "unknown"

            )

        );

        addMetadataSymbol(

            System.getProperty(

                "user.dir",

                "unknown"

            )

        );

        addMetadataSymbol(

            System.getProperty(

                "file.separator",

                "/"

            )

        );

        addMetadataSymbol(

            System.getProperty(

                "path.separator",

                ":"

            )

        );

        addMetadataSymbol(

            escapeMetadata(

                System.getProperty(

                    "line.separator",

                    "\n"

                )

            )

        );

        addMetadataSymbol("ASH       ");

        addMetadataSymbol(

            "ASH_ELF64_OBJECT"

        );

        commentStrings.add(

            "ASH ELF64 x86-64 object"

        );

        commentStrings.add(

            "source=" + absolute

        );

        addMetadataSymbol(

            "THE_ASHLEY_PROJECT"

        );

        return this;

    }

    private void addMetadataSymbol(

        String metadata

    ) {

        if (

            metadata == null ||

            metadata.isEmpty()

        ) {

            return;

        }

        fileSymbols.add(metadata);

    }

    private String escapeMetadata(

        String value

    ) {

        if (value == null) {

            return "";

        }

        return value

            .replace("\\\\", "\\\\\\\\")

            .replace("\r", "\\\r")

            .replace("\n", "\\\n")

            .replace("\t", "\\\t");

    }

    public JavaElfOBJ label(

        String name

    ) {

        requireCurrentSection();

        return labelAt(

            name,

            b.offset()

        );

    }

    public JavaElfOBJ labelAt(

        String name,

        int absoluteFileOffset

    ) {

        checkName(name);

        if (current == null) {

            throw new IllegalStateException(

                "No hay sección activa para el símbolo '" +

                name +

                "'"

            );

        }

        long rel;

        if (current.nobits()) {

            rel =

                current.bssSize;

        } else {

            if (

                current.rawDataStart < 0

            ) {

                current.rawDataStart =

                    absoluteFileOffset;

            }

            rel =

                (long) absoluteFileOffset -

                current.rawDataStart;

            if (rel < 0) {

                throw new IllegalStateException(

                    "Offset inválido para '" +

                    name +

                    "'"

                );

            }

        }

        Sym s =

            symbols.get(name);

        if (s == null) {

            s = new Sym(name);

            symbols.put(name, s);

        }

        s.value = rel;

        s.shndx =

            current.sectionIndex;

        s.type =

            current.exec()

                ? STT_NOTYPE

                : STT_OBJECT;

        s.bind =

            pendingGlobal.contains(name)

                ? STB_GLOBAL

                : pendingWeak.contains(name)

                    ? STB_WEAK

                    : STB_LOCAL;

        return this;

    }

    public JavaElfOBJ markFunction(

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

        s.type = STT_FUNC;

        s.bind = STB_GLOBAL;

        return this;

    }

    public JavaElfOBJ size(

        String name,

        long size

    ) {

        Sym s =

            symbols.get(name);

        if (s == null) {

            throw new IllegalArgumentException(

                "Símbolo no definido: " +

                name

            );

        }

        s.size = size;

        return this;

    }

    private static void checkName(

        String name

    ) {

        if (

            name == null ||

            name.isEmpty()

        ) {

            throw new IllegalArgumentException(

                "Nombre de símbolo inválido"

            );

        }

    }

    private JavaElfOBJ reloc(

        String symbolName,

        int type,

        long addend

    ) {

        requireFileSection();

        long off =

            (long) b.offset() -

            current.rawDataStart;

        current.relocs.add(

            new Reloc(

                off,

                symbolName,

                type,

                addend

            )

        );

        return this;

    }

    public JavaElfOBJ relocAt(

        String sectionName,

        long offsetInSection,

        String symbolName,

        int type,

        long addend

    ) {

        Section s =

            sectionByName.get(

                sectionName

            );

        if (s == null) {

            throw new IllegalArgumentException(

                "Sección no declarada: " +

                sectionName

            );

        }

        s.relocs.add(

            new Reloc(

                offsetInSection,

                symbolName,

                type,

                addend

            )

        );

        return this;

    }

    public JavaElfOBJ ripRel32(

        String symbolName

    ) {

        return ripRel32(

            symbolName,

            R_X86_64_PC32,

            -4

        );

    }

    public JavaElfOBJ ripRel32(

        String symbolName,

        int relocType,

        long addend

    ) {

        reloc(

            symbolName,

            relocType,

            addend

        );

        b.file_write_intLE(0);

        return this;

    }

    public JavaElfOBJ callExtern(

        String symbolName

    ) {

        requireFileSection();

        b.file_write(0xE8);

        return ripRel32(

            symbolName,

            R_X86_64_PLT32,

            -4

        );

    }

    public JavaElfOBJ jmpExtern(

        String symbolName

    ) {

        requireFileSection();

        b.file_write(0xE9);

        return ripRel32(

            symbolName,

            R_X86_64_PLT32,

            -4

        );

    }

    public JavaElfOBJ callGot(

        String symbolName

    ) {

        requireFileSection();

        b.file_write(0xFF);

        b.file_write(0x15);

        return ripRel32(

            symbolName,

            R_X86_64_GOTPCREL,

            -4

        );

    }

    public JavaElfOBJ callOrJmpRel32(

        String symbolName

    ) {

        return ripRel32(

            symbolName,

            R_X86_64_PLT32,

            -4

        );

    }

    public JavaElfOBJ abs32(

        String symbolName

    ) {

        return abs32(

            symbolName,

            0

        );

    }

    public JavaElfOBJ abs32(

        String symbolName,

        long addend

    ) {

        reloc(

            symbolName,

            R_X86_64_32,

            addend

        );

        b.file_write_intLE(0);

        return this;

    }

    public JavaElfOBJ abs32s(

        String symbolName,

        long addend

    ) {

        reloc(

            symbolName,

            R_X86_64_32S,

            addend

        );

        b.file_write_intLE(0);

        return this;

    }

    public JavaElfOBJ abs64(

        String symbolName

    ) {

        return abs64(

            symbolName,

            0

        );

    }

    public JavaElfOBJ abs64(

        String symbolName,

        long addend

    ) {

        reloc(

            symbolName,

            R_X86_64_64,

            addend

        );

        b.file_write(

            u64(0)

        );

        return this;

    }

    @Override

    public void defineFunctionSymbol(

        String name,

        int fileOffset,

        boolean exported

    ) {

        Section text =

            ensureBackendTextSection();

        if (

            fileOffset <

            text.rawDataStart

        ) {

            throw new IllegalStateException(

                "Offset de función inválido para '" +

                name +

                "'"

            );

        }

        if (exported) {

            global(name);

        }

        Sym s =

            symbols.get(name);

        if (s == null) {

            s = new Sym(name);

            symbols.put(name, s);

        }

        s.value =

            (long) fileOffset -

            text.rawDataStart;

        s.shndx =

            text.sectionIndex;

        s.type = STT_FUNC;

        s.bind =

            exported ||

            pendingGlobal.contains(name)

                ? STB_GLOBAL

                : pendingWeak.contains(name)

                    ? STB_WEAK

                    : STB_LOCAL;

        arch.Diagnostics.log(

            "[JavaElfOBJ] FUNCTION '" +

            name +

            "'" +

            " absolute=0x" +

            Integer.toHexString(

                fileOffset

            ) +

            " value=0x" +

            Long.toHexString(

                s.value

            ) +

            " shndx=" +

            s.shndx +

            " bind=" +

            s.bind

        );

    }

    public void defineDataSymbol(

        String name,

        int fileOffset,

        int size,

        boolean exported

    ) {

        checkName(name);

        if (size < 0) {

            throw new IllegalArgumentException(

                "Tamaño negativo para símbolo de datos '" +

                name +

                "': " +

                size

            );

        }

        Section target =

            findSectionContainingOffset(

                fileOffset

            );

        if (target == null) {

            Section text =

                sectionByName.get(".text");

            if (

                text != null &&

                text.rawDataStart >= 0 &&

                fileOffset >=

                    text.rawDataStart &&

                fileOffset <= b.offset()

            ) {

                target = text;

            }

        }

        if (target == null) {

            throw new IllegalStateException(

                "No se puede determinar la sección del objeto '" +

                name +

                "' en offset 0x" +

                Integer.toHexString(

                    fileOffset

                )

            );

        }

        if (exported) {

            global(name);

        }

        Sym s =

            symbols.get(name);

        if (s == null) {

            s = new Sym(name);

            symbols.put(name, s);

        }

        s.value =

            (long) fileOffset -

            target.rawDataStart;

        s.size = size;

        s.shndx =

            target.sectionIndex;

        s.type = STT_OBJECT;

        s.bind =

            exported ||

            pendingGlobal.contains(name)

                ? STB_GLOBAL

                : pendingWeak.contains(name)

                    ? STB_WEAK

                    : STB_LOCAL;

        arch.Diagnostics.log(

            "[JavaElfOBJ] OBJECT '" +

            name +

            "'" +

            " absolute=0x" +

            Integer.toHexString(

                fileOffset

            ) +

            " value=0x" +

            Long.toHexString(

                s.value

            ) +

            " size=" +

            s.size +

            " shndx=" +

            s.shndx +

            " bind=" +

            s.bind

        );

    }

    public void defineObjectSymbol(

        String name,

        int fileOffset,

        boolean exported

    ) {

        defineDataSymbol(

            name,

            fileOffset,

            0,

            exported

        );

    }

    public void defineObjectSymbol(

        String name,

        int fileOffset,

        int size,

        boolean exported

    ) {

        defineDataSymbol(

            name,

            fileOffset,

            size,

            exported

        );

    }

    private Section findSectionContainingOffset(

        int fileOffset

    ) {

        for (Section s : sections) {

            if (s.nobits()) {

                continue;

            }

            if (s.rawDataStart < 0) {

                continue;

            }

            int end;

            if (s.rawDataEnd >= 0) {

                end = s.rawDataEnd;

            } else {

                end = b.offset();

            }

            if (

                fileOffset >=

                    s.rawDataStart &&

                fileOffset <= end

            ) {

                return s;

            }

        }

        return null;

    }

    private Map<String, Integer> layoutUnits;

    private Map<String, Integer> symbolAddrs;

    private Set<String> codeUnits = new HashSet<>();

    public void setLayoutUnits(Map<String, Integer> starts, Map<String, Integer> addrs, Set<String> code) {
        this.layoutUnits = new HashMap<>(starts);
        this.symbolAddrs = new HashMap<>(addrs);
        this.codeUnits = new HashSet<>(code);
    }

    private boolean isCodeUnit(String name) {
        return name != null && codeUnits.contains(name);
    }

    private static final class Seg {
        int start, end;
        String sec;
        int newOff;
    }

    private static Seg segOf(List<Seg> segs, int abs) {
        int lo = 0, hi = segs.size() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            Seg g = segs.get(mid);
            if (abs < g.start) hi = mid - 1;
            else if (abs >= g.end) lo = mid + 1;
            else return g;
        }
        return null;
    }

    private void splitSections() {

        if (arch.Sections.isEmpty() || layoutUnits == null)
            return;

        Section text = sectionByName.get(".text");

        if (text == null || text.rawDataStart < 0)
            return;

        int ts = text.rawDataStart;
        int te = text.rawDataEnd >= 0 ? text.rawDataEnd : b.offset();

        byte[] all = b.toByteArray();

        TreeMap<Integer, String> starts = new TreeMap<>();
        for (Map.Entry<String, Integer> e : layoutUnits.entrySet()) {
            int at = e.getValue();
            if (at >= ts && at < te) {
                String prev = starts.get(at);
                if (prev == null || arch.Sections.of(prev) == null)
                    starts.put(at, e.getKey());
            }
        }

        boolean any = false;
        for (String n : starts.values())
            if (arch.Sections.of(n) != null) any = true;

        if (!any)
            return;

        List<Seg> segs = new ArrayList<>();
        int cursor = ts;
        for (Map.Entry<Integer, String> e : starts.entrySet()) {
            int at = e.getKey();
            if (at > cursor) {
                Seg g = new Seg(); g.start = cursor; g.end = at; g.sec = ".text"; segs.add(g);
            }
            Integer next = starts.higherKey(at);
            Seg g = new Seg();
            g.start = at;
            g.end = next == null ? te : next;
            arch.Sections.Placement pl = arch.Sections.of(e.getValue());
            g.sec = pl == null ? ".text" : pl.section;
            segs.add(g);
            cursor = g.end;
        }
        if (cursor < te) {
            Seg g = new Seg(); g.start = cursor; g.end = te; g.sec = ".text"; segs.add(g);
        }

        if (System.getenv("ASH_SECDEBUG") != null) {
            for (Seg g : segs) System.err.println("SEG " + Integer.toHexString(g.start) + "-" + Integer.toHexString(g.end) + " " + g.sec + " " + starts.get(g.start));
            for (Object[] f : b.symbolFixups()) System.err.println("FIX " + Integer.toHexString((Integer) f[0]) + " " + f[1] + " -> " + symbolAddrs.get(f[1]));
        }

        Map<String, ByteArrayOutputStream> out = new LinkedHashMap<>();
        out.put(".text", new ByteArrayOutputStream());
        Map<String, Boolean> secExec = new HashMap<>();

        for (Seg g : segs) {
            ByteArrayOutputStream o = out.computeIfAbsent(g.sec, k -> new ByteArrayOutputStream());
            int want = (g.start - ts) & 15;
            boolean code = isCodeUnit(starts.get(g.start));
            while ((o.size() & 15) != want)
                o.write(code ? 0xCC : 0);
            g.newOff = o.size();
            o.write(all, g.start, g.end - g.start);
            if (!g.sec.equals(".text"))
                secExec.merge(g.sec, code, Boolean::logicalOr);
        }

        Map<String, byte[]> bytes = new LinkedHashMap<>();
        for (Map.Entry<String, ByteArrayOutputStream> e : out.entrySet())
            bytes.put(e.getKey(), e.getValue().toByteArray());

        for (String name : bytes.keySet()) {
            if (name.equals(".text") || sectionByName.containsKey(name))
                continue;
            boolean code = secExec.getOrDefault(name, false);
            arch.Sections.Placement pl = null;
            for (Map.Entry<String, arch.Sections.Placement> e : arch.Sections.all().entrySet())
                if (e.getValue().section.equals(name)) { pl = e.getValue(); break; }
            long flags = SHF_ALLOC;
            if (pl == null ? !code : pl.write(code)) flags |= SHF_WRITE;
            if (pl == null ? code : pl.exec(code)) flags |= SHF_EXECINSTR;
            Section ns = new Section(name, SHT_PROGBITS, flags, 16);
            ns.sectionIndex = sections.size() + 1;
            sections.add(ns);
            sectionByName.put(name, ns);
        }

        List<Reloc> old = new ArrayList<>(text.relocs);
        text.relocs.clear();
        for (Reloc r : old) {
            int abs = ts + (int) r.offsetInSection;
            Seg g = segOf(segs, abs);
            if (g == null) { text.relocs.add(r); continue; }
            sectionByName.get(g.sec).relocs.add(new Reloc(
                g.newOff + (abs - g.start), r.symbolName, r.type, r.addend));
        }

        for (Object[] f : b.symbolFixups()) {
            int pos = (Integer) f[0];
            String sym = (String) f[1];
            Integer target = symbolAddrs.get(sym);
            Seg sg = segOf(segs, pos);
            if (target == null || sg == null)
                continue;
            Seg tg = segOf(segs, target);
            if (tg == null)
                continue;
            int sOff = sg.newOff + (pos - sg.start);
            int tOff = tg.newOff + (target - tg.start);
            byte[] dst = bytes.get(sg.sec);
            int v;
            if (sg.sec.equals(tg.sec)) {
                v = tOff - (sOff + 4);
            } else {
                v = 0;
                sectionByName.get(sg.sec).relocs.add(new Reloc(
                    sOff, sym, isCodeUnit(sym) ? R_X86_64_PLT32 : R_X86_64_PC32, -4));
            }
            dst[sOff] = (byte) v;
            dst[sOff + 1] = (byte) (v >> 8);
            dst[sOff + 2] = (byte) (v >> 16);
            dst[sOff + 3] = (byte) (v >> 24);
        }

        for (Sym sy : symbols.values()) {
            if (sy.shndx != text.sectionIndex)
                continue;
            int abs = ts + (int) sy.value;
            Seg g = segOf(segs, abs);
            if (g == null)
                continue;
            sy.value = g.newOff + (abs - g.start);
            sy.shndx = sectionByName.get(g.sec).sectionIndex;
            if (!isCodeUnit(sy.name) && layoutUnits.containsKey(sy.name))
                sy.type = STT_OBJECT;
        }

        b.truncate(ts);
        b.file_write(bytes.get(".text"));
        text.rawDataEnd = b.offset();

        for (Map.Entry<String, byte[]> e : bytes.entrySet()) {
            if (e.getKey().equals(".text"))
                continue;
            while ((b.offset() & 15) != 0)
                b.file_write(0);
            Section sc = sectionByName.get(e.getKey());
            sc.rawDataStart = b.offset();
            b.file_write(e.getValue());
            sc.rawDataEnd = b.offset();
        }

        current = text;
    }

    public String end(

        String outputDir,

        String filename

    ) {

        splitSections();

        endBaseOffset = b.offset();
        endBuffer = new ByteArrayOutputStream(512);

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

                expectedTextStart();

            if (current == null) {

                current = text;

            }

        }

        closeCurrentSection();

        for (Section s : sections) {

            if (s.nobits()) {

                continue;

            }

            if (s.rawDataStart == -1) {

                s.rawDataStart = 0;

                s.rawDataEnd = 0;

            } else if (

                s.rawDataEnd == -1

            ) {

                s.rawDataEnd =

                    s.rawDataStart;

            }

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

        for (Section s : sections) {

            List<Sym> funcs =

                new ArrayList<>();

            for (

                Sym sym :

                symbols.values()

            ) {

                if (

                    sym.type == STT_FUNC &&

                    sym.shndx ==

                        s.sectionIndex

                ) {

                    funcs.add(sym);

                }

            }

            funcs.sort(

                Comparator.comparingLong(

                    x -> x.value

                )

            );

            for (

                int i = 0;

                i < funcs.size();

                i++

            ) {

                Sym f = funcs.get(i);

                if (f.size != 0) {

                    continue;

                }

                long next =

                    (

                        i + 1 <

                        funcs.size()

                    )

                    ? funcs

                        .get(i + 1)

                        .value

                    : s.size();

                f.size =

                    Math.max(

                        0,

                        next - f.value

                    );

            }

        }

        List<Sym> locals =

            new ArrayList<>();

        List<Sym> globals =

            new ArrayList<>();

        for (String f : fileSymbols) {

            Sym fs =

                new Sym(f);

            fs.bind = STB_LOCAL;

            fs.type = STT_FILE;

            fs.shndx = SHN_ABS;

            locals.add(fs);

        }

        for (

            Sym s :

            symbols.values()

        ) {

            if (s.bind == STB_LOCAL) {

                locals.add(s);

            } else {

                globals.add(s);

            }

        }

        int idx = 1;

        for (Sym s : locals) {

            s.index = idx++;

        }

        int firstGlobal = idx;

        for (Sym s : globals) {

            s.index = idx++;

        }

        int totalSymbols = idx;

        int n = sections.size();

        int gnuStackIdx = n + 1;

        int commentIdx = n + 2;

        List<Section> relaSections =

            new ArrayList<>();

        for (Section s : sections) {

            if (!s.relocs.isEmpty()) {

                relaSections.add(s);

            }

        }

        int firstRelaIdx = n + 3;

        int symtabIdx =

            firstRelaIdx +

            relaSections.size();

        int strtabIdx =

            symtabIdx + 1;

        int shstrtabIdx =

            strtabIdx + 1;

        int totalHeaders =

            shstrtabIdx + 1;

        ByteArrayOutputStream shstr =

            new ByteArrayOutputStream();

        shstr.write(0);

        Map<String, Integer> shMap =

            new HashMap<>();

        int[] secNameOff =

            new int[n];

        for (int i = 0; i < n; i++) {

            secNameOff[i] =

                addStr(

                    shstr,

                    shMap,

                    outName(

                        sections

                            .get(i)

                            .name

                    )

                );

        }

        int gnuStackName =

            addStr(

                shstr,

                shMap,

                ".note.GNU-stack"

            );

        int commentName =

            addStr(

                shstr,

                shMap,

                ".comment"

            );

        int[] relaNameOff =

            new int[

                relaSections.size()

            ];

        for (

            int i = 0;

            i < relaSections.size();

            i++

        ) {

            relaNameOff[i] =

                addStr(

                    shstr,

                    shMap,

                    ".rela" +

                    outName(

                        relaSections

                            .get(i)

                            .name

                    )

                );

        }

        int symtabName =

            addStr(

                shstr,

                shMap,

                ".symtab"

            );

        int strtabName =

            addStr(

                shstr,

                shMap,

                ".strtab"

            );

        int shstrtabName =

            addStr(

                shstr,

                shMap,

                ".shstrtab"

            );

        ByteArrayOutputStream strtab =

            new ByteArrayOutputStream();

        strtab.write(0);

        Map<String, Integer> strMap =

            new HashMap<>();

        for (Sym s : locals) {

            addStr(

                strtab,

                strMap,

                s.name

            );

        }

        for (Sym s : globals) {

            addStr(

                strtab,

                strMap,

                s.name

            );

        }

        List<byte[]> headers =

            new ArrayList<>(

                totalHeaders

            );

        headers.add(

            new byte[SHDR_SIZE]

        );

        for (int i = 0; i < n; i++) {

            Section s =

                sections.get(i);

            long size =

                s.size();

            long off =

                (

                    s.nobits() ||

                    size == 0

                )

                ? 0

                : s.rawDataStart;

            headers.add(

                shdr(

                    secNameOff[i],

                    s.type,

                    (WRITABLE_TEXT && ".text".equals(s.name))
                        ? (s.flags | SHF_WRITE)
                        : s.flags,

                    off,

                    size,

                    0,

                    0,

                    s.align,

                    0

                )

            );

        }

        padEnd(8);

        headers.add(

            shdr(

                gnuStackName,

                SHT_PROGBITS,

                0,

                endOffset(),

                0,

                0,

                0,

                1,

                0

            )

        );

        ByteArrayOutputStream comment =

            new ByteArrayOutputStream();

        for (

            String c :

            commentStrings

        ) {

            byte[] raw =

                c.getBytes(

                    StandardCharsets.UTF_8

                );

            comment.write(

                raw,

                0,

                raw.length

            );

            comment.write(0);

        }

        if (comment.size() == 0) {

            byte[] raw =

                "ASH ELF64 x86-64 object"

                    .getBytes(

                        StandardCharsets.UTF_8

                    );

            comment.write(

                raw,

                0,

                raw.length

            );

            comment.write(0);

        }

        int commentOff =

            endOffset();

        endWrite(

            comment.toByteArray()

        );

        headers.add(

            shdr(

                commentName,

                SHT_PROGBITS,

                SHF_MERGE |

                SHF_STRINGS,

                commentOff,

                comment.size(),

                0,

                0,

                1,

                1

            )

        );

        for (

            int i = 0;

            i < relaSections.size();

            i++

        ) {

            Section s =

                relaSections.get(i);

            padEnd(8);

            int relaOff =

                endOffset();

            byte[] rela = new byte[RELA_SIZE];

            for (

                Reloc r :

                s.relocs

            ) {

                Sym target =

                    symbols.get(

                        r.symbolName

                    );

                putLongLE(rela, 0, r.offsetInSection);
                putLongLE(
                    rela,
                    8,
                    ((long) target.index << 32) |
                    (r.type & 0xFFFFFFFFL)
                );
                putLongLE(rela, 16, r.addend);

                endWrite(rela);

            }

            headers.add(

                shdr(

                    relaNameOff[i],

                    SHT_RELA,

                    SHF_INFO_LINK,

                    relaOff,

                    (long)

                        s.relocs.size()

                        * RELA_SIZE,

                    symtabIdx,

                    s.sectionIndex,

                    8,

                    RELA_SIZE

                )

            );

        }

        padEnd(8);

        int symtabOff =

            endOffset();

        endWrite(

            new byte[SYM_SIZE]

        );

        for (Sym s : locals) {

            writeSymbol(

                s,

                strMap

            );

        }

        for (Sym s : globals) {

            writeSymbol(

                s,

                strMap

            );

        }

        headers.add(

            shdr(

                symtabName,

                SHT_SYMTAB,

                0,

                symtabOff,

                (long)

                    totalSymbols *

                    SYM_SIZE,

                strtabIdx,

                firstGlobal,

                8,

                SYM_SIZE

            )

        );

        int strtabOff =

            endOffset();

        endWrite(

            strtab.toByteArray()

        );

        headers.add(

            shdr(

                strtabName,

                SHT_STRTAB,

                0,

                strtabOff,

                strtab.size(),

                0,

                0,

                1,

                0

            )

        );

        int shstrtabOff =

            endOffset();

        endWrite(

            shstr.toByteArray()

        );

        headers.add(

            shdr(

                shstrtabName,

                SHT_STRTAB,

                0,

                shstrtabOff,

                shstr.size(),

                0,

                0,

                1,

                0

            )

        );

        padEnd(8);

        int shoff =

            endOffset();

        if (

            headers.size() !=

            totalHeaders

        ) {

            throw new IllegalStateException(

                "Inconsistencia interna: " +

                headers.size() +

                " headers, esperados " +

                totalHeaders

            );

        }

        for (byte[] h : headers) {
            endWrite(h);
        }

        byte[] metadata = endBuffer.toByteArray();
        b.file_write(metadata);
        endBuffer = null;

        b.patchBytes(

            fileHeaderPos,

            buildElfHeader(

                shoff,

                totalHeaders,

                shstrtabIdx

            )

        );

        arch.Diagnostics.log(

            "JavaElfOBJ.end(): " +

            sections.size() +

            " secciones, " +

            totalSymbols +

            " símbolos, symtab@" +

            symtabOff +

            ", shoff@" +

            shoff

        );

        return b.BUILD(

            outputDir,

            filename

        );

    }

    private int endOffset() {
        return endBaseOffset + endBuffer.size();
    }

    private void endWrite(byte[] data) {
        if (data == null || data.length == 0) {
            return;
        }
        endBuffer.write(data, 0, data.length);
    }

    private void padEnd(int alignment) {
        int current = endOffset();
        int aligned = alignUp(current, alignment);
        int count = aligned - current;

        while (count > 0) {
            int n = Math.min(count, ZERO_PAD.length);
            endBuffer.write(ZERO_PAD, 0, n);
            count -= n;
        }
    }

    private static final byte[] ZERO_PAD = new byte[64];

    private static void putIntLE(byte[] data, int off, int value) {
        data[off] = (byte) value;
        data[off + 1] = (byte) (value >>> 8);
        data[off + 2] = (byte) (value >>> 16);
        data[off + 3] = (byte) (value >>> 24);
    }

    private static void putShortLE(byte[] data, int off, int value) {
        data[off] = (byte) value;
        data[off + 1] = (byte) (value >>> 8);
    }

    private static void putLongLE(byte[] data, int off, long value) {
        data[off] = (byte) value;
        data[off + 1] = (byte) (value >>> 8);
        data[off + 2] = (byte) (value >>> 16);
        data[off + 3] = (byte) (value >>> 24);
        data[off + 4] = (byte) (value >>> 32);
        data[off + 5] = (byte) (value >>> 40);
        data[off + 6] = (byte) (value >>> 48);
        data[off + 7] = (byte) (value >>> 56);
    }

    private byte[] buildElfHeader(

        int shoff,

        int shnum,

        int shstrndx

    ) {

        ByteBuffer bb =

            ByteBuffer

                .allocate(EHDR_SIZE)

                .order(

                    ByteOrder.LITTLE_ENDIAN

                );

        bb.put((byte) 0x7F);

        bb.put((byte) 'E');

        bb.put((byte) 'L');

        bb.put((byte) 'F');

        bb.put((byte) 2);

        bb.put((byte) 1);

        bb.put((byte) 1);

        bb.put((byte) 0);

        bb.put(new byte[8]);

        bb.putShort(

            (short) ET_REL

        );

        bb.putShort(

            (short) EM_X86_64

        );

        bb.putInt(1);

        bb.putLong(0);

        bb.putLong(0);

        bb.putLong(shoff);

        bb.putInt(0);

        bb.putShort(

            (short) EHDR_SIZE

        );

        bb.putShort((short) 0);

        bb.putShort((short) 0);

        bb.putShort(

            (short) SHDR_SIZE

        );

        bb.putShort(

            (short) shnum

        );

        bb.putShort(

            (short) shstrndx

        );

        return bb.array();

    }

    private void writeSymbol(
        Sym s,
        Map<String, Integer> strMap
    ) {
        byte[] data = symbolScratch;
        Integer nameOff = strMap.get(s.name);

        putIntLE(
            data,
            0,
            nameOff == null ? 0 : nameOff
        );

        data[4] = (byte) (
            ((s.bind & 0xF) << 4) |
            (s.type & 0xF)
        );
        data[5] = 0;
        putShortLE(data, 6, s.shndx);
        putLongLE(data, 8, s.value);
        putLongLE(data, 16, s.size);

        endWrite(data);
    }

    private static byte[] shdr(

        int name,

        int type,

        long flags,

        long offset,

        long size,

        int link,

        int info,

        long align,

        long entsize

    ) {

        ByteBuffer bb =

            ByteBuffer

                .allocate(SHDR_SIZE)

                .order(

                    ByteOrder.LITTLE_ENDIAN

                );

        bb.putInt(name);

        bb.putInt(type);

        bb.putLong(flags);

        bb.putLong(0);

        bb.putLong(offset);

        bb.putLong(size);

        bb.putInt(link);

        bb.putInt(info);

        bb.putLong(align);

        bb.putLong(entsize);

        return bb.array();

    }

    private static int addStr(

        ByteArrayOutputStream bo,

        Map<String, Integer> map,

        String s

    ) {

        if (s.isEmpty()) {

            return 0;

        }

        Integer o =

            map.get(s);

        if (o != null) {

            return o;

        }

        int off =

            bo.size();

        byte[] raw =

            s.getBytes(

                StandardCharsets.UTF_8

            );

        bo.write(

            raw,

            0,

            raw.length

        );

        bo.write(0);

        map.put(

            s,

            off

        );

        return off;

    }

    private void pad(

        int alignment

    ) {

        int aligned =

            alignUp(

                b.offset(),

                alignment

            );

        if (aligned > b.offset()) {

            b.fill_with_until(

                (byte) 0x00,

                aligned

            );

        }

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

                        (v >>

                            (8 * i)

                        ) &

                        0xFF

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
