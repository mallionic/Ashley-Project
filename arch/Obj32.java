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

package arch;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class Obj32 {

    private Obj32() {
    }

    public static final class Sym {
        public final String name;
        public final int offset;
        public final boolean global;

        public Sym(String name, int offset, boolean global) {
            this.name = name;
            this.offset = offset;
            this.global = global;
        }
    }

    public static final class Reloc {
        public final int pos;
        public final int kind;
        public final String name;

        public Reloc(int pos, int kind, String name) {
            this.pos = pos;
            this.kind = kind;
            this.name = name;
        }
    }

    public static byte[] elf32(byte[] text, List<Sym> syms, List<Reloc> relocs) {
        return elf32(text, syms, relocs, 0, 0);
    }

    public static byte[] elf32(byte[] text, List<Sym> syms, List<Reloc> relocs,
                               int dataSize, int bssSize) {

        boolean extra = dataSize > 0 && bssSize > 0;

        List<Sym> locals = new ArrayList<>();
        List<Sym> globals = new ArrayList<>();

        for (Sym s : syms)
            (s.global || s.offset < 0 ? globals : locals).add(s);

        List<Sym> ordered = new ArrayList<>();
        ordered.addAll(locals);
        ordered.addAll(globals);

        int SEC_TEXT = 1, SEC_DATA = extra ? 2 : 0, SEC_BSS = extra ? 3 : 0;
        int iSym = extra ? 4 : 2, iStr = iSym + 1, iShs = iSym + 2;
        int iRelText = iSym + 3, iRelData = iSym + 4;
        int nSections = extra ? 9 : 6;

        int nSecSyms = extra ? 3 : 1;

        Map<String, Integer> symIndex = new HashMap<>();
        ByteArrayOutputStream strtab = new ByteArrayOutputStream();
        strtab.write(0);

        ByteBuffer symtab = ByteBuffer.allocate((1 + nSecSyms + ordered.size()) * 16)
            .order(ByteOrder.LITTLE_ENDIAN);

        symtab.put(new byte[16]);

        for (int sec : new int[]{SEC_TEXT, SEC_DATA, SEC_BSS}) {
            if (sec == 0) continue;
            symtab.putInt(0).putInt(0).putInt(0)
                  .put((byte) 3).put((byte) 0).putShort((short) sec);
        }

        int idx = 1 + nSecSyms;

        for (Sym s : ordered) {
            int nameOff = strtab.size();
            byte[] nb = s.name.getBytes(StandardCharsets.UTF_8);
            strtab.write(nb, 0, nb.length);
            strtab.write(0);

            boolean fn = s.offset >= 0;
            symtab.putInt(nameOff)
                  .putInt(Math.max(s.offset, 0))
                  .putInt(0)
                  .put((byte) (((s.global || !fn ? 1 : 0) << 4) | (fn ? 2 : 0)))
                  .put((byte) 0)
                  .putShort((short) (fn ? 1 : 0));
            symIndex.put(s.name, idx++);
        }

        int nText = 0;
        for (Reloc r : relocs) if (r.kind != 3) nText++;

        ByteBuffer relT = ByteBuffer.allocate(nText * 8).order(ByteOrder.LITTLE_ENDIAN);

        for (Reloc r : relocs) {
            if (r.kind == 0)
                relT.putInt(r.pos).putInt((1 << 8) | 1);
            else if (r.kind == 2)
                relT.putInt(r.pos).putInt((2 << 8) | 1);
            else if (r.kind == 1)
                relT.putInt(r.pos).putInt((symIndex.get(r.name) << 8) | 4);
        }

        ByteBuffer relD = ByteBuffer.allocate(extra ? 8 : 0).order(ByteOrder.LITTLE_ENDIAN);

        if (extra)
            relD.putInt(0).putInt((3 << 8) | 1);

        byte[] shstr = (extra
            ? "\0.text\0.data\0.bss\0.symtab\0.strtab\0.shstrtab\0.rel.text\0.rel.data\0"
            : "\0.text\0.symtab\0.strtab\0.shstrtab\0.rel.text\0")
            .getBytes(StandardCharsets.UTF_8);

        int off = 52;
        int textOff = off; off += text.length; off = (off + 3) & ~3;
        int dataOff = off; off += extra ? dataSize : 0; off = (off + 3) & ~3;
        int symOff = off; off += symtab.capacity();
        int strOff = off; off += strtab.size();
        int shsOff = off; off += shstr.length; off = (off + 3) & ~3;
        int relTOff = off; off += relT.capacity(); off = (off + 3) & ~3;
        int relDOff = off; off += relD.capacity(); off = (off + 3) & ~3;
        int shOff = off;

        ByteBuffer o = ByteBuffer.allocate(shOff + nSections * 40).order(ByteOrder.LITTLE_ENDIAN);

        o.put(new byte[]{0x7F, 'E', 'L', 'F', 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0});
        o.putShort((short) 1).putShort((short) 3).putInt(1).putInt(0).putInt(0).putInt(shOff)
         .putInt(0).putShort((short) 52).putShort((short) 0).putShort((short) 0)
         .putShort((short) 40).putShort((short) nSections).putShort((short) iShs);

        o.position(textOff); o.put(text);
        o.position(symOff); o.put(symtab.array());
        o.position(strOff); o.put(strtab.toByteArray());
        o.position(shsOff); o.put(shstr);
        o.position(relTOff); o.put(relT.array());
        o.position(relDOff); o.put(relD.array());

        o.position(shOff);
        o.put(new byte[40]);

        String names = new String(shstr, StandardCharsets.UTF_8);
        int nText0 = names.indexOf(".text\0");

        sh(o, nText0, 1, 6, textOff, text.length, 0, 0, 16, 0);

        if (extra) {
            sh(o, names.indexOf(".data\0"), 1, 3, dataOff, dataSize, 0, 0, 4, 0);
            sh(o, names.indexOf(".bss\0"), 8, 3, dataOff + dataSize, bssSize, 0, 0, 16, 0);
        }

        int firstGlobal = 1 + nSecSyms + locals.size();

        sh(o, names.indexOf(".symtab\0"), 2, 0, symOff, symtab.capacity(), iStr, firstGlobal, 4, 16);
        sh(o, names.indexOf(".strtab\0"), 3, 0, strOff, strtab.size(), 0, 0, 1, 0);
        sh(o, names.indexOf(".shstrtab\0"), 3, 0, shsOff, shstr.length, 0, 0, 1, 0);
        sh(o, names.indexOf(".rel.text\0"), 9, 0, relTOff, relT.capacity(), iSym, SEC_TEXT, 4, 8);

        if (extra)
            sh(o, names.indexOf(".rel.data\0"), 9, 0, relDOff, relD.capacity(), iSym, SEC_DATA, 4, 8);

        return o.array();
    }

    private static void sh(ByteBuffer o, int name, int type, int flags, int off,
                           int size, int link, int info, int align, int entsize) {
        o.putInt(name).putInt(type).putInt(flags).putInt(0).putInt(off)
         .putInt(size).putInt(link).putInt(info).putInt(align).putInt(entsize);
    }

    public static byte[] coff32(byte[] text, List<Sym> syms, List<Reloc> relocs) {

        List<String> names = new ArrayList<>();
        ByteArrayOutputStream strs = new ByteArrayOutputStream();

        List<byte[]> symRecs = new ArrayList<>();
        Map<String, Integer> index = new HashMap<>();

        symRecs.add(coffSym(".text", 0, 1, 3, strs));

        int i = 1;

        for (Sym s : syms) {
            String n = "_" + s.name;
            boolean fn = s.offset >= 0;
            symRecs.add(coffSym(n, Math.max(s.offset, 0), fn ? 1 : 0,
                fn && !s.global ? 3 : 2, strs, fn ? 0x20 : 0));
            index.put(s.name, i++);
        }

        ByteBuffer rel = ByteBuffer.allocate(relocs.size() * 10).order(ByteOrder.LITTLE_ENDIAN);

        for (Reloc r : relocs) {
            if (r.kind == 0)
                rel.putInt(r.pos).putInt(0).putShort((short) 6);
            else
                rel.putInt(r.pos).putInt(index.get(r.name)).putShort((short) 0x14);
        }

        int textOff = 20 + 40;
        int relOff = textOff + text.length;
        int symOff = relOff + rel.capacity();

        ByteBuffer o = ByteBuffer.allocate(symOff + symRecs.size() * 18 + 4 + strs.size())
            .order(ByteOrder.LITTLE_ENDIAN);

        o.putShort((short) 0x14C).putShort((short) 1).putInt(0)
         .putInt(symOff).putInt(symRecs.size()).putShort((short) 0).putShort((short) 0);

        byte[] nm = new byte[8];
        System.arraycopy(".text".getBytes(StandardCharsets.UTF_8), 0, nm, 0, 5);

        o.put(nm).putInt(0).putInt(0).putInt(text.length).putInt(textOff).putInt(relOff)
         .putInt(0).putShort((short) relocs.size()).putShort((short) 0)
         .putInt(0xE0500020);

        o.put(text);
        o.put(rel.array());

        for (byte[] r : symRecs)
            o.put(r);

        o.putInt(4 + strs.size());
        o.put(strs.toByteArray());

        return o.array();
    }

    private static byte[] coffSym(String name, int value, int section, int cls,
                                  ByteArrayOutputStream strs) {
        return coffSym(name, value, section, cls, strs, 0);
    }

    private static byte[] coffSym(String name, int value, int section, int cls,
                                  ByteArrayOutputStream strs, int type) {

        ByteBuffer b = ByteBuffer.allocate(18).order(ByteOrder.LITTLE_ENDIAN);
        byte[] nb = name.getBytes(StandardCharsets.UTF_8);

        if (nb.length <= 8) {
            byte[] f = new byte[8];
            System.arraycopy(nb, 0, f, 0, nb.length);
            b.put(f);
        } else {
            b.putInt(0).putInt(4 + strs.size());
            strs.write(nb, 0, nb.length);
            strs.write(0);
        }

        b.putInt(value).putShort((short) section).putShort((short) type)
         .put((byte) cls).put((byte) 0);

        return b.array();
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
