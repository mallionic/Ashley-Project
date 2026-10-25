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

import java.util.*;

import Builder.Untokenizer.Token;
import Builder.Untokenizer.Type;

public final class Redefines {

    private Redefines() { }

    private static final Map<String, String> GLOBAL = new HashMap<>();

    private static Token at(List<Token> t, int i) {
        return i < t.size() ? t.get(i) : t.get(t.size() - 1);
    }

    private static boolean word(Token t, String w) {
        return t.type == Type.IDENT && t.text.equals(w);
    }

    private static Token tok(Type type, String text, Token like) {
        return new Token(type, text, like.line, like.column);
    }

    public static List<Token> expand(List<Token> in) {

        Map<String, String> local = new HashMap<>();
        List<Token> out = new ArrayList<>(in.size());

        for (int i = 0; i < in.size(); i++) {

            Token t = in.get(i);

            if (!(word(t, "redefine") && !(i > 0 && at(in, i - 1).type == Type.DOT))) {
                out.add(t);
                continue;
            }

            int j = i + 1;
            boolean global = false;

            if (word(at(in, j), "global")) {
                global = true;
                j++;
            }

            int open = j;

            while (at(in, open).type != Type.LPAREN && at(in, open).type != Type.EOF)
                open++;

            if (at(in, open).type == Type.EOF || open == j)
                throw new IllegalStateException(
                    "redefine (linea " + t.line + "): falta la firma  tipo nombre(parametros)");

            Token nameTok = at(in, open - 1);

            if (nameTok.type != Type.IDENT)
                throw new IllegalStateException(
                    "redefine (linea " + t.line + "): nombre de funcion invalido");

            int close = matching(in, open);

            if (at(in, close + 1).type == Type.LBRACE) {

                List<Token> ret = new ArrayList<>(in.subList(j, open - 1));

                if (ret.isEmpty())
                    throw new IllegalStateException(
                        "redefine (linea " + t.line + "): falta el tipo de retorno");

                out.add(tok(Type.AT, "@", t));
                out.add(tok(Type.IDENT, "ovr", t));
                out.add(tok(Type.LBRACKET, "[", t));
                out.add(tok(Type.NUMBER, global ? "100" : "1", t));
                out.add(tok(Type.RBRACKET, "]", t));

                out.add(tok(Type.IDENT, "pub", t));
                out.add(tok(Type.IDENT, "func", t));
                out.add(nameTok);

                for (int k = open; k <= close; k++)
                    out.add(in.get(k));

                out.add(tok(Type.COLON, ":", t));
                out.addAll(ret);

                i = close;
                continue;
            }

            int k = close + 1;

            while (
                at(in, k).type != Type.EOF &&
                !(at(in, k).type == Type.IDENT && at(in, k + 1).type == Type.LPAREN)
            ) {
                if (at(in, k).type == Type.SEMICOLON)
                    break;
                k++;
            }

            if (!(at(in, k).type == Type.IDENT && at(in, k + 1).type == Type.LPAREN))
                throw new IllegalStateException(
                    "redefine (linea " + t.line + "): falta el nombre nuevo:  "
                    + "redefine tipo " + nameTok.text + "(...) tipo nuevo(...)");

            String neu = at(in, k).text;
            int neuClose = matching(in, k + 1);

            if (neu.equals(nameTok.text))
                throw new IllegalStateException(
                    "redefine (linea " + t.line + "): el nombre nuevo es igual al viejo");

            if (global) GLOBAL.put(neu, nameTok.text);
            else local.put(neu, nameTok.text);

            i = neuClose;

            if (at(in, i + 1).type == Type.SEMICOLON)
                i++;
        }

        if (local.isEmpty() && GLOBAL.isEmpty())
            return out;

        List<Token> res = new ArrayList<>(out.size());

        for (int i = 0; i < out.size(); i++) {

            Token t = out.get(i);

            if (t.type == Type.IDENT) {

                String target = local.containsKey(t.text) ? local.get(t.text) : GLOBAL.get(t.text);

                boolean afterFunc =
                    i > 0 && (word(out.get(i - 1), "func") || out.get(i - 1).type == Type.DOT);

                boolean call = i + 1 < out.size() && out.get(i + 1).type == Type.LPAREN;
                boolean addr = i > 0 && out.get(i - 1).type == Type.BITAND;

                if (target != null && !afterFunc && (call || addr)) {
                    res.add(new Token(Type.IDENT, target, t.line, t.column));
                    continue;
                }
            }

            res.add(t);
        }

        return res;
    }

    private static int matching(List<Token> in, int open) {

        int depth = 0;

        for (int i = open; i < in.size(); i++) {

            Type ty = in.get(i).type;

            if (ty == Type.LPAREN) depth++;
            else if (ty == Type.RPAREN) {
                depth--;
                if (depth == 0) return i;
            }
        }

        throw new IllegalStateException("redefine: parentesis sin cerrar (linea " + in.get(open).line + ")");
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
