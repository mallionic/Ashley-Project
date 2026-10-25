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

public final class Conditional {

    private final Cursor c;
    private final Exprs exprs;
    private final Vars vars;
    private final Inject inject;

    java.util.function.Supplier<Statement> general;

    public Conditional(
        Cursor c,
        Exprs exprs,
        Vars vars,
        Inject inject
    ) {
        this.c = c;
        this.exprs = exprs;
        this.vars = vars;
        this.inject = inject;
    }

    public IfStmt parseIf() {

        if (c.isKeyword("elif"))
            c.advance();
        else
            c.expectKeyword("if");

        c.expect(
            Type.LPAREN,
            "("
        );

        Expression condition =
            parseCondition();

        c.expect(
            Type.RPAREN,
            ")"
        );

        List<Statement> thenBody =
            parseBlock();

        List<Statement> elseBody =
            null;

        if (c.isKeyword("else")) {

            c.advance();

            if (c.isKeyword("if")) {

                IfStmt nested =
                    parseIf();

                elseBody =
                    new ArrayList<>();

                elseBody.add(nested);

            } else {

                elseBody =
                    parseBlock();
            }

        } else if (c.isKeyword("elif")) {

            elseBody =
                new ArrayList<>();

            elseBody.add(parseIf());
        }

        return new IfStmt(
            condition,
            thenBody,
            elseBody
        );
    }

    public WhileStmt parseWhile() {

        c.expectKeyword("while");

        c.expect(
            Type.LPAREN,
            "("
        );

        Expression condition =
            parseCondition();

        c.expect(
            Type.RPAREN,
            ")"
        );

        List<Statement> body =
            parseBlock();

        return new WhileStmt(
            condition,
            body
        );
    }

    public ForStmt parseFor() {

        c.expectKeyword("for");

        c.expect(
            Type.LPAREN,
            "("
        );

        Statement init =
            parseForInit();

        if (init == null || init instanceof ExpressionStmt)
            c.expect(Type.SEMICOLON, ";");
        else
            c.match(Type.SEMICOLON);

        Expression condition =
            null;

        if (!c.check(Type.SEMICOLON)) {

            condition =
                parseCondition();
        }

        c.expect(
            Type.SEMICOLON,
            ";"
        );

        Statement update =
            null;

        if (!c.check(Type.RPAREN)) {

            if (vars.isAssignmentAhead()) {

                update =
                    vars.parseAssignment();

            } else {

                Expression updateExpr =
                    exprs.parseExpression();

                update =
                    new ExpressionStmt(
                        updateExpr
                    );

                c.match(Type.SEMICOLON);
            }
        }

        c.expect(
            Type.RPAREN,
            ")"
        );

        List<Statement> body =
            parseBlock();

        return new ForStmt(
            init,
            condition,
            update,
            body
        );
    }

    private Statement parseForInit() {

        if (c.check(Type.SEMICOLON))
            return null;

        if (vars.isDeclarationAhead())
            return vars.parseDeclaration(false);

        if (vars.isAssignmentAhead())
            return vars.parseAssignment();

        Expression e =
            exprs.parseExpression();

        return new ExpressionStmt(e);
    }

    private static int switchCounter = 0;

    public Statement parseSwitch() {

        c.expectKeyword("switch");
        c.expect(Type.LPAREN, "(");

        Expression subject = parseCondition();

        c.expect(Type.RPAREN, ")");
        c.expect(Type.LBRACE, "{");

        int id = switchCounter++;
        String sw = "__sw" + id;
        String matched = "__swm" + id;

        List<Expression[]> noop = null;

        List<List<Expression>> caseValues = new ArrayList<>();
        List<List<Statement>> caseBodies = new ArrayList<>();
        List<Expression> everyValue = new ArrayList<>();

        while (!c.check(Type.RBRACE) && !c.check(Type.EOF)) {

            List<Expression> values = new ArrayList<>();

            if (c.isKeyword("case")) {

                c.advance();

                do {
                    Expression v = exprs.parseExpression();
                    values.add(v);
                    everyValue.add(v);
                } while (c.match(Type.COMMA));

                c.expect(Type.COLON, ":");

            } else if (c.isKeyword("default")) {

                c.advance();
                c.expect(Type.COLON, ":");
                values = null;

            } else {
                throw c.error("expected 'case' or 'default' inside switch");
            }

            List<Statement> body = new ArrayList<>();

            while (
                !c.check(Type.RBRACE) &&
                !c.check(Type.EOF) &&
                !c.isKeyword("case") &&
                !c.isKeyword("default")
            ) {
                while (c.match(Type.SEMICOLON)) { }

                if (
                    c.check(Type.RBRACE) ||
                    c.isKeyword("case") ||
                    c.isKeyword("default")
                )
                    break;

                body.add(parseStatement());
            }

            caseValues.add(values);
            caseBodies.add(body);
        }

        c.expect(Type.RBRACE, "}");

        Expression swRef = new ValueExpr(sw, "identifier");
        Expression mRef = new ValueExpr(matched, "identifier");

        Expression zero = new ValueExpr("0", "int64");
        Expression one = new ValueExpr("1", "int64");

        List<Statement> loopBody = new ArrayList<>();

        for (int i = 0; i < caseValues.size(); i++) {

            List<Expression> values = caseValues.get(i);

            Expression cond;

            if (values == null) {

                Expression any = null;

                for (Expression v : everyValue) {
                    Expression eq = new BinaryExpr(
                        new ValueExpr(sw, "identifier"), "==", v
                    );
                    any = any == null ? eq : new BinaryExpr(any, "||", eq);
                }

                cond = new BinaryExpr(
                    new ValueExpr(matched, "identifier"), "!=", zero
                );

                if (any != null)
                    cond = new BinaryExpr(
                        cond, "||",
                        new NotExpr(any)
                    );

            } else {

                cond = new BinaryExpr(
                    new ValueExpr(matched, "identifier"), "!=", zero
                );

                Expression hit = null;

                for (Expression v : values) {
                    Expression eq = new BinaryExpr(
                        new ValueExpr(sw, "identifier"), "==", v
                    );
                    hit = hit == null ? eq : new BinaryExpr(hit, "||", eq);
                }

                cond = new BinaryExpr(cond, "||", hit);
            }

            List<Statement> then = new ArrayList<>();
            then.add(new Assignment(matched, one));
            then.addAll(caseBodies.get(i));

            loopBody.add(new IfStmt(cond, then, new ArrayList<>()));
        }

        loopBody.add(new BreakStmt());

        Expression always =
            new BinaryExpr(new ValueExpr("1", "int64"), "==", new ValueExpr("1", "int64"));

        List<Statement> outer = new ArrayList<>();
        outer.add(new VariableDecl("int64", sw, subject));
        outer.add(new VariableDecl("int64", matched, new ValueExpr("0", "int64")));
        outer.add(new WhileStmt(always, loopBody));

        return new IfStmt(
            new BinaryExpr(new ValueExpr("1", "int64"), "==", new ValueExpr("1", "int64")),
            outer,
            new ArrayList<>()
        );
    }

    private static int repeatCounter = 0;

    public Statement parseRepeat() {

        c.advance();

        c.expect(Type.LPAREN, "(");
        Expression times = parseCondition();
        c.expect(Type.RPAREN, ")");

        List<Statement> body = parseBlock();

        String counter = "__rp" + (repeatCounter++);

        Statement init =
            new VariableDecl("int64", counter, new ValueExpr("0", "int64"));

        Expression cond =
            new BinaryExpr(new ValueExpr(counter, "identifier"), "<", times);

        Statement step =
            new Assignment(
                counter,
                new BinaryExpr(
                    new ValueExpr(counter, "identifier"),
                    "+",
                    new ValueExpr("1", "int64")
                )
            );

        return new ForStmt(init, cond, step, body);
    }

    public Statement parseLoop() {

        c.advance();

        List<Statement> body = parseBlock();

        Expression always =
            new BinaryExpr(new ValueExpr("1", "int64"), "==", new ValueExpr("1", "int64"));

        return new WhileStmt(always, body);
    }

    private List<Statement> parseBlock() {

        c.expect(
            Type.LBRACE,
            "{"
        );

        List<Statement> statements =
            new ArrayList<>();

        while (
            !c.check(Type.RBRACE) &&
            !c.check(Type.EOF)
        ) {

            while (
                c.match(Type.SEMICOLON)
            ) {

            }

            if (c.check(Type.RBRACE))
                break;

            statements.add(
                parseStatement()
            );
        }

        c.expect(
            Type.RBRACE,
            "}"
        );

        return statements;
    }

    private Statement parseStatement() {

        if (c.isKeyword("if"))
            return parseIf();

        if (c.isKeyword("switch"))
            return parseSwitch();

        if (c.isKeyword("repeat"))
            return parseRepeat();

        if (c.isKeyword("loop"))
            return parseLoop();

        if (c.isKeyword("while"))
            return parseWhile();

        if (c.isKeyword("for"))
            return parseFor();

        if (c.isKeyword("break")) {

            c.advance();

            c.match(Type.SEMICOLON);

            return new BreakStmt();
        }

        if (c.isKeyword("continue")) {

            c.advance();

            c.match(Type.SEMICOLON);

            return new ContinueStmt();
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
                c.match(Type.SEMICOLON) ||
                c.check(Type.RBRACE)
            ) {

                return new ReturnStmt(null);
            }

            Expression e =
                exprs.parseExpression();

            c.match(Type.SEMICOLON);

            return new ReturnStmt(e);
        }

        if (general != null)
            return general.get();

        if (inject.isAhead())
            return inject.parse(false);

        if (vars.isDeclarationAhead())
            return vars.parseDeclaration(false);

        if (c.check(Type.STAR))
            return vars.parseDerefAssignment();

        if (vars.isIndexAssignmentAhead())
            return vars.parseIndexAssignment();

        if (vars.isAssignmentAhead())
            return vars.parseAssignment();

        if (isExpressionStart()) {

            Expression e =
                exprs.parseExpression();

            c.match(Type.SEMICOLON);

            return new ExpressionStmt(e);
        }

        throw c.error(
            "Sentencia inesperada: " +
            c.peek()
        );
    }

private boolean isExpressionStart() {

    if (c.check(Type.IDENT))
        return true;

    if (c.check(Type.PLUSPLUS))
        return true;

    if (c.check(Type.MINUSMINUS))
        return true;

    if (c.check(Type.LPAREN))
        return true;

    if (c.check(Type.BITAND) || c.check(Type.BITNOT))
        return true;

    if (c.check(Type.STAR))
        return true;

    if (c.check(Type.PLUS))
        return true;

    if (c.check(Type.MINUS))
        return true;

    if (c.check(Type.NUMBER))
        return true;

    if (c.check(Type.FLOAT))
        return true;

    if (c.check(Type.STRING))
        return true;

    if (c.check(Type.CHAR))
        return true;

    return false;
}

    private boolean isNext(Type type) {

        if (c.rawAt(1) == null)
            return false;

        Type next =
            c.rawAt(1).type;

        return next == type;
    }

    private Expression bin(
        Expression l,
        String op,
        Expression r
    ) {

        return new BinaryExpr(
            l,
            op,
            r
        );
    }

    private Expression parseCondition() {

        return parseOr();
    }

    private Expression parseOr() {

        Expression l =
            parseAnd();

        while (isTextOp("||")) {

            c.advance();

            Expression r =
                parseAnd();

            l =
                bin(
                    l,
                    "||",
                    r
                );
        }

        return l;
    }

    private Expression parseAnd() {

        Expression l =
            parseNot();

        while (isTextOp("&&")) {

            c.advance();

            Expression r =
                parseNot();

            l =
                bin(
                    l,
                    "&&",
                    r
                );
        }

        return l;
    }

    private Expression parseNot() {

        if (isTextOp("!")) {

            c.advance();

            return new NotExpr(
                parseNot()
            );
        }

        return parseCondPrimary();
    }

    private Expression parseCondPrimary() {

        if (c.check(Type.LPAREN)) {

            int m = c.mark();

            try {
                c.advance();
                Expression inner = parseCondition();
                if (c.match(Type.RPAREN) && isConditionEnd())
                    return inner;
            } catch (RuntimeException ignored) {
            }

            c.reset(m);
        }

        return exprs.parseExpression();
    }

    private boolean isConditionEnd() {
        return c.check(Type.RPAREN) || c.check(Type.EOF) || c.check(Type.SEMICOLON)
            || c.check(Type.LBRACE) || isTextOp("&&") || isTextOp("||");
    }

    private boolean isTextOp(String text) {

        if (c.check(Type.EOF))
            return false;

        return text.equals(
            c.peek().text
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
