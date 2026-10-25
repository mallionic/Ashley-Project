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

public final class CoffWriter {
    private CoffWriter() {}

    static final int CNT_CODE = 0x20,
            CNT_INIT = 0x40,
            CNT_UNINIT = 0x80,
            LNK_INFO = 0x200,
            LNK_REMOVE = 0x800,
            LNK_NRELOC_OVFL = 0x01000000,
            MEM_DISCARD = 0x02000000,
            MEM_EXEC = 0x20000000,
            MEM_READ = 0x40000000,
            MEM_WRITE = 0x80000000;

    private static final class Sym {
        byte[] name8;
        int strOff = -1;
        String name;
        long value;
        int sect;
        int type;
        int cls;
        int naux;
        byte[] aux;
    }

    static int alignBits(int align) {
        return align <= 1
                ? 0x00100000
                : (ObjModel.log2(Math.min(align, 8192)) + 1) << 20;
    }

    static int chars(Section s) {
        int c;

        if (s.info) {
            c = LNK_INFO | LNK_REMOVE;
        } else {
            if (s.nobits)
                c = CNT_UNINIT;
            else if (s.exec)
                c = CNT_CODE;
            else
                c = CNT_INIT;

            if (s.exec)
                c |= MEM_EXEC | (s.nobits ? 0 : CNT_CODE);

            if (s.read)
                c |= MEM_READ;

            if (s.write)
                c |= MEM_WRITE;

            if (!s.alloc)
                c |= MEM_DISCARD;
        }

        if (!s.info)
            c |= alignBits(s.align);
        else
            c |= alignBits(1);

        return c;
    }

    public static byte[] write(ObjModel m) {
        final boolean is64 = m.fmt.is64;
        final int nSec = m.sections.size();

        if (nSec > 32767)
            throw new AsmError(I18n.tr("demasiadas secciones para COFF"));

        ByteArrayOutputStream strs = new ByteArrayOutputStream();
        final Map<String, Integer> strPos = new HashMap<>();

        java.util.function.Function<String, Integer> addStr = s -> {
            Integer o = strPos.get(s);

            if (o != null)
                return o;

            int pos = 4 + strs.size();
            byte[] b = s.getBytes(StandardCharsets.UTF_8);

            strs.write(b, 0, b.length);
            strs.write(0);

            strPos.put(s, pos);

            return pos;
        };

        List<Sym> syms = new ArrayList<>();
        Map<String, Integer> symIdx = new HashMap<>();
        Map<String, Integer> secSym = new HashMap<>();

        int rec = 0;

        if (!m.fileName.isEmpty()) {
            byte[] fn = m.fileName.getBytes(StandardCharsets.UTF_8);

            int naux = Math.max(1, (fn.length + 17) / 18);

            Sym f = new Sym();
            f.name = ".file";
            f.sect = -2;
            f.cls = 103;
            f.naux = naux;
            f.aux = Arrays.copyOf(fn, naux * 18);

            syms.add(f);
            rec += 1 + naux;
        }

        int[] secNum = new int[nSec];

        for (int i = 0; i < nSec; i++) {
            Section s = m.sections.get(i);

            secNum[i] = i + 1;

            Sym y = new Sym();
            y.name = s.name;
            y.sect = i + 1;
            y.cls = 3;
            y.naux = 1;

            ByteArrayOutputStream a = new ByteArrayOutputStream();

            ObjModel.le(a, s.size, 4);
            ObjModel.le(a, Math.min(s.relocs.size(), 0xFFFF), 2);
            ObjModel.le(a, 0, 2);
            ObjModel.le(a, 0, 4);
            ObjModel.le(a, 0, 2);

            a.write(0);
            a.write(0);
            a.write(0);
            a.write(0);
            a.write(0);

            y.aux = Arrays.copyOf(a.toByteArray(), 18);

            secSym.put(s.name, rec);

            syms.add(y);
            rec += 2;
        }

        List<Symbol> order = new ArrayList<>();

        for (Symbol y : m.symbols)
            if (y.bind == Bind.LOCAL)
                order.add(y);

        for (Symbol y : m.symbols)
            if (y.bind != Bind.LOCAL)
                order.add(y);

        for (Symbol u : order) {
            if (u.bind == Bind.WEAK)
                throw new AsmError(
                        I18n.tr(
                                "los simbolos WEAK no estan soportados en COFF: %s",
                                u.name
                        )
                );

            Sym y = new Sym();

            y.name = u.name;
            y.cls = u.bind == Bind.LOCAL ? 3 : 2;
            y.type = u.type == Type.FUNC ? 0x20 : 0;

            switch (u.where) {
                case DEFINED -> {
                    y.sect = m.sections.indexOf(m.section(u.sec)) + 1;
                    y.value = u.value;
                }

                case UNDEF -> {
                    y.sect = 0;
                }

                case ABS -> {
                    y.sect = -1;
                    y.value = u.value;
                }

                case COMMON -> {
                    y.sect = 0;
                    y.value = u.size;
                }
            }

            symIdx.put(u.key, rec);
            syms.add(y);
            rec++;
        }

        final int nRec = rec;

        for (Sym y : syms) {
            byte[] nb = y.name.getBytes(StandardCharsets.UTF_8);

            if (nb.length <= 8)
                y.name8 = Arrays.copyOf(nb, 8);
            else
                y.strOff = addStr.apply(y.name);
        }

        byte[][] content = new byte[nSec][];
        List<byte[]> relBytes = new ArrayList<>();
        int[] relCount = new int[nSec];

        for (int i = 0; i < nSec; i++) {
            Section s = m.sections.get(i);

            content[i] = s.nobits
                    ? new byte[0]
                    : Arrays.copyOf(s.data, (int) s.size);

            ByteArrayOutputStream ro = new ByteArrayOutputStream();
            List<Reloc> rl = s.relocs;

            boolean ovfl = rl.size() > 0xFFFF;

            if (ovfl) {
                ObjModel.le(ro, rl.size() + 1, 4);
                ObjModel.le(ro, 0, 4);
                ObjModel.le(ro, 0, 2);
            }

            for (Reloc r : rl) {
                int sym;

                if (r.ext != null) {
                    Integer si = symIdx.get(r.ext);

                    if (si == null)
                        throw new AsmError(
                                I18n.tr(
                                        "reubicacion contra un simbolo desconocido: %s",
                                        r.ext
                                )
                        );

                    sym = si;
                } else {
                    sym = secSym.get(r.tsec);
                }

                int[] tv = type(m.fmt, r);

                if (!s.nobits)
                    ObjModel.patch(content[i], r.offset, tv[1], r.size);

                ObjModel.le(ro, r.offset, 4);
                ObjModel.le(ro, sym, 4);
                ObjModel.le(ro, tv[0], 2);
            }

            relBytes.add(ro.toByteArray());
            relCount[i] = rl.size() + (ovfl ? 1 : 0);
        }

        byte[][] secName = new byte[nSec][];

        for (int i = 0; i < nSec; i++) {
            byte[] nb =
                    m.sections.get(i)
                            .name
                            .getBytes(StandardCharsets.UTF_8);

            if (nb.length <= 8)
                secName[i] = Arrays.copyOf(nb, 8);
            else
                secName[i] = Arrays.copyOf(
                        ("/" + addStr.apply(m.sections.get(i).name))
                                .getBytes(StandardCharsets.US_ASCII),
                        8
                );
        }

        int pos = 20 + 40 * nSec;

        long[] rawOff = new long[nSec];
        long[] relOff = new long[nSec];

        for (int i = 0; i < nSec; i++) {
            if (content[i].length > 0) {
                rawOff[i] = pos;
                pos += content[i].length;
            }
        }

        for (int i = 0; i < nSec; i++) {
            if (relBytes.get(i).length > 0) {
                relOff[i] = pos;
                pos += relBytes.get(i).length;
            }
        }

        int symOff = pos;

        ByteArrayOutputStream o = new ByteArrayOutputStream();

        ObjModel.le(o, is64 ? 0x8664 : 0x14C, 2);
        ObjModel.le(o, nSec, 2);
        ObjModel.le(o, 0, 4);

        ObjModel.le(o, symOff, 4);
        ObjModel.le(o, nRec, 4);
        ObjModel.le(o, 0, 2);
        ObjModel.le(o, 0, 2);

        for (int i = 0; i < nSec; i++) {
            Section s = m.sections.get(i);

            o.write(secName[i], 0, 8);

            ObjModel.le(o, 0, 4);
            ObjModel.le(o, 0, 4);

            ObjModel.le(o, s.size, 4);
            ObjModel.le(o, rawOff[i], 4);
            ObjModel.le(o, relOff[i], 4);
            ObjModel.le(o, 0, 4);

            ObjModel.le(o, Math.min(relCount[i], 0xFFFF), 2);
            ObjModel.le(o, 0, 2);

            int c = chars(s);

            if (s.relocs.size() > 0xFFFF)
                c |= LNK_NRELOC_OVFL;

            ObjModel.le(o, c, 4);
        }

        for (int i = 0; i < nSec; i++)
            o.write(content[i], 0, content[i].length);

        for (int i = 0; i < nSec; i++)
            o.write(
                    relBytes.get(i),
                    0,
                    relBytes.get(i).length
            );

        for (Sym y : syms) {
            if (y.name8 != null) {
                o.write(y.name8, 0, 8);
            } else {
                ObjModel.le(o, 0, 4);
                ObjModel.le(o, y.strOff, 4);
            }

            ObjModel.le(o, y.value, 4);
            ObjModel.le(o, y.sect, 2);
            ObjModel.le(o, y.type, 2);

            o.write(y.cls);
            o.write(y.naux);

            if (y.naux > 0)
                o.write(y.aux, 0, 18 * y.naux);
        }

        ObjModel.le(o, 4 + strs.size(), 4);

        byte[] sb = strs.toByteArray();
        o.write(sb, 0, sb.length);

        return o.toByteArray();
    }

    static int[] type(Fmt f, Reloc r) {
        int n = r.size;

        if (f == Fmt.WIN64) {
            switch (r.kind) {
                case ABS:
                    if (n == 8)
                        return new int[]{1, (int) r.addend};

                    if (n == 4)
                        return new int[]{2, (int) r.addend};
                    break;

                case IMAGEBASE:
                    if (n == 4)
                        return new int[]{3, (int) r.addend};
                    break;

                case PCREL:
                case PLT:
                    if (n == 4) {
                        int t = r.trail;

                        if (t >= 1 && t <= 5)
                            return new int[]{
                                    4 + t,
                                    (int) (r.addend + 4 + t)
                            };

                        return new int[]{
                                4,
                                (int) (r.addend + 4)
                        };
                    }
                    break;

                default:
                    break;
            }
        } else {
            switch (r.kind) {
                case ABS:
                    if (n == 4)
                        return new int[]{6, (int) r.addend};

                    if (n == 2)
                        return new int[]{1, (int) r.addend};
                    break;

                case IMAGEBASE:
                    if (n == 4)
                        return new int[]{7, (int) r.addend};
                    break;

                case PCREL:
                case PLT:
                    if (n == 4)
                        return new int[]{
                                0x14,
                                (int) (r.addend + 4)
                        };

                    if (n == 2)
                        return new int[]{
                                2,
                                (int) (r.addend + 2)
                        };
                    break;

                default:
                    break;
            }
        }

        throw new AsmError(
                I18n.tr(
                        "reubicacion no soportada en %s: %s de %s bytes",
                        f.id,
                        r.kind,
                        n
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
