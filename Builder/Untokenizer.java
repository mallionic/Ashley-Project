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

package Builder;

import Builder.ash.AsmBlocks;
import Builder.ash.Directives;
import java.util.ArrayList;
import java.util.List;

public class Untokenizer {

    public enum Type {
        HASH,

        IDENT,

        NUMBER,
        FLOAT,

        STRING,
        CHAR,

        AT,

        LPAREN,
        RPAREN,

        LBRACE,
        RBRACE,

        LBRACKET,
        RBRACKET,

        COMMA,
        COLON,
        SEMICOLON,

        DOT,

        ASSIGN,
        PLUSPLUS,
        MINUSMINUS,
        PLUS,
        MINUS,
        STAR,
        SLASH,
        PERCENT,

        EQ,
        NE,
        LT,
        GT,
        LE,
        GE,
        SHL,
        SHR,
        COMPOUND,

        AND,
        OR,
        NOT,

        POW,

        SQPOW,

        BITAND,
        BITOR,
        BITXOR,
        BITNOT,

        ARROW,

        EOF
    }

    public static class Token {

        public final Type type;
        public final String text;

        public final int line;
        public final int column;

        public Token(
            Type type,
            String text,
            int line,
            int column
        ) {
            this.type = type;
            this.text = text;
            this.line = line;
            this.column = column;
        }

        @Override
        public String toString() {

            return type +
                   "(" +
                   text +
                   ")" +
                   "@" +
                   line +
                   ":" +
                   column;
        }
    }

    private final String source;

    private int position;
    private int line;
    private int column;

    public Untokenizer(String source) {

        this.source = Directives.process(AsmBlocks.extract(source));

        this.position = 0;
        this.line = 1;
        this.column = 1;
    }

    public List<Token> tokenize() {

        List<Token> tokens =
            new ArrayList<>();

        while (!eof()) {

            char c = peek();

            if (Character.isWhitespace(c)) {

                whitespace();

                continue;
            }

            if (
                c == '/' &&
                peekNext('/')
            ) {

                lineComment();

                continue;
            }

            if (
                c == '/' &&
                peekNext('*')
            ) {

                blockComment();

                continue;
            }

            int tokenLine = line;
            int tokenColumn = column;

            if (c == '"') {

                tokens.add(
                    source.startsWith("\"\"\"", position)
                        ? tripleString(tokenLine, tokenColumn)
                        : stringLiteral(tokenLine, tokenColumn)
                );

                continue;
            }

            if (c == '\'') {

                tokens.add(
                    charLiteral(
                        tokenLine,
                        tokenColumn
                    )
                );

                continue;
            }

            if (c == '@') {

                add(
                    tokens,
                    Type.AT,
                    "@",
                    tokenLine,
                    tokenColumn
                );

                advance();

                continue;
            }

            if (
                c == '-' &&
                peekNext('-') &&
                peekNextNext('>')
            ) {

                add(
                    tokens,
                    Type.ARROW,
                    "-->",
                    tokenLine,
                    tokenColumn
                );

                advance();
                advance();
                advance();

                continue;
            }

            if (position + 2 < source.length()) {

                String three =
                    source.substring(position, position + 3);

                if (three.equals("***")) {

                    add(tokens, Type.SQPOW, three,
                        tokenLine, tokenColumn);

                    advance();
                    advance();
                    advance();

                    continue;
                }

                if (three.equals("<<=") || three.equals(">>=")) {

                    add(tokens, Type.COMPOUND, three,
                        tokenLine, tokenColumn);

                    advance();
                    advance();
                    advance();

                    continue;
                }
            }

            if (position + 1 < source.length()) {

                String two =
                    source.substring(
                        position,
                        position + 2
                    );

                Type twoType =
                    twoCharacterOperator(two);

                if (twoType != null) {

                    add(
                        tokens,
                        twoType,
                        two,
                        tokenLine,
                        tokenColumn
                    );

                    advance();
                    advance();

                    continue;
                }
            }

            Type single =
                singleCharacter(c);

            if (single != null) {

                add(
                    tokens,
                    single,
                    String.valueOf(c),
                    tokenLine,
                    tokenColumn
                );

                advance();

                continue;
            }

            if (Character.isDigit(c)) {

                tokens.add(
                    number(
                        tokenLine,
                        tokenColumn
                    )
                );

                continue;
            }

            if (
                Character.isLetter(c) ||
                c == '_'
            ) {

                tokens.add(
                    identifier(
                        tokenLine,
                        tokenColumn
                    )
                );

                continue;
            }

            throw error(
                "Caracter inesperado '" +
                c +
                "'"
            );
        }

        tokens.add(
            new Token(
                Type.EOF,
                "",
                line,
                column
            )
        );

        return desugarCompound(memberIncrements(mergeStrings(collapseCTypes(tokens))));
    }

    private static boolean isTypeWord(String w) {
        switch (w) {
            case "unsigned": case "signed": case "long": case "short":
            case "int": case "char":
                return true;
            default:
                return false;
        }
    }

    private static String cType(int uns, int longs, boolean shrt, boolean chr) {

        boolean u = uns > 0;

        if (chr)  return u ? "uint8" : "int8";
        if (shrt) return u ? "uint16" : "int16";
        if (longs > 0) return u ? "uint64" : "int64";

        return u ? "uint32" : "int32";
    }

    private List<Token> collapseCTypes(List<Token> in) {

        List<Token> out = new ArrayList<>(in.size());

        for (int i = 0; i < in.size(); i++) {

            Token t = in.get(i);

            boolean start =
                t.type == Type.IDENT &&
                (
                    t.text.equals("unsigned") || t.text.equals("signed") ||
                    t.text.equals("long") || t.text.equals("short")
                ) &&
                (i == 0 || in.get(i - 1).type != Type.DOT);

            if (!start) {
                out.add(t);
                continue;
            }

            int uns = 0, sgn = 0, longs = 0, j = i;
            boolean shrt = false, chr = false, hasInt = false;

            while (
                j < in.size() &&
                in.get(j).type == Type.IDENT &&
                isTypeWord(in.get(j).text)
            ) {
                switch (in.get(j).text) {
                    case "unsigned": uns++; break;
                    case "signed":   sgn++; break;
                    case "long":     longs++; break;
                    case "short":    shrt = true; break;
                    case "char":     chr = true; break;
                    default:         hasInt = true; break;
                }
                j++;
            }

            if (j == i + 1 && uns == 0 && sgn == 0) {
                out.add(t);
                continue;
            }

            out.add(new Token(Type.IDENT, cType(uns, longs, shrt, chr), t.line, t.column));
            i = j - 1;
        }

        return out;
    }

    private static Type compoundType(String op) {

        switch (op) {
            case "+": return Type.PLUS;
            case "-": return Type.MINUS;
            case "*": return Type.STAR;
            case "/": return Type.SLASH;
            case "%": return Type.PERCENT;
            case "<<": return Type.SHL;
            case "&": return Type.BITAND;
            case "|": return Type.BITOR;
            case "^": return Type.BITXOR;
            default: return Type.SHR;
        }
    }

    private List<Token> memberIncrements(List<Token> in) {

        List<Token> out = new ArrayList<>(in.size());

        for (int i = 0; i < in.size(); i++) {

            Token t = in.get(i);

            boolean inc = t.type == Type.PLUSPLUS;
            boolean dec = t.type == Type.MINUSMINUS;

            if ((inc || dec) && !out.isEmpty()) {

                int n = out.size();
                int start = -1;

                if (n >= 3 && out.get(n - 1).type == Type.IDENT
                    && out.get(n - 2).type == Type.DOT && out.get(n - 3).type == Type.IDENT)
                    start = n - 3;
                else if (n >= 4 && out.get(n - 1).type == Type.IDENT
                    && out.get(n - 2).type == Type.GT && out.get(n - 3).type == Type.MINUS
                    && out.get(n - 4).type == Type.IDENT)
                    start = n - 4;

                Type next = i + 1 < in.size() ? in.get(i + 1).type : Type.EOF;

                if (start >= 0 && (next == Type.SEMICOLON || next == Type.RPAREN || next == Type.EOF)) {

                    List<Token> lv = new ArrayList<>(out.subList(start, n));

                    out.add(new Token(Type.ASSIGN, "=", t.line, t.column));
                    out.addAll(lv);
                    out.add(new Token(inc ? Type.PLUS : Type.MINUS, inc ? "+" : "-", t.line, t.column));
                    out.add(new Token(Type.NUMBER, "1", t.line, t.column));

                    continue;
                }
            }

            out.add(t);
        }

        return out;
    }

    private List<Token> desugarCompound(List<Token> in) {

        boolean any = false;

        for (Token t : in)
            if (t.type == Type.COMPOUND) { any = true; break; }

        if (!any)
            return in;

        List<Token> out = new ArrayList<>(in.size() + 16);
        java.util.ArrayDeque<Integer> pending = new java.util.ArrayDeque<>();
        int depth = 0;

        for (Token t : in) {

            boolean closer =
                t.type == Type.RPAREN ||
                t.type == Type.RBRACKET ||
                t.type == Type.RBRACE;

            boolean sep =
                t.type == Type.SEMICOLON ||
                t.type == Type.COMMA ||
                t.type == Type.EOF;

            while (
                !pending.isEmpty() &&
                (
                    (closer && depth == pending.peek()) ||
                    (sep && depth == pending.peek())
                )
            ) {
                pending.pop();
                out.add(new Token(Type.RPAREN, ")", t.line, t.column));
            }

            if (t.type == Type.COMPOUND) {

                int j = out.size() - 1;
                int start = -1;

                while (j >= 0) {

                    Token p = out.get(j);

                    if (p.type == Type.RBRACKET) {

                        int d = 0;

                        while (j >= 0) {
                            Type pt = out.get(j).type;
                            if (pt == Type.RBRACKET) d++;
                            if (pt == Type.LBRACKET) { d--; if (d == 0) break; }
                            j--;
                        }

                        j--;
                        continue;
                    }

                    if (p.type == Type.IDENT) {

                        start = j;

                        if (
                            j >= 2 &&
                            out.get(j - 1).type == Type.DOT &&
                            out.get(j - 2).type == Type.IDENT
                        ) {
                            j -= 2;
                            continue;
                        }

                        if (
                            j >= 3 &&
                            out.get(j - 1).type == Type.GT &&
                            out.get(j - 2).type == Type.MINUS &&
                            out.get(j - 3).type == Type.IDENT
                        ) {
                            j -= 3;
                            continue;
                        }

                        break;
                    }

                    break;
                }

                if (start < 0)
                    throw error("Asignacion compuesta sin variable a la izquierda");

                List<Token> lv =
                    new ArrayList<>(out.subList(start, out.size()));

                out.add(new Token(Type.ASSIGN, "=", t.line, t.column));
                out.addAll(lv);

                String op = t.text.substring(0, t.text.length() - 1);

                out.add(new Token(compoundType(op), op, t.line, t.column));
                out.add(new Token(Type.LPAREN, "(", t.line, t.column));

                pending.push(depth);

                continue;
            }

            if (
                t.type == Type.LPAREN ||
                t.type == Type.LBRACKET ||
                t.type == Type.LBRACE
            ) depth++;

            if (closer) depth--;

            out.add(t);
        }

        return out;
    }

    private Type singleCharacter(char c) {

        switch (c) {

            case '(':
                return Type.LPAREN;

            case ')':
                return Type.RPAREN;

            case '{':
                return Type.LBRACE;

            case '}':
                return Type.RBRACE;

            case '[':
                return Type.LBRACKET;

            case ']':
                return Type.RBRACKET;

            case ',':
                return Type.COMMA;

            case ':':
                return Type.COLON;

            case ';':
                return Type.SEMICOLON;

            case '.':
                return Type.DOT;

            case '=':
                return Type.ASSIGN;

            case '+':
                return Type.PLUS;

            case '-':
                return Type.MINUS;

            case '*':
                return Type.STAR;

            case '/':
                return Type.SLASH;

            case '#':
                return Type.HASH;

            case '%':
                return Type.PERCENT;

            case '<':
                return Type.LT;

            case '>':
                return Type.GT;

            case '!':
                return Type.NOT;

            case '&':
                return Type.BITAND;

            case '|':
                return Type.BITOR;

            case '^':
                return Type.BITXOR;

            case '~':
                return Type.BITNOT;

            default:
                return null;
        }
    }
private Type twoCharacterOperator(
    String text
) {
    switch (text) {

        case "++":
            return Type.PLUSPLUS;

        case "--":
            return Type.MINUSMINUS;

        case "==":
            return Type.EQ;

        case "!=":
            return Type.NE;

        case "**":
            return Type.POW;

        case "+=":
        case "-=":
        case "*=":
        case "/=":
        case "%=":
        case "&=":
        case "|=":
        case "^=":
            return Type.COMPOUND;

        case "<<":
            return Type.SHL;

        case ">>":
            return Type.SHR;

        case "<=":
            return Type.LE;

        case ">=":
            return Type.GE;

        case "&&":
            return Type.AND;

        case "||":
            return Type.OR;

        default:
            return null;
    }
}

    private void skipIntSuffix() {
        int n = 0;
        while (
            !eof() &&
            n < 3 &&
            (peek() == 'u' || peek() == 'U' || peek() == 'l' || peek() == 'L')
        ) {
            advance();
            n++;
        }
    }

    private Token number(
        int tokenLine,
        int tokenColumn
    ) {

        int start = position;

        boolean floating = false;

        if (
            peek() == '0' &&
            position + 1 < source.length() &&
            (
                source.charAt(position + 1) == 'x' ||
                source.charAt(position + 1) == 'X'
            )
        ) {

            advance();
            advance();

            while (
                !eof() &&
                isHex(peek())
            ) {
                advance();
            }

            int hexEnd = position;
            skipIntSuffix();

            return new Token(
                Type.NUMBER,
                source.substring(
                    start,
                    hexEnd
                ),
                tokenLine,
                tokenColumn
            );
        }

        if (
            peek() == '0' &&
            position + 1 < source.length() &&
            (
                source.charAt(position + 1) == 'b' ||
                source.charAt(position + 1) == 'B'
            )
        ) {

            advance();
            advance();

            while (
                !eof() &&
                (
                    peek() == '0' ||
                    peek() == '1'
                )
            ) {
                advance();
            }

            int binEnd = position;
            skipIntSuffix();

            return new Token(
                Type.NUMBER,
                source.substring(
                    start,
                    binEnd
                ),
                tokenLine,
                tokenColumn
            );
        }

        while (
            !eof() &&
            Character.isDigit(peek())
        ) {
            advance();
        }

        if (
            !eof() &&
            peek() == '.' &&
            position + 1 < source.length() &&
            Character.isDigit(
                source.charAt(position + 1)
            )
        ) {

            floating = true;

            advance();

            while (
                !eof() &&
                Character.isDigit(peek())
            ) {
                advance();
            }
        }

        if (
            !eof() &&
            (
                peek() == 'f' ||
                peek() == 'F'
            )
        ) {

            floating = true;

            advance();
        }

        int decEnd = position;

        if (!floating)
            skipIntSuffix();

        String text =
            source.substring(
                start,
                decEnd
            );

        return new Token(
            floating
                ? Type.FLOAT
                : Type.NUMBER,
            text,
            tokenLine,
            tokenColumn
        );
    }

    private Token identifier(
        int tokenLine,
        int tokenColumn
    ) {

        int start = position;

        advance();

        while (!eof()) {

            char c = peek();

            if (
                Character.isLetterOrDigit(c) ||
                c == '_'
            ) {
                advance();
            } else {
                break;
            }
        }

        return new Token(
            Type.IDENT,
            source.substring(
                start,
                position
            ),
            tokenLine,
            tokenColumn
        );
    }

    private Token tripleString(
        int tokenLine,
        int tokenColumn
    ) {

        advance();
        advance();
        advance();

        StringBuilder sb = new StringBuilder("\"");

        while (!eof()) {

            char c = peek();

            if (c == '"' && source.startsWith("\"\"\"", position)) {

                advance();
                advance();
                advance();

                sb.append('"');

                return new Token(
                    Type.STRING,
                    sb.toString(),
                    tokenLine,
                    tokenColumn
                );
            }

            if (c == '\\') {

                sb.append(c);
                advance();

                if (!eof()) {
                    sb.append(peek());
                    advance();
                }

                continue;
            }

            if (c == '\r') {
                advance();
                continue;
            }

            if (c == '\n')
                sb.append("\\n");
            else if (c == '"')
                sb.append("\\\"");
            else
                sb.append(c);

            advance();
        }

        throw error(
            "String \"\"\" sin cerrar"
        );
    }

    private List<Token> mergeStrings(List<Token> in) {

        List<Token> out = new ArrayList<>(in.size());

        for (Token t : in) {

            if (
                t.type == Type.STRING &&
                !out.isEmpty() &&
                out.get(out.size() - 1).type == Type.STRING
            ) {

                Token prev = out.remove(out.size() - 1);

                String a = prev.text.substring(0, prev.text.length() - 1);
                String b = t.text.substring(1);

                out.add(new Token(Type.STRING, a + b, prev.line, prev.column));

                continue;
            }

            out.add(t);
        }

        return out;
    }

    private Token stringLiteral(
        int tokenLine,
        int tokenColumn
    ) {

        int start = position;

        advance();

        while (!eof()) {

            char c = peek();

            if (c == '\\') {

                advance();

                if (!eof())
                    advance();

                continue;
            }

            if (c == '"') {

                advance();

                return new Token(
                    Type.STRING,
                    source.substring(
                        start,
                        position
                    ),
                    tokenLine,
                    tokenColumn
                );
            }

            if (c == '\n')
                throw error(
                    "String sin cerrar"
                );

            advance();
        }

        throw error(
            "String sin cerrar"
        );
    }

    private Token charLiteral(
        int tokenLine,
        int tokenColumn
    ) {

        int start = position;

        advance();

        while (!eof()) {

            char c = peek();

            if (c == '\\') {

                advance();

                if (!eof())
                    advance();

                continue;
            }

            if (c == '\'') {

                advance();

                return new Token(
                    Type.CHAR,
                    source.substring(
                        start,
                        position
                    ),
                    tokenLine,
                    tokenColumn
                );
            }

            advance();
        }

        throw error(
            "Char sin cerrar"
        );
    }

    private void lineComment() {

        advance();
        advance();

        while (
            !eof() &&
            peek() != '\n'
        ) {
            advance();
        }
    }

    private void blockComment() {

        int startLine = line;

        advance();
        advance();

        while (!eof()) {

            if (
                peek() == '*' &&
                peekNext('/')
            ) {

                advance();
                advance();

                return;
            }

            advance();
        }

        throw new IllegalArgumentException(
            "Comentario de bloque sin cerrar " +
            "en linea " +
            startLine
        );
    }

    private void whitespace() {

        while (
            !eof() &&
            Character.isWhitespace(peek())
        ) {
            advance();
        }
    }

    private boolean eof() {

        return position >= source.length();
    }

    private char peek() {

        return source.charAt(position);
    }

    private boolean peekNext(
        char expected
    ) {

        return position + 1 < source.length()
            && source.charAt(position + 1) == expected;
    }

    private boolean peekNextNext(
        char expected
    ) {

        return position + 2 < source.length()
            && source.charAt(position + 2) == expected;
    }

    private boolean isHex(char c) {

        return Character.isDigit(c) ||
               (c >= 'a' && c <= 'f') ||
               (c >= 'A' && c <= 'F');
    }

    private void add(
        List<Token> tokens,
        Type type,
        String text,
        int line,
        int column
    ) {

        tokens.add(
            new Token(
                type,
                text,
                line,
                column
            )
        );
    }

    private void advance() {

        if (eof())
            return;

        if (source.charAt(position) == '\n') {

            line++;
            column = 1;

        } else {

            column++;
        }

        position++;
    }

    private IllegalArgumentException error(
        String message
    ) {

        return new IllegalArgumentException(
            "ASH lexer error at " +
            line +
            ":" +
            column +
            ": " +
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
