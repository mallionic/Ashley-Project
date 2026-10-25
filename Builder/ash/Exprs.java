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

import Builder.Untokenizer.Token;
import Builder.Untokenizer.Type;
import Builder.ash.Ast.*;

public final class Exprs {

    private final Cursor c;

    public Exprs(Cursor c) {
        this.c = c;
    }

    public static String opcode(String operator) {

        switch (operator) {

            case "+":
                return "add";

            case "-":
                return "sub";

            case "*":
                return "imul";

            case "/":
                return "div";

            case "%":
                return "mod";

            case "<<":
                return "shl";

            case ">>":
                return "shr";

            case "&":
                return "and";

            case "|":
                return "or";

            case "^":
                return "xor";

            default:
                throw new IllegalArgumentException(
                    "Operador no soportado en ASHIR: " +
                    operator
                );
        }
    }

    public Expression parseExpression() {
        return parseBitOr();
    }

    private Expression parseBitOr() {

        Expression left = parseBitXor();

        while (c.check(Type.BITOR))
            left = new BinaryExpr(left, c.advance().text, parseBitXor());

        return left;
    }

    private Expression parseBitXor() {

        Expression left = parseBitAnd();

        while (c.check(Type.BITXOR))
            left = new BinaryExpr(left, c.advance().text, parseBitAnd());

        return left;
    }

    private Expression parseBitAnd() {

        Expression left = parseComparison();

        while (c.check(Type.BITAND))
            left = new BinaryExpr(left, c.advance().text, parseComparison());

        return left;
    }

    private Expression parseComparison() {

        Expression left =
            parseShift();

        while (
            c.check(Type.EQ) ||
            c.check(Type.NE) ||
            c.check(Type.LT) ||
            c.check(Type.LE) ||
            c.check(Type.GT) ||
            c.check(Type.GE)
        ) {

            String op =
                c.advance().text;

            Expression right =
                parseShift();

            left =
                new BinaryExpr(
                    left,
                    op,
                    right
                );
        }

        return left;
    }

    private Expression parseShift() {

        Expression left =
            parseAdditive();

        while (
            c.check(Type.SHL) ||
            c.check(Type.SHR)
        ) {

            String op =
                c.advance().text;

            left =
                new BinaryExpr(
                    left,
                    op,
                    parseAdditive()
                );
        }

        return left;
    }

    private Expression parseAdditive() {

        Expression left =
            parseMultiplicative();

        while (
            c.check(Type.PLUS) ||
            c.check(Type.MINUS)
        ) {

            String op =
                c.advance().text;

            left =
                new BinaryExpr(
                    left,
                    op,
                    parseMultiplicative()
                );
        }

        return left;
    }

    private Expression parseMultiplicative() {

        Expression left =
            parsePower();

        while (
            c.check(Type.STAR) ||
            c.check(Type.SLASH) ||
            c.check(Type.PERCENT)
        ) {

            String op =
                c.advance().text;

            left =
                new BinaryExpr(
                    left,
                    op,
                    parsePower()
                );
        }

        return left;
    }

    public static boolean usedPow = false;

    public static boolean usedSqPow = false;

    private Expression parsePower() {

        Expression base = parseUnary();

        if (c.match(Type.SQPOW)) {

            Expression n = parsePower();

            java.math.BigInteger b = literalValue(base);
            java.math.BigInteger k = literalValue(n);

            if (b != null && k != null) {

                if (k.signum() < 0)
                    throw c.error("el numero de cuadrados en '***' no puede ser negativo");

                java.math.BigInteger r = b;

                for (int i = 0; i < k.intValue(); i++) {
                    r = r.multiply(r);
                    if (r.bitLength() > 64)
                        throw c.error("el resultado de '***' no cabe en 64 bits");
                }

                return new ValueExpr(r.toString(), "integer");
            }

            usedSqPow = true;

            CallExpr call = new CallExpr("__ash_sqpow");
            call.arguments.add(base);
            call.arguments.add(n);

            return call;
        }

        if (!c.match(Type.POW))
            return base;

        Expression exp = parsePower();

        java.math.BigInteger b = literalValue(base);
        java.math.BigInteger e = literalValue(exp);

        if (b != null && e != null) {

            if (e.signum() < 0)
                throw c.error("exponente negativo en '**' (solo enteros)");

            if (e.bitLength() > 31 || e.intValue() > 4096)
                throw c.error("exponente demasiado grande en '**'");

            java.math.BigInteger r = b.pow(e.intValue());

            if (r.bitLength() > 64)
                throw c.error("el resultado de '**' no cabe en 64 bits: " + r);

            return new ValueExpr(r.toString(), "integer");
        }

        usedPow = true;

        CallExpr call = new CallExpr("__ash_pow");
        call.arguments.add(base);
        call.arguments.add(exp);

        return call;
    }

    private static java.math.BigInteger literalValue(Expression e) {

        if (!(e instanceof ValueExpr))
            return null;

        ValueExpr v = (ValueExpr) e;

        if (!"integer".equals(v.type))
            return null;

        String t = v.value.replace("_", "");

        try {
            if (t.startsWith("0x") || t.startsWith("0X"))
                return new java.math.BigInteger(t.substring(2), 16);
            if (t.startsWith("0b") || t.startsWith("0B"))
                return new java.math.BigInteger(t.substring(2), 2);
            return new java.math.BigInteger(t);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public Expression parseUnary() {

        if (c.check(Builder.Untokenizer.Type.HASH)) {
            c.advance();
            return new LengthExpr(parseUnary());
        }

        if (c.check(Builder.Untokenizer.Type.LBRACE)) {
            c.advance();

            java.util.List<Expression> el = new java.util.ArrayList<>();

            if (!c.check(Builder.Untokenizer.Type.RBRACE)) {
                while (true) {
                    el.add(parseExpression());

                    if (!c.match(Builder.Untokenizer.Type.COMMA))
                        break;

                    if (c.check(Builder.Untokenizer.Type.RBRACE))
                        break;
                }
            }

            c.expect(Builder.Untokenizer.Type.RBRACE, "}");

            return new ArrayLiteralExpr(el);
        }

        Expression e = parseUnaryCore();

        while (c.check(Builder.Untokenizer.Type.LBRACKET)) {
            c.advance();

            Expression idx = parseExpression();

            c.expect(Builder.Untokenizer.Type.RBRACKET, "]");

            e = new IndexExpr(e, idx);
        }

        return e;
    }

    private Expression parseUnaryCore() {

        if (c.match(Type.PLUSPLUS)) {

            Expression value =
                parseUnary();

            return new UnaryExpr(
                "++",
                value
            );
        }

        if (c.match(Type.MINUSMINUS)) {

            Expression value =
                parseUnary();

            return new UnaryExpr(
                "--",
                value
            );
        }

        if (c.match(Type.BITAND)) {

            return new AddressOfExpr(
                parseUnary()
            );
        }

        if (c.match(Type.BITNOT)) {

            return new BinaryExpr(
                parseUnary(),
                "^",
                new ValueExpr("-1", "integer")
            );
        }

        if (c.match(Type.STAR)) {

            return new DereferenceExpr(
                parseUnary()
            );
        }

        if (c.match(Type.MINUS)) {

            return new BinaryExpr(
                new ValueExpr(
                    "0",
                    "integer"
                ),
                "-",
                parseUnary()
            );
        }

        if (c.match(Type.PLUS)) {

            return parseUnary();
        }

        return parsePostfix();
    }

private Expression parsePostfix() {

    Expression expression =
        parsePrimary();

    while (true) {

        if (c.match(Type.PLUSPLUS)) {

            expression =
                new UnaryExpr(
                    "++",
                    expression,
                    true
                );

            continue;
        }

        if (c.match(Type.MINUSMINUS)) {

            expression =
                new UnaryExpr(
                    "--",
                    expression,
                    true
                );

            continue;
        }

        break;
    }

    return expression;
}

    private boolean isCastAhead() {

        if (!c.check(Type.LPAREN))
            return false;

        Token first =
            c.rawAt(1);

        if (
            first == null ||
            first.type != Type.IDENT ||
            !Nums.isType(first.text)
        ) {
            return false;
        }

        int p = 2;

        Token t =
            c.rawAt(p);

        if (
            t != null &&
            t.type == Type.STAR
        ) {

            p++;

            t =
                c.rawAt(p);
        }

        return (
            t != null &&
            t.type == Type.RPAREN
        );
    }

    private Expression parsePrimary() {

        if (isCastAhead()) {

            c.expect(
                Type.LPAREN,
                "("
            );

            String type =
                c.parseTypeWithPointer();

            c.expect(
                Type.RPAREN,
                ")"
            );

            return new CastExpr(
                Nums.valueType(type),
                parseUnary()
            );
        }

        if (c.match(Type.LPAREN)) {

            Expression e =
                parseExpression();

            c.expect(
                Type.RPAREN,
                ")"
            );

            return e;
        }

        if (c.check(Type.IDENT)) {

            if (
                c.peek().text.equals("L") &&
                c.peekAt(1).type == Type.STRING &&
                c.peekAt(1).line == c.peek().line &&
                c.peekAt(1).column == c.peek().column + 1
            ) {
                c.advance();
                StringBuilder w = new StringBuilder();
                w.append(StringConv.decodeEscapes(StringConv.stripQuotes(c.advance().text)));

                while (
                    c.check(Type.IDENT) && c.peek().text.equals("L") &&
                    c.peekAt(1).type == Type.STRING
                ) {
                    c.advance();
                    w.append(StringConv.decodeEscapes(StringConv.stripQuotes(c.advance().text)));
                }
                return new GlobalAddrExpr(WideStrings.symbolFor(w.toString()), "word");
            }

            String name =
                c.advance().text;

            if (isChainAhead()) {
                Expression cur = new ValueExpr(name, "identifier");
                while (true) {
                    if (c.check(Type.MINUS) && c.peekAt(1).type == Type.GT && c.peekAt(2).type == Type.IDENT) {
                        c.advance(); c.advance();
                        cur = new ChainExpr(cur, c.advance().text, true);
                        continue;
                    }
                    if (c.text(c.peek()).equals(".") && c.peekAt(1).type == Type.IDENT) {
                        c.advance();
                        cur = new ChainExpr(cur, c.advance().text, false);
                        continue;
                    }
                    break;
                }
                if (c.match(Type.LPAREN)) {
                    CallExpr tmp = new CallExpr("__icall");
                    parseCallArguments(tmp);
                    PtrCallExpr pc = new PtrCallExpr(cur);
                    pc.arguments.addAll(tmp.arguments);
                    return pc;
                }
                return cur;
            }

            if (
                c.check(Type.MINUS)
                && c.peekAt(1).type == Type.GT
                && c.peekAt(2).type == Type.IDENT
            ) {
                c.advance();
                c.advance();

                return new MemberExpr(name, c.advance().text, true);
            }

            if (
                c.text(c.peek()).equals(".")
            ) {

                c.advance();

                String member =
                    c.expect(
                        Type.IDENT,
                        "miembro"
                    ).text;

                if (!c.check(Type.LPAREN))
                    return new MemberExpr(name, member, false);

                c.expect(
                    Type.LPAREN,
                    "("
                );

                CallExpr call =
                    new CallExpr(
                        name + "_" + member
                    );

                parseCallArguments(call);

                return call;
            }

            if (c.match(Type.LPAREN)) {

                CallExpr call =
                    new CallExpr(name);

                parseCallArguments(call);

                if (name.equals("va_count") && call.arguments.isEmpty())
                    return new ValueExpr("__vac", "identifier");

                if (name.equals("va_arg") && call.arguments.size() == 1) {

                    CallExpr g = new CallExpr("__va_get");

                    g.arguments.add(call.arguments.get(0));

                    for (int vi = 0; vi < Funcs.VARIADIC_MAX; vi++)
                        g.arguments.add(new ValueExpr("__va" + vi, "identifier"));

                    return g;
                }

                return call;
            }

            return new ValueExpr(
                name,
                "identifier"
            );
        }

        if (c.check(Type.NUMBER)) {

            return new ValueExpr(
                c.advance().text,
                "integer"
            );
        }

        if (c.check(Type.FLOAT)) {

            return new ValueExpr(
                c.advance().text,
                "float"
            );
        }

        if (c.check(Type.STRING)) {

            return new ValueExpr(
                c.advance().text,
                "string"
            );
        }

        if (c.check(Type.CHAR)) {

            return new ValueExpr(
                c.advance().text,
                "char"
            );
        }

        throw c.error(
            "Se esperaba una expresion, encontrado " +
            c.peek()
        );
    }

    private boolean isChainAhead() {
        int k = 0;
        int levels = 0;
        boolean anyArrow = false;
        while (true) {
            if (c.peekAt(k).type == Type.MINUS && c.peekAt(k + 1).type == Type.GT && c.peekAt(k + 2).type == Type.IDENT) {
                k += 3; levels++; anyArrow = true; continue;
            }
            if (c.text(c.peekAt(k)).equals(".") && c.peekAt(k + 1).type == Type.IDENT) {
                k += 2; levels++; continue;
            }
            break;
        }
        if (levels >= 2) return true;
        return levels == 1 && anyArrow && c.peekAt(k).type == Type.LPAREN;
    }

    private void parseCallArguments(
        CallExpr call
    ) {

        if (!c.check(Type.RPAREN)) {

            do {

                call.arguments.add(
                    parseExpression()
                );

            } while (
                c.match(Type.COMMA)
            );
        }

        c.expect(
            Type.RPAREN,
            ")"
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
