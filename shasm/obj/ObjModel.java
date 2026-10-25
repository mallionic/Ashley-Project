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

package shasm.obj;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

public final class ObjModel {

    public enum Fmt {
        ELF32(false, false, "elf32"),
        ELF64(true,  false, "elf64"),
        WIN32(false, true,  "win32"),
        WIN64(true,  true,  "win64");

        public final boolean is64;
        public final boolean coff;
        public final String id;

        Fmt(boolean is64, boolean coff, String id) {
            this.is64 = is64;
            this.coff = coff;
            this.id = id;
        }

        public boolean elf() {
            return !coff;
        }

        public String ext() {
            return coff ? ".obj" : ".o";
        }
    }

    public enum Kind {
        ABS,
        PCREL,
        PLT,
        GOTPCREL,
        GOT,
        GOTOFF,
        GOTPC,
        IMAGEBASE
    }

    public static final class Reloc {
        public String sec;
        public long offset;
        public int size;
        public Kind kind = Kind.ABS;
        public boolean signed;
        public String ext;
        public String tsec;
        public long addend;
        public int trail;
    }

    public static final class Section {
        public String name;
        public byte[] data = new byte[0];
        public long size;
        public boolean nobits;
        public int align = 1;

        public boolean alloc = true;
        public boolean exec;
        public boolean write;
        public boolean tls;
        public boolean info;
        public boolean read = true;
        public boolean merge;
        public boolean strings;

        public int entsize;

        public final List<Reloc> relocs = new ArrayList<>();
    }

    public enum Bind {
        LOCAL,
        GLOBAL,
        WEAK
    }

    public enum Type {
        NOTYPE,
        OBJECT,
        FUNC
    }

    public enum Where {
        DEFINED,
        UNDEF,
        ABS,
        COMMON
    }

    public static final class Symbol {
        public String name;
        public String key;

        public Where where = Where.DEFINED;

        public String sec;
        public long value;
        public long size;
        public long align;

        public Bind bind = Bind.GLOBAL;
        public Type type = Type.NOTYPE;

        public int vis;
    }

    public Fmt fmt;
    public String fileName = "";

    public final List<Section> sections = new ArrayList<>();
    public final List<Symbol> symbols = new ArrayList<>();

    public ObjModel(Fmt fmt) {
        this.fmt = fmt;
    }

    public Section section(String name) {
        for (Section s : sections) {
            if (s.name.equals(name))
                return s;
        }
        return null;
    }

    public static void le(ByteArrayOutputStream out, long value, int size) {
        for (int i = 0; i < size; i++)
            out.write((int)(value >>> (i * 8)) & 0xff);
    }

    public static void patch(byte[] data, long offset, long value, int size) {
        int p = (int)offset;

        for (int i = 0; i < size; i++)
            data[p + i] = (byte)(value >>> (i * 8));
    }

    public static int log2(int x) {
        int n = 0;

        while ((1 << n) < x)
            n++;

        return n;
    }

    public static long alignUp(long value, long alignment) {
        if (alignment <= 1)
            return value;

        return ((value + alignment - 1) / alignment) * alignment;
    }

    public static void pad(ByteArrayOutputStream out, long target) {
        while (out.size() < target)
            out.write(0);
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
