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

import java.util.HashSet;
import java.util.Set;

import Builder.Untokenizer.Token;
import Builder.Untokenizer.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Typedefs {

    private Typedefs() {
    }

    private static final class Annotation {
        String name;
        List<List<Token>> paramTypes = new ArrayList<>();
        List<String> paramNames = new ArrayList<>();
        List<Token> body = new ArrayList<>();
    }

    private static IllegalArgumentException error(Token t, String message) {
        return new IllegalArgumentException(
            "line " + t.line + ": " + message
        );
    }

    private static boolean isIdent(Token t, String text) {
        return t != null && t.type == Type.IDENT && t.text.equals(text);
    }

    public static List<Token> expand(List<Token> in) {
        return annotations(typedefs(in));
    }

    private static final Map<String, List<Token>> GLOBAL = new HashMap<>();

    private static List<Token> typedefs(List<Token> in) {

        Map<String, List<Token>> aliases = new HashMap<>(GLOBAL);
        Set<String> local = new HashSet<>();
        List<Token> out = new ArrayList<>(in.size());

        for (int i = 0; i < in.size(); i++) {

            Token t = in.get(i);

            if (!isIdent(t, "typedef")) {
                out.add(t);
                continue;
            }

            int j = i + 1;
            List<Token> body = new ArrayList<>();

            while (
                j < in.size() &&
                in.get(j).type != Type.SEMICOLON &&
                in.get(j).type != Type.EOF &&
                (body.isEmpty() || in.get(j).line == t.line)
            ) {
                body.add(in.get(j));
                j++;
            }

            if (body.size() < 2 || body.get(body.size() - 1).type != Type.IDENT)
                throw error(t, "typedef needs a type and a name: typedef uint32 u32;");

            Token name = body.remove(body.size() - 1);

            List<Token> resolved = new ArrayList<>();
            for (Token b : body) {
                List<Token> a = b.type == Type.IDENT ? aliases.get(b.text) : null;
                if (a != null) resolved.addAll(a);
                else resolved.add(b);
            }

            if (local.contains(name.text))
                throw error(name, "typedef '" + name.text + "' is already defined");

            local.add(name.text);
            aliases.put(name.text, resolved);
            GLOBAL.put(name.text, resolved);

            if (j < in.size() && in.get(j).type == Type.SEMICOLON)
                j++;

            i = j - 1;
        }

        if (aliases.isEmpty())
            return in;

        List<Token> res = new ArrayList<>(out.size());

        for (int i = 0; i < out.size(); i++) {

            Token t = out.get(i);

            boolean afterDot =
                i > 0 && out.get(i - 1).type == Type.DOT;

            List<Token> a =
                t.type == Type.IDENT && !afterDot
                    ? aliases.get(t.text)
                    : null;

            if (a == null) {
                res.add(t);
                continue;
            }

            for (Token x : a)
                res.add(new Token(x.type, x.text, t.line, t.column));
        }

        return res;
    }

    private static int matching(List<Token> l, int open, Type o, Type c) {
        int d = 0;
        for (int i = open; i < l.size(); i++) {
            Type ty = l.get(i).type;
            if (ty == o) d++;
            else if (ty == c && --d == 0) return i;
        }
        return -1;
    }

    private static List<List<Token>> splitTop(List<Token> l) {
        List<List<Token>> r = new ArrayList<>();
        List<Token> cur = new ArrayList<>();
        int d = 0;
        for (Token t : l) {
            if (t.type == Type.LPAREN || t.type == Type.LBRACKET || t.type == Type.LBRACE) d++;
            if (t.type == Type.RPAREN || t.type == Type.RBRACKET || t.type == Type.RBRACE) d--;
            if (t.type == Type.COMMA && d == 0) {
                r.add(cur);
                cur = new ArrayList<>();
            } else
                cur.add(t);
        }
        if (!cur.isEmpty() || !r.isEmpty())
            r.add(cur);
        return r;
    }

    private static List<Token> annotations(List<Token> in) {

        Map<String, Annotation> defs = new HashMap<>();
        List<Token> rest = new ArrayList<>(in.size());

        for (int i = 0; i < in.size(); i++) {

            Token t = in.get(i);

            if (
                t.type == Type.AT &&
                i + 2 < in.size() &&
                in.get(i + 1).type == Type.IDENT &&
                in.get(i + 2).type == Type.LPAREN
            ) {
                int close = matching(in, i + 2, Type.LPAREN, Type.RPAREN);

                if (
                    close > 0 &&
                    close + 1 < in.size() &&
                    in.get(close + 1).type == Type.LBRACE
                ) {
                    int end = matching(in, close + 1, Type.LBRACE, Type.RBRACE);

                    if (end < 0)
                        throw error(t, "annotation '" + in.get(i + 1).text + "' is missing a closing '}'");

                    Annotation a = new Annotation();
                    a.name = in.get(i + 1).text;

                    if (defs.containsKey(a.name))
                        throw error(t, "annotation '@" + a.name + "' is already defined");

                    List<Token> params = in.subList(i + 3, close);
                    int n = 0;

                    for (List<Token> p : splitTop(params)) {
                        if (p.isEmpty())
                            continue;
                        n++;
                        Token last = p.get(p.size() - 1);
                        if (p.size() >= 2 && last.type == Type.IDENT) {
                            a.paramNames.add(last.text);
                            a.paramTypes.add(new ArrayList<>(p.subList(0, p.size() - 1)));
                        } else {
                            a.paramNames.add(n == 1 ? "value" : "value" + n);
                            a.paramTypes.add(new ArrayList<>(p));
                        }
                    }

                    a.body.addAll(in.subList(close + 2, end));
                    defs.put(a.name, a);

                    i = end;
                    continue;
                }
            }

            rest.add(t);
        }

        if (defs.isEmpty())
            return in;

        List<Token> out = new ArrayList<>(rest.size());
        List<Annotation> pending = new ArrayList<>();
        List<List<List<Token>>> pendingArgs = new ArrayList<>();

        for (int i = 0; i < rest.size(); i++) {

            Token t = rest.get(i);

            if (
                t.type == Type.AT &&
                i + 1 < rest.size() &&
                rest.get(i + 1).type == Type.IDENT &&
                defs.containsKey(rest.get(i + 1).text)
            ) {
                Annotation a = defs.get(rest.get(i + 1).text);
                List<List<Token>> args = new ArrayList<>();
                int j = i + 2;

                if (j < rest.size() && rest.get(j).type == Type.LPAREN) {
                    int close = matching(rest, j, Type.LPAREN, Type.RPAREN);
                    if (close < 0)
                        throw error(t, "unterminated argument list for '@" + a.name + "'");
                    args = splitTop(rest.subList(j + 1, close));
                    j = close + 1;
                }

                if (args.size() != a.paramNames.size())
                    throw error(
                        t,
                        "'@" + a.name + "' expects " + a.paramNames.size() +
                        " argument(s), got " + args.size()
                    );

                pending.add(a);
                pendingArgs.add(args);
                i = j - 1;
                continue;
            }

            out.add(t);

            if (!pending.isEmpty() && isIdent(t, "func")) {

                int j = i + 1;
                int paren = 0;

                while (j < rest.size()) {
                    Token h = rest.get(j);
                    if (h.type == Type.LPAREN) paren++;
                    if (h.type == Type.RPAREN) paren--;
                    if (h.type == Type.LBRACE && paren == 0) break;
                    out.add(h);
                    j++;
                }

                if (j >= rest.size())
                    throw error(t, "annotated function has no body");

                Token brace = rest.get(j);
                out.add(brace);

                for (int k = 0; k < pending.size(); k++) {

                    Annotation a = pending.get(k);
                    List<List<Token>> args = pendingArgs.get(k);

                    for (int p = 0; p < a.paramNames.size(); p++) {
                        for (Token x : a.paramTypes.get(p))
                            out.add(new Token(x.type, x.text, brace.line, brace.column));
                        out.add(new Token(Type.IDENT, a.paramNames.get(p), brace.line, brace.column));
                        out.add(new Token(Type.ASSIGN, "=", brace.line, brace.column));
                        out.addAll(args.get(p));
                        out.add(new Token(Type.SEMICOLON, ";", brace.line, brace.column));
                    }

                    out.addAll(a.body);
                }

                pending.clear();
                pendingArgs.clear();
                i = j;
            }
        }

        if (!pending.isEmpty())
            throw new IllegalArgumentException(
                "annotation '@" + pending.get(0).name +
                "' must be placed before a function"
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
