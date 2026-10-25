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

package arch.builtin;

import java.util.*;

import Builder.ash.StringConv;
import arch.builtin.Tokens.Token;
import arch.builtin.Tokens.Type;

public class Parsing {

    public static class Program {

        public final List<FunctionDecl> functions =
            new ArrayList<>();

        public final List<ExternDecl> externs =
            new ArrayList<>();

        public final List<RawStmt> raws =
            new ArrayList<>();
    }

    public static class ExternDecl {

        public String name;
        public String alias;
        public String returnType;
        public boolean variadic;

        public final List<Param> params =
            new ArrayList<>();

        public ExternDecl(
            String name,
            String alias,
            String returnType
        ) {
            this.name = name;
            this.alias = alias;
            this.returnType = returnType;
        }
    }

    public static class FunctionDecl {

        public String name;
        public String returnType;

        public final List<Param> params =
            new ArrayList<>();

        public List<Stmt> body =
            new ArrayList<>();

        public boolean entry;

        public boolean naked;

        public boolean efiapi;
    }

    public static class Param {

        public final String type;
        public final String name;

        public Param(
            String type,
            String name
        ) {
            this.type = type;
            this.name = name;
        }
    }

    public static abstract class Stmt {
    }

    public static class TypeStmt extends Stmt {

        public String name;
        public String type;
    }

    public static class PtrDeclStmt extends Stmt {

        public String register;
        public String name;
        public String type;
        public long size;
    }

    public static class RegAssignStmt extends Stmt {

        public String name;
        public String opcode;

        public final List<String> args =
            new ArrayList<>();
    }

    public static class CallStmt extends Stmt {

        public String destination;
        public String function;

        public final List<String> args =
            new ArrayList<>();
    }

    public static class StoreStmt extends Stmt {

        public String pointer;
        public String value;
    }

    public static class IncDecStmt extends Stmt {

        public String operator;
        public String variable;

        public IncDecStmt(
            String operator,
            String variable
        ) {
            this.operator = operator;
            this.variable = variable;
        }
    }

    public static class RawStmt extends Stmt {

        public String directive;
        public String value;

        public String stringName;
        public String stringValue;

        public int gvarSize;

        public byte[] gdata;
        public int gdataBias;
    }

    public static class ReturnStmt extends Stmt {

        public String type;
        public String value;
    }

    public static class MacroBlockStmt extends Stmt {

        public String macroName;

        public final List<String> argsRaw =
            new ArrayList<>();

        public List<Stmt> body =
            new ArrayList<>();

        public List<Stmt> elseBody;
    }

    public static class ExpandedStmt extends Stmt {

        public List<Stmt> statements =
            new ArrayList<>();
    }

    private List<Token> tokens;
    private int pos;

    private final MacroRegistry macros;

    public Parsing() {
        this(new MacroRegistry());
    }

    public Parsing(MacroRegistry macros) {
        this.macros = macros;
    }

    public Program parse(List<Token> toks) {

        if (toks == null) {
            throw new IllegalArgumentException(
                "ASHIR parser: lista de tokens es null"
            );
        }

        tokens = toks;
        pos = 0;

        Program program =
            new Program();

        skipSemicolons();

        while (!check(Type.EOF)) {

            if (
                check(Type.IDENT) &&
                "extern".equals(peek().text)
            ) {

                program.externs.add(
                    parseExtern()
                );

                skipSemicolons();

                continue;
            }

            if (check(Type.DOT)) {

                RawStmt raw =
                    parseRaw();

                program.raws.add(raw);

                skipSemicolons();

                continue;
            }

            boolean entry = false;
            boolean naked = false;
            boolean efiapi = false;

            while (check(Type.LBRACKET)) {

                advance();

                Token attr =
                    expect(
                        Type.IDENT,
                        "atributo"
                    );

                expect(
                    Type.RBRACKET,
                    "]"
                );

                if ("entry".equals(attr.text)) {

                    entry = true;

                } else if ("naked".equals(attr.text)) {

                    naked = true;

                } else if ("efiapi".equals(attr.text)) {

                    efiapi = true;

                } else {

                    throw error(
                        "Atributo desconocido: " +
                        attr.text
                    );
                }
            }

            FunctionDecl fn =
                parseFunction();

            fn.entry =
                entry;

            fn.naked =
                naked;

            fn.efiapi =
                efiapi;

            program.functions.add(fn);

            skipSemicolons();
        }

        return program;
    }

    private ExternDecl parseExtern() {

        expectIdent("extern");

        String returnType =
            parseType();

        String name =
            expect(
                Type.IDENT,
                "nombre del simbolo externo"
            ).text;

        expect(
            Type.LPAREN,
            "("
        );

        ExternDecl ext =
            new ExternDecl(
                name,
                name,
                returnType
            );

        if (!check(Type.RPAREN)) {

            do {

                if (check(Type.DOT)) {

                    expect(Type.DOT, "...");
                    expect(Type.DOT, "...");
                    expect(Type.DOT, "...");

                    ext.variadic = true;
                    break;
                }

                String type =
                    parseType();

                String param =
                    check(Type.IDENT)
                        ? advance().text
                        : "__p" + ext.params.size();

                if (match(Type.LBRACKET)) {

                    expect(
                        Type.RBRACKET,
                        "]"
                    );

                    type = "ptr";
                }

                ext.params.add(
                    new Param(
                        type,
                        param
                    )
                );

            } while (match(Type.COMMA));
        }

        expect(
            Type.RPAREN,
            ")"
        );

        if (
            check(Type.IDENT) &&
            "as".equals(peek().text)
        ) {

            advance();

            ext.alias =
                expect(
                    Type.IDENT,
                    "alias externo"
                ).text;

            expect(
                Type.LPAREN,
                "("
            );

            int argumentCount = 0;

            if (!check(Type.RPAREN)) {

                do {

                    expect(
                        Type.IDENT,
                        "argumento del alias"
                    );

                    argumentCount++;

                } while (match(Type.COMMA));
            }

            expect(
                Type.RPAREN,
                ")"
            );

            if (
                argumentCount !=
                ext.params.size()
            ) {

                throw error(
                    "El alias externo '" +
                    ext.alias +
                    "' tiene " +
                    argumentCount +
                    " argumentos, pero la funcion externa tiene " +
                    ext.params.size()
                );
            }
        }

        return ext;
    }

    private FunctionDecl parseFunction() {

        expectIdent("func");

        FunctionDecl fn =
            new FunctionDecl();

        String prefixReturnType = null;

        if (
            check(Type.IDENT) &&
            isType(peek().text)
        ) {

            prefixReturnType =
                advance().text;
        }

        fn.name =
            expect(
                Type.IDENT,
                "nombre de funcion"
            ).text;

        expect(
            Type.LPAREN,
            "("
        );

        if (!check(Type.RPAREN)) {

            do {

                String type =
                    parseType();

                String name =
                    expect(
                        Type.IDENT,
                        "nombre de parametro"
                    ).text;

                if (match(Type.LBRACKET)) {

                    expect(
                        Type.RBRACKET,
                        "]"
                    );

                    type = "ptr";
                }

                fn.params.add(
                    new Param(
                        type,
                        name
                    )
                );

            } while (match(Type.COMMA));
        }

        expect(
            Type.RPAREN,
            ")"
        );

        String suffixReturnType = null;

        if (match(Type.COLON)) {

            suffixReturnType =
                parseType();
        }

        if (
            prefixReturnType != null &&
            suffixReturnType != null
        ) {

            if (
                !prefixReturnType.equals(
                    suffixReturnType
                )
            ) {

                throw error(
                    "El tipo de retorno antes y despues " +
                    "de la funcion no coincide: " +
                    prefixReturnType +
                    " != " +
                    suffixReturnType
                );
            }

            fn.returnType =
                prefixReturnType;

        } else if (
            prefixReturnType != null
        ) {

            fn.returnType =
                prefixReturnType;

        } else if (
            suffixReturnType != null
        ) {

            fn.returnType =
                suffixReturnType;

        } else {

            fn.returnType =
                "void";
        }

        fn.body =
            parseBlock();

        return fn;
    }

    private String parseType() {

        Token t =
            peek();

        if (
            t.type == Type.IDENT &&
            isType(t.text)
        ) {

            advance();

            return t.text;
        }

        throw error(
            "Se esperaba un tipo pero se encontro " +
            t
        );
    }

    private boolean isType(String s) {

        if (s == null)
            return false;

        switch (s) {

            case "void":

            case "byte":
            case "word":
            case "dword":
            case "qword":

            case "int8":
            case "int16":
            case "int32":
            case "int64":

            case "uint8":
            case "uint16":
            case "uint32":
            case "uint64":

            case "float32":
            case "float64":

            case "ptr":

            case "string":
            case "cstring":

                return true;

            default:

                return false;
        }
    }

    private List<Stmt> parseBlock() {

        expect(
            Type.LBRACE,
            "{"
        );

        List<Stmt> stmts =
            new ArrayList<>();

        while (!check(Type.RBRACE)) {

            if (check(Type.EOF)) {

                throw error(
                    "Fin de archivo dentro de un bloque; " +
                    "se esperaba '}'"
                );
            }

            if (match(Type.SEMICOLON))
                continue;

            Stmt stmt =
                parseStmt();

            stmts.add(stmt);

            skipSemicolons();
        }

        expect(
            Type.RBRACE,
            "}"
        );

        return stmts;
    }

    private Stmt parseStmt() {

        if (check(Type.SEMICOLON)) {

            advance();

            return new ExpandedStmt();
        }

        if (check(Type.DOT)) {

            if (
                peekAt(1).type == Type.IDENT &&
                "type".equals(peekAt(1).text)
            ) {

                return parseTypeStmt();
            }

            return parseRaw();
        }

        if (check(Type.MACRO))
            return parseMacro();

        if (
            check(Type.IDENT) &&
            "ptr".equals(peek().text) &&
            peekAt(1).type == Type.LBRACKET
        ) {

            return parsePtrDecl();
        }

        if (
            check(Type.IDENT) &&
            (
                "inc".equals(peek().text) ||
                "dec".equals(peek().text)
            )
        ) {

            Stmt result =
                parseIncDec();

            skipSemicolons();

            return result;
        }

        if (
            check(Type.IDENT) &&
            "ret".equals(peek().text)
        ) {

            Stmt result =
                parseReturn();

            skipSemicolons();

            return result;
        }

        if (
            check(Type.IDENT) &&
            "store".equals(peek().text) &&
            (
                peekAt(1).type == Type.LBRACKET ||
                peekAt(1).type == Type.LPAREN
            )
        ) {

            Stmt result =
                parseStore();

            skipSemicolons();

            return result;
        }

        if (isCallAhead())
            return parseCall();

        if (
            check(Type.IDENT) &&
            isJcc(peek().text)
        ) {

            return parseJcc();
        }

        if (
            check(Type.IDENT) &&
            "label".equals(peek().text)
        ) {

            advance();

            String label =
                expect(
                    Type.IDENT,
                    "nombre de etiqueta"
                ).text;

            RegAssignStmt s =
                new RegAssignStmt();

            s.name = "__label";
            s.opcode = "label";
            s.args.add(label);

            skipSemicolons();

            return s;
        }

        if (check(Type.REGVAR)) {

            Stmt result =
                parseRegAssign();

            skipSemicolons();

            return result;
        }

        throw error(
            "Sentencia inesperada: " +
            peek()
        );
    }

    private Stmt parseIncDec() {

        Token opcode =
            expect(
                Type.IDENT,
                "inc/dec"
            );

        if (
            !"inc".equals(opcode.text) &&
            !"dec".equals(opcode.text)
        ) {

            throw error(
                "Operacion inc/dec invalida: " +
                opcode.text
            );
        }

        Token variable =
            expect(
                Type.IDENT,
                "nombre de variable para " +
                opcode.text
            );

        if (
            variable.text == null ||
            variable.text.isEmpty()
        ) {

            throw error(
                opcode.text +
                " requiere el nombre de una variable"
            );
        }

        IncDecStmt stmt =
            new IncDecStmt(
                opcode.text,
                variable.text
            );

        skipSemicolons();

        return stmt;
    }

    private boolean isJcc(String opcode) {

        if (opcode == null)
            return false;

        switch (opcode.toLowerCase()) {

            case "je":
            case "jz":

            case "jne":
            case "jnz":

            case "jg":
            case "jge":

            case "jl":
            case "jle":

            case "ja":
            case "jae":

            case "jb":
            case "jbe":

                return true;

            default:

                return false;
        }
    }

    private Stmt parseJcc() {

        String opcode =
            expect(
                Type.IDENT,
                "salto condicional"
            ).text;

        expect(
            Type.LBRACKET,
            "["
        );

        RegAssignStmt s =
            new RegAssignStmt();

        s.name = "__jcc";
        s.opcode = opcode;

        if (!check(Type.RBRACKET)) {

            do {

                s.args.add(
                    parseArg()
                );

            } while (
                match(Type.COMMA)
            );
        }

        expect(
            Type.RBRACKET,
            "]"
        );

        skipSemicolons();

        return s;
    }

    private boolean isCallAhead() {

        if (
            check(Type.IDENT) &&
            "call".equals(peek().text) &&
            peekAt(1).type == Type.LBRACKET
        ) {

            return true;
        }

        if (
            check(Type.REGVAR) &&
            peekAt(1).type == Type.IDENT &&
            "call".equals(peekAt(1).text) &&
            peekAt(2).type == Type.LBRACKET
        ) {

            return true;
        }

        return false;
    }

    private Stmt parseCall() {

        CallStmt stmt =
            new CallStmt();

        if (check(Type.REGVAR)) {

            stmt.destination =
                advance().text;
        }

        expectIdent("call");

        expect(
            Type.LBRACKET,
            "["
        );

        Token fn =
            expect(
                Type.IDENT,
                "nombre de funcion"
            );

        stmt.function =
            fn.text;

        if (match(Type.COMMA)) {

            do {

                stmt.args.add(
                    parseArg()
                );

            } while (
                match(Type.COMMA)
            );
        }

        expect(
            Type.RBRACKET,
            "]"
        );

        skipSemicolons();

        return stmt;
    }

    private TypeStmt parseTypeStmt() {

        expect(
            Type.DOT,
            "."
        );

        Token directive =
            expect(
                Type.IDENT,
                "directiva"
            );

        if (!"type".equals(directive.text)) {

            throw error(
                "Directiva de tipo desconocida: ." +
                directive.text
            );
        }

        TypeStmt stmt =
            new TypeStmt();

        stmt.name =
            expect(
                Type.IDENT,
                "nombre de variable"
            ).text;

        stmt.type =
            parseType();

        skipSemicolons();

        return stmt;
    }

    private RawStmt parseRaw() {

        expect(
            Type.DOT,
            "."
        );

        Token directive =
            expect(
                Type.IDENT,
                "directiva"
            );

        if (
            !directive.text.equals("string") &&
            !directive.text.equals("gvar") &&
            !directive.text.equals("gdata") &&
            !directive.text.equals("section") &&
            !directive.text.equals("raw8") &&
            !directive.text.equals("raw16") &&
            !directive.text.equals("raw32") &&
            !directive.text.equals("raw64")
        ) {

            throw error(
                "Directiva ASHIR desconocida: ." +
                directive.text
            );
        }

        RawStmt stmt =
            new RawStmt();

        stmt.directive =
            directive.text;

        if ("gdata".equals(directive.text)) {

            stmt.stringName = expect(Type.IDENT, "nombre del dato global").text;

            String hex = expect(Type.IDENT, "bytes en hexadecimal (xAABB..)").text;

            if (!hex.startsWith("x") || (hex.length() & 1) == 0)
                throw error(".gdata: se esperaban bytes en hexadecimal tras 'x'");

            stmt.gdata = new byte[(hex.length() - 1) / 2];

            for (int q = 0; q < stmt.gdata.length; q++)
                stmt.gdata[q] = (byte) Integer.parseInt(hex.substring(1 + 2 * q, 3 + 2 * q), 16);

            stmt.gvarSize = stmt.gdata.length;
            stmt.value = "0";

            if (check(Type.NUMBER))
                stmt.gdataBias = Integer.parseInt(advance().text);

            skipSemicolons();
            return stmt;
        }

        if ("section".equals(directive.text)) {

            stmt.stringName = expect(Type.IDENT, "simbolo").text;
            stmt.stringValue = StringConv.stripQuotes(expect(Type.STRING, "nombre de seccion").text);
            stmt.value = check(Type.STRING) ? StringConv.stripQuotes(advance().text) : "";

            skipSemicolons();
            return stmt;
        }

        if ("gvar".equals(directive.text)) {

            Token gname =
                expect(
                    Type.IDENT,
                    "nombre de variable global"
                );

            Token gsize =
                expect(
                    Type.NUMBER,
                    "tamano de variable global"
                );

            stmt.stringName = gname.text;
            stmt.gvarSize = Integer.parseInt(gsize.text);
            stmt.value = parseArg();

        } else if ("string".equals(directive.text)) {

            Token symbol =
                expect(
                    Type.IDENT,
                    "simbolo de string"
                );

            Token string =
                expect(
                    Type.STRING,
                    "literal de string"
                );

            stmt.stringName =
                symbol.text;

            String raw =
                StringConv.stripQuotes(
                    string.text
                );

            stmt.stringValue =
                StringConv.decodeEscapes(
                    raw
                );

            stmt.value =
                symbol.text +
                " " +
                stmt.stringValue;

        } else {

            stmt.value =
                parseArg();
        }

        skipSemicolons();

        return stmt;
    }

    private Stmt parsePtrDecl() {

        expectIdent("ptr");

        expect(
            Type.LBRACKET,
            "["
        );

        String register =
            expect(
                Type.IDENT,
                "registro"
            ).text;

        expect(
            Type.ARROW,
            "-->"
        );

        String name =
            expect(
                Type.PTRVAR,
                "$variable"
            ).text;

        String type =
            parseType();

        long size = 0;

        if (match(Type.LT)) {

            size =
                Long.decode(
                    expect(
                        Type.NUMBER,
                        "tamano"
                    ).text
                );

            expect(
                Type.GT,
                ">"
            );
        }

        expect(
            Type.RBRACKET,
            "]"
        );

        skipSemicolons();

        PtrDeclStmt s =
            new PtrDeclStmt();

        s.register = register;
        s.name = name;
        s.type = type;
        s.size = size;

        return s;
    }

    private Stmt parseStore() {

        expectIdent("store");

        boolean bracket =
            match(Type.LBRACKET);

        if (!bracket) {

            expect(
                Type.LPAREN,
                "("
            );
        }

        Token pointerToken =
            advance();

        if (
            pointerToken.type != Type.IDENT &&
            pointerToken.type != Type.PTRVAR &&
            pointerToken.type != Type.REGVAR
        ) {

            throw error(
                "Se esperaba un puntero pero se encontro " +
                pointerToken
            );
        }

        String pointer =
            pointerToken.text;

        expect(
            Type.COMMA,
            ","
        );

        String value =
            parseArg();

        if (bracket) {

            expect(
                Type.RBRACKET,
                "]"
            );

        } else {

            expect(
                Type.RPAREN,
                ")"
            );
        }

        skipSemicolons();

        StoreStmt s =
            new StoreStmt();

        s.pointer = pointer;
        s.value = value;

        return s;
    }

    private Stmt parseRegAssign() {

        String name =
            expect(
                Type.REGVAR,
                "%registro virtual"
            ).text;

        String opcode =
            expect(
                Type.IDENT,
                "operacion"
            ).text;

        expect(
            Type.LBRACKET,
            "["
        );

        RegAssignStmt s =
            new RegAssignStmt();

        s.name = name;
        s.opcode = opcode;

        if (!check(Type.RBRACKET)) {

            do {

                s.args.add(
                    parseArg()
                );

            } while (
                match(Type.COMMA)
            );
        }

        expect(
            Type.RBRACKET,
            "]"
        );

        skipSemicolons();

        return s;
    }

    private String parseArg() {

        Token t =
            advance();

        if (
            t.type == Type.OP &&
            "-".equals(t.text) &&
            check(Type.NUMBER)
        ) {
            return "-" + advance().text;
        }

        switch (t.type) {

            case NUMBER:
            case IDENT:
            case PTRVAR:
            case REGVAR:
            case STRING:

                return t.text;

            default:

                throw error(
                    "Argumento invalido: " +
                    t
                );
        }
    }

    private Stmt parseReturn() {

        expectIdent("ret");

        ReturnStmt s =
            new ReturnStmt();

        s.type =
            parseType();

        if (
            !"void".equals(s.type) &&
            !check(Type.RBRACE) &&
            !check(Type.EOF) &&
            !check(Type.SEMICOLON)
        ) {

            s.value =
                parseArg();
        }

        skipSemicolons();

        return s;
    }

    private Stmt parseMacro() {

        Token m =
            expect(
                Type.MACRO,
                "@macro"
            );

        expect(
            Type.LPAREN,
            "("
        );

        StringBuilder raw =
            new StringBuilder();

        int depth = 1;

        while (depth > 0) {

            if (check(Type.EOF)) {

                throw error(
                    "Fin de archivo dentro de los argumentos " +
                    "de la macro @" +
                    m.text
                );
            }

            Token t =
                advance();

            if (t.type == Type.LPAREN)
                depth++;

            if (t.type == Type.RPAREN) {

                depth--;

                if (depth == 0)
                    break;
            }

            raw
                .append(t.text)
                .append(' ');
        }

        MacroBlockStmt block =
            new MacroBlockStmt();

        block.macroName =
            m.text;

        if (raw.length() > 0) {

            block.argsRaw.add(
                raw.toString().trim()
            );
        }

        block.body =
            parseBlock();

        if (
            check(Type.MACRO) &&
            "else".equals(peek().text)
        ) {

            advance();

            block.elseBody =
                parseBlock();
        }

        return macros.expand(
            block,
            this
        );
    }

    private void skipSemicolons() {

        while (check(Type.SEMICOLON))
            advance();
    }

    private Token peek() {
        return tokens.get(pos);
    }

    private Token peekAt(int offset) {

        int p =
            pos + offset;

        if (
            p >= 0 &&
            p < tokens.size()
        ) {

            return tokens.get(p);
        }

        return tokens.get(
            tokens.size() - 1
        );
    }

    private Token advance() {
        return tokens.get(pos++);
    }

    private boolean check(Type t) {
        return peek().type == t;
    }

    private boolean match(Type t) {

        if (check(t)) {

            advance();

            return true;
        }

        return false;
    }

    private Token expect(
        Type t,
        String what
    ) {

        if (!check(t)) {

            throw error(
                "Se esperaba " +
                what +
                " pero se encontro " +
                peek()
            );
        }

        return advance();
    }

    private void expectIdent(String text) {

        if (
            !check(Type.IDENT) ||
            !text.equals(peek().text)
        ) {

            throw error(
                "Se esperaba '" +
                text +
                "' pero se encontro " +
                peek()
            );
        }

        advance();
    }

    private IllegalArgumentException error(
        String msg
    ) {

        return new IllegalArgumentException(
            "Error de parseo (linea " +
            peek().line +
            "): " +
            msg
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
