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

package shasm.asm;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.regex.*;
import shasm.*;
import shasm.i18n.I18n;

public abstract class AsmCore implements Expr.Ctx {

    public static final class ExternalReloc {
        public String section;
        public long offset;
        public String symbol;
        public int size;
        public boolean plt;
        public long addend;

        public boolean rel, signed;
        public String tsec;
        public int trail;

        public ExternalReloc(
            String section,
            long offset,
            String symbol,
            int size,
            boolean plt,
            long addend
        ) {
            this.section = section;
            this.offset = offset;
            this.symbol = symbol;
            this.size = size;
            this.plt = plt;
            this.addend = addend;
        }
    }

    public final List<ExternalReloc> externalRelocs =
        new ArrayList<>();

    static final class Sec {
        String name;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        long start;
        boolean nobits;
        int align = 1;
        long nbSize;

        long size() {
            return nobits ? nbSize : buf.size();
        }
    }

    static final class Check {
        final String key;
        final BooleanSupplier ok;

        Check(String k, BooleanSupplier o) {
            key = k;
            ok = o;
        }
    }

    static final class Pfx {
        int osz, asz;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    }

    static final class Fix {
        int off;
        int size;
        long target;
        boolean rel;
        boolean known;
        int line;

        String ext;
        String tsec;
        long tbase;
        boolean signed;
        boolean plt;
    }

    public static final class ListEntry {
        public long addr;
        public byte[] bytes;
        public String src;
        public int no;
    }

    public final InsnDb db;
    public final Map<String, Reg> regs;

    protected final List<Preproc.Line> lines;

    public final List<String> warnings = new ArrayList<>();
    public final List<String> errors = new ArrayList<>();
    public final List<ListEntry> listing = new ArrayList<>();

    protected final List<Path> includeDirs;

    protected List<Sec> secs;
    protected Sec cur;

    protected int bits;
    protected long org;

    protected Map<String, Long> sym;
    protected Map<String, Long> prev;

    protected Map<String, Long> secStartPrev = new HashMap<>();

    protected final Set<String> banned = new HashSet<>();

    protected List<Check> checks;

    protected boolean changed;
    protected boolean lastPass;
    protected boolean defaultRel;

    protected String lastGlobal = "";

    protected int curLineIdx;
    protected Preproc.Line curLine;

    protected int siteSeq;

    protected String undefinedSeen;

    protected ListEntry curList;

    protected final Set<String> externSymbols = new HashSet<>();

    protected final Set<String> globalSymbols = new HashSet<>();

    protected final Set<String> weakSymbols = new HashSet<>();

    public boolean objMode;

    public void setObjectMode(boolean b) {
        objMode = b;
    }

    protected final Set<String> refs =
        new LinkedHashSet<>();

    protected Map<String, String> labelSec =
        new HashMap<>();

    protected Map<String, String> prevLabelSec =
        new HashMap<>();

    protected static final class RelocInfo {
        String ext;
        String tsec;
        long base;
    }

    protected final Map<Op, RelocInfo> relocInfo =
        new IdentityHashMap<>();

    protected String labelSecName(String n) {
        String s = labelSec.get(n);
        return s != null ? s : prevLabelSec.get(n);
    }

    protected Sec secByName(String n) {
        for (Sec s : secs)
            if (s.name.equals(n))
                return s;

        return null;
    }

    private final class LabelMap extends HashMap<String, Long> {

        @Override
        public Long put(String k, Long v) {
            if (cur != null && v != null && v == here())
                labelSec.put(k, cur.name);
            else
                labelSec.remove(k);

            return super.put(k, v);
        }
    }

    protected void recordReloc(
        Fix f,
        long secOff,
        long addend,
        int trail
    ) {
        ExternalReloc r =
            new ExternalReloc(
                cur.name,
                secOff,
                f.ext,
                f.size,
                f.plt && f.ext != null,
                addend
            );

        r.rel = f.rel;
        r.signed = f.signed;
        r.tsec = f.tsec;
        r.trail = trail;

        externalRelocs.add(r);
    }

    protected AsmCore(
        InsnDb db,
        Map<String, Reg> regs,
        List<Preproc.Line> lines,
        int defaultBits,
        List<Path> includeDirs
    ) {
        this.db = db;
        this.regs = regs;
        this.lines = lines;
        this.bits = defaultBits;
        this.includeDirs = includeDirs;
        this.startBits = defaultBits;
    }

    protected final int startBits;

    public void declareExtern(String name) {
        if (name == null)
            return;

        String n = qualify(name);

        if (n.isBlank())
            return;

        externSymbols.add(n);

    }

    public void declareGlobal(String name) {
        if (name == null)
            return;

        String n = qualify(name);

        if (n.isBlank())
            return;

        globalSymbols.add(n);
    }

    public void declareWeak(String name) {
        if (name == null)
            return;

        String n = qualify(name);

        if (n.isBlank())
            return;

        weakSymbols.add(n);
        globalSymbols.add(n);
    }

    public boolean isExtern(String name) {
        if (name == null)
            return false;

        return externSymbols.contains(qualify(name));
    }

    public boolean isGlobal(String name) {
        if (name == null)
            return false;

        String n = qualify(name);

        return globalSymbols.contains(n)
            || weakSymbols.contains(n);
    }

    public boolean isWeak(String name) {
        if (name == null)
            return false;

        return weakSymbols.contains(qualify(name));
    }

    @Override
    public Long symbol(String name) {
        String n = qualify(name);

        if (objMode && externSymbols.contains(n)) {
            refs.add(n);
            return 0L;
        }

        Long v = sym.get(n);

        if (v == null)
            v = prev.get(n);

        if (v != null) {
            if (objMode && labelSecName(n) != null)
                refs.add(n);

            return v;
        }

        undefinedSeen = n;
        return null;
    }

    @Override
    public boolean external(String name) {
        String n = qualify(name);

        boolean e = externSymbols.contains(n);

        if (e && objMode)
            refs.add(n);

        return e;
    }

    @Override
    public long here() {
        return cur.start + cur.size();
    }

    @Override
    public long sectionStart() {
        return cur.start;
    }

    @Override
    public Reg reg(String name) {
        return regs.get(name);
    }

    protected String qualify(String n) {
        if (n == null)
            return "";

        if (n.startsWith(".") && !n.startsWith(".."))
            return lastGlobal + n;

        return n;
    }

    public byte[] assemble() {
        prev = new HashMap<>();
        prevLabelSec = new HashMap<>();
        labelSec = new HashMap<>();

        lastPass = false;

        int pass = 0;

        while (true) {

            if (++pass > 100) {

                runPass();

                errors.add(
                    I18n.tr(
                        "error: el ensamblado no converge (demasiadas pasadas)"
                    )
                );

                return output();
            }

            runPass();

            boolean ck = false;

            for (Check c : checks) {

                if (!c.ok.getAsBoolean()) {
                    banned.add(c.key);
                    ck = true;
                }
            }

            Map<String, Long> ns = layout();

            boolean stable =
                sym.equals(prev)
                && ns.equals(secStartPrev)
                && !ck;

            secStartPrev = ns;
            prev = new HashMap<>(sym);

            if (stable && pass > 1)
                break;
        }

        lastPass = true;

        runPass();

        return output();
    }

    protected Map<String, Long> layout() {
        Map<String, Long> m =
            new LinkedHashMap<>();

        long pos = org;
        boolean first = true;

        for (Sec s : orderedSections()) {

            long st =
                first
                    ? org
                    : alignUp(pos, s.align);

            m.put(s.name, st);

            pos = st + s.size();
            first = false;
        }

        return m;
    }

    protected List<Sec> orderedSections() {
        List<Sec> l =
            new ArrayList<>(secs);

        l.sort(
            Comparator.comparingInt(
                s ->
                    s.nobits
                        ? 2
                        : (s.name.equals(".text") ? 0 : 1)
            )
        );

        return l;
    }

    protected static long alignUp(long v, int a) {
        return a <= 1
            ? v
            : (v + a - 1) / a * a;
    }

    protected byte[] output() {
        ByteArrayOutputStream o =
            new ByteArrayOutputStream();

        long pos = org;
        boolean first = true;

        for (Sec s : orderedSections()) {

            long st =
                first
                    ? org
                    : alignUp(pos, s.align);

            if (!first) {

                while (pos < st) {
                    o.write(0);
                    pos++;
                }
            }

            if (!s.nobits) {

                byte[] b =
                    s.buf.toByteArray();

                o.write(
                    b,
                    0,
                    b.length
                );

                pos = st + b.length;

            } else {

                pos = st;
            }

            first = false;
        }

        return o.toByteArray();
    }

    protected void newSection(String name) {

        for (Sec s : secs) {

            if (s.name.equals(name)) {
                cur = s;
                return;
            }
        }

        Sec s = new Sec();

        s.name = name;

        s.align =
            name.equals(".text")
                ? 16
                : (
                    name.equals(".data")
                    || name.equals(".rodata")
                        ? 4
                        : (
                            name.equals(".bss")
                                ? 4
                                : 1
                        )
                );

        if (name.equals(".bss"))
            s.nobits = true;

        secs.add(s);
        cur = s;

        s.start =
            secs.size() == 1
                ? org
                : secStartPrev.getOrDefault(
                    name,
                    0L
                );
    }

    protected void runPass() {

        prevLabelSec = labelSec;
        labelSec = new HashMap<>();

        sym = new LabelMap();

        externalRelocs.clear();
        relocInfo.clear();
        refs.clear();

        checks = new ArrayList<>();

        changed = false;

        secs = new ArrayList<>();

        bits = startBits;

        org = 0;

        lastGlobal = "";

        defaultRel = false;

        errors.clear();
        warnings.clear();
        listing.clear();

        cur = null;

        newSection(".text");

        siteSeq = 0;

        for (int i = 0; i < lines.size(); i++) {

            curLineIdx = i;
            curLine = lines.get(i);

            String t =
                curLine.text.strip();

            if (t.isEmpty())
                continue;

            curList = new ListEntry();

            curList.addr = here();
            curList.src = t;
            curList.no = curLine.no;

            int before =
                (int) cur.size();

            try {

                statement(
                    t,
                    null
                );

            } catch (AsmError e) {

                if (
                    undefinedSeen != null
                    && externSymbols.contains(
                        undefinedSeen
                    )
                ) {

                    undefinedSeen = null;

                } else {

                    errors.add(
                        I18n.tr(
                            "%s:%s: error: %s",
                            curLine.file,
                            curLine.no,
                            e.getMessage()
                        )
                    );
                }

            } catch (RuntimeException e) {

                errors.add(
                    I18n.tr(
                        "%s:%s: error interno: %s",
                        curLine.file,
                        curLine.no,
                        e
                    )
                );
            }

            if (!cur.nobits) {

                byte[] all =
                    cur.buf.toByteArray();

                if (all.length > before) {

                    curList.bytes =
                        Arrays.copyOfRange(
                            all,
                            before,
                            all.length
                        );

                    listing.add(curList);
                }
            }
        }
    }

    protected abstract void statement(
        String t,
        Pfx inherited
    );

    public void runFinal() {
    }

    public void setLastPass(boolean b) {
        lastPass = b;
    }

    public static final class ExportSection {

        public final String name;
        public final byte[] data;
        public final long size;
        public final boolean nobits;
        public final int align;

        public ExportSection(
            String name,
            byte[] data,
            long size,
            boolean nobits,
            int align
        ) {
            this.name = name;
            this.data = data;
            this.size = size;
            this.nobits = nobits;
            this.align = align;
        }
    }

    public static final class ExportSymbol {

        public final String name;
        public final String sec;
        public final long value;

        public final boolean undef;

        public final boolean global;

        public final boolean weak;

        public ExportSymbol(
            String name,
            String sec,
            long value
        ) {
            this(
                name,
                sec,
                value,
                false,
                true,
                false
            );
        }

        public ExportSymbol(
            String name,
            String sec,
            long value,
            boolean undef,
            boolean global,
            boolean weak
        ) {
            this.name = name;
            this.sec = sec;
            this.value = value;
            this.undef = undef;
            this.global = global;
            this.weak = weak;
        }
    }

    public List<ExportSection> exportSections() {

        List<ExportSection> out =
            new ArrayList<>();

        for (Sec s : orderedSections()) {

            byte[] data =
                s.nobits
                    ? new byte[0]
                    : s.buf.toByteArray();

            out.add(
                new ExportSection(
                    s.name,
                    data,
                    s.size(),
                    s.nobits,
                    Math.max(1, s.align)
                )
            );
        }

        return out;
    }

    public List<ExportSymbol> exportSymbols() {

        List<ExportSymbol> out =
            new ArrayList<>();

        if (sym == null)
            return out;

        Set<String> done =
            new HashSet<>();

        for (var e : labelSec.entrySet()) {

            String n = e.getKey();

            if (
                !globalSymbols.contains(n)
                && !weakSymbols.contains(n)
            )
                continue;

            Sec s =
                secByName(e.getValue());

            Long v =
                sym.get(n);

            if (s == null || v == null)
                continue;

            out.add(
                new ExportSymbol(
                    n,
                    s.name,
                    v - s.start,
                    false,
                    true,
                    weakSymbols.contains(n)
                )
            );

            done.add(n);
        }

        for (String n : globalSymbols) {

            if (
                done.contains(n)
                || externSymbols.contains(n)
            )
                continue;

            Long v =
                sym.get(n);

            if (v != null) {

                out.add(
                    new ExportSymbol(
                        n,
                        null,
                        v,
                        false,
                        true,
                        weakSymbols.contains(n)
                    )
                );
            }
        }

        for (String n : externSymbols) {

            if (done.contains(n))
                continue;

            out.add(
                new ExportSymbol(
                    n,
                    null,
                    0,
                    true,
                    true,
                    weakSymbols.contains(n)
                )
            );
        }

        out.sort(
            Comparator.comparing(
                x -> x.name
            )
        );

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
