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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import Builder.Exception;
import Builder.Untokenizer.Token;
import Builder.Untokenizer.Type;

public final class Macros {

    private static final int MAX_MACRO_EXPANSION_DEPTH = 128;

    private static final class Macro {

        final String name;
        final List<Token> body;
        final boolean block;

        Macro(
            String name,
            List<Token> body,
            boolean block
        ) {
            this.name = name;
            this.body = body;
            this.block = block;
        }
    }

    private static final Map<String, Macro> GLOBAL = new HashMap<>();

    private final Map<String, Macro> macros =
        new HashMap<>(GLOBAL);

    private final java.util.Set<String> localNames = new java.util.HashSet<>();

    public List<Token> preprocess(
        List<Token> input
    ) {

        if (input == null || input.isEmpty()) {
            return input == null
                ? Collections.<Token>emptyList()
                : new ArrayList<>(input);
        }

        macros.clear();
        macros.putAll(GLOBAL);
        localNames.clear();

        List<Token> program =
            new ArrayList<>();

        int i = 0;

        while (i < input.size()) {

            Token token =
                input.get(i);

            if (token == null) {
                i++;
                continue;
            }

            if (isIdentifierToken(token, "define")) {

                i = parseDefine(
                    input,
                    i,
                    token
                );

                continue;
            }

            if (isIdentifierToken(token, "macro")) {

                i = parseMacro(
                    input,
                    i,
                    token
                );

                continue;
            }

            program.add(token);
            i++;
        }

        String[][] builtin = {
            {"true", "1"}, {"false", "0"}, {"TRUE", "1"}, {"FALSE", "0"}
        };

        for (String[] d : builtin) {

            if (!macros.containsKey(d[0])) {

                List<Token> body = new ArrayList<>();
                body.add(new Token(Type.NUMBER, d[1], 0, 0));

                macros.put(d[0], new Macro(d[0], body, false));
            }
        }

        List<Token> expanded =
            expandTokens(
                program,
                0,
                new ArrayList<String>()
            );

        for (Token token : expanded) {

            if (token == null) {
                continue;
            }

            if (token.type == Type.IDENT &&
                macros.containsKey(token.text)) {

                throw macroError(
                    token,
                    "La macro '" +
                    token.text +
                    "' no pudo expandirse"
                );
            }
        }

        return expanded;
    }

    private int parseDefine(
        List<Token> input,
        int start,
        Token defineToken
    ) {

        int i = start + 1;

        if (i >= input.size()) {

            throw macroError(
                defineToken,
                "Se esperaba el nombre de la macro"
            );
        }

        Token nameToken =
            input.get(i);

        if (nameToken == null ||
            nameToken.type != Type.IDENT) {

            throw macroError(
                nameToken,
                "Se esperaba el nombre de la macro"
            );
        }

        String name =
            nameToken.text;

        if (name == null || name.isEmpty()) {

            throw macroError(
                nameToken,
                "El nombre de la macro no puede estar vacio"
            );
        }

        i++;

        if (localNames.contains(name)) {

            throw macroError(
                nameToken,
                "La macro '" +
                name +
                "' ya fue definida"
            );
        }

        List<Token> body =
            new ArrayList<>();

        int parenDepth = 0;
        int bracketDepth = 0;
        int braceDepth = 0;

        boolean foundSemicolon = false;
        boolean implicitEnd = false;

        while (i < input.size()) {

            Token token =
                input.get(i);

            if (token == null) {
                i++;
                continue;
            }

            if (
                !body.isEmpty() &&
                parenDepth == 0 &&
                bracketDepth == 0 &&
                braceDepth == 0 &&
                token.type != Type.SEMICOLON &&
                (
                    token.type == Type.EOF ||
                    token.line != body.get(body.size() - 1).line
                )
            ) {
                foundSemicolon = true;
                implicitEnd = true;
                break;
            }

            if (token.type == Type.SEMICOLON &&
                parenDepth == 0 &&
                bracketDepth == 0 &&
                braceDepth == 0) {

                foundSemicolon = true;
                break;
            }

            if (token.type == Type.LPAREN) {
                parenDepth++;
            }
            else if (token.type == Type.RPAREN) {

                if (parenDepth > 0) {
                    parenDepth--;
                }
            }

            else if (token.type == Type.LBRACKET) {
                bracketDepth++;
            }
            else if (token.type == Type.RBRACKET) {

                if (bracketDepth > 0) {
                    bracketDepth--;
                }
            }

            else if (token.type == Type.LBRACE) {
                braceDepth++;
            }
            else if (token.type == Type.RBRACE) {

                if (braceDepth > 0) {
                    braceDepth--;
                }
            }

            body.add(token);
            i++;
        }

        if (!foundSemicolon) {

            throw macroError(
                nameToken,
                "La macro '" +
                name +
                "' requiere ';'"
            );
        }

        if (body.isEmpty()) {

            throw macroError(
                nameToken,
                "La macro '" +
                name +
                "' no puede estar vacia"
            );
        }

        macros.put(
            name,
            new Macro(
                name,
                Collections.unmodifiableList(
                    new ArrayList<>(body)
                ),
                false
            )
        );
        localNames.add(name);
        GLOBAL.put(name, macros.get(name));

        return implicitEnd ? i : i + 1;
    }

    private int parseMacro(
        List<Token> input,
        int start,
        Token macroToken
    ) {

        int i = start + 1;

        if (i >= input.size()) {

            throw macroError(
                macroToken,
                "Se esperaba el nombre de la macro"
            );
        }

        Token nameToken =
            input.get(i);

        if (nameToken == null ||
            nameToken.type != Type.IDENT) {

            throw macroError(
                nameToken,
                "Se esperaba el nombre de la macro"
            );
        }

        String name =
            nameToken.text;

        if (name == null || name.isEmpty()) {

            throw macroError(
                nameToken,
                "El nombre de la macro no puede estar vacio"
            );
        }

        i++;

        if (localNames.contains(name)) {

            throw macroError(
                nameToken,
                "La macro '" +
                name +
                "' ya fue definida"
            );
        }

        if (i >= input.size() ||
            input.get(i) == null ||
            input.get(i).type != Type.LBRACE) {

            throw macroError(
                nameToken,
                "La macro '" +
                name +
                "' requiere un bloque '{ ... }'"
            );
        }

        List<Token> body =
            collectBalancedBlock(
                input,
                i
            );

        macros.put(
            name,
            new Macro(
                name,
                Collections.unmodifiableList(
                    new ArrayList<>(body)
                ),
                true
            )
        );
        localNames.add(name);
        GLOBAL.put(name, macros.get(name));

        i =
            skipBalancedBlock(
                input,
                i
            );

        if (i < input.size() &&
            input.get(i) != null &&
            input.get(i).type == Type.SEMICOLON) {

            i++;
        }

        return i;
    }

    private List<Token> expandTokens(
        List<Token> input,
        int depth,
        List<String> stack
    ) {

        if (depth > MAX_MACRO_EXPANSION_DEPTH) {

            throw new IllegalStateException(
                "Profundidad maxima de expansion de macros excedida: " +
                stack
            );
        }

        List<Token> output =
            new ArrayList<>();

        for (Token token : input) {

            if (token == null) {
                continue;
            }

            if (token.type != Type.IDENT) {

                output.add(token);
                continue;
            }

            Macro macro =
                macros.get(token.text);

            if (macro == null) {

                output.add(token);
                continue;
            }

            if (stack.contains(macro.name)) {

                StringBuilder cycle =
                    new StringBuilder();

                for (String name : stack) {

                    if (cycle.length() > 0) {
                        cycle.append(" -> ");
                    }

                    cycle.append(name);
                }

                if (cycle.length() > 0) {
                    cycle.append(" -> ");
                }

                cycle.append(macro.name);

                throw macroError(
                    token,
                    "Expansion recursiva de macro: " +
                    cycle
                );
            }

            List<String> nextStack =
                new ArrayList<>(stack);

            nextStack.add(macro.name);

            List<Token> expandedBody =
                expandTokens(
                    macro.body,
                    depth + 1,
                    nextStack
                );

            output.addAll(expandedBody);
        }

        return output;
    }

    private List<Token> collectBalancedBlock(
        List<Token> input,
        int start
    ) {

        if (start < 0 ||
            start >= input.size() ||
            input.get(start) == null ||
            input.get(start).type != Type.LBRACE) {

            throw new IllegalArgumentException(
                "collectBalancedBlock requiere '{'"
            );
        }

        List<Token> body =
            new ArrayList<>();

        int depth = 0;

        for (int i = start;
             i < input.size();
             i++) {

            Token token =
                input.get(i);

            if (token == null) {
                continue;
            }

            if (token.type == Type.LBRACE) {

                depth++;

                if (depth == 1) {
                    continue;
                }
            }

            if (token.type == Type.RBRACE) {

                depth--;

                if (depth == 0) {
                    return body;
                }
            }

            if (depth >= 1) {
                body.add(token);
            }
        }

        throw macroError(
            input.get(start),
            "Bloque de macro sin '}'"
        );
    }

    private int skipBalancedBlock(
        List<Token> input,
        int start
    ) {

        if (start < 0 ||
            start >= input.size() ||
            input.get(start) == null ||
            input.get(start).type != Type.LBRACE) {

            throw new IllegalArgumentException(
                "skipBalancedBlock requiere '{'"
            );
        }

        int depth = 0;

        for (int i = start;
             i < input.size();
             i++) {

            Token token =
                input.get(i);

            if (token == null) {
                continue;
            }

            if (token.type == Type.LBRACE) {

                depth++;
                continue;
            }

            if (token.type == Type.RBRACE) {

                depth--;

                if (depth == 0) {
                    return i + 1;
                }
            }
        }

        throw macroError(
            input.get(start),
            "Bloque de macro sin '}'"
        );
    }

    private static Exception macroError(
        Token token,
        String message
    ) {

        return new Exception(
            message,
            token != null
                ? token.line
                : -1,
            token != null
                ? token.column
                : -1,
            null,
            "error"
        );
    }

    private static boolean isIdentifierToken(
        Token token,
        String text
    ) {

        return token != null &&
               token.type == Type.IDENT &&
               text != null &&
               text.equals(token.text);
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
