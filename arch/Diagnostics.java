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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Diagnostics {

    private Diagnostics() {
    }

    private static final String RESET  = "\033[0m";
    private static final String BOLD   = "\033[1m";
    private static final String YELLOW = "\033[33m";
    private static final String GREEN  = "\033[32m";
    private static final String PURPLE = "\033[35m";
    private static final String RED    = "\033[31m";

    public static boolean TRACE = false;
    public static boolean DEBUG = false;
    public static boolean SILENT = false;
    public static String SUCCESS_FORMAT = "File has been compiled: {file}";

    public static java.nio.file.Path OUT_OVERRIDE = null;

    public static java.nio.file.Path OUT_DIR = null;

    private static java.nio.file.Path relocate(java.nio.file.Path file) {

        java.nio.file.Path target = OUT_OVERRIDE;

        if (target == null && OUT_DIR != null)
            target = OUT_DIR.resolve(file.getFileName());

        if (target == null)
            return file;

        try {
            java.nio.file.Path abs = target.toAbsolutePath();

            if (abs.equals(file.toAbsolutePath()))
                return file;

            if (abs.getParent() != null)
                java.nio.file.Files.createDirectories(abs.getParent());

            java.nio.file.Path dir = file.toAbsolutePath().getParent();

            java.nio.file.Files.move(
                file, abs,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            );

            try {
                if (dir != null && dir.getFileName() != null &&
                    dir.getFileName().toString().equals("out"))
                    java.nio.file.Files.deleteIfExists(dir);
            } catch (Exception ignored) { }

            return abs;

        } catch (java.io.IOException e) {
            System.err.println("warning: no se pudo mover la salida a " + target + ": " + e.getMessage());
            return file;
        }
    }

    public static void built(java.nio.file.Path file, String mode) {
        file = relocate(file);
        if (SILENT)
            return;
        long size = -1;
        try {
            size = java.nio.file.Files.size(file);
        } catch (java.io.IOException ignored) {
        }
        String msg = SUCCESS_FORMAT
            .replace("{file}", String.valueOf(file))
            .replace("{size}", String.valueOf(size))
            .replace("{mode}", mode);
        System.out.println(paint(GREEN, "[BUILD SUCCESS]") + " " + msg);
    }

    public static void log(Object o) {
        if (DEBUG) System.out.println(o);
    }

    public static void log() {
        if (DEBUG) System.out.println();
    }

    public static void logn(Object o) {
        if (DEBUG) System.out.print(o);
    }

    private static int errors = 0;
    private static int warnings = 0;

    private static final boolean COLOR =
        System.getenv("NO_COLOR") == null &&
        (System.console() != null || System.getenv("FORCE_COLOR") != null);

    private static String paint(String color, String text) {
        return COLOR ? color + BOLD + text + RESET : text;
    }

    public static void warning(String message) {
        warnings++;
        System.err.println(paint(YELLOW, "warning: ") + translate(message));
    }

    public static void tip(String message) {
        System.err.println(paint(GREEN, "tip: ") + message);
    }

    public static void error(String message) {
        errors++;
        String m = translate(message);
        System.err.println(paint(PURPLE, "error: ") + m);
        String t = tipFor(m);
        if (t != null)
            tip(t);
    }

    public static void internal(Throwable e) {
        errors++;
        System.err.println(
            paint(RED, "internal compiler error: ") +
            describe(e)
        );
        System.err.println(
            paint(GREEN, "tip: ") +
            "this is a bug in ashc, not in your program. " +
            "Re-run with --trace and report it."
        );
        if (TRACE)
            e.printStackTrace();
    }

    public static void noteError() {
        errors++;
    }

    public static int errorCount() {
        return errors;
    }

    public static void summary() {
        if (errors == 0 && warnings == 0)
            return;
        StringBuilder sb = new StringBuilder();
        if (errors > 0)
            sb.append(errors).append(errors == 1 ? " error" : " errors");
        if (warnings > 0) {
            if (sb.length() > 0)
                sb.append(", ");
            sb.append(warnings).append(warnings == 1 ? " warning" : " warnings");
        }
        System.err.println(sb + " generated.");
    }

    private static String describe(Throwable e) {
        String m = e.getMessage();
        String n = e.getClass().getSimpleName();
        if (e instanceof NullPointerException ||
            e instanceof IndexOutOfBoundsException ||
            e instanceof ClassCastException)
            return "unexpected " + n + " while generating code";
        return m == null ? n : translate(m);
    }

    private static final Map<Pattern, String> RULES = new LinkedHashMap<>();

    private static void rule(String regex, String repl) {
        RULES.put(Pattern.compile(regex), repl);
    }

    static {
        rule("S[ií]mbolo does not exist: (.+)", "unknown symbol '$1'");
        rule("S[ií]mbolo no existe: (.+)", "unknown symbol '$1'");
        rule("La variable '(.+)' ya existe", "variable '$1' is already declared");
        rule("Variable/par[aá]metro no existe: (.+)", "unknown variable or parameter '$1'");
        rule("addr: variable no encontrada: (.+)", "cannot take the address of unknown symbol '$1'");
        rule("Argumento extern no encontrado: (.+)", "unknown argument '$1' in call");
        rule("Argumento floating extern no encontrado: (.+)", "unknown floating-point argument '$1' in call");
        rule("Demasiados argumentos enteros para extern '(.+)': (\\d+)", "too many integer arguments for '$1' ($2)");
        rule("Demasiados argumentos floating-point para extern '(.+)': (\\d+)", "too many floating-point arguments for '$1' ($2)");
        rule("El extern '(.+)' requiere (\\d+) argumentos, recibidos (\\d+)", "'$1' expects $2 arguments, got $3");
        rule("La macro '(.+)' requiere ';'", "definition of '$1' is not terminated");
        rule("La macro '(.+)' ya fue definida", "'$1' is already defined");
        rule("La macro '(.+)' no puede estar vacia", "'$1' cannot have an empty body");
        rule("Se esperaba una expresion, encontrado (.+)", "expected an expression, found $1");
        rule("Sentencia inesperada: (.+)", "unexpected statement: $1");
        rule("Caracter inesperado '(.+)'", "unexpected character '$1'");
        rule("Operador no soportado en ASHIR: (.+)", "operator '$1' is not supported");
        rule("Operacion ASHIR desconocida: (.+)", "unknown operation '$1'");
        rule("Divisi[oó]n por cero", "division by zero");
        rule("M[oó]dulo por cero", "modulo by zero");
        rule("Shift fuera de rango: (.+)", "shift count $1 is out of range");
        rule("Tipos incompatibles: (.+) y (.+)", "incompatible types: '$1' and '$2'");
        rule("No se puede indexar un literal; asignalo a una variable string", "cannot index a literal; assign it to a string variable first");
        rule("Falta el archivo despu[eé]s de -o\\.", "missing file name after -o");
        rule("--win y --elf son formatos de salida mutuamente excluyentes\\.", "--win and --elf are mutually exclusive");
        rule("Asignacion compuesta sin variable a la izquierda", "compound assignment needs a variable on the left");
        rule("shift requiere cuenta constante: (.+)", "shift count must be a constant, got '$1'");
        rule("El par[aá]metro no es entero: (.+)", "parameter '$1' is not an integer");
    }

    private static final String[][] WORDS = {
        {"no existe", "does not exist"}, {"no encontrado", "not found"},
        {"no encontrada", "not found"}, {"inválido", "invalid"},
        {"invalido", "invalid"}, {"inválida", "invalid"},
        {"Se esperaba", "expected"}, {"se esperaba", "expected"},
        {"encontrado", "found"}, {"función", "function"},
        {"funcion", "function"}, {"variable", "variable"},
        {"argumentos", "arguments"}, {"Argumento", "Argument"},
        {"requiere", "requires"}, {"no soportado", "not supported"},
        {"desconocido", "unknown"}, {"desconocida", "unknown"},
        {"archivo", "file"}, {"Falta", "Missing"}, {"Error", "Error"},
        {"No se pudo", "Could not"}, {"no puede", "cannot"},
        {"sin nombre", "has no name"}, {"fuera de rango", "out of range"},
    };

    public static String translate(String message) {
        if (message == null)
            return "";
        for (Map.Entry<Pattern, String> r : RULES.entrySet()) {
            Matcher m = r.getKey().matcher(message);
            if (m.find())
                return m.replaceFirst(r.getValue());
        }
        String out = message;
        for (String[] w : WORDS)
            out = out.replace(w[0], w[1]);
        return out;
    }

    private static String tipFor(String m) {
        if (m.contains("unknown variable") || m.contains("unknown symbol"))
            return "check the spelling, or declare it before use";
        if (m.contains("already declared"))
            return "assigning a new value does not need a type: write `name = value;`";
        if (m.contains("not terminated"))
            return "end the definition with ';' or keep it on one line";
        if (m.contains("expects") && m.contains("arguments"))
            return "check the function signature";
        if (m.contains("is not available in 32-bit flat mode"))
            return "32-bit output has no libc: only calloc/free/exit exist (built in); write your own I/O routine or build with --bits 64";
        if (m.contains("shift count must be a constant"))
            return "use a literal count, or a variable and let ashc call the runtime helper";
        return null;
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
