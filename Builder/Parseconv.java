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

package Builder;

import java.util.List;

import Builder.Untokenizer.Token;
import Builder.Untokenizer.Type;
import Builder.ash.Annotations;
import Builder.ash.Ast;
import Builder.ash.Cursor;
import Builder.ash.Exprs;
import Builder.ash.Externs;
import Builder.ash.Funcs;
import Builder.ash.Cracks;
import Builder.ash.Builtins;
import Builder.ash.Generator;
import Builder.ash.Visibility;
import Builder.ash.Redefines;
import Builder.ash.ParamCopies;
import Builder.ash.Structs;
import Builder.ash.Globals;
import Builder.ash.Imports;
import Builder.ash.NlibEmbedded;
import Builder.ash.Inject;
import Builder.ash.Macros;
import Builder.ash.Typedefs;
import Builder.ash.Nums;
import Builder.ash.StringConv;
import Builder.ash.Vars;

public class Parseconv {

    public static class Result {
        public final String ashir;
        public final long originOffset;
        public final int bits;

        public Result(
            String ashir,
            long originOffset,
            int bits
        ) {
            this.ashir = ashir;
            this.originOffset = originOffset;
            this.bits = bits;
        }
    }

    private final Cursor cursor;
    private final Annotations annotations;
    private final Externs externs;
    private final Imports imports;
    private final Funcs funcs;

    private long originOffset;
    private int bits = 64;

    public Parseconv(List<Token> tokens) {
        this(tokens, 0);
    }

    public Parseconv(
        List<Token> tokens,
        long builderOrg
    ) {
        preloadImportTypes(tokens);

        this.cursor = new Cursor(
            Typedefs.expand(
                Structs.expand(
                    Redefines.expand(
                        new Macros().preprocess(tokens)
                    )
                )
            )
        );

        Nums nums = new Nums(cursor);
        StringConv strings = new StringConv(cursor, nums);
        Exprs exprs = new Exprs(cursor);

        Vars vars = new Vars(
            cursor,
            exprs,
            strings
        );

        Inject inject = new Inject(
            cursor,
            exprs,
            nums
        );

        this.annotations = new Annotations(
            cursor,
            builderOrg
        );

        this.externs = new Externs(cursor);

        this.imports = new Imports(cursor);

        this.funcs = new Funcs(
            cursor,
            exprs,
            vars,
            inject
        );
    }

    public long getOriginOffset() {
        return originOffset;
    }

    public int getBits() {
        return bits;
    }

    public static java.nio.file.Path NLIB_DIR = null;
    public static java.nio.file.Path PROJECT_ROOT = null;

    public static boolean NO_LIBC = false;
    public static int FORCED_BITS = 0;

    private static String hexAsm(String text) {
        StringBuilder h = new StringBuilder("x");
        for (byte b : text.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            h.append(String.format("%02x", b));
        return h.toString();
    }

    private static String noLibcRuntime(String ashir) {

        String externs =
            "extern qword calloc(qword n, qword size)\n" +
            "extern qword free(qword p)\n" +
            "extern qword exit(qword code)\n";

        if (!ashir.contains(externs))
            return ashir;

        String calloc =
            "func qword calloc(qword n, qword size) {\n" +
            "    .type r qword\n" +
            "    %r mov[0]\n" +
            "    %_ asm[" + hexAsm(
                "mov rsi, @var:n\nimul rsi, @var:size\nxor edi, edi\n" +
                "mov edx, 3\nmov r10d, 0x22\nmov r8, -1\nxor r9d, r9d\n" +
                "mov eax, 9\nsyscall\nmov @var:r, rax\n") + "]\n" +
            "    ret qword r\n" +
            "}\n\n";

        String free =
            "func qword free(qword p) {\n    ret qword 0\n}\n\n";

        String exit =
            "func qword exit(qword code) {\n" +
            "    %_ asm[" + hexAsm(
                "mov rdi, @var:code\nmov eax, 231\nsyscall\n") + "]\n" +
            "    ret qword 0\n" +
            "}\n\n";

        return ashir.replace(externs, calloc + free + exit);
    }

    private static final java.util.Set<String> IMPORTED =
        new java.util.HashSet<>();

    static String pruneImported(String ashir) {

        if (IMPORTED.isEmpty())
            return ashir;

        java.util.regex.Matcher m =
            java.util.regex.Pattern.compile(
                "(?m)^(\\[entry\\]\\n)?func \\S+ (\\w+)\\([^\\n]*\\{\\n.*?^\\}\\n",
                java.util.regex.Pattern.DOTALL
            ).matcher(ashir);

        java.util.Map<String, String> blocks = new java.util.LinkedHashMap<>();
        java.util.Map<String, java.util.Set<String>> refs = new java.util.HashMap<>();
        java.util.List<String> roots = new java.util.ArrayList<>();

        java.util.regex.Pattern ref =
            java.util.regex.Pattern.compile("(?:call|addr|mov)\\[(\\w+)");

        while (m.find()) {

            String name = m.group(2);
            blocks.put(name, m.group());

            java.util.Set<String> r = new java.util.HashSet<>();
            java.util.regex.Matcher rm = ref.matcher(m.group());
            while (rm.find()) r.add(rm.group(1));
            refs.put(name, r);

            if (!IMPORTED.contains(name))
                roots.add(name);
        }

        java.util.Set<String> live = new java.util.HashSet<>();
        java.util.ArrayDeque<String> work = new java.util.ArrayDeque<>(roots);

        while (!work.isEmpty()) {
            String n = work.pop();
            if (!live.add(n)) continue;
            for (String x : refs.getOrDefault(n, java.util.Collections.emptySet()))
                if (blocks.containsKey(x)) work.push(x);
        }

        String out = ashir;

        for (java.util.Map.Entry<String, String> e : blocks.entrySet())
            if (IMPORTED.contains(e.getKey()) && !live.contains(e.getKey()))
                out = out.replace(e.getValue(), "");

        return out;
    }

    private static final java.util.Set<java.nio.file.Path> MERGED =
        new java.util.HashSet<>();

    private final java.util.Set<String> libraryFunctions =
        new java.util.HashSet<>();

    private String shake(String ashir) {

        if (libraryFunctions.isEmpty())
            return ashir;

        java.util.regex.Pattern header =
            java.util.regex.Pattern.compile(
                "^func\\s+\\S+\\s+([A-Za-z0-9_$.]+)\\("
            );

        String[] lines = ashir.split("\n", -1);

        java.util.List<int[]> blocks = new java.util.ArrayList<>();
        java.util.List<String> names = new java.util.ArrayList<>();

        int i = 0;

        while (i < lines.length) {

            java.util.regex.Matcher m = header.matcher(lines[i]);

            if (m.find()) {

                int start = i;

                if (start > 0 && lines[start - 1].startsWith("["))
                    start--;

                int j = i;

                while (j < lines.length && !lines[j].equals("}"))
                    j++;

                blocks.add(new int[]{start, j});
                names.add(m.group(1));
                i = j + 1;

            } else
                i++;
        }

        java.util.List<StringBuilder> text = new java.util.ArrayList<>();

        for (int[] b : blocks) {
            StringBuilder sb = new StringBuilder();
            for (int k = b[0]; k <= b[1] && k < lines.length; k++)
                sb.append(lines[k]).append('\n');
            text.add(sb);
        }

        boolean[] live = new boolean[blocks.size()];

        for (int k = 0; k < live.length; k++)
            live[k] = !libraryFunctions.contains(names.get(k));

        boolean changed = true;

        while (changed) {

            changed = false;

            for (int k = 0; k < live.length; k++) {

                if (live[k])
                    continue;

                java.util.regex.Pattern use =
                    java.util.regex.Pattern.compile(
                        "[\\[,\\s]" + java.util.regex.Pattern.quote(names.get(k)) + "[\\],\\s]"
                    );

                for (int o = 0; o < live.length; o++) {
                    if (live[o] && o != k && use.matcher(text.get(o)).find()) {
                        live[k] = true;
                        changed = true;
                        break;
                    }
                }
            }
        }

        boolean[] drop = new boolean[lines.length];

        for (int k = 0; k < live.length; k++) {
            if (!live[k])
                for (int l = blocks.get(k)[0]; l <= blocks.get(k)[1] && l < lines.length; l++)
                    drop[l] = true;
        }

        StringBuilder out = new StringBuilder();

        for (int l = 0; l < lines.length; l++) {
            if (!drop[l]) {
                out.append(lines[l]);
                if (l < lines.length - 1)
                    out.append('\n');
            }
        }

        return out.toString();
    }

    private static final java.util.Set<java.nio.file.Path> TYPES_PRELOADED = new java.util.HashSet<>();

    private static void preloadImportTypes(List<Token> tokens) {

        java.util.List<Ast.Import> found = new java.util.ArrayList<>();

        for (int i = 0; i + 1 < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.type != Type.IDENT || !t.text.equals("import")) continue;
            Token n = tokens.get(i + 1);
            if (n.type == Type.STRING)
                found.add(new Ast.Import(StringConv.stripQuotes(n.text), false));
            else if (n.type == Type.LT && i + 2 < tokens.size() && tokens.get(i + 2).type == Type.IDENT)
                found.add(new Ast.Import(tokens.get(i + 2).text, true));
        }

        if (found.isEmpty()) return;

        java.nio.file.Path[] dirs = importDirs();
        java.util.List<java.nio.file.Path> files;

        files = new java.util.ArrayList<>();

        for (Ast.Import imp : found) {
            try {
                for (java.nio.file.Path f : Imports.resolveAll(java.util.List.of(imp), dirs[0], dirs[1]))
                    if (!files.contains(f)) files.add(f);
            } catch (RuntimeException e) {

            }
        }

        for (int k = files.size() - 1; k >= 0; k--) {
            java.nio.file.Path f = files.get(k);
            if (!java.nio.file.Files.isRegularFile(f) || !TYPES_PRELOADED.add(f)) continue;
            try {
                String text = java.nio.file.Files.readString(f);
                Typedefs.expand(Structs.expand(new Macros().preprocess(new Untokenizer(text).tokenize())));
            } catch (java.lang.Exception ignored) {

            }
        }
    }

    private static java.nio.file.Path[] importDirs() {

        java.nio.file.Path nlib = NLIB_DIR;

        if (nlib == null) {
            java.nio.file.Path base =
                PROJECT_ROOT != null
                    ? PROJECT_ROOT
                    : java.nio.file.Paths.get(".").toAbsolutePath();

            for (String c : new String[]{
                base.resolve("nlib").toString(),
                base.resolve("../nlib").toString(),
                "nlib", "../nlib"
            }) {
                java.nio.file.Path p =
                    java.nio.file.Paths.get(c).toAbsolutePath().normalize();
                if (java.nio.file.Files.isDirectory(p)) { nlib = p; break; }
            }
        }

        java.nio.file.Path root =
            PROJECT_ROOT != null
                ? PROJECT_ROOT
                : java.nio.file.Paths.get(".").toAbsolutePath().normalize();

        if (nlib == null)
            nlib = root.resolve("nlib");

        return new java.nio.file.Path[]{nlib, root};
    }

    private void mergeImports(Ast.Program program) {

        if (program.imports.isEmpty())
            return;

        java.nio.file.Path nlib = NLIB_DIR;

        if (nlib == null) {
            java.nio.file.Path base =
                PROJECT_ROOT != null
                    ? PROJECT_ROOT
                    : java.nio.file.Paths.get(".").toAbsolutePath();

            for (String c : new String[]{
                base.resolve("nlib").toString(),
                base.resolve("../nlib").toString(),
                "nlib", "../nlib"
            }) {
                java.nio.file.Path p =
                    java.nio.file.Paths.get(c).toAbsolutePath().normalize();
                if (java.nio.file.Files.isDirectory(p)) { nlib = p; break; }
            }
        }

        java.nio.file.Path root =
            PROJECT_ROOT != null
                ? PROJECT_ROOT
                : java.nio.file.Paths.get(".").toAbsolutePath().normalize();

        if (nlib == null)
            nlib = root.resolve("nlib");

        java.util.List<java.nio.file.Path> files;

        files = Imports.resolveAll(program.imports, nlib, root);

        final java.nio.file.Path nlibAbs = nlib.toAbsolutePath().normalize();

        final class Unit {
            java.nio.file.Path path;
            Ast.Program prog;
            boolean isNlib;
        }

        java.util.List<Unit> units = new java.util.ArrayList<>();

        for (java.nio.file.Path f : files) {

            if (!java.nio.file.Files.isRegularFile(f)
                || !f.toString().endsWith(".ash")
                || !MERGED.add(f))
                continue;

            String text;

            try {
                text = java.nio.file.Files.readString(f);
            } catch (java.io.IOException e) {
                throw new IllegalArgumentException(
                    "cannot read import '" + f + "': " + e.getMessage()
                );
            }

            Parseconv sub =
                new Parseconv(new Untokenizer(text).tokenize());

            Unit u = new Unit();
            u.path = f;
            u.prog = sub.parseProgram();

            java.nio.file.Path fa = f.toAbsolutePath().normalize();
            java.nio.file.Path emb = NlibEmbedded.currentDir();

            u.isNlib =
                fa.startsWith(nlibAbs) ||
                (emb != null && fa.startsWith(emb.toAbsolutePath().normalize()));

            for (Ast.Function fn : u.prog.functions) {
                IMPORTED.add(fn.name);
                fn.origin = f.getFileName().toString();
            }

            units.add(u);
        }

        for (Unit u : units)
            for (Ast.Function fn : u.prog.functions)
                if (!fn.overrideSet && u.isNlib)
                    fn.override = -1;

        java.util.List<Ast.Function> mainFunctions = new java.util.ArrayList<>(program.functions);
        java.util.List<Ast.Function> all = new java.util.ArrayList<>(program.functions);

        for (Unit u : units)
            all.addAll(u.prog.functions);

        for (Unit u : units)
            if (u.isNlib) applyOverrideAssignments(u.prog.overrides, u.prog.functions, all);

        for (Unit u : units)
            if (!u.isNlib) applyOverrideAssignments(u.prog.overrides, u.prog.functions, all);

        applyOverrideAssignments(program.overrides, mainFunctions, all);

        for (Unit u : units) {

            for (Ast.Function fn : u.prog.functions) {

                int at = -1;

                for (int k = 0; k < program.functions.size(); k++)
                    if (program.functions.get(k).name.equals(fn.name)) { at = k; break; }

                if (at < 0) {
                    fn.entry = false;
                    program.functions.add(fn);
                    libraryFunctions.add(fn.name);
                    continue;
                }

                Ast.Function cur = program.functions.get(at);

                if (fn.override > cur.override) {

                    if (cur.entry)
                        continue;

                    fn.entry = false;
                    program.functions.set(at, fn);
                    libraryFunctions.add(fn.name);

                } else if (fn.override == cur.override
                           && libraryFunctions.contains(fn.name)) {

                    throw new IllegalArgumentException(
                        "'" + fn.name + "' esta definida en " + cur.origin + " y en " + fn.origin
                        + " con los mismos derechos (override=" + fn.override
                        + "): el compilador no sabe cual usar. Usa @ovr[N] o "
                        + fn.name.replace('_', '.') + ".override = N;"
                    );
                }

            }

            for (Ast.GlobalVar gv : u.prog.globals) {

                boolean have = false;

                for (Ast.GlobalVar h : program.globals)
                    if (h.name.equals(gv.name)) { have = true; break; }

                if (!have)
                    program.globals.add(gv);
            }

            for (Ast.Extern ex : u.prog.externs) {

                boolean exists = false;

                for (Ast.Extern g : program.externs)
                    if (g.name.equals(ex.name)) { exists = true; break; }

                if (!exists)
                    program.externs.add(ex);
            }
        }
    }

    private static void applyOverrideAssignments(
        java.util.Map<String, Long> map,
        java.util.List<Ast.Function> own,
        java.util.List<Ast.Function> all
    ) {
        for (java.util.Map.Entry<String, Long> e : map.entrySet()) {

            String key = e.getKey();

            if (key.startsWith("@"))
                continue;

            String name = key.replace('.', '_');

            boolean hit = false;

            for (Ast.Function fn : own)
                hit |= setRight(fn, name, e.getValue());

            if (!hit)
                for (Ast.Function fn : all)
                    setRight(fn, name, e.getValue());
        }
    }

    private static boolean setRight(Ast.Function fn, String name, long v) {

        if (fn.name.equals(name)) {
            fn.override = v;
            fn.overrideSet = true;
            fn.overrideExact = true;
            return true;
        }

        if (fn.name.startsWith(name + "_") && !fn.overrideExact) {
            fn.override = v;
            fn.overrideSet = true;
            return true;
        }

        return false;
    }

    private static void addSqPowHelper(Ast.Program program) {

        if (!Exprs.usedSqPow)
            return;

        for (Ast.Function f : program.functions)
            if (f.name.equals("__ash_sqpow")) return;

        String src =
            "func __ash_sqpow(int64 base, int64 n): int64 {\n" +
            "    int64 r = base;\n" +
            "    for (int64 i = 0; i < n; i++) {\n" +
            "        r = r * r;\n" +
            "    }\n" +
            "    return r;\n" +
            "}\n";

        boolean strict = Generator.STRICT;

        Ast.Program h =
            new Parseconv(new Untokenizer(src).tokenize()).parseProgram();

        Generator.STRICT = strict;

        for (Ast.Function f : h.functions) {
            f.entry = false;
            program.functions.add(f);
        }
    }

    private static void addPowHelper(Ast.Program program) {

        if (!Exprs.usedPow)
            return;

        for (Ast.Function f : program.functions)
            if (f.name.equals("__ash_pow")) return;

        String src =
            "func __ash_pow(int64 base, int64 ex): int64 {\n" +
            "    int64 r = 1;\n" +
            "    int64 b = base;\n" +
            "    int64 e = ex;\n" +
            "    while (e > 0) {\n" +
            "        if ((e & 1) == 1) { r = r * b; }\n" +
            "        b = b * b;\n" +
            "        e = e >> 1;\n" +
            "    }\n" +
            "    return r;\n" +
            "}\n";

        boolean strict = Generator.STRICT;

        Ast.Program h =
            new Parseconv(new Untokenizer(src).tokenize()).parseProgram();

        Generator.STRICT = strict;

        for (Ast.Function f : h.functions) {
            f.entry = false;
            program.functions.add(f);
        }
    }

    private static void addVariadicHelper(Ast.Program program) {

        boolean any = false;

        for (Ast.Function f : program.functions) {
            if (f.variadic) any = true;
            if (f.name.equals("__va_get")) return;
        }

        if (!any) return;

        StringBuilder sb = new StringBuilder();

        sb.append("func __va_get(int64 i");

        for (int k = 0; k < Funcs.VARIADIC_MAX; k++)
            sb.append(", int64 a").append(k);

        sb.append("): int64 {\n");

        for (int k = 0; k < Funcs.VARIADIC_MAX; k++)
            sb.append("    if (i == ").append(k)
              .append(") { return a").append(k).append("; }\n");

        sb.append("    return 0;\n}\n");

        boolean strict = Generator.STRICT;

        Ast.Program h =
            new Parseconv(new Untokenizer(sb.toString()).tokenize()).parseProgram();

        Generator.STRICT = strict;

        for (Ast.Function f : h.functions) {
            f.entry = false;
            program.functions.add(f);
        }
    }

    public String convert() {
        Ast.Program program = parseProgram();

        MERGED.clear();
        IMPORTED.clear();
        mergeImports(program);

        addVariadicHelper(program);
        addPowHelper(program);
        addSqPowHelper(program);

        Visibility.check(program);
        Builtins.rewrite(program);        ParamCopies.rewrite(program);
        Structs.rewrite(program);
        Cracks.rewrite(program);
        Globals.rewrite(program);

        originOffset = program.originOffset;
        bits = program.bits;

        String ir = shake(new Generator().generate(program));

        int effective = FORCED_BITS != 0 ? FORCED_BITS : program.bits;

        if (NO_LIBC && effective == 64)
            ir = noLibcRuntime(ir);

        return ir;
    }

    public static String convert(
        List<Untokenizer.Token> tokens
    ) {
        return new Parseconv(tokens).convert();
    }

    public static Result convertWithSettings(
        List<Untokenizer.Token> tokens
    ) {
        return convertWithSettings(tokens, 0);
    }

    public static Result convertWithSettings(
        List<Untokenizer.Token> tokens,
        long currentOrg
    ) {
        Parseconv p = new Parseconv(
            tokens,
            currentOrg
        );

        String ashir = p.convert();

        return new Result(
            ashir,
            p.getOriginOffset(),
            p.getBits()
        );
    }

    private Ast.Program parseProgram() {
        Ast.Program program = new Ast.Program();

        while (
            cursor.check(Type.STRING) &&
            cursor.peek().text.replace("\"", "").trim().equals("use strict")
        ) {
            cursor.advance();
            cursor.match(Type.SEMICOLON);
            Generator.STRICT = true;
        }

        while (!cursor.check(Type.EOF)) {

            if (tryParseOverrideAssignment(program))
                continue;

            if (tryParseGlobal(program))
                continue;

            if (isToken("import")) {
                program.imports.add(
                    imports.parse()
                );
                continue;
            }

            if (isToken("extern")) {
                program.externs.add(
                    externs.parse()
                );
                continue;
            }

            boolean entry = annotations.parse(program);

            Long ovr = annotations.takeOverride();

            String[] section = annotations.takeSection();

            boolean naked = annotations.takeNaked();

            boolean efiapi = annotations.takeEfiapi();

            int nGlobals = program.globals.size();
            if (tryParseGlobal(program)) {
                if (section != null)
                    for (int q = nGlobals; q < program.globals.size(); q++)
                        program.globals.get(q).section = section;
                continue;
            }

            if (isToken("import")) {
                program.imports.add(imports.parse());
                continue;
            }

            if (isToken("extern")) {
                program.externs.add(externs.parse());
                continue;
            }

            boolean isPrivate = false;

            if (isToken("pub") || isToken("priv")) {
                isPrivate = isToken("priv");
                cursor.advance();
            }

            if (isToken("class")) {

                cursor.advance();

                String className =
                    cursor.expect(Type.IDENT, "class name").text;

                if (cursor.match(Type.LPAREN))
                    cursor.expect(Type.RPAREN, ")");

                cursor.expect(Type.LBRACE, "{");

                while (!cursor.check(Type.RBRACE)) {

                    if (cursor.check(Type.EOF))
                        throw cursor.error(
                            "class '" + className + "' is missing a closing '}'"
                        );

                    boolean memberPrivate = isPrivate;

                    if (isToken("pub") || isToken("priv")) {
                        memberPrivate = isToken("priv") || isPrivate;
                        cursor.advance();
                    }

                    if (!isToken("func")) {
                        throw cursor.error(
                            "expected 'func' inside class '" + className + "'"
                        );
                    }

                    Ast.Function m =
                        funcs.parseFunction();

                    m.name = className + "_" + m.name;
                    m.isPrivate = memberPrivate;
                    m.owner = className;

                    if (ovr != null) {
                        m.override = ovr;
                        m.overrideSet = true;
                    }

                    program.functions.add(m);
                }

                cursor.expect(Type.RBRACE, "}");

                continue;
            }

            if (!isToken("func")) {
                throw cursor.error(
                    "expected 'func', 'class', 'extern' or 'import'"
                );
            }

            Ast.Function fn =
                funcs.parseFunction();

            fn.entry = entry;
            fn.isPrivate = isPrivate;
            fn.section = section;
            fn.naked = naked;
            fn.efiapi = efiapi;

            if (ovr != null) {
                fn.override = ovr;
                fn.overrideSet = true;
            }

            program.functions.add(fn);
        }

        return program;
    }

    private boolean tryParseGlobal(Ast.Program program) {

        int i = 0;
        boolean general = false, ungeneral = false, isStatic = false, runnable = false;

        while (cursor.peekAt(i).type == Type.IDENT) {

            String t = cursor.peekAt(i).text;

            if (t.equals("general")) general = true;
            else if (t.equals("ungeneral")) ungeneral = true;
            else if (t.equals("static")) isStatic = true;
            else if (t.equals("runnable")) runnable = true;
            else if (t.equals("const") || t.equals("pub") || t.equals("priv")) { }
            else break;

            i++;
        }

        Token typeTok = cursor.peekAt(i);

        if (typeTok.type != Type.IDENT || !Nums.isType(typeTok.text))
            return false;

        String norm = Nums.normalizeType(typeTok.text);

        String ptrRaw = null;
        if (cursor.peekAt(i + 1).type == Type.STAR &&
            (Structs.has(typeTok.text) || Globals.bits(norm) != 0 || typeTok.text.equals("void"))) {
            StringBuilder st = new StringBuilder(typeTok.text);
            int k = i + 1;
            while (cursor.peekAt(k).type == Type.STAR) { st.append('*'); k++; }
            if (cursor.peekAt(k).type == Type.IDENT) {
                ptrRaw = st.toString();

                for (int q = i + 1; q < k; q++) cursor.removeAt(i + 1);
                norm = "qword";
            }
        }

        if (Globals.bits(norm) == 0)
            return false;

        if (cursor.peekAt(i + 1).type == Type.LBRACKET)
            return parseGlobalArray(program, i, general, ungeneral, isStatic, runnable);

        if (runnable)
            throw cursor.error("'runnable' solo se aplica a arrays: runnable byte[] codigo = {0xC3};");

        if (cursor.peekAt(i + 1).type != Type.IDENT)
            return false;

        Token nameTok = cursor.peekAt(i + 1);

        if (!general && !ungeneral)
            throw cursor.error(
                "variable '" + nameTok.text + "' declared outside a function: at top level only "
                + "general, ungeneral, extern, import, define, func, class and annotations are allowed"
                + "\nhint: a variable declared outside a function must say who can see it. "
                + "Write 'general " + typeTok.text + " " + nameTok.text + " = ...;' (public: visible to C and to other files "
                + "under the name '" + nameTok.text + "') or 'ungeneral " + typeTok.text + " " + nameTok.text
                + " = ...;' (private to this script). "
                + "Add 'static' when the value must change at run time ('general static " + typeTok.text + " "
                + nameTok.text + " = ...;'); without 'static' the variable is a constant."
            );

        for (int n = 0; n < i + 2; n++)
            cursor.advance();

        if (general && ungeneral)
            throw cursor.error("'" + nameTok.text + "': general y ungeneral a la vez");

        Ast.GlobalVar g = new Ast.GlobalVar();
        g.name = nameTok.text;
        g.rawType = ptrRaw != null ? ptrRaw : typeTok.text;
        g.type = norm;
        g.isStatic = isStatic;
        g.general = general || !ungeneral && false;
        g.ungeneral = ungeneral;

        if (cursor.match(Type.ASSIGN)) {

            Ast.Expression init = new Exprs(cursor).parseExpression();

            java.math.BigInteger v = Globals.constEval(init);

            if (v == null)
                throw cursor.error("el valor inicial de la global '" + g.name + "' debe ser constante");

            try {
                Globals.checkRange(g.name, g.rawType, g.type, v);
            } catch (IllegalStateException ex) {
                throw cursor.error(ex.getMessage());
            }

            g.value = v.longValue();
        }

        cursor.match(Type.SEMICOLON);

        for (Ast.GlobalVar h : program.globals)
            if (h.name.equals(g.name))
                throw cursor.error("la global '" + g.name + "' ya esta definida");

        program.globals.add(g);

        return true;
    }

    private boolean parseGlobalArray(
        Ast.Program program, int i,
        boolean general, boolean ungeneral, boolean isStatic, boolean runnable
    ) {

        Token typeTok = cursor.peekAt(i);

        if (!general && !ungeneral && !runnable)
            throw cursor.error(
                "array '" + cursor.peekAt(i + 2).text + "' declared outside a function: write "
                + "'general " + typeTok.text + "[] nombre = {...};' or 'ungeneral ...'"
            );

        for (int n = 0; n <= i; n++)
            cursor.advance();

        cursor.expect(Type.LBRACKET, "[");

        int count = -1;

        if (!cursor.check(Type.RBRACKET)) {
            java.math.BigInteger n = Globals.constEval(new Exprs(cursor).parseExpression());
            if (n == null || n.signum() < 0)
                throw cursor.error("el tamano de un array global debe ser una constante >= 0");
            count = n.intValue();
        }

        cursor.expect(Type.RBRACKET, "]");

        Token nameTok = cursor.expect(Type.IDENT, "nombre del array");

        Ast.GlobalVar g = new Ast.GlobalVar();
        g.name = nameTok.text;
        g.rawType = typeTok.text;
        g.type = "qword";
        g.elemType = Nums.valueType(typeTok.text);
        g.isStatic = isStatic || runnable;
        g.general = general;
        g.ungeneral = ungeneral || !general;
        g.runnable = runnable;
        g.elements = new java.util.ArrayList<>();

        int esz = Globals.bits(g.elemType) / 8;

        if (cursor.match(Type.ASSIGN)) {

            if (cursor.check(Type.STRING)) {

                if (esz != 1)
                    throw cursor.error("solo los arrays de bytes/char se inicializan con texto");

                StringBuilder txt = new StringBuilder();
                while (cursor.check(Type.STRING))
                    txt.append(StringConv.decodeEscapes(StringConv.stripQuotes(cursor.advance().text)));

                byte[] u = txt.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                for (byte x : u)
                    g.elements.add(java.math.BigInteger.valueOf(x & 0xFF));
                g.elements.add(java.math.BigInteger.ZERO);

            } else {

                cursor.expect(Type.LBRACE, "{");

                while (!cursor.check(Type.RBRACE)) {

                    java.math.BigInteger v = Globals.constEval(new Exprs(cursor).parseExpression());

                    if (v == null)
                        throw cursor.error("los elementos del array global '" + g.name + "' deben ser constantes");

                    g.elements.add(v);

                    if (!cursor.match(Type.COMMA))
                        break;
                }

                cursor.expect(Type.RBRACE, "}");
            }
        }

        if (count < 0)
            count = g.elements.size();

        if (g.elements.size() > count)
            throw cursor.error("el array '" + g.name + "' tiene " + g.elements.size()
                + " valores pero su tamano es " + count);

        g.count = count;

        cursor.match(Type.SEMICOLON);

        for (Ast.GlobalVar h : program.globals)
            if (h.name.equals(g.name))
                throw cursor.error("la global '" + g.name + "' ya esta definida");

        program.globals.add(g);

        return true;
    }

    private boolean tryParseOverrideAssignment(Ast.Program program) {

        int i = 0;
        StringBuilder key = new StringBuilder();

        if (cursor.peekAt(0).type == Type.AT) {
            key.append('@');
            i = 1;
        }

        if (cursor.peekAt(i).type != Type.IDENT)
            return false;

        key.append(cursor.peekAt(i).text);
        i++;

        while (
            cursor.peekAt(i).type == Type.DOT &&
            cursor.peekAt(i + 1).type == Type.IDENT
        ) {
            key.append('.').append(cursor.peekAt(i + 1).text);
            i += 2;
        }

        String k = key.toString();

        if (!(k.endsWith(".override") || k.endsWith(".ovr")))
            return false;

        if (cursor.peekAt(i).type != Type.ASSIGN)
            return false;

        i++;

        boolean neg = false;

        if (cursor.peekAt(i).type == Type.MINUS) {
            neg = true;
            i++;
        }

        if (cursor.peekAt(i).type != Type.NUMBER)
            return false;

        String t = cursor.peekAt(i).text.replace("_", "");

        java.math.BigInteger b;

        try {
            b = t.startsWith("0x") || t.startsWith("0X")
                ? new java.math.BigInteger(t.substring(2), 16)
                : new java.math.BigInteger(t);
        } catch (NumberFormatException e) {
            throw cursor.error("valor de override invalido: " + t);
        }

        if (neg) b = b.negate();

        if (b.bitLength() > 63)
            throw cursor.error("override fuera del rango int64 con signo: " + b);

        i++;

        if (cursor.peekAt(i).type == Type.SEMICOLON)
            i++;

        for (int n = 0; n < i; n++)
            cursor.advance();

        k = k.substring(0, k.lastIndexOf('.'));

        program.overrides.put(k, b.longValue());

        if (k.startsWith("@"))
            Annotations.RIGHTS.put(k, b.longValue());

        return true;
    }

    private boolean isToken(String value) {
        if (cursor.check(Type.EOF)) {
            return false;
        }

        return value.equals(
            cursor.peek().text
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
