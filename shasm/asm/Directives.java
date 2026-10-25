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

public abstract class Directives extends AsmCore {
    protected Directives(InsnDb db, Map<String, Reg> regs, List<Preproc.Line> lines, int defaultBits, List<Path> includeDirs) {
        super(db, regs, lines, defaultBits, includeDirs);
    }

    protected static final Set<String> PSEUDO = Set.of("db","dw","dd","dq","dt","do","dy","dz","resb","resw","resd","resq","rest","reso","resy","resz","incbin","equ");
    protected static final Set<String> DIRECTIVES = Set.of(
        "bits",
        "org",
        "section",
        "segment",
        "global",
        "extern",
        "common",
        "default",
        "cpu",
        "float",
        "absolute",
        "align",
        "alignb",
        "fill",
        "bootsig",
        "use16",
        "use32",
        "use64",
        "warning",
        "map",
        "list",
        "times",
        "static",
        "required",
        "sectalign",
        "group",
        "export",
        "import",
        "dopt",
        "incbin",

        "%definesdk",
        "%author",
        "%license",
        "%docs",
        "%mallionic",
        "%comment",
        "%link",
        "%dev",
        "%team",

        "%import",
        "%using",
        "@import",
        "@using"
    );
    protected static final Set<String> PREFIXES = Set.of("lock","rep","repe","repz","repne","repnz","xacquire","xrelease","bnd","notrack","o16","o32","o64","a16","a32","a64","strict");

    protected boolean knownWord(String w) {
        String l = w.toLowerCase();
        return PSEUDO.contains(l) || DIRECTIVES.contains(l) || PREFIXES.contains(l) || (db.has(l) && !l.startsWith("res") );
    }

    protected void statement(String t, Pfx inherited) {
        t = t.strip();
        if (t.isEmpty()) return;

        if (t.startsWith("[") && t.endsWith("]")) {
            String in = t.substring(1, t.length() - 1).strip();
            String w = in.split("\\s+")[0].toLowerCase();
            if (DIRECTIVES.contains(w)) { directive(w, in.length() > w.length() ? in.substring(w.length()).strip() : ""); return; }
        }

        Matcher lm = Pattern.compile("^([A-Za-z_.?@$][\\w$#@~.?]*)(:{1,2})?(?:\\s+(.*))?$").matcher(t);
        String rest = t;
        if (lm.matches() && inherited == null) {
            String w = lm.group(1); boolean colon = lm.group(2) != null; String after = lm.group(3) == null ? "" : lm.group(3).strip();
            boolean isLabel = false;
            if (colon) isLabel = true;
            else if (!knownWord(w)) {
                isLabel = true;
            }
            if (isLabel) {
                String aw = after.split("\\s+")[0].toLowerCase();
                if (aw.equals("equ")) {
                    String ex = after.substring(3).strip();
                    Val v = evalKnown(ex, false);
                    define(w, v.v, false);
                    return;
                }
                if (!w.equals("$")) define(w, here(), true);
                rest = after;
                if (rest.isEmpty()) return;
            }
        }

        Pfx pf = inherited != null ? inherited : new Pfx();
        while (true) {
            Matcher wm = Pattern.compile("^([A-Za-z_][\\w]*)(?:\\s+(.*))?$").matcher(rest);
            if (!wm.matches()) break;
            String w = wm.group(1).toLowerCase();
            String after = wm.group(2) == null ? "" : wm.group(2);
            if (w.equals("times")) {
                int sp = splitTimes(after);
                String cnt = after.substring(0, sp).strip(), body = after.substring(sp).strip();
                Val c = evalVal(cnt, false);
                if (!c.known) { if (lastPass) throw new AsmError(I18n.tr("TIMES: valor no resuelto")); return; }
                if (c.v < 0) throw new AsmError(I18n.tr("TIMES: el valor es negativo (%s)", c.v));
                for (long k = 0; k < c.v; k++) statement(body, null);
                return;
            }
            switch (w) {
                case "lock" -> pf.bytes.write(0xF0);
                case "rep", "repe", "repz" -> pf.bytes.write(0xF3);
                case "repne", "repnz" -> pf.bytes.write(0xF2);
                case "xacquire", "bnd" -> pf.bytes.write(0xF2);
                case "xrelease" -> pf.bytes.write(0xF3);
                case "notrack" -> pf.bytes.write(0x3E);
                case "o16" -> pf.osz = 16; case "o32" -> pf.osz = 32; case "o64" -> pf.osz = 64;
                case "a16" -> pf.asz = 16; case "a32" -> pf.asz = 32; case "a64" -> pf.asz = 64;
                case "strict" -> { }
                default -> { w = null; }
            }
            if (w == null) break;
            if (after.isEmpty()) {
                return;
            }
            rest = after;
        }
        Matcher im = Pattern.compile("^([A-Za-z_][\\w.]*)(?:\\s+(.*))?$").matcher(rest);
        if (!im.matches()) throw new AsmError(I18n.tr("sintaxis invalida: '%s'", rest));
        String mn = im.group(1), args = im.group(2) == null ? "" : im.group(2).strip();
        String lmn = mn.toLowerCase();
        if (DIRECTIVES.contains(lmn) && !db.has(lmn)) { directive(lmn, args); return; }
        if (lmn.equals("align") || lmn.equals("alignb") || lmn.equals("fill") || lmn.equals("bootsig")) { directive(lmn, args); return; }
        if (lmn.startsWith("d") && lmn.length() == 2 && "bwdqtoyz".indexOf(lmn.charAt(1)) >= 0) { data(lmn.charAt(1), args); return; }
        if (lmn.startsWith("res") && lmn.length() == 4 && "bwdqtoyz".indexOf(lmn.charAt(3)) >= 0) { reserve(lmn.charAt(3), args); return; }
        if (lmn.equals("incbin")) { incbin(args); return; }
        if (lmn.equals("equ")) throw new AsmError(I18n.tr("EQU sin etiqueta"));
        instruction(mn, args, pf);
    }

    protected int splitTimes(String s) {

        int depth = 0; char q = 0; int i = 0;
        for (; i < s.length(); i++) {
            char c = s.charAt(i);
            if (q != 0) { if (c == q) q = 0; continue; }
            if (c == '\'' || c == '"') q = c;
            else if (c == '(' || c == '[') depth++; else if (c == ')' || c == ']') depth--;
            else if (Character.isWhitespace(c) && depth == 0) {

                String before = s.substring(0, i).stripTrailing();
                String after = s.substring(i).stripLeading();
                if (before.isEmpty()) continue;
                char lc = before.charAt(before.length() - 1);
                char fc = after.isEmpty() ? ' ' : after.charAt(0);
                if ("+-*/%&|^~<>(,".indexOf(lc) >= 0) continue;
                if ("+-*/%&|^<>)".indexOf(fc) >= 0 && !after.startsWith("-") ) continue;
                if (fc == '-' ) continue;
                return i;
            }
        }
        return s.length();
    }

    protected void define(String name, long v, boolean label) {
        String n = qualify(name);
        if (sym.containsKey(n)) throw new AsmError(I18n.tr("simbolo '%s' redefinido", name));
        sym.put(n, v);
        if (label && !name.startsWith(".")) lastGlobal = name;
    }

    protected Val evalVal(String ex, boolean allowRegs) {
        undefinedSeen = null;
        Val v = Expr.eval(ex, this, allowRegs);
        return v;
    }
    protected Val evalKnown(String ex, boolean regs) {
        Val v = evalVal(ex, regs);
        if (!v.known && lastPass) throw new AsmError(I18n.tr("simbolo no definido en la expresion: %s", ex));
        return v;
    }

    protected void directive(String d, String args) {
        switch (d) {
            case "bits" -> {
                int b = (int) evalKnown(args, false).v;
                if (b != 16 && b != 32 && b != 64) throw new AsmError(I18n.tr("BITS debe ser 16, 32 o 64"));
                bits = b;
            }
            case "use16" -> bits = 16; case "use32" -> bits = 32; case "use64" -> bits = 64;
            case "org" -> {
                if (cur.size() > 0 || secs.size() > 1) throw new AsmError(I18n.tr("ORG debe aparecer antes de generar codigo"));
                org = evalKnown(args, false).v; cur.start = org;
            }
            case "section", "segment" -> {
                String[] p = args.split("\\s+");
                String nm = p[0];
                newSection(nm);
                for (int i = 1; i < p.length; i++) {
                    String a = p[i].toLowerCase();
                    if (a.startsWith("align=")) cur.align = (int) evalKnown(a.substring(6), false).v;
                    else if (a.equals("nobits")) cur.nobits = true;
                    else if (a.equals("progbits")) cur.nobits = false;
                }
            }
            case "default" -> { String a = args.toLowerCase(); if (a.contains("rel")) defaultRel = !a.contains("nobnd") ? true : defaultRel; if (a.equals("abs")) defaultRel = false; }
            case "global" -> {
                for (String n : splitArgs(args)) {
                    if (!n.isBlank()) declareGlobal(n.trim());
                }
            }
            case "extern" -> {
                for (String n : splitArgs(args)) {
                    if (!n.isBlank()) declareExtern(n.trim());
                }
            }
            case "common", "static", "cpu", "float", "warning", "map", "list", "required", "sectalign", "group", "export", "import", "dopt", "absolute" -> { }
            case "align", "alignb" -> {
                List<String> a = splitArgs(args);
                long n = evalKnown(a.get(0), false).v;
                if (n <= 0 || (n & (n - 1)) != 0) throw new AsmError(I18n.tr("ALIGN requiere una potencia de dos"));
                long off = here() - cur.start;
                long pad = (n - (off % n)) % n;
                if (d.equals("alignb") || cur.nobits) { zeros(pad); return; }
                if (a.size() > 1) { int fv = (int) evalKnown(a.get(1), false).v; for (long i = 0; i < pad; i++) cur.buf.write(fv); }
                else for (long i = 0; i < pad; i++) cur.buf.write(0x90);
            }
            case "fill" -> doFill(args);
            case "bootsig" -> { cur.buf.write(0x55); cur.buf.write(0xAA); }
            default -> throw new AsmError(I18n.tr("directiva no soportada: %s", d));
        }
    }

    protected void doFill(String args) {
        String valS, sizeS;
        Matcher m = Pattern.compile("^(.*?)\\s*(?:-->|->|=>|\\bto\\b|\\bhasta\\b)\\s*(.*)$", Pattern.CASE_INSENSITIVE).matcher(args);
        if (m.matches()) { valS = m.group(1); sizeS = m.group(2); }
        else {
            List<String> a = splitArgs(args);
            if (a.isEmpty() || a.get(0).isEmpty()) throw new AsmError(I18n.tr("FILL requiere un tamano"));
            sizeS = a.get(0); valS = a.size() > 1 ? a.get(1) : "0";
        }
        Val sz = evalVal(sizeS, false), vv = evalVal(valS.isBlank() ? "0" : valS, false);
        if (!sz.known || !vv.known) { if (lastPass) throw new AsmError(I18n.tr("FILL: valor no resuelto")); return; }
        long have = here() - cur.start;
        long pad = sz.v - have;
        if (pad < 0) throw new AsmError(I18n.tr("FILL: la seccion ya ocupa %s bytes, mas de los %s pedidos", have, sz.v));
        if (cur.nobits) { zeros(pad); return; }
        for (long i = 0; i < pad; i++) cur.buf.write((int) vv.v & 0xFF);
    }

    protected void zeros(long n) {
        if (cur.nobits) cur.nbSize += n; else for (long i = 0; i < n; i++) cur.buf.write(0);
    }

    protected static List<String> splitArgs(String s) {
        List<String> r = new ArrayList<>();
        int depth = 0; char q = 0; int st = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (q != 0) { if (c == '\\' && q == '`') i++; else if (c == q) q = 0; continue; }
            if (c == '\'' || c == '"' || c == '`') q = c;
            else if (c == '[' || c == '(') depth++; else if (c == ']' || c == ')') depth--;
            else if (c == ',' && depth == 0) { r.add(s.substring(st, i).strip()); st = i + 1; }
        }
        String last = s.substring(st).strip();
        if (!(r.isEmpty() && last.isEmpty())) r.add(last);
        return r;
    }

    protected void reserve(char sz, String args) {
        Val v = evalVal(args, false);
        if (!v.known) { if (lastPass) throw new AsmError(I18n.tr("RESx: valor no resuelto")); return; }
        int unit = unitOf(sz);
        if (v.v < 0) throw new AsmError(I18n.tr("RESx con valor negativo"));
        zeros(v.v * unit);
    }
    protected static int unitOf(char sz) {
        return switch (sz) { case 'b' -> 1; case 'w' -> 2; case 'd' -> 4; case 'q' -> 8; case 't' -> 10; case 'o' -> 16; case 'y' -> 32; case 'z' -> 64; default -> 1; };
    }

    protected void data(char sz, String args) {
        int unit = unitOf(sz);
        if (args.isBlank()) throw new AsmError(I18n.tr("DB/DW/... requiere operandos"));
        for (String item : splitArgs(args)) {
            if (item.equals("?")) { zeros(unit); continue; }
            Val v = evalVal(item, false);
            if (!v.known && lastPass) throw new AsmError(I18n.tr("simbolo no definido: %s", (undefinedSeen != null ? undefinedSeen : item)));
            if (v.isString && v.bytes != null) {
                byte[] b = v.bytes;
                for (byte x : b) cur.buf.write(x);
                int padn = (unit - (b.length % unit)) % unit;
                if (b.length == 0) padn = 0;
                for (int i = 0; i < padn; i++) cur.buf.write(0);
                continue;
            }
            if (v.isFloat) { floatData(sz, v.dbl); continue; }
            if (v.hasRegs()) throw new AsmError(I18n.tr("no se pueden usar registros en datos"));
            emitLE(v.v, Math.min(unit, 8));
            if (unit > 8) for (int i = 8; i < unit; i++) cur.buf.write((v.v < 0 && unit > 8) ? 0xFF : 0);
            checkRange(v, unit);
        }
    }

    protected void checkRange(Val v, int unit) {
        if (!lastPass || !v.known || unit >= 8) return;
        long lo = -(1L << (unit * 8 - 1)), hi = (1L << (unit * 8)) - 1;
        if (v.v < lo || v.v > hi) warnings.add(I18n.tr("%s:%s: aviso: el dato excede los limites (%s bytes)", curLine.file, curLine.no, unit));
    }

    protected void floatData(char sz, double d) {
        switch (sz) {
            case 'd' -> emitLE(Float.floatToIntBits((float) d) & 0xFFFFFFFFL, 4);
            case 'q' -> emitLE(Double.doubleToLongBits(d), 8);
            case 't' -> {
                long bitsD = Double.doubleToLongBits(d);
                long sign = bitsD >>> 63, exp = (bitsD >>> 52) & 0x7FF, man = bitsD & 0xFFFFFFFFFFFFFL;
                long e80, m80;
                if (exp == 0 && man == 0) { e80 = 0; m80 = 0; }
                else if (exp == 0x7FF) { e80 = 0x7FFF; m80 = (1L << 63) | (man << 11); }
                else { e80 = exp - 1023 + 16383; m80 = (1L << 63) | (man << 11); }
                emitLE(m80, 8); emitLE((sign << 15) | e80, 2);
            }
            case 'w' -> {
                int f = Float.floatToIntBits((float) d);
                int s = (f >>> 16) & 0x8000, e = ((f >>> 23) & 0xFF) - 127 + 15, m = f & 0x7FFFFF;
                int h = e <= 0 ? s : e >= 31 ? s | 0x7C00 : s | (e << 10) | (m >> 13);
                emitLE(h, 2);
            }
            default -> throw new AsmError(I18n.tr("constante de coma flotante no valida aqui"));
        }
    }

    protected void emitLE(long v, int n) { for (int i = 0; i < n; i++) cur.buf.write((int) (v >>> (8 * i)) & 0xFF); }

    protected void incbin(String args) {
        List<String> a = splitArgs(args);
        String fn = a.get(0); if (fn.length() >= 2) fn = fn.substring(1, fn.length() - 1);
        Path p = null;
        List<Path> cand = new ArrayList<>();
        Path cd = Paths.get(curLine.file).getParent(); if (cd != null) cand.add(cd.resolve(fn));
        cand.add(Paths.get(fn)); for (Path d : includeDirs) cand.add(d.resolve(fn));
        for (Path c : cand) if (Files.exists(c)) { p = c; break; }
        if (p == null) throw new AsmError(I18n.tr("incbin: no se encuentra '%s'", fn));
        try {
            byte[] b = Files.readAllBytes(p);
            long skip = a.size() > 1 ? evalKnown(a.get(1), false).v : 0;
            long len = a.size() > 2 ? evalKnown(a.get(2), false).v : b.length - skip;
            for (long i = skip; i < skip + len && i < b.length; i++) cur.buf.write(b[(int) i]);
        } catch (IOException e) { throw new AsmError(I18n.tr("incbin: %s", e.getMessage())); }
    }

    protected abstract void instruction(String mn, String args, Pfx pf);

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
