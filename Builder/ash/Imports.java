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

package Builder.ash;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import Builder.Marked;
import Builder.Untokenizer.Token;
import Builder.Untokenizer.Type;
import Builder.ash.Ast.Import;

public final class Imports {

    private final Cursor c;

    private static final Pattern FUNCTION_PATTERN =
        Pattern.compile(
            "(?:pub\\s+)?func\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(",
            Pattern.MULTILINE
        );

    private static final Pattern CALL_PATTERN =
        Pattern.compile(
            "\\b([A-Za-z_][A-Za-z0-9_]*)\\s*\\(",
            Pattern.MULTILINE
        );

    private static final Set<String> NON_FUNCTION_CALLS =
        Set.of(
            "if",
            "while",
            "for",
            "func",
            "return",
            "sizeof",
            "typeof",
            "cast"
        );

    public Imports(Cursor c) {
        this.c = c;
    }

    public Import parse() {
        if (!is("import")) {
            Marked.error(
                "test.ash",
                5,
                10,
                "@Entry func main(): uint64 {",
                8,
                "Se esperaba 'func', 'extern' o 'import'"
            );
        }

        c.advance();

        if (c.match(Type.LT)) {
            String path = parsePath(Type.GT);

            c.expect(Type.GT, ">");

            c.match(Type.SEMICOLON);

            return new Import(path, true);
        }

        if (c.check(Type.STRING)) {
            String token = c.advance().text;
            String path = StringConv.stripQuotes(token);

            c.match(Type.SEMICOLON);

            return new Import(path, false);
        }

        throw c.error(
            "Se esperaba '<biblioteca>' o \"biblioteca\" después de import"
        );
    }

    private String parsePath(Type end) {
        StringBuilder path = new StringBuilder();

        while (!c.check(end) && !c.check(Type.EOF)) {
            path.append(c.advance().text);
        }

        if (path.length() == 0) {
            throw c.error(
                "La ruta de import no puede estar vacía"
            );
        }

        return path.toString().trim();
    }

    private boolean is(String value) {
        if (c.check(Type.EOF)) {
            return false;
        }

        return value.equals(c.peek().text);
    }

    public static Path resolve(
        Import imp,
        Path nlib,
        Path projectRoot
    ) {
        if (imp == null) {
            throw new IllegalArgumentException(
                "Import nulo"
            );
        }

        if (imp.system) {
            return resolveNlib(
                imp.path,
                nlib
            );
        }

        return resolveProject(
            imp.path,
            projectRoot
        );
    }

    private static Path resolveNlib(
        String name,
        Path nlib
    ) {
        String normalized = normalizeSystem(name);

        if (nlib != null) {

            Path source = nlib.resolve(
                normalized + ".ash"
            ).normalize();

            if (Files.isRegularFile(source)) {
                return source;
            }

            Path library = nlib.resolve(
                normalized + ".a"
            ).normalize();

            if (Files.isRegularFile(library)) {
                return library;
            }
        }

        for (String ext : new String[] { ".ash", ".a" }) {

            if (NlibEmbedded.has(normalized + ext)) {

                Path emb = NlibEmbedded.materialize()
                    .resolve(normalized + ext)
                    .normalize();

                if (Files.isRegularFile(emb)) {
                    return emb;
                }
            }
        }

        throw new IllegalStateException(
            "No se encontró la biblioteca <"
            + name
            + "> ni en nlib ("
            + (nlib == null ? "sin carpeta" : nlib.toString())
            + ") ni embebida. Disponibles: "
            + NlibEmbedded.names()
        );
    }

    private static Path resolveProject(
        String name,
        Path root
    ) {
        if (root == null) {
            throw new IllegalArgumentException(
                "La raíz del proyecto no puede ser null"
            );
        }

        if (name != null && name.trim().endsWith(".ash")) {

            String rel = name.trim();

            Path direct = root.resolve(rel).normalize();

            if (Files.isRegularFile(direct))
                return direct;

            Path parent = root.getParent();

            if (parent != null) {
                Path up = parent.resolve(rel).normalize();
                if (Files.isRegularFile(up))
                    return up;
            }

            Path cwd = Paths.get(rel).toAbsolutePath().normalize();

            if (Files.isRegularFile(cwd))
                return cwd;
        }

        String normalized = normalize(name);

        Path source = root.resolve(
            normalized + ".ash"
        ).normalize();

        if (Files.isRegularFile(source)) {
            return source;
        }

        Path library = root.resolve(
            normalized + ".a"
        ).normalize();

        if (Files.isRegularFile(library)) {
            return library;
        }

        throw new IllegalStateException(
            "No se encontró la biblioteca \""
            + name
            + "\" en el proyecto: "
            + source
        );
    }

    private static String normalizeSystem(String path) {
        if (path == null) {
            throw new IllegalArgumentException(
                "Ruta nativa nula"
            );
        }

        String result = path.trim();

        while (result.startsWith(".")) {
            result = result.substring(1);
        }

        while (result.startsWith("/")) {
            result = result.substring(1);
        }

        while (result.endsWith("/")) {
            result = result.substring(
                0,
                result.length() - 1
            );
        }

        if (result.isEmpty()) {
            throw new IllegalArgumentException(
                "Ruta nativa vacía"
            );
        }

        if (result.contains("..")) {
            throw new IllegalArgumentException(
                "Ruta de import inválida: " + path
            );
        }

        if (result.contains("/")) {
            throw new IllegalArgumentException(
                "Las bibliotecas de nlib deben estar "
                + "directamente dentro de nlib/: <"
                + path
                + ">"
            );
        }

        if (result.contains(".")) {
            throw new IllegalArgumentException(
                "Las bibliotecas de nlib usan nombres simples: <"
                + path
                + ">"
            );
        }

        return result;
    }

    private static String normalize(String path) {
        if (path == null) {
            throw new IllegalArgumentException(
                "Ruta de import nula"
            );
        }

        String result = path.trim();

        while (result.startsWith(".")) {
            result = result.substring(1);
        }

        while (result.startsWith("/")) {
            result = result.substring(1);
        }

        while (result.endsWith("/")) {
            result = result.substring(
                0,
                result.length() - 1
            );
        }

        result = result.replace('\\', '/');
        result = result.replace('.', '/');

        while (result.contains("//")) {
            result = result.replace("//", "/");
        }

        if (result.isEmpty()) {
            throw new IllegalArgumentException(
                "Ruta de import vacía"
            );
        }

        if (result.contains("..")) {
            throw new IllegalArgumentException(
                "Ruta de import inválida: " + path
            );
        }

        return result;
    }

    public static List<Path> resolveAll(
        List<Import> imports,
        Path nlib,
        Path projectRoot
    ) {
        LinkedHashSet<Path> result =
            new LinkedHashSet<>();

        if (imports == null) {
            return new ArrayList<>();
        }

        Deque<Import> pending =
            new ArrayDeque<>(imports);

        Set<Path> visited =
            new HashSet<>();

        while (!pending.isEmpty()) {
            Import imp = pending.removeFirst();

            if (imp == null) {
                continue;
            }

            Path path = resolve(
                imp,
                nlib,
                projectRoot
            ).normalize().toAbsolutePath();

            if (!visited.add(path)) {
                continue;
            }

            result.add(path);

            if (!Files.isRegularFile(path)) {
                continue;
            }

            List<Import> nested =
                readImports(path);

            for (Import nestedImport : nested) {

                if (!nestedImport.system && path.getParent() != null) {
                    String rel = nestedImport.path.trim();
                    Path local = path.getParent().resolve(rel.endsWith(".ash") ? rel : rel + ".ash").normalize();
                    if (Files.isRegularFile(local))
                        nestedImport = new Import(local.toString(), false);
                }

                Path nestedPath =
                    resolve(
                        nestedImport,
                        nlib,
                        projectRoot
                    ).normalize().toAbsolutePath();

                if (!visited.contains(nestedPath)) {
                    pending.addLast(nestedImport);
                }
            }
        }

        return new ArrayList<>(result);
    }

    private static List<Import> readImports(Path source) {
        List<Import> result =
            new ArrayList<>();

        try {
            String text =
                Files.readString(source)

                    .replaceAll("(?s)/\\*.*?\\*/", " ")
                    .replaceAll("//[^\\n]*", "");

            Matcher system =
                Pattern.compile(
                    "\\bimport\\s*<([^>]+)>\\s*;?"
                ).matcher(text);

            while (system.find()) {
                result.add(
                    new Import(
                        system.group(1).trim(),
                        true
                    )
                );
            }

            Matcher project =
                Pattern.compile(
                    "\\bimport\\s*\"([^\"]+)\"\\s*;?"
                ).matcher(text);

            while (project.find()) {
                result.add(
                    new Import(
                        project.group(1).trim(),
                        false
                    )
                );
            }

        } catch (Exception e) {
            throw new IllegalStateException(
                "No se pudo leer el import: "
                + source,
                e
            );
        }

        return result;
    }

    public static Map<String, Path> indexFunctions(
        List<Path> sources
    ) {
        Map<String, Path> functions =
            new LinkedHashMap<>();

        if (sources == null) {
            return functions;
        }

        for (Path source : sources) {
            if (source == null) {
                continue;
            }

            String text = readSource(source);

            Matcher matcher =
                FUNCTION_PATTERN.matcher(text);

            while (matcher.find()) {
                String name =
                    matcher.group(1);

                if (!functions.containsKey(name)) {
                    functions.put(
                        name,
                        source
                    );
                }
            }
        }

        return functions;
    }

    public static Map<String, String> readFunctions(
        List<Path> sources
    ) {
        Map<String, String> functions =
            new LinkedHashMap<>();

        if (sources == null) {
            return functions;
        }

        for (Path source : sources) {
            if (source == null) {
                continue;
            }

            String text = readSource(source);

            Matcher matcher =
                FUNCTION_PATTERN.matcher(text);

            while (matcher.find()) {
                String name =
                    matcher.group(1);

                int open =
                    text.indexOf(
                        '{',
                        matcher.end()
                    );

                if (open < 0) {
                    continue;
                }

                int close =
                    findMatchingBrace(
                        text,
                        open
                    );

                if (close < 0) {
                    continue;
                }

                String body =
                    text.substring(
                        open + 1,
                        close
                    );

                functions.putIfAbsent(
                    name,
                    body
                );
            }
        }

        return functions;
    }

    public static Set<String> findCalls(
        String functionBody
    ) {
        LinkedHashSet<String> calls =
            new LinkedHashSet<>();

        if (functionBody == null ||
            functionBody.isEmpty()) {
            return calls;
        }

        Matcher matcher =
            CALL_PATTERN.matcher(
                functionBody
            );

        while (matcher.find()) {
            String name =
                matcher.group(1);

            if (NON_FUNCTION_CALLS.contains(name)) {
                continue;
            }

            calls.add(name);
        }

        return calls;
    }

    public static LinkedHashSet<String> resolveFunctionDependencies(
        String entryFunction,
        List<Path> sources
    ) {
        if (entryFunction == null ||
            entryFunction.trim().isEmpty()) {
            throw new IllegalArgumentException(
                "La función inicial no puede estar vacía"
            );
        }

        Map<String, String> functions =
            readFunctions(sources);

        LinkedHashSet<String> result =
            new LinkedHashSet<>();

        Deque<String> pending =
            new ArrayDeque<>();

        pending.add(entryFunction);

        while (!pending.isEmpty()) {
            String function =
                pending.removeFirst();

            if (!result.add(function)) {
                continue;
            }

            String body =
                functions.get(function);

            if (body == null) {
                continue;
            }

            for (String called :
                    findCalls(body)) {

                if (functions.containsKey(called) &&
                    !result.contains(called)) {
                    pending.addLast(called);
                }
            }
        }

        return result;
    }

    public static List<Path> resolveFunctionSources(
        String entryFunction,
        List<Path> sources
    ) {
        if (entryFunction == null ||
            entryFunction.trim().isEmpty()) {
            throw new IllegalArgumentException(
                "La función inicial no puede estar vacía"
            );
        }

        Map<String, Path> index =
            indexFunctions(sources);

        LinkedHashSet<String> functions =
            resolveFunctionDependencies(
                entryFunction,
                sources
            );

        LinkedHashSet<Path> result =
            new LinkedHashSet<>();

        for (String function : functions) {
            Path source =
                index.get(function);

            if (source != null) {
                result.add(source);
            }
        }

        return new ArrayList<>(result);
    }

    public static List<String> resolveFunctionOrder(
        String entryFunction,
        List<Path> sources
    ) {
        LinkedHashSet<String> dependencies =
            resolveFunctionDependencies(
                entryFunction,
                sources
            );

        return new ArrayList<>(
            dependencies
        );
    }

    private static String readSource(Path source) {
        try {
            return Files.readString(source);
        } catch (Exception e) {
            throw new IllegalStateException(
                "No se pudo leer: "
                + source,
                e
            );
        }
    }

    private static int findMatchingBrace(
        String text,
        int open
    ) {
        int depth = 0;

        boolean string = false;
        boolean character = false;
        boolean lineComment = false;
        boolean blockComment = false;
        boolean escape = false;

        for (int i = open; i < text.length(); i++) {
            char ch = text.charAt(i);

            if (lineComment) {
                if (ch == '\n') {
                    lineComment = false;
                }

                continue;
            }

            if (blockComment) {
                if (ch == '*' &&
                    i + 1 < text.length() &&
                    text.charAt(i + 1) == '/') {

                    blockComment = false;
                    i++;
                }

                continue;
            }

            if (string) {
                if (escape) {
                    escape = false;
                    continue;
                }

                if (ch == '\\') {
                    escape = true;
                    continue;
                }

                if (ch == '"') {
                    string = false;
                }

                continue;
            }

            if (character) {
                if (escape) {
                    escape = false;
                    continue;
                }

                if (ch == '\\') {
                    escape = true;
                    continue;
                }

                if (ch == '\'') {
                    character = false;
                }

                continue;
            }

            if (ch == '/' &&
                i + 1 < text.length()) {

                char next =
                    text.charAt(i + 1);

                if (next == '/') {
                    lineComment = true;
                    i++;
                    continue;
                }

                if (next == '*') {
                    blockComment = true;
                    i++;
                    continue;
                }
            }

            if (ch == '"') {
                string = true;
                continue;
            }

            if (ch == '\'') {
                character = true;
                continue;
            }

            if (ch == '{') {
                depth++;
                continue;
            }

            if (ch == '}') {
                depth--;

                if (depth == 0) {
                    return i;
                }
            }
        }

        return -1;
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
