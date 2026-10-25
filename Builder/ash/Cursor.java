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

import java.util.List;

import Builder.Marked;
import Builder.Untokenizer.Token;
import Builder.Untokenizer.Type;

public final class Cursor {

    private final List<Token> tokens;
    private int position;

    public void removeAt(int k) { tokens.remove(position + k); }

    public int mark() { return position; }

    public void reset(int mark) { position = mark; }

    public Cursor(List<Token> tokens) {
        this.tokens = tokens;
        this.position = 0;
    }

    public Token peek() {

        if (tokens.isEmpty())
            return null;

        if (position >= tokens.size())
            return tokens.get(tokens.size() - 1);

        return tokens.get(position);
    }

    public Token peekAt(int offset) {

        if (tokens.isEmpty())
            return null;

        int p =
            position + offset;

        if (
            p < 0 ||
            p >= tokens.size()
        ) {
            return tokens.get(
                tokens.size() - 1
            );
        }

        return tokens.get(p);
    }

    public Token rawAt(int offset) {

        int p =
            position + offset;

        if (
            p < 0 ||
            p >= tokens.size()
        ) {
            return null;
        }

        return tokens.get(p);
    }

    public boolean checkAhead(
        int offset,
        Type type
    ) {

        Token token =
            peekAt(offset);

        return token != null &&
               token.type == type;
    }

    public Token advance() {

        if (tokens.isEmpty())
            return null;

        if (position >= tokens.size())
            return tokens.get(
                tokens.size() - 1
            );

        return tokens.get(
            position++
        );
    }

    public boolean check(Type type) {

        Token token =
            peek();

        return token != null &&
               token.type == type;
    }

    public boolean match(Type type) {

        if (!check(type))
            return false;

        advance();

        return true;
    }

    public String text(Token token) {

        if (
            token == null ||
            token.text == null
        ) {
            return "";
        }

        return token.text;
    }

    public boolean checkText(String text) {

        return text.equals(
            text(peek())
        );
    }

    public boolean matchText(String text) {

        if (!checkText(text))
            return false;

        advance();

        return true;
    }

    public boolean checkOperator(Type type) {

        return check(type);
    }

    public void expectText(
        String text,
        String what
    ) {

        if (
            !text(peek()).equals(text)
        ) {

            throw error(
                "Se esperaba " +
                what +
                " pero se encontro " +
                peek()
            );
        }

        advance();
    }

    public boolean isKeyword(String text) {

        Token token =
            peek();

        return token != null &&
               token.type == Type.IDENT &&
               text.equals(token.text);
    }

    public void expectKeyword(String text) {

        if (!isKeyword(text)) {

            throw error(
                "Se esperaba '" +
                text +
                "' pero se encontro " +
                peek()
            );
        }

        advance();
    }

    public Token expect(
        Type type,
        String what
    ) {

        if (!check(type)) {

            throw error(
                "Se esperaba " +
                what +
                " pero se encontro " +
                peek()
            );
        }

        return advance();
    }

    public String expectType(String what) {

        if (!check(Type.IDENT)) {

            throw error(
                "Se esperaba " +
                what +
                " pero se encontro " +
                peek()
            );
        }

        String type =
            peek().text;

        if (!Nums.isType(type)) {

            throw error(
                "Se esperaba " +
                what +
                " pero '" +
                type +
                "' no es un tipo ASH"
            );
        }

        return advance().text;
    }

    public String lastRawType;

    public String parseTypeWithPointer() {

        String type =
            expectType("tipo");

        lastRawType = type;

        if (match(Type.STAR)) {

            lastRawType = type + "*";

            String pointee = Nums.normalizeType(type);

            while (match(Type.STAR)) {
                pointee = "qword";
            }

            return "void".equals(pointee) ? "ptr" : "ptr:" + pointee;
        }

        return Nums.valueType(type);
    }

    public Marked.MarkedException error(
        String message
    ) {

        Token token =
            peek();

        int line =
            token != null
                ? token.line
                : -1;

        int column =
            token != null
                ? token.column
                : -1;

        String source =
            token != null
                ? text(token)
                : "";

        int length =
            Math.max(
                1,
                source.length()
            );

        Marked.error(
            "<input>",
            line,
            column,
            source,
            length,
            message
        );

        return new Marked.MarkedException(
            "<input>",
            line,
            column,
            message
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
