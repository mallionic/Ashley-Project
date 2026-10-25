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

import Builder.Untokenizer.Type;
import Builder.ash.Ast.*;

import java.util.ArrayList;
import java.util.List;

public final class Vars {

    private final Cursor c;
    private final Exprs exprs;
    private final StringConv strings;

    public Vars(Cursor c, Exprs exprs, StringConv strings) {
        this.c = c;
        this.exprs = exprs;
        this.strings = strings;
    }

    public boolean isDeclarationAhead() {

        if (!c.check(Type.IDENT) || !Nums.isType(c.peek().text))
            return false;

        Type n = c.peekAt(1).type;

        if (n == Type.ASSIGN || n == Type.DOT || n == Type.COMPOUND || n == Type.SEMICOLON)
            return false;

        if (n == Type.MINUS && c.peekAt(2).type == Type.GT)
            return false;

        if (n == Type.LBRACKET) {

            int i = 1, depth = 0;
            do {
                Type t = c.peekAt(i).type;
                if (t == Type.LBRACKET) depth++;
                else if (t == Type.RBRACKET) depth--;
                else if (t == Type.EOF) return true;
                i++;
            } while (depth > 0);

            return c.peekAt(i).type == Type.IDENT || c.peekAt(i).type == Type.LBRACKET;
        }

        return true;
    }

    public boolean isAssignmentAhead() {
        return c.check(Type.IDENT) && c.peekAt(1).type == Type.ASSIGN;
    }

    public boolean isIndexAssignmentAhead() {
        if (!c.check(Type.IDENT) || c.peekAt(1).type != Type.LBRACKET)
            return false;

        int i = 1;

        while (c.peekAt(i).type == Type.LBRACKET) {
            int depth = 0;

            do {
                Type t = c.peekAt(i).type;

                if (t == Type.LBRACKET) depth++;
                else if (t == Type.RBRACKET) depth--;
                else if (t == Type.EOF) return false;

                i++;
            } while (depth > 0);
        }

        return c.peekAt(i).type == Type.ASSIGN;
    }

    public VariableDecl parseDeclaration(boolean volatileVariable) {
        String type = c.parseTypeWithPointer();
        String rawType = c.lastRawType;

        List<Expression> sizes = new ArrayList<>();
        int dims = 0;

        while (c.match(Type.LBRACKET)) {
            dims++;
            sizes.add(c.check(Type.RBRACKET) ? null : exprs.parseExpression());
            c.expect(Type.RBRACKET, "]");
        }

        String name = c.expect(Type.IDENT, "nombre de variable").text;

        while (c.match(Type.LBRACKET)) {
            dims++;
            sizes.add(c.check(Type.RBRACKET) ? null : exprs.parseExpression());
            c.expect(Type.RBRACKET, "]");
        }

        Expression value = null;

        if (c.match(Type.ASSIGN)) {
            value = exprs.parseExpression();

            if (dims == 0)
                strings.validateConversion(type, value);
        }

        c.match(Type.SEMICOLON);

        String full = Nums.valueType(type);

        for (int i = 0; i < dims; i++)
            full += "[]";

        VariableDecl d = new VariableDecl(full, name, value, volatileVariable);
        d.rawType = rawType;

        if (dims > 0)
            ArrayInfo.put(d, sizes);

        return d;
    }

    public Statement parseAssignment() {
        String name = c.advance().text;
        c.advance();

        Expression value = exprs.parseExpression();
        c.match(Type.SEMICOLON);

        return new Assignment(name, value);
    }

    public Statement parseIndexAssignment() {
        Expression target = exprs.parseUnary();

        c.expect(Type.ASSIGN, "=");

        Expression value = exprs.parseExpression();
        c.match(Type.SEMICOLON);

        return new Assignment(target, value);
    }

    public Statement parseDerefAssignment() {
        Expression target = exprs.parseUnary();

        if (c.match(Type.ASSIGN)) {
            Expression value = exprs.parseExpression();
            c.match(Type.SEMICOLON);
            return new Assignment(target, value);
        }

        throw c.error("Se esperaba '=' despues de una expresion de puntero");
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
