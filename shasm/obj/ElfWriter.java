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
import java.nio.charset.StandardCharsets;
import java.util.*;

import shasm.AsmError;
import shasm.i18n.I18n;
import shasm.obj.ObjModel.*;

public final class ElfWriter {

    private ElfWriter() {}

    static final int SHT_PROGBITS = 1;
    static final int SHT_SYMTAB   = 2;
    static final int SHT_STRTAB   = 3;
    static final int SHT_RELA     = 4;
    static final int SHT_NOBITS   = 8;
    static final int SHT_REL      = 9;

    static final int SHF_WRITE = 1;
    static final int SHF_ALLOC = 2;
    static final int SHF_EXEC  = 4;
    static final int SHF_MERGE = 0x10;
    static final int SHF_STRINGS = 0x20;
    static final int SHF_INFO_LINK = 0x40;
    static final int SHF_TLS = 0x400;

    private static final class StrTab {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final Map<String,Integer> offsets = new HashMap<>();

        StrTab() {
            out.write(0);
        }

        int add(String s) {
            if (s == null || s.isEmpty())
                return 0;

            Integer old = offsets.get(s);
            if (old != null)
                return old;

            int p = out.size();

            byte[] b = s.getBytes(StandardCharsets.UTF_8);
            out.write(b, 0, b.length);
            out.write(0);

            offsets.put(s, p);
            return p;
        }

        byte[] bytes() {
            return out.toByteArray();
        }
    }

    private static final class Sym {
        int name;
        long value;
        long size;
        int info;
        int other;
        int shndx;
    }

    public static byte[] write(ObjModel m) {
        boolean is64 = m.fmt.is64;

        int userSections = m.sections.size();

        Map<String,Integer> secIndex = new LinkedHashMap<>();

        for (int i = 0; i < userSections; i++)
            secIndex.put(m.sections.get(i).name, i + 1);

        List<Section> relocSections = new ArrayList<>();

        for (Section s : m.sections) {
            if (!s.relocs.isEmpty())
                relocSections.add(s);
        }

        int shstrIndex = userSections + 1;
        int symtabIndex = userSections + 2;
        int strtabIndex = userSections + 3;

        StrTab strtab = new StrTab();
        StrTab shstrtab = new StrTab();

        int[] sectionName = new int[userSections];

        for (int i = 0; i < userSections; i++)
            sectionName[i] = shstrtab.add(m.sections.get(i).name);

        int shstrName = shstrtab.add(".shstrtab");
        int symtabName = shstrtab.add(".symtab");
        int strtabName = shstrtab.add(".strtab");

        Map<Section,Integer> relocName = new HashMap<>();

        for (Section s : relocSections) {
            relocName.put(
                s,
                shstrtab.add((is64 ? ".rela" : ".rel") + s.name)
            );
        }

        List<Sym> symbols = new ArrayList<>();

        symbols.add(new Sym());

        if (m.fileName != null && !m.fileName.isEmpty()) {
            Sym file = new Sym();

            file.name = strtab.add(m.fileName);
            file.info = (0 << 4) | 4;
            file.shndx = 0xfff1;

            symbols.add(file);
        }

        Map<String,Integer> sectionSymbols = new HashMap<>();

        for (Section s : m.sections) {
            Sym x = new Sym();

            x.info = 3;
            x.shndx = secIndex.get(s.name);

            sectionSymbols.put(s.name, symbols.size());
            symbols.add(x);
        }

        Map<String,Integer> symbolIndex = new HashMap<>();

        for (Symbol s : m.symbols) {
            if (s.bind != Bind.LOCAL)
                continue;

            symbolIndex.put(s.key, symbols.size());
            symbols.add(makeSymbol(s, strtab, secIndex));
        }

        int firstGlobal = symbols.size();

        for (Symbol s : m.symbols) {
            if (s.bind == Bind.LOCAL)
                continue;

            symbolIndex.put(s.key, symbols.size());
            symbols.add(makeSymbol(s, strtab, secIndex));
        }

        byte[][] sectionData = new byte[userSections][];

        for (int i = 0; i < userSections; i++) {
            Section s = m.sections.get(i);

            if (s.nobits)
                sectionData[i] = new byte[0];
            else
                sectionData[i] =
                    Arrays.copyOf(s.data, (int)s.size);
        }

        Map<Section,byte[]> relocationData = new LinkedHashMap<>();

        for (Section s : relocSections) {
            ByteArrayOutputStream ro = new ByteArrayOutputStream();

            for (Reloc r : s.relocs) {
                int symbol;

                if (r.ext != null) {
                    Integer x = symbolIndex.get(r.ext);

                    if (x == null)
                        throw new AsmError(
                            I18n.tr(
                                "simbolo de reubicacion desconocido: %s",
                                r.ext
                            )
                        );

                    symbol = x;
                } else {
                    Integer x = sectionSymbols.get(r.tsec);

                    if (x == null)
                        throw new AsmError(
                            I18n.tr(
                                "seccion de reubicacion desconocida: %s",
                                r.tsec
                            )
                        );

                    symbol = x;
                }

                int type = relocationType(m.fmt, r);

                if (is64) {
                    ObjModel.le(ro, r.offset, 8);

                    long info =
                        ((long)symbol << 32) |
                        (type & 0xffffffffL);

                    ObjModel.le(ro, info, 8);
                    ObjModel.le(ro, r.addend, 8);
                } else {
                    ObjModel.le(ro, r.offset, 4);

                    long info =
                        ((long)symbol << 8) |
                        (type & 0xff);

                    ObjModel.le(ro, info, 4);
                }
            }

            relocationData.put(s, ro.toByteArray());
        }

        ByteArrayOutputStream symout = new ByteArrayOutputStream();

        for (Sym s : symbols) {
            if (is64) {
                ObjModel.le(symout, s.name, 4);
                symout.write(s.info);
                symout.write(s.other);
                ObjModel.le(symout, s.shndx, 2);
                ObjModel.le(symout, s.value, 8);
                ObjModel.le(symout, s.size, 8);
            } else {
                ObjModel.le(symout, s.name, 4);
                ObjModel.le(symout, s.value, 4);
                ObjModel.le(symout, s.size, 4);
                symout.write(s.info);
                symout.write(s.other);
                ObjModel.le(symout, s.shndx, 2);
            }
        }

        byte[] symData = symout.toByteArray();
        byte[] strData = strtab.bytes();
        byte[] shstrData = shstrtab.bytes();

        int ehsize = is64 ? 64 : 52;
        int shentsize = is64 ? 64 : 40;
        int wordAlign = is64 ? 8 : 4;

        ByteArrayOutputStream file = new ByteArrayOutputStream();

        ObjModel.pad(file, ehsize);

        long[] sectionOffset = new long[userSections];

        for (int i = 0; i < userSections; i++) {
            Section s = m.sections.get(i);

            ObjModel.pad(
                file,
                ObjModel.alignUp(
                    file.size(),
                    Math.max(1, s.align)
                )
            );

            sectionOffset[i] = file.size();

            if (!s.nobits)
                file.write(
                    sectionData[i],
                    0,
                    sectionData[i].length
                );
        }

        ObjModel.pad(file, ObjModel.alignUp(file.size(), 1));

        long shstrOffset = file.size();

        file.write(
            shstrData,
            0,
            shstrData.length
        );

        ObjModel.pad(
            file,
            ObjModel.alignUp(file.size(), wordAlign)
        );

        long symOffset = file.size();

        file.write(
            symData,
            0,
            symData.length
        );

        long strOffset = file.size();

        file.write(
            strData,
            0,
            strData.length
        );

        Map<Section,Long> relocOffset = new HashMap<>();

        for (Section s : relocSections) {
            ObjModel.pad(
                file,
                ObjModel.alignUp(file.size(), wordAlign)
            );

            relocOffset.put(s, (long)file.size());

            byte[] r = relocationData.get(s);

            file.write(r, 0, r.length);
        }

        ObjModel.pad(
            file,
            ObjModel.alignUp(file.size(), wordAlign)
        );

        long sectionHeaderOffset = file.size();

        int sectionCount =
            1 +
            userSections +
            3 +
            relocSections.size();

        writeSectionHeader(
            file, is64,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0
        );

        for (int i = 0; i < userSections; i++) {
            Section s = m.sections.get(i);

            long flags = 0;

            if (s.write) flags |= SHF_WRITE;
            if (s.alloc) flags |= SHF_ALLOC;
            if (s.exec) flags |= SHF_EXEC;
            if (s.merge) flags |= SHF_MERGE;
            if (s.strings) flags |= SHF_STRINGS;
            if (s.tls) flags |= SHF_TLS;

            writeSectionHeader(
                file,
                is64,
                sectionName[i],
                s.nobits ? SHT_NOBITS : SHT_PROGBITS,
                flags,
                0,
                sectionOffset[i],
                s.nobits ? s.size : sectionData[i].length,
                0,
                0,
                Math.max(1, s.align),
                s.entsize
            );
        }

        writeSectionHeader(
            file,
            is64,
            shstrName,
            SHT_STRTAB,
            0, 0,
            shstrOffset,
            shstrData.length,
            0, 0, 1, 0
        );

        writeSectionHeader(
            file,
            is64,
            symtabName,
            SHT_SYMTAB,
            0, 0,
            symOffset,
            symData.length,
            strtabIndex,
            firstGlobal,
            wordAlign,
            is64 ? 24 : 16
        );

        writeSectionHeader(
            file,
            is64,
            strtabName,
            SHT_STRTAB,
            0, 0,
            strOffset,
            strData.length,
            0, 0, 1, 0
        );

        for (Section s : relocSections) {
            byte[] r = relocationData.get(s);

            writeSectionHeader(
                file,
                is64,
                relocName.get(s),
                is64 ? SHT_RELA : SHT_REL,
                SHF_INFO_LINK,
                0,
                relocOffset.get(s),
                r.length,
                symtabIndex,
                secIndex.get(s.name),
                wordAlign,
                is64 ? 24 : 8
            );
        }

        byte[] result = file.toByteArray();

        ByteArrayOutputStream header = new ByteArrayOutputStream();

        header.write(0x7f);
        header.write('E');
        header.write('L');
        header.write('F');

        header.write(is64 ? 2 : 1);
        header.write(1);
        header.write(1);
        header.write(0);

        for (int i = 0; i < 8; i++)
            header.write(0);

        ObjModel.le(header, 1, 2);
        ObjModel.le(header, is64 ? 62 : 3, 2);
        ObjModel.le(header, 1, 4);

        ObjModel.le(header, 0, is64 ? 8 : 4);
        ObjModel.le(header, 0, is64 ? 8 : 4);
        ObjModel.le(
            header,
            sectionHeaderOffset,
            is64 ? 8 : 4
        );

        ObjModel.le(header, 0, 4);

        ObjModel.le(header, ehsize, 2);
        ObjModel.le(header, 0, 2);
        ObjModel.le(header, 0, 2);

        ObjModel.le(header, shentsize, 2);
        ObjModel.le(header, sectionCount, 2);
        ObjModel.le(header, shstrIndex, 2);

        byte[] hb = header.toByteArray();

        System.arraycopy(
            hb,
            0,
            result,
            0,
            hb.length
        );

        return result;
    }

    private static Sym makeSymbol(
        Symbol s,
        StrTab strtab,
        Map<String,Integer> secIndex
    ) {
        Sym x = new Sym();

        x.name = strtab.add(s.name);
        x.size = s.size;
        x.other = s.vis;

        int bind =
            s.bind == Bind.LOCAL ? 0 :
            s.bind == Bind.WEAK ? 2 : 1;

        int type =
            s.type == Type.FUNC ? 2 :
            s.type == Type.OBJECT ? 1 : 0;

        x.info = (bind << 4) | type;

        switch (s.where) {
            case DEFINED -> {
                Integer n = secIndex.get(s.sec);

                if (n == null)
                    throw new AsmError(
                        I18n.tr(
                            "seccion desconocida: %s",
                            s.sec
                        )
                    );

                x.shndx = n;
                x.value = s.value;
            }

            case UNDEF -> {
                x.shndx = 0;
                x.value = 0;
            }

            case ABS -> {
                x.shndx = 0xfff1;
                x.value = s.value;
            }

            case COMMON -> {
                x.shndx = 0xfff2;
                x.value = s.align;
            }
        }

        return x;
    }

    private static void writeSectionHeader(
        ByteArrayOutputStream out,
        boolean is64,
        long name,
        long type,
        long flags,
        long addr,
        long offset,
        long size,
        long link,
        long info,
        long align,
        long entsize
    ) {
        int w = is64 ? 8 : 4;

        ObjModel.le(out, name, 4);
        ObjModel.le(out, type, 4);
        ObjModel.le(out, flags, w);
        ObjModel.le(out, addr, w);
        ObjModel.le(out, offset, w);
        ObjModel.le(out, size, w);
        ObjModel.le(out, link, 4);
        ObjModel.le(out, info, 4);
        ObjModel.le(out, align, w);
        ObjModel.le(out, entsize, w);
    }

    private static int relocationType(
        Fmt fmt,
        Reloc r
    ) {
        if (fmt == Fmt.ELF64) {
            return switch (r.kind) {
                case ABS -> switch (r.size) {
                    case 8 -> 1;
                    case 4 -> r.signed ? 11 : 10;
                    case 2 -> 12;
                    default -> throw unsupported(fmt, r);
                };

                case PCREL -> switch (r.size) {
                    case 8 -> 24;
                    case 4 -> 2;
                    case 2 -> 13;
                    default -> throw unsupported(fmt, r);
                };

                case PLT -> {
                    if (r.size == 4) yield 4;
                    throw unsupported(fmt, r);
                }

                case GOTPCREL -> {
                    if (r.size == 4) yield 9;
                    throw unsupported(fmt, r);
                }

                case GOT -> {
                    if (r.size == 4) yield 3;
                    if (r.size == 8) yield 27;
                    throw unsupported(fmt, r);
                }

                case GOTOFF -> {
                    if (r.size == 8) yield 25;
                    throw unsupported(fmt, r);
                }

                case GOTPC -> {
                    if (r.size == 4) yield 26;
                    throw unsupported(fmt, r);
                }

                case IMAGEBASE ->
                    throw unsupported(fmt, r);
            };
        }

        return switch (r.kind) {
            case ABS -> switch (r.size) {
                case 4 -> 1;
                case 2 -> 20;
                default -> throw unsupported(fmt, r);
            };

            case PCREL -> switch (r.size) {
                case 4 -> 2;
                case 2 -> 21;
                default -> throw unsupported(fmt, r);
            };

            case PLT -> {
                if (r.size == 4) yield 4;
                throw unsupported(fmt, r);
            }

            case GOT -> {
                if (r.size == 4) yield 3;
                throw unsupported(fmt, r);
            }

            case GOTOFF -> {
                if (r.size == 4) yield 9;
                throw unsupported(fmt, r);
            }

            case GOTPC -> {
                if (r.size == 4) yield 10;
                throw unsupported(fmt, r);
            }

            case GOTPCREL, IMAGEBASE ->
                throw unsupported(fmt, r);
        };
    }

    private static AsmError unsupported(
        Fmt fmt,
        Reloc r
    ) {
        return new AsmError(
            I18n.tr(
                "reubicacion no soportada en %s: %s de %s bytes",
                fmt.id,
                r.kind,
                r.size
            )
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
