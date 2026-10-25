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
import java.util.ArrayList;
import java.util.List;
import Builder.ash.Ast.*;

public final class Funcs {

    public static boolean isTerminateAhead(Cursor c) {
        if (!c.check(Type.IDENT) || !"terminate".equals(c.peek().text))
            return false;
        Type n = c.peekAt(1).type;
        return n != Type.ASSIGN && n != Type.LPAREN && n != Type.DOT
            && n != Type.COMPOUND && n != Type.PLUSPLUS && n != Type.MINUSMINUS;
    }

    private final Cursor c;
    private final Exprs exprs;
    private final Vars vars;
    private final Inject inject;
    private final Conditional conditional;

    public Funcs(
        Cursor c,
        Exprs exprs,
        Vars vars,
        Inject inject
    ) {
        this.c = c;
        this.exprs = exprs;
        this.vars = vars;
        this.inject = inject;
        this.conditional =
            new Conditional(
                c,
                exprs,
                vars,
                inject
            );
        this.conditional.general = this::parseStatement;
    }

    public Function parseFunction() {
        c.expectKeyword("func");

        Function fn = new Function();

        if (
            c.check(Type.IDENT)
            && Nums.isType(c.peek().text)
            && c.peekAt(1).type == Type.IDENT
        ) {
            fn.returnType =
                c.parseTypeWithPointer();

            fn.name =
                c.expect(
                    Type.IDENT,
                    "nombre de funcion"
                ).text;

        } else {
            fn.returnType = "void";

            fn.name =
                c.expect(
                    Type.IDENT,
                    "nombre de funcion"
                ).text;
        }

        c.expect(Type.LPAREN, "(");

        if (!c.check(Type.RPAREN)) {
            do {

                if (c.check(Type.DOT)) {

                    c.expect(Type.DOT, "...");
                    c.expect(Type.DOT, "...");
                    c.expect(Type.DOT, "...");

                    fn.variadic = true;
                    fn.fixedParams = fn.parameters.size();

                    fn.parameters.add(new Parameter("qword", "__vac"));

                    for (int vi = 0; vi < VARIADIC_MAX; vi++)
                        fn.parameters.add(new Parameter("qword", "__va" + vi));

                    break;
                }

                String type =
                    c.parseTypeWithPointer();

                String rawT = c.lastRawType;

                String name =
                    c.expect(
                        Type.IDENT,
                        "nombre de parametro"
                    ).text;

                if (c.match(Type.LBRACKET)) {
                    c.expect(Type.RBRACKET, "]");
                    type = "ptr";
                }

                Parameter np =
                    new Parameter(
                        Nums.valueType(type),
                        name
                    );

                np.rawType = rawT;

                fn.parameters.add(np);

            } while (c.match(Type.COMMA));
        }

        c.expect(Type.RPAREN, ")");

        if (c.match(Type.COLON))
            fn.returnType =
                c.parseTypeWithPointer();

        fn.returnType =
            Nums.valueType(fn.returnType);

        parseBody(fn);

        return fn;
    }

    public static final int VARIADIC_MAX = 8;

    private void parseBody(Function fn) {
        c.expect(Type.LBRACE, "{");

        while (
            !c.check(Type.RBRACE)
            && !c.check(Type.EOF)
        ) {
            while (c.match(Type.SEMICOLON)) {
            }

            if (c.check(Type.RBRACE))
                break;

            if (isCrackAhead()) {
                parseCrack(fn);
                continue;
            }

            fn.statements.add(
                parseStatement()
            );
        }

        c.expect(Type.RBRACE, "}");
    }

    private Statement parseMatrix() {

        c.advance();

        String declared = null;

        if (
            c.check(Type.IDENT) &&
            Nums.isType(c.peek().text) &&
            c.peekAt(1).type == Type.IDENT
        ) {
            declared = c.advance().text;
        }

        String name = c.expect(Type.IDENT, "nombre de matriz").text;

        List<Expression> dims = new ArrayList<>();

        while (c.match(Type.LBRACKET)) {
            dims.add(c.check(Type.RBRACKET) ? null : exprs.parseExpression());
            c.expect(Type.RBRACKET, "]");
        }

        if (dims.size() > 2)
            throw c.error("una matriz tiene como maximo 2 dimensiones (filas y columnas)");

        if (!c.check(Type.LBRACE)) {

            if (declared == null)
                throw c.error("matrix '" + name + "': escribe los elementos { [tipo valor], ... } o el tipo: matrix uint64 " + name + "[3][4];");

            if (dims.size() != 2 || dims.get(0) == null || dims.get(1) == null)
                throw c.error("matrix '" + name + "': indica filas y columnas: " + name + "[filas][columnas]");

            c.match(Type.SEMICOLON);

            VariableDecl d = new VariableDecl(Nums.normalizeType(declared) + "[][]", name, null, false);
            d.rawType = declared;
            ArrayInfo.put(d, dims);
            return d;
        }

        c.expect(Type.LBRACE, "{");

        List<List<Expression>> rows = new ArrayList<>();
        List<Integer> rowLine = new ArrayList<>();
        String widest = null;
        int widestBits = 0;
        int lastLine = -1;

        while (!c.check(Type.RBRACE) && !c.check(Type.EOF)) {

            if (c.match(Type.COMMA) || c.match(Type.SEMICOLON))
                continue;

            Builder.Untokenizer.Token open = c.expect(Type.LBRACKET, "[");

            String t = c.expect(Type.IDENT, "tipo del elemento").text;

            if (!Nums.isType(t))
                throw c.error("tipo desconocido en la matriz: " + t);

            int bits = Globals.bits(Nums.normalizeType(t));

            if (bits == 0)
                throw c.error("la matriz solo admite enteros: " + t);

            if (bits > widestBits) { widestBits = bits; widest = t; }

            Expression v = exprs.parseExpression();

            c.expect(Type.RBRACKET, "]");

            if (open.line != lastLine) {
                rows.add(new ArrayList<>());
                rowLine.add(open.line);
                lastLine = open.line;
            }

            rows.get(rows.size() - 1).add(new CastExpr(t, v));
        }

        c.expect(Type.RBRACE, "}");
        c.match(Type.SEMICOLON);

        if (rows.isEmpty())
            throw c.error("matrix '" + name + "' vacia");

        int cols = rows.get(0).size();

        for (int r = 1; r < rows.size(); r++)
            if (rows.get(r).size() != cols)
                throw c.error(
                    "matrix '" + name + "': la fila " + (r + 1) + " (linea " + rowLine.get(r) + ") tiene "
                    + rows.get(r).size() + " elementos y la primera tiene " + cols
                );

        List<Expression> rowLits = new ArrayList<>();

        for (List<Expression> r : rows)
            rowLits.add(new ArrayLiteralExpr(r));

        VariableDecl d = new VariableDecl(
            Nums.normalizeType(widest) + "[][]",
            name,
            new ArrayLiteralExpr(rowLits),
            false
        );

        d.rawType = widest;

        List<Expression> sizes = new ArrayList<>();
        sizes.add(null);
        sizes.add(null);
        ArrayInfo.put(d, sizes);

        return d;
    }

    private boolean isCrackAhead() {
        return c.peekAt(0).type == Type.IDENT
            && c.peekAt(1).type == Type.MINUS
            && c.peekAt(2).type == Type.GT
            && c.peekAt(3).type == Type.LPAREN;
    }

    private void parseCrack(Function fn) {

        Crack crack = new Crack();

        crack.name = c.advance().text;

        c.advance();
        c.advance();
        c.expect(Type.LPAREN, "(");

        if (!c.check(Type.RPAREN)) {
            do {
                String type = c.parseTypeWithPointer();

                String pname =
                    c.expect(Type.IDENT, "nombre de parametro").text;

                crack.params.add(
                    new Parameter(Nums.valueType(type), pname)
                );

            } while (c.match(Type.COMMA));
        }

        c.expect(Type.RPAREN, ")");

        for (Crack other : fn.cracks)
            if (other.name.equals(crack.name))
                throw c.error("el crack '" + crack.name + "' ya existe en '" + fn.name + "'");

        c.expect(Type.LBRACE, "{");

        while (!c.check(Type.RBRACE) && !c.check(Type.EOF)) {

            while (c.match(Type.SEMICOLON)) { }

            if (c.check(Type.RBRACE))
                break;

            crack.body.add(parseStatement());
        }

        c.expect(Type.RBRACE, "}");
        c.match(Type.SEMICOLON);

        fn.cracks.add(crack);
    }

    private Statement parseStatement() {

        if (c.isKeyword("if"))
            return conditional.parseIf();

        if (c.isKeyword("switch"))
            return conditional.parseSwitch();

        if (c.isKeyword("repeat"))
            return conditional.parseRepeat();

        if (c.isKeyword("loop"))
            return conditional.parseLoop();

        if (c.isKeyword("while"))
            return conditional.parseWhile();

        if (c.isKeyword("for"))
            return conditional.parseFor();

        if (c.isKeyword("volatile")) {
            c.advance();

            if (inject.isAhead())
                return inject.parse(true);

            if (vars.isDeclarationAhead())
                return vars.parseDeclaration(true);

            throw c.error(
                "'volatile' debe preceder una declaracion de variable "
                + "o una operacion ashley observable"
            );
        }

        if (c.isKeyword("matrix") && !(c.peekAt(1).type == Type.ASSIGN)) {
            return parseMatrix();
        }

        if (c.isKeyword("unreturnable") || c.isKeyword("unreturn")) {

            c.advance();

            UnreturnableStmt u = new UnreturnableStmt();

            Builder.Untokenizer.Token t = c.peek();
            Builder.Untokenizer.Token n = c.peekAt(1);

            if (
                t.type == Type.IDENT
                && Nums.isType(t.text)
                && (n.type == Type.SEMICOLON || n.type == Type.RBRACE || n.type == Type.EOF)
            ) {
                c.advance();
                u.typeHint = t.text;
            } else {
                u.value = exprs.parseExpression();
            }

            c.match(Type.SEMICOLON);

            return u;
        }

        if (Funcs.isTerminateAhead(c)) {

            c.advance();

            ReturnStmt t;

            if (c.match(Type.SEMICOLON) || c.check(Type.RBRACE))
                t = new ReturnStmt(null);
            else {
                t = new ReturnStmt(exprs.parseExpression());
                c.match(Type.SEMICOLON);
            }

            t.terminate = true;
            return t;
        }

        if (c.isKeyword("return")) {
            c.advance();

            if (
                c.match(Type.SEMICOLON)
                || c.check(Type.RBRACE)
            ) {
                return new ReturnStmt(null);
            }

            Expression e =
                exprs.parseExpression();

            c.match(Type.SEMICOLON);

            return new ReturnStmt(e);
        }

        if (inject.isAhead())
            return inject.parse(false);

        if (vars.isDeclarationAhead())
            return vars.parseDeclaration(false);

        if (c.check(Type.STAR))
            return vars.parseDerefAssignment();

        boolean dotAssign =
            c.peekAt(0).type == Type.IDENT
            && c.peekAt(1).type == Type.DOT
            && c.peekAt(2).type == Type.IDENT
            && c.peekAt(3).type == Type.ASSIGN;

        boolean arrowAssign =
            c.peekAt(0).type == Type.IDENT
            && c.peekAt(1).type == Type.MINUS
            && c.peekAt(2).type == Type.GT
            && c.peekAt(3).type == Type.IDENT
            && c.peekAt(4).type == Type.ASSIGN;

        if (c.peekAt(0).type == Type.IDENT && !dotAssign && !arrowAssign) {
            int k = 1, levels = 0;
            while (true) {
                if (c.peekAt(k).type == Type.MINUS && c.peekAt(k + 1).type == Type.GT && c.peekAt(k + 2).type == Type.IDENT) { k += 3; levels++; continue; }
                if (c.peekAt(k).type == Type.DOT && c.peekAt(k + 1).type == Type.IDENT) { k += 2; levels++; continue; }
                break;
            }
            if (levels >= 2 && c.peekAt(k).type == Type.ASSIGN) {
                Expression target = exprs.parseExpression();
                c.expect(Type.ASSIGN, "=");
                Expression value = exprs.parseExpression();
                c.match(Type.SEMICOLON);
                return new Assignment(target, value);
            }
        }

        if (dotAssign || arrowAssign) {

            MemberAssignStmt m = new MemberAssignStmt();

            m.base = c.advance().text;
            m.arrow = arrowAssign;
            c.advance();
            if (arrowAssign) c.advance();
            m.member = c.advance().text;
            c.advance();
            do {
                m.values.add(exprs.parseExpression());
            } while (c.match(Type.COMMA));

            c.match(Type.SEMICOLON);

            return m;
        }

        if (vars.isIndexAssignmentAhead())
            return vars.parseIndexAssignment();

        if (vars.isAssignmentAhead())
            return vars.parseAssignment();

        if (
            "++".equals(c.peek().text)
            || "--".equals(c.peek().text)
        ) {
            String op =
                c.peek().text;

            c.advance();

            if (!c.check(Type.IDENT)) {
                throw c.error(
                    op + " requiere un identificador"
                );
            }

            String name =
                c.expect(
                    Type.IDENT,
                    "identificador"
                ).text;

            c.match(Type.SEMICOLON);

            return new ExpressionStmt(
                new UnaryExpr(
                    op,
                    new ValueExpr(
                        "identifier",
                        name
                    ),
                    false
                )
            );
        }

        if (c.check(Type.IDENT)) {

            String nextText =
                c.peekAt(1).text;

            if (
                "++".equals(nextText)
                || "--".equals(nextText)
            ) {
                String name =
                    c.peek().text;

                c.advance();
                c.advance();

                c.match(Type.SEMICOLON);

                return new ExpressionStmt(
                    new UnaryExpr(
                        nextText,
                        new ValueExpr(
                            "identifier",
                            name
                        ),
                        true
                    )
                );
            }
        }

        if (
            c.check(Type.IDENT)
            && (
                c.peekAt(1).type == Type.LPAREN
                || c.text(
                    c.peekAt(1)
                ).equals(".")
                || (c.peekAt(1).type == Type.MINUS && c.peekAt(2).type == Type.GT)
            )
        ) {
            Expression e =
                exprs.parseExpression();

            c.match(Type.SEMICOLON);

            if (e instanceof CallExpr || e instanceof PtrCallExpr)
                return new ExpressionStmt(e);

            throw c.error(
                "La expresion no puede utilizarse como sentencia"
            );
        }

        throw c.error(
            "Sentencia inesperada: "
            + c.peek()
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
