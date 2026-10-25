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

package shasm;

import shasm.i18n.I18n;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public final class Preproc {
    public static final class Line {
        public final String text, file;
        public final int no;
        Line(String t, String f, int n) { text = t; file = f; no = n; }
    }

    public static final class Doc {
        public final String kind, text, file;
        public final int no;
        Doc(String k, String t, String f, int n) { kind = k; text = t; file = f; no = n; }
    }

    public static final class Metadata {
        private final Map<String, String> values = new LinkedHashMap<>();
        public String get(String key) { return values.get(key.toLowerCase()); }
        public boolean has(String key) { return values.containsKey(key.toLowerCase()); }
        public Map<String, String> all() { return Collections.unmodifiableMap(values); }
        void put(String key, String value) { values.put(key.toLowerCase(), value); }
    }

    static final class Def {
        String name;
        List<String> params;
        String body;
        boolean ci;
    }

    static final class Macro {
        String name;
        int min, max;
        boolean greedy;
        List<String> defaults = new ArrayList<>();
        List<String> body = new ArrayList<>();
        boolean ci;
    }

    private static final Set<String> METADATA = Set.of(
        "definesdk", "author", "license", "docs",
        "mallionic", "comment", "link", "dev", "team"
    );

    private final Map<String, Def> defs = new HashMap<>();
    private final Map<String, Macro> macros = new HashMap<>();
    private final List<Line> out = new ArrayList<>();
    private final List<Doc> docs = new ArrayList<>();
    private final Metadata metadata = new Metadata();
    private final List<Path> includeDirs;

    private final Expr.Ctx constCtx = new Expr.Ctx() {
        public Long symbol(String n) { return null; }
        public long here() { return 0; }
        public long sectionStart() { return 0; }
        public Reg reg(String n) { return null; }
    };

    private int uniq = 0;
    public int bitsDefault = 16;
    public final List<String> warnings = new ArrayList<>();

    public Preproc(List<Path> includeDirs) {
        this.includeDirs = includeDirs;
    }

    public Metadata metadata() {
        return metadata;
    }

    public List<Doc> docs() {
        return Collections.unmodifiableList(docs);
    }

    public List<Line> lines() {
        return Collections.unmodifiableList(out);
    }

    public void define(String name, String body) {
        Def d = new Def();
        d.name = name;
        d.body = body;
        defs.put(name, d);
    }

    public List<Line> run(Path file) throws IOException {
        out.clear();
        docs.clear();
        metadata.values.clear();
        List<String> ls = Files.readAllLines(file, StandardCharsets.ISO_8859_1);
        feed(ls, file.toString(), 1, file.getParent(), 0);
        return out;
    }

    public List<Line> runText(String name, String text) {
        out.clear();
        docs.clear();
        metadata.values.clear();
        feed(Arrays.asList(text.split("\r?\n", -1)), name, 1, null, 0);
        return out;
    }

    static String stripComment(String l) {
        char q = 0;
        for (int i = 0; i < l.length(); i++) {
            char c = l.charAt(i);
            if (q != 0) {
                if (c == '\\' && q == '`') i++;
                else if (c == q) q = 0;
            } else if (c == '\'' || c == '"' || c == '`') {
                q = c;
            } else if (c == ';') {
                return l.substring(0, i);
            }
        }
        return l;
    }

    private static List<String> splitTop(String s) {
        List<String> r = new ArrayList<>();
        int depth = 0, br = 0;
        char q = 0;
        int st = 0;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            if (q != 0) {
                if (c == q) q = 0;
                continue;
            }

            if (c == '\'' || c == '"' || c == '`') q = c;
            else if (c == '{') depth++;
            else if (c == '}') depth--;
            else if (c == '[') br++;
            else if (c == ']') br--;
            else if (c == ',' && depth == 0 && br == 0) {
                r.add(s.substring(st, i).trim());
                st = i + 1;
            }
        }

        r.add(s.substring(st).trim());
        if (r.size() == 1 && r.get(0).isEmpty()) r.clear();
        return r;
    }

    private static boolean isId(char c) {
        return Character.isLetterOrDigit(c) ||
               c == '_' || c == '$' || c == '#' || c == '@' ||
               c == '~' || c == '.' || c == '?';
    }

    private static boolean isIdStart(char c) {
        return Character.isLetter(c) ||
               c == '_' || c == '.' || c == '?' || c == '@';
    }

    private static String unquote(String s) {
        s = s.strip();

        if (s.startsWith("\"\"\"") && s.endsWith("\"\"\"") && s.length() >= 6)
            return s.substring(3, s.length() - 3);

        if (s.length() >= 2 &&
            ((s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') ||
             (s.charAt(0) == '\'' && s.charAt(s.length() - 1) == '\'')))
            return s.substring(1, s.length() - 1);

        return s;
    }

    private static boolean startsTriple(String s) {
        return s.strip().startsWith("\"\"\"");
    }

    private static int tripleStart(String s) {
        return s.indexOf("\"\"\"");
    }

    private static int tripleEnd(String s, int from) {
        return s.indexOf("\"\"\"", from);
    }

    private String readTriple(
        List<String> lines,
        int[] index,
        String first,
        String file,
        int no
    ) {
        String s = first.strip();
        int start = tripleStart(s);

        if (start < 0)
            return unquote(s);

        int p = start + 3;
        int end = tripleEnd(s, p);

        if (end >= 0)
            return s.substring(p, end);

        StringBuilder b = new StringBuilder();
        if (p < s.length())
            b.append(s.substring(p));

        while (++index[0] < lines.size()) {
            String x = lines.get(index[0]);
            int e = tripleEnd(x, 0);

            if (e >= 0) {
                if (b.length() > 0) b.append('\n');
                b.append(x, 0, e);
                return b.toString();
            }

            if (b.length() > 0) b.append('\n');
            b.append(x);
        }

        throw new AsmError(I18n.tr("%s:%s: cadena multilinea sin cierre", file, no));
    }

    private boolean metadataLine(
        List<String> lines,
        int[] index,
        String t,
        String file,
        int no
    ) {
        if (!t.startsWith("%"))
            return false;

        Matcher m = Pattern.compile("^%([A-Za-z_][A-Za-z0-9_]*)\\b(?:\\s+(.*))?$")
            .matcher(t);

        if (!m.matches())
            return false;

        String key = m.group(1).toLowerCase();
        if (!METADATA.contains(key))
            return false;

        String value = m.group(2);

        if (value == null || value.strip().isEmpty()) {
            metadata.put(key, "");
            return true;
        }

        value = value.strip();

        if (startsTriple(value)) {
            int[] p = { index[0] };
            String v = readTriple(lines, p, value, file, no);
            index[0] = p[0];
            metadata.put(key, v);
            return true;
        }

        metadata.put(key, unquote(value));
        return true;
    }

    private boolean ashDoc(
        List<String> lines,
        int[] index,
        String raw,
        String file,
        int no
    ) {
        String s = raw.strip();

        if (s.startsWith(";@")) {
            String x = s.substring(2).strip();

            Matcher m = Pattern.compile(
                "^([A-Za-z_][A-Za-z0-9_-]*)(?:\\s+(.*))?$"
            ).matcher(x);

            if (m.matches()) {
                String kind = m.group(1);
                String text = m.group(2) == null ? "" : m.group(2).strip();
                docs.add(new Doc(kind, text, file, no));
            }

            return true;
        }

        if (s.startsWith(";*")) {
            StringBuilder b = new StringBuilder();
            String x = s.substring(2);
            int end = x.indexOf("*;");

            if (end >= 0) {
                b.append(x, 0, end);
                docs.add(new Doc("block", b.toString().strip(), file, no));
                return true;
            }

            if (!x.isEmpty())
                b.append(x);

            while (++index[0] < lines.size()) {
                String z = lines.get(index[0]);
                int e = z.indexOf("*;");

                if (e >= 0) {
                    if (b.length() > 0) b.append('\n');
                    b.append(z, 0, e);
                    docs.add(new Doc("block", b.toString().strip(), file, no));
                    return true;
                }

                if (b.length() > 0) b.append('\n');
                b.append(z);
            }

            throw new AsmError(
                I18n.tr("%s:%s: comentario AshDoc ;* sin cierre *;", file, no)
            );
        }

        if (s.startsWith(";\"\"\"")) {
            StringBuilder b = new StringBuilder();
            String x = s.substring(4);
            int end = x.indexOf("\"\"\"");

            if (end >= 0) {
                b.append(x, 0, end);
                docs.add(new Doc("block", b.toString().strip(), file, no));
                return true;
            }

            if (!x.isEmpty())
                b.append(x);

            while (++index[0] < lines.size()) {
                String z = lines.get(index[0]);
                int e = z.indexOf("\"\"\"");

                if (e >= 0) {
                    if (b.length() > 0) b.append('\n');
                    b.append(z, 0, e);
                    docs.add(new Doc("block", b.toString().strip(), file, no));
                    return true;
                }

                if (b.length() > 0) b.append('\n');
                b.append(z);
            }

            throw new AsmError(
                I18n.tr("%s:%s: comentario AshDoc ;\"\"\" sin cierre", file, no)
            );
        }

        return false;
    }

    private String cleanComments(
        List<String> lines,
        int[] index,
        String raw,
        String file,
        int no
    ) {
        String s = raw;
        StringBuilder outLine = new StringBuilder();
        int i = 0;
        char q = 0;

        while (i < s.length()) {
            char c = s.charAt(i);

            if (q != 0) {
                outLine.append(c);

                if (c == '\\' && q == '`' && i + 1 < s.length()) {
                    outLine.append(s.charAt(++i));
                } else if (c == q) {
                    q = 0;
                }

                i++;
                continue;
            }

            if (c == '\'' || c == '"' || c == '`') {
                q = c;
                outLine.append(c);
                i++;
                continue;
            }

            if (c == ';') {
                if (s.startsWith(";*", i) || s.startsWith(";\"\"\"", i))
                    break;

                break;
            }

            outLine.append(c);
            i++;
        }

        return outLine.toString().stripTrailing();
    }

    private String expand(String text, int depth, Set<String> active, String file, int lineNo) {
        if (depth > 64)
            throw new AsmError(I18n.tr("expansion de macros demasiado profunda"));

        StringBuilder o = new StringBuilder();
        int i = 0, n = text.length();

        while (i < n) {
            char c = text.charAt(i);

            if (c == '\'' || c == '"' || c == '`') {
                int j = i + 1;

                while (j < n && text.charAt(j) != c) {
                    if (c == '`' && text.charAt(j) == '\\') j++;
                    j++;
                }

                j = Math.min(n, j + 1);
                o.append(text, i, j);
                i = j;
                continue;
            }

            if (Character.isDigit(c)) {
                int j = i;

                while (j < n &&
                       (Character.isLetterOrDigit(text.charAt(j)) ||
                        text.charAt(j) == '_' ||
                        text.charAt(j) == '.'))
                    j++;

                o.append(text, i, j);
                i = j;
                continue;
            }

            if (c == '%' && i + 1 < n && text.charAt(i + 1) == '+') {
                while (o.length() > 0 && o.charAt(o.length() - 1) == ' ')
                    o.setLength(o.length() - 1);

                i += 2;
                while (i < n && text.charAt(i) == ' ') i++;
                continue;
            }

            if (c == '$' && i + 1 < n && text.charAt(i + 1) == '$') {
                o.append("$$");
                i += 2;
                continue;
            }

            if (isIdStart(c) ||
                (c == '$' && i + 1 < n && isIdStart(text.charAt(i + 1)))) {

                int j = i + 1;
                while (j < n && isId(text.charAt(j))) j++;

                String id = text.substring(i, j);

                if (id.equals("__LINE__")) {
                    o.append(lineNo);
                    i = j;
                    continue;
                }

                if (id.equals("__FILE__")) {
                    o.append('"').append(file).append('"');
                    i = j;
                    continue;
                }

                Def d = defs.get(id);

                if (d == null) {
                    d = defs.get(id.toLowerCase());
                    if (d != null && !d.ci) d = null;
                }

                if (d != null && !active.contains(d.name)) {
                    if (d.params == null) {
                        Set<String> a2 = new HashSet<>(active);
                        a2.add(d.name);

                        o.append(expand(
                            d.body,
                            depth + 1,
                            a2,
                            file,
                            lineNo
                        ));

                        i = j;
                        continue;
                    }

                    int k = j;
                    while (k < n && text.charAt(k) == ' ') k++;

                    if (k < n && text.charAt(k) == '(') {
                        int lvl = 0, st = k + 1, e = -1;
                        char q = 0;

                        for (int m = k; m < n; m++) {
                            char ch = text.charAt(m);

                            if (q != 0) {
                                if (ch == q) q = 0;
                                continue;
                            }

                            if (ch == '\'' || ch == '"') q = ch;
                            else if (ch == '(') lvl++;
                            else if (ch == ')') {
                                lvl--;
                                if (lvl == 0) {
                                    e = m;
                                    break;
                                }
                            }
                        }

                        if (e < 0)
                            throw new AsmError(
                                I18n.tr("falta ')' al invocar la macro %s", d.name)
                            );

                        List<String> args =
                            splitParen(text.substring(st, e));

                        String body = d.body;
                        Map<String, String> map = new HashMap<>();

                        for (int a = 0; a < d.params.size(); a++)
                            map.put(
                                d.params.get(a),
                                a < args.size()
                                    ? expand(
                                        args.get(a),
                                        depth + 1,
                                        active,
                                        file,
                                        lineNo
                                      ).trim()
                                    : ""
                            );

                        body = substIds(body, map);

                        Set<String> a2 = new HashSet<>(active);
                        a2.add(d.name);

                        o.append(expand(
                            body,
                            depth + 1,
                            a2,
                            file,
                            lineNo
                        ));

                        i = e + 1;
                        continue;
                    }
                }

                o.append(id);
                i = j;
                continue;
            }

            o.append(c);
            i++;
        }

        return o.toString();
    }

    private static List<String> splitParen(String s) {
        List<String> r = new ArrayList<>();
        int lvl = 0, st = 0;
        char q = 0;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            if (q != 0) {
                if (c == q) q = 0;
                continue;
            }

            if (c == '\'' || c == '"') q = c;
            else if (c == '(') lvl++;
            else if (c == ')') lvl--;
            else if (c == ',' && lvl == 0) {
                r.add(s.substring(st, i).trim());
                st = i + 1;
            }
        }

        r.add(s.substring(st).trim());
        return r;
    }

    private static String substIds(String body, Map<String, String> map) {
        StringBuilder o = new StringBuilder();
        int i = 0, n = body.length();

        while (i < n) {
            char c = body.charAt(i);

            if (isIdStart(c)) {
                int j = i + 1;
                while (j < n && isId(body.charAt(j))) j++;

                String id = body.substring(i, j);
                o.append(map.containsKey(id) ? map.get(id) : id);
                i = j;
            } else if (Character.isDigit(c)) {
                int j = i;

                while (j < n && Character.isLetterOrDigit(body.charAt(j)))
                    j++;

                o.append(body, i, j);
                i = j;
            } else {
                o.append(c);
                i++;
            }
        }

        return o.toString();
    }

    private static final class Cond {
        boolean active, taken, parentActive;
    }

    private void feed(
        List<String> raw,
        String file,
        int firstLine,
        Path dir,
        int depth
    ) {
        if (depth > 32)
            throw new AsmError(
                I18n.tr("%include / expansion demasiado anidada")
            );

        List<String> lines = new ArrayList<>();
        List<Integer> nos = new ArrayList<>();

        for (int i = 0; i < raw.size(); i++) {
            String l = raw.get(i);
            int no = firstLine + i;

            while (stripComment(l).stripTrailing().endsWith("\\") &&
                   i + 1 < raw.size()) {

                String sc = stripComment(l).stripTrailing();
                l = sc.substring(0, sc.length() - 1) +
                    " " +
                    raw.get(++i);
            }

            lines.add(l);
            nos.add(no);
        }

        Deque<Cond> conds = new ArrayDeque<>();

        for (int idx = 0; idx < lines.size(); idx++) {
            String rawLine = lines.get(idx);
            int no = nos.get(idx);

            try {
                String rawTrim = rawLine.strip();

                if (ashDoc(lines, new int[]{idx}, rawLine, file, no))
                    continue;

                if (rawTrim.startsWith(";*") ||
                    rawTrim.startsWith(";\"\"\""))
                    continue;

                int[] cursor = { idx };

                if (metadataLine(
                    lines,
                    cursor,
                    rawTrim,
                    file,
                    no
                )) {
                    idx = cursor[0];
                    continue;
                }

                String l = cleanComments(
                    lines,
                    cursor,
                    rawLine,
                    file,
                    no
                );

                if (cursor[0] != idx)
                    idx = cursor[0];

                l = l.stripTrailing();
                String t = l.strip();

                boolean live =
                    conds.isEmpty() ||
                    conds.peek().active;

                if (t.startsWith("%")) {
                    String[] w = t.split("\\s+", 2);
                    String dir0 = w[0].toLowerCase();
                    String rest = w.length > 1 ? w[1] : "";

                    switch (dir0) {
                        case "%if", "%ifn", "%ifdef", "%ifndef",
                             "%ifnum", "%ifstr", "%ifid", "%ifidn",
                             "%ifnidn", "%ifidni", "%ifmacro",
                             "%ifnmacro", "%ifempty" -> {
                            Cond c = new Cond();
                            c.parentActive = live;
                            boolean r =
                                live &&
                                condition(dir0, rest, file, no);

                            c.active = r;
                            c.taken = r;
                            conds.push(c);
                            continue;
                        }

                        case "%elif", "%elifdef", "%elifndef",
                             "%elifn", "%elifnum", "%elifidn",
                             "%elifstr" -> {
                            Cond c = conds.peek();

                            if (c == null)
                                throw new AsmError(
                                    I18n.tr("%elif sin %if")
                                );

                            if (c.taken || !c.parentActive) {
                                c.active = false;
                            } else {
                                boolean r = condition(
                                    "%if" + dir0.substring(5),
                                    rest,
                                    file,
                                    no
                                );

                                c.active = r;
                                c.taken |= r;
                            }

                            continue;
                        }

                        case "%else" -> {
                            Cond c = conds.peek();

                            if (c == null)
                                throw new AsmError(
                                    I18n.tr("%else sin %if")
                                );

                            c.active = c.parentActive && !c.taken;
                            c.taken = true;
                            continue;
                        }

                        case "%endif" -> {
                            if (conds.isEmpty())
                                throw new AsmError(
                                    I18n.tr("%endif sin %if")
                                );

                            conds.pop();
                            continue;
                        }

                        default -> { }
                    }
                }

                if (!live)
                    continue;

                if (t.isEmpty()) {
                    out.add(new Line("", file, no));
                    continue;
                }

                if (t.startsWith("%")) {
                    String[] w = t.split("\\s+", 2);
                    String d0 = w[0].toLowerCase();
                    String rest =
                        w.length > 1 ? w[1].strip() : "";

                    switch (d0) {
                        case "%define", "%xdefine",
                             "%idefine", "%ixdefine" -> {
                            doDefine(
                                rest,
                                d0.contains("x"),
                                d0.contains("i"),
                                file,
                                no
                            );
                            continue;
                        }

                        case "%undef" -> {
                            defs.remove(rest.strip());
                            continue;
                        }

                        case "%assign", "%iassign" -> {
                            Matcher m = Pattern.compile(
                                "^(\\S+)\\s+(.*)$"
                            ).matcher(rest);

                            if (!m.matches())
                                throw new AsmError(
                                    I18n.tr("%assign mal formado")
                                );

                            Val v = Expr.eval(
                                expand(
                                    m.group(2),
                                    0,
                                    new HashSet<>(),
                                    file,
                                    no
                                ),
                                constCtx
                            );

                            if (!v.known)
                                throw new AsmError(
                                    I18n.tr(
                                        "%assign requiere una expresion constante"
                                    )
                                );

                            define(
                                m.group(1),
                                Long.toString(v.v)
                            );
                            continue;
                        }

                        case "%defstr" -> {
                            Matcher m = Pattern.compile(
                                "^(\\S+)\\s+(.*)$"
                            ).matcher(rest);

                            if (!m.matches())
                                throw new AsmError(
                                    I18n.tr("%defstr mal formado")
                                );

                            define(
                                m.group(1),
                                "\"" +
                                expand(
                                    m.group(2),
                                    0,
                                    new HashSet<>(),
                                    file,
                                    no
                                ).replace("\"", "\\\"") +
                                "\""
                            );

                            continue;
                        }

                        case "%strlen" -> {
                            Matcher m = Pattern.compile(
                                "^(\\S+)\\s+(.*)$"
                            ).matcher(rest);

                            if (!m.matches())
                                throw new AsmError(
                                    I18n.tr("%strlen mal formado")
                                );

                            String s = expand(
                                m.group(2),
                                0,
                                new HashSet<>(),
                                file,
                                no
                            ).strip();

                            if (s.length() >= 2)
                                s = s.substring(1, s.length() - 1);

                            define(
                                m.group(1),
                                Integer.toString(s.length())
                            );

                            continue;
                        }

                        case "%include" -> {
                            String fn = expand(
                                rest,
                                0,
                                new HashSet<>(),
                                file,
                                no
                            ).strip();

                            if (fn.length() >= 2 &&
                                (fn.charAt(0) == '"' ||
                                 fn.charAt(0) == '\'' ||
                                 fn.charAt(0) == '<')) {
                                fn = fn.substring(
                                    1,
                                    fn.length() - 1
                                );
                            }

                            Path p = findFile(fn, dir);

                            try {
                                feed(
                                    Files.readAllLines(
                                        p,
                                        StandardCharsets.ISO_8859_1
                                    ),
                                    p.toString(),
                                    1,
                                    p.getParent(),
                                    depth + 1
                                );
                            } catch (IOException e) {
                                throw new AsmError(
                                    I18n.tr(
                                        "no se puede abrir %%include '%s'",
                                        fn
                                    )
                                );
                            }

                            continue;
                        }

                        case "%macro", "%imacro" -> {
                            Macro m = parseMacroHeader(
                                rest,
                                d0.equals("%imacro")
                            );

                            int lvl = 1;
                            idx++;

                            for (; idx < lines.size(); idx++) {
                                String bl =
                                    stripComment(lines.get(idx)).strip();

                                String bw =
                                    bl.split("\\s+")[0].toLowerCase();

                                if (bw.equals("%macro") ||
                                    bw.equals("%imacro"))
                                    lvl++;

                                if (bw.equals("%endmacro")) {
                                    if (--lvl == 0)
                                        break;
                                }

                                m.body.add(bl);
                            }

                            if (idx >= lines.size())
                                throw new AsmError(
                                    I18n.tr(
                                        "%macro sin %endmacro"
                                    )
                                );

                            macros.put(
                                m.ci ? m.name.toLowerCase() : m.name,
                                m
                            );

                            continue;
                        }

                        case "%rep" -> {
                            Val v = Expr.eval(
                                expand(
                                    rest,
                                    0,
                                    new HashSet<>(),
                                    file,
                                    no
                                ),
                                constCtx
                            );

                            if (!v.known)
                                throw new AsmError(
                                    I18n.tr(
                                        "%rep requiere una expresion constante"
                                    )
                                );

                            List<String> body =
                                new ArrayList<>();

                            int lvl = 1;
                            idx++;

                            for (; idx < lines.size(); idx++) {
                                String bl =
                                    stripComment(lines.get(idx)).strip();

                                String bw =
                                    bl.split("\\s+")[0].toLowerCase();

                                if (bw.equals("%rep"))
                                    lvl++;

                                if (bw.equals("%endrep")) {
                                    if (--lvl == 0)
                                        break;
                                }

                                body.add(lines.get(idx));
                            }

                            if (idx >= lines.size())
                                throw new AsmError(
                                    I18n.tr(
                                        "%rep sin %endrep"
                                    )
                                );

                            if (v.v < 0 || v.v > 1_000_000)
                                throw new AsmError(
                                    I18n.tr(
                                        "conteo de %rep invalido"
                                    )
                                );

                            for (long r = 0; r < v.v; r++)
                                feed(
                                    body,
                                    file,
                                    no + 1,
                                    dir,
                                    depth + 1
                                );

                            continue;
                        }

                        case "%error", "%fatal" ->
                            throw new AsmError(
                                expand(
                                    rest,
                                    0,
                                    new HashSet<>(),
                                    file,
                                    no
                                )
                            );

                        case "%warning" -> {
                            warnings.add(
                                I18n.tr(
                                    "%s:%s: aviso: %s",
                                    file,
                                    no,
                                    expand(
                                        rest,
                                        0,
                                        new HashSet<>(),
                                        file,
                                        no
                                    )
                                )
                            );
                            continue;
                        }

                        case "%pragma", "%line",
                            "%clear", "%null" -> {
                            continue;
}

                        default ->
                            throw new AsmError(
                                I18n.tr(
                                    "directiva de preprocesador desconocida: %s",
                                    w[0]
                                )
                            );
                    }
                }

                String lead = "";
                String stmt = t;

                Matcher lm = Pattern.compile(
                    "^([A-Za-z_.?@$][\\w$#@~.?]*:)\\s*(.*)$"
                ).matcher(t);

                if (lm.matches()) {
                    lead = lm.group(1) + " ";
                    stmt = lm.group(2);
                }

                Matcher wm = Pattern.compile(
                    "^([A-Za-z_.?@$][\\w$#@~.?]*)(?:\\s+(.*))?$"
                ).matcher(stmt);

                if (wm.matches()) {
                    Macro m = macros.get(wm.group(1));

                    if (m == null) {
                        m = macros.get(
                            wm.group(1).toLowerCase()
                        );

                        if (m != null && !m.ci)
                            m = null;
                    }

                    if (m != null) {
                        if (!lead.isEmpty())
                            out.add(
                                new Line(
                                    lead.trim(),
                                    file,
                                    no
                                )
                            );

                        String argstr =
                            wm.group(2) == null
                                ? ""
                                : expand(
                                    wm.group(2),
                                    0,
                                    new HashSet<>(),
                                    file,
                                    no
                                );

                        invoke(
                            m,
                            argstr,
                            file,
                            no,
                            depth
                        );

                        continue;
                    }
                }

                out.add(
                    new Line(
                        expand(
                            t,
                            0,
                            new HashSet<>(),
                            file,
                            no
                        ),
                        file,
                        no
                    )
                );

            } catch (AsmError e) {
                throw new AsmError(
                    I18n.tr(
                        "%s:%s: error: %s",
                        file,
                        no,
                        e.getMessage()
                    )
                );
            }
        }

        if (!conds.isEmpty())
            throw new AsmError(
                I18n.tr(
                    "%s: error: %%if sin %%endif",
                    file
                )
            );
    }

    private Path findFile(String fn, Path dir) {
        List<Path> cand = new ArrayList<>();

        if (dir != null)
            cand.add(dir.resolve(fn));

        cand.add(Paths.get(fn));

        for (Path d : includeDirs)
            cand.add(d.resolve(fn));

        for (Path p : cand)
            if (Files.exists(p))
                return p;

        throw new AsmError(
            I18n.tr(
                "no se encuentra el fichero '%s'",
                fn
            )
        );
    }

    private boolean condition(
        String d,
        String rest,
        String file,
        int no
    ) {
        boolean neg = false;
        String base = d;

        if (base.startsWith("%ifn") &&
            !base.equals("%ifndef") &&
            !base.equals("%ifnum") &&
            !base.equals("%ifnidn") &&
            !base.equals("%ifnmacro")) {

            neg = true;
            base = "%if" + base.substring(4);
        }

        boolean r;

        switch (base) {
            case "%ifdef" ->
                r = isDefined(rest.strip());

            case "%ifndef" ->
                r = !isDefined(rest.strip());

            case "%ifmacro" ->
                r = macros.containsKey(
                    rest.strip().split("\\s+")[0]
                );

            case "%ifnmacro" ->
                r = !macros.containsKey(
                    rest.strip().split("\\s+")[0]
                );

            case "%ifempty" ->
                r = expand(
                    rest,
                    0,
                    new HashSet<>(),
                    file,
                    no
                ).isBlank();

            case "%ifnum" -> {
                Val v;

                try {
                    v = Expr.eval(
                        expand(
                            rest,
                            0,
                            new HashSet<>(),
                            file,
                            no
                        ),
                        constCtx
                    );

                    r = v.known;
                } catch (AsmError e) {
                    r = false;
                }
            }

            case "%ifstr" -> {
                String s =
                    expand(
                        rest,
                        0,
                        new HashSet<>(),
                        file,
                        no
                    ).strip();

                r =
                    s.length() >= 2 &&
                    (s.charAt(0) == '"' ||
                     s.charAt(0) == '\'');
            }

            case "%ifid" ->
                r =
                    expand(
                        rest,
                        0,
                        new HashSet<>(),
                        file,
                        no
                    ).strip().matches(
                        "[A-Za-z_.?@$][\\w$#@~.?]*"
                    );

            case "%ifidn", "%ifnidn", "%ifidni" -> {
                List<String> a =
                    splitTop(
                        expand(
                            rest,
                            0,
                            new HashSet<>(),
                            file,
                            no
                        )
                    );

                if (a.size() != 2)
                    throw new AsmError(
                        I18n.tr(
                            "%s requiere dos argumentos",
                            d
                        )
                    );

                boolean eq =
                    base.equals("%ifidni")
                        ? a.get(0).equalsIgnoreCase(a.get(1))
                        : a.get(0).equals(a.get(1));

                r =
                    base.equals("%ifnidn")
                        ? !eq
                        : eq;
            }

            default -> {
                Val v =
                    Expr.eval(
                        expand(
                            rest,
                            0,
                            new HashSet<>(),
                            file,
                            no
                        ),
                        constCtx
                    );

                if (!v.known)
                    throw new AsmError(
                        I18n.tr(
                            "%if requiere una expresion constante"
                        )
                    );

                r = v.v != 0;
            }
        }

        return neg ? !r : r;
    }

    private boolean isDefined(String n) {
        return defs.containsKey(n) ||
               macros.containsKey(n);
    }

    private void doDefine(
        String rest,
        boolean eager,
        boolean ci,
        String file,
        int no
    ) {
        Matcher m = Pattern.compile(
            "^([A-Za-z_.?@$][\\w$#@~.?]*)(\\(([^)]*)\\))?\\s*(.*)$"
        ).matcher(rest);

        if (!m.matches())
            throw new AsmError(
                I18n.tr("%define mal formado")
            );

        Def d = new Def();

        d.name =
            ci
                ? m.group(1).toLowerCase()
                : m.group(1);

        d.ci = ci;

        if (m.group(2) != null) {
            d.params = new ArrayList<>();

            for (String p : m.group(3).split(","))
                if (!p.isBlank())
                    d.params.add(p.trim());
        }

        d.body =
            eager
                ? expand(
                    m.group(4),
                    0,
                    new HashSet<>(),
                    file,
                    no
                )
                : m.group(4);

        defs.put(d.name, d);
    }

    private Macro parseMacroHeader(
        String rest,
        boolean ci
    ) {
        String[] w = rest.split("\\s+", 3);

        if (w.length < 2)
            throw new AsmError(
                I18n.tr(
                    "%macro requiere nombre y numero de parametros"
                )
            );

        Macro m = new Macro();
        m.name = w[0];
        m.ci = ci;

        String cnt = w[1];

        if (cnt.endsWith("+")) {
            m.greedy = true;
            cnt = cnt.substring(0, cnt.length() - 1);
        }

        if (cnt.contains("-")) {
            String[] mm = cnt.split("-");

            m.min = Integer.parseInt(mm[0]);
            m.max =
                mm[1].equals("*")
                    ? 1 << 20
                    : Integer.parseInt(mm[1]);

        } else {
            m.min = m.max =
                Integer.parseInt(cnt);
        }

        if (w.length > 2)
            for (String s : splitTop(w[2]))
                m.defaults.add(s);

        return m;
    }

    private void invoke(
        Macro m,
        String argstr,
        String file,
        int no,
        int depth
    ) {
        List<String> args = splitTop(argstr);

        if (m.greedy && args.size() > m.max) {
            List<String> a2 =
                new ArrayList<>(
                    args.subList(0, m.max - 1)
                );

            a2.add(
                String.join(
                    ", ",
                    args.subList(
                        m.max - 1,
                        args.size()
                    )
                )
            );

            args = a2;
        }

        if (args.size() < m.min ||
            args.size() > m.max)
            throw new AsmError(
                I18n.tr(
                    "numero de parametros incorrecto para la macro '%s'",
                    m.name
                )
            );

        for (
            int i = args.size();
            i < m.max && i < 64;
            i++
        ) {
            int di = i - m.min;

            if (di >= 0 &&
                di < m.defaults.size())
                args.add(m.defaults.get(di));
            else
                break;
        }

        String uid = "..@" + (++uniq) + ".";
        List<String> body = new ArrayList<>();

        for (String bl : m.body) {
            String x = bl;

            x = x.replace(
                "%0",
                Integer.toString(args.size())
            );

            Matcher pm = Pattern.compile(
                "%(-?\\d+)|%\\{(-?\\d+)\\}|%%([\\w$#@~.?]+)"
            ).matcher(x);

            StringBuffer sb = new StringBuffer();

            while (pm.find()) {
                String rep;

                if (pm.group(3) != null) {
                    rep = uid + pm.group(3);
                } else {
                    int k =
                        Integer.parseInt(
                            pm.group(1) != null
                                ? pm.group(1)
                                : pm.group(2)
                        );

                    rep =
                        k >= 1 && k <= args.size()
                            ? args.get(k - 1)
                            : "";

                    if (k < 1)
                        rep = "%" + k;
                }

                pm.appendReplacement(
                    sb,
                    Matcher.quoteReplacement(rep)
                );
            }

            pm.appendTail(sb);
            body.add(sb.toString());
        }

        feed(
            body,
            file,
            no,
            null,
            depth + 1
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
