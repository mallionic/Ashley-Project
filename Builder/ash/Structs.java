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
import Builder.ash.Ast.*;

public final class Structs {

    private Structs() { }

    public static final class Field {
        public String name;
        public String rawType;
        public String norm;
        public int size;
        public int offset;
        public int align;

        public String ptrType;

        public Def embed;
    }

    public static final class Def {
        public String name;
        public final List<Field> fields = new ArrayList<>();
        public int size;
        public int align = 1;

        public Field field(String n) {
            for (Field f : fields) if (f.name.equals(n)) return f;
            return null;
        }
    }

    private static final Map<String, Def> DEFS = new HashMap<>();

    public static boolean has(String type) {
        return type != null && DEFS.containsKey(type);
    }

    public static Def get(String type) {
        return DEFS.get(type);
    }

    private static Token at(List<Token> t, int i) {
        return i < t.size() ? t.get(i) : t.get(t.size() - 1);
    }

    private static boolean word(Token t, String w) {
        return t.type == Type.IDENT && t.text.equals(w);
    }

    private static final Map<String, String> ALIASES = new HashMap<>();

    private static void scanAliases(List<Token> in) {
        for (int i = 0; i + 3 < in.size(); i++) {
            if (!word(in.get(i), "typedef") || word(at(in, i + 1), "struct")) continue;
            Token base = at(in, i + 1);
            if (base.type != Type.IDENT) continue;
            int j = i + 2;
            StringBuilder stars = new StringBuilder();
            while (at(in, j).type == Type.STAR) { stars.append('*'); j++; }
            if (at(in, j).type == Type.IDENT && at(in, j + 1).type == Type.SEMICOLON)
                ALIASES.put(at(in, j).text, base.text + stars);
        }
    }

    private static String resolveAlias(String t) {
        for (int guard = 0; guard < 16; guard++) {
            String base = t.replace("*", "");
            String stars = t.substring(base.length());
            String a = ALIASES.get(base);
            if (a == null) return t;
            t = a + stars;
        }
        return t;
    }

    public static List<Token> expand(List<Token> in) {

        scanAliases(in);

        List<Token> out = new ArrayList<>(in.size());

        for (int i = 0; i < in.size(); i++) {

            Token t = in.get(i);

            if (word(t, "typedef") && word(at(in, i + 1), "struct")) {

                int j = i + 2;
                String tag = null;

                if (at(in, j).type == Type.IDENT && at(in, j + 1).type == Type.LBRACE) {
                    tag = at(in, j).text;
                    j++;
                }

                if (at(in, j).type == Type.LBRACE) {

                    int[] end = new int[1];
                    List<Field> fields = parseFields(in, j, end);

                    j = end[0];

                    String alias = null;

                    if (at(in, j).type == Type.IDENT) {
                        alias = at(in, j).text;
                        j++;
                    }

                    if (at(in, j).type == Type.SEMICOLON) j++;

                    String name = alias != null ? alias : tag;

                    if (name == null)
                        throw new IllegalStateException("struct sin nombre (linea " + t.line + ")");

                    Def d = build(name, fields);

                    DEFS.put(name, d);
                    if (tag != null) DEFS.put(tag, d);

                    i = j - 1;
                    continue;
                }
            }

            if (word(t, "struct") && at(in, i + 1).type == Type.IDENT) {

                String name = at(in, i + 1).text;

                if (at(in, i + 2).type == Type.LBRACE) {

                    int[] end = new int[1];
                    List<Field> fields = parseFields(in, i + 2, end);

                    int j = end[0];

                    if (at(in, j).type == Type.SEMICOLON) j++;

                    DEFS.put(name, build(name, fields));

                    i = j - 1;
                    continue;
                }

                if (DEFS.containsKey(name))
                    continue;
            }

            out.add(t);
        }

        return out;
    }

    private static List<Field> parseFields(List<Token> in, int open, int[] endOut) {

        List<Field> fields = new ArrayList<>();

        int i = open + 1;

        while (at(in, i).type != Type.RBRACE && at(in, i).type != Type.EOF) {

            if (at(in, i).type == Type.SEMICOLON) { i++; continue; }

            Token typeTok = at(in, i);

            if (typeTok.type != Type.IDENT)
                throw new IllegalStateException(
                    "campo de struct invalido en la linea " + typeTok.line + ": " + typeTok.text);

            i++;

            String typeName = resolveAlias(typeTok.text);
            boolean pointer = typeName.endsWith("*");
            typeName = typeName.replace("*", "");

            while (at(in, i).type == Type.STAR) { pointer = true; i++; }

            Token nameTok = at(in, i);

            if (nameTok.type != Type.IDENT)
                throw new IllegalStateException(
                    "falta el nombre del campo en la linea " + nameTok.line);

            i++;

            if (at(in, i).type == Type.LBRACKET)
                throw new IllegalStateException(
                    "campo '" + nameTok.text + "': los arrays dentro de struct no estan soportados todavia");

            Field f = new Field();
            f.name = nameTok.text;

            if (pointer) {
                f.rawType = "uint64";
                f.norm = "qword";
                f.ptrType = typeName;
                f.size = 8;
            } else if (DEFS.containsKey(typeName)) {

                f.embed = DEFS.get(typeName);
                f.size = f.embed.size;
                f.align = f.embed.align;
            } else {

                if (!Nums.isType(typeName))
                    throw new IllegalStateException(
                        "campo '" + nameTok.text + "': tipo no soportado '" + typeTok.text +
                        "' (usa enteros, bool, punteros u otra struct ya definida)");

                f.norm = Nums.normalizeType(typeName);

                if (Globals.bits(f.norm) == 0)
                    throw new IllegalStateException(
                        "campo '" + nameTok.text + "': tipo no soportado '" + typeTok.text + "'");

                f.rawType = typeName;
                f.size = Globals.bits(f.norm) / 8;
            }

            if (f.align == 0) f.align = f.size;

            fields.add(f);

            if (at(in, i).type == Type.SEMICOLON) i++;
        }

        endOut[0] = i + 1;

        return fields;
    }

    private static Def build(String name, List<Field> fields) {

        Def d = new Def();
        d.name = name;

        int off = 0;

        for (Field f : fields) {

            if (d.field(f.name) != null)
                throw new IllegalStateException("campo repetido '" + f.name + "' en struct " + name);

            int al = Math.max(1, f.align);
            off = (off + al - 1) / al * al;
            f.offset = off;
            off += f.size;

            d.align = Math.max(d.align, al);
            d.fields.add(f);
        }

        d.size = Math.max(1, (off + d.align - 1) / d.align * d.align);

        return d;
    }

    private static final class Info {
        Def def;
        boolean pointer;
    }

    private static Map<String, Info> scope;

    private static Info infoOf(String raw) {

        if (raw == null) return null;

        boolean ptr = raw.endsWith("*");
        String base = ptr ? raw.substring(0, raw.length() - 1) : raw;

        Def d = DEFS.get(base);

        if (d == null) return null;

        Info i = new Info();
        i.def = d;
        i.pointer = ptr;
        return i;
    }

    public static void rewrite(Program program) {

        Map<String, Info> globalScope = new HashMap<>();

        for (GlobalVar g : program.globals) {
            Info gi = infoOf(g.rawType);
            if (gi != null && gi.pointer)
                globalScope.put(g.name, gi);
        }

        for (Function fn : program.functions) {

            scope = new HashMap<>(globalScope);

            for (Parameter p : fn.parameters) {

                Info i = infoOf(p.rawType);

                if (i != null) {
                    if (!i.pointer)
                        throw new IllegalStateException(
                            "parametro '" + p.name + "' de '" + fn.name +
                            "': pasa la estructura por puntero (" + p.rawType + "*)");
                    scope.put(p.name, i);
                    p.type = "ptr";
                }
            }

            collect(fn.statements);

            block(fn.statements);
        }

        scope = null;
    }

    private static void collect(List<Statement> body) {

        if (body == null) return;

        for (Statement st : body) {

            if (st instanceof VariableDecl) {
                VariableDecl d = (VariableDecl) st;
                Info i = infoOf(d.rawType);
                if (i != null) scope.put(d.name, i);
            } else if (st instanceof IfStmt) {
                collect(((IfStmt) st).thenBranch);
                collect(((IfStmt) st).elseBranch);
            } else if (st instanceof WhileStmt) {
                collect(((WhileStmt) st).body);
            } else if (st instanceof ForStmt) {
                collect(((ForStmt) st).body);
            }
        }
    }

    private static Info infoOfExpr(Expression e) {
        if (e instanceof ValueExpr && "identifier".equals(((ValueExpr) e).type))
            return scope.get(((ValueExpr) e).value);
        if (e instanceof ChainExpr) {
            ChainExpr c = (ChainExpr) e;
            Field f = fieldOf(c.base, c.arrow, c.member);
            if (f.embed != null) {
                Info i = new Info(); i.def = f.embed; i.pointer = false; return i;
            }
            if (f.ptrType != null && DEFS.containsKey(f.ptrType)) {
                Info i = new Info(); i.def = DEFS.get(f.ptrType); i.pointer = true; return i;
            }
        }
        return null;
    }

    private static String describe(Expression e) {
        if (e instanceof ValueExpr) return ((ValueExpr) e).value;
        if (e instanceof ChainExpr) {
            ChainExpr c = (ChainExpr) e;
            return describe(c.base) + (c.arrow ? "->" : ".") + c.member;
        }
        return "(expresion)";
    }

    private static Field fieldOf(Expression base, boolean arrow, String member) {
        Info i = infoOfExpr(base);
        String b = describe(base);
        if (i == null)
            throw new IllegalStateException(
                "'" + b + (arrow ? "->" : ".") + member + "': '" + b + "' no es una estructura");
        if (arrow && !i.pointer)
            throw new IllegalStateException("'" + b + "' es una estructura, usa '" + b + "." + member + "'");
        if (!arrow && i.pointer)
            throw new IllegalStateException("'" + b + "' es un puntero a estructura, usa '" + b + "->" + member + "'");
        Field f = i.def.field(member);
        if (f == null)
            throw new IllegalStateException(
                "la estructura " + i.def.name + " no tiene el campo '" + member + "'");
        return f;
    }

    private static Expression structAddress(Expression base, boolean arrow) {
        if (base instanceof ValueExpr) {
            Info i = infoOfExpr(base);
            return i.pointer ? base : new AddressOfExpr(base);
        }
        ChainExpr c = (ChainExpr) base;
        Field f = fieldOf(c.base, c.arrow, c.member);
        Expression addr = fieldAddr(c.base, c.arrow, c.member);
        if (f.embed != null) return addr;
        return new DereferenceExpr(new CastExpr(f.rawType + "*", addr));
    }

    private static Expression fieldAddr(Expression base, boolean arrow, String member) {
        Field f = fieldOf(base, arrow, member);
        Expression bytes = new CastExpr("uint8*", structAddress(base, arrow));
        if (f.offset != 0)
            bytes = new BinaryExpr(bytes, "+", new ValueExpr(Integer.toString(f.offset), "integer"));
        return bytes;
    }

    private static Expression chainValue(ChainExpr c) {
        Field f = fieldOf(c.base, c.arrow, c.member);
        Expression addr = fieldAddr(c.base, c.arrow, c.member);
        if (f.embed != null) return addr;
        return new DereferenceExpr(new CastExpr(f.rawType + "*", addr));
    }

    private static Expression fieldPtr(String base, boolean arrow, String member) {

        Info i = scope.get(base);

        if (i == null)
            throw new IllegalStateException(
                "'" + base + (arrow ? "->" : ".") + member + "': '" + base + "' no es una estructura");

        if (arrow && !i.pointer)
            throw new IllegalStateException("'" + base + "' es una estructura, usa '" + base + "." + member + "'");

        Field f = i.def.field(member);

        if (f == null)
            throw new IllegalStateException(
                "la estructura " + i.def.name + " no tiene el campo '" + member + "'");

        Expression addr = i.pointer
            ? new ValueExpr(base, "identifier")
            : new AddressOfExpr(new ValueExpr(base, "identifier"));

        Expression bytes = new CastExpr("uint8*", addr);

        if (f.offset != 0)
            bytes = new BinaryExpr(bytes, "+", new ValueExpr(Integer.toString(f.offset), "integer"));

        return new CastExpr(f.rawType + "*", bytes);
    }

    private static void block(List<Statement> body) {

        if (body == null) return;

        for (int i = 0; i < body.size(); i++)
            body.set(i, stmt(body.get(i)));
    }

    private static Statement stmt(Statement st) {

        if (st == null) return null;

        if (st instanceof VariableDecl) {

            VariableDecl d = (VariableDecl) st;
            Info info = infoOf(d.rawType);

            if (info == null) {
                d.value = expr(d.value);
                return d;
            }

            if (info.pointer) {
                d.type = "ptr:byte";
                d.value = expr(d.value);
                return d;
            }

            if (d.value != null)
                throw new IllegalStateException(
                    "'" + d.name + "': inicializar una estructura con '=' no esta soportado; asigna campo a campo");

            VariableDecl nd = new VariableDecl("uint8[]", d.name, null, d.volatileVariable);
            nd.rawType = d.rawType;

            List<Expression> sizes = new ArrayList<>();
            sizes.add(new ValueExpr(Integer.toString(info.def.size), "integer"));
            ArrayInfo.put(nd, sizes);

            return nd;
        }

        if (st instanceof MemberAssignStmt) {

            MemberAssignStmt m = (MemberAssignStmt) st;

            if (!scope.containsKey(m.base))
                return st;

            if (m.values.size() != 1)
                throw new IllegalStateException("'" + m.base + "." + m.member + "' recibe un solo valor");

            Expression target = fieldPtr(m.base, m.arrow, m.member);
            Field f = scope.get(m.base).def.field(m.member);

            return new Assignment(
                new DereferenceExpr(target),
                new CastExpr(f.rawType, expr(m.values.get(0)))
            );
        }

        if (st instanceof Assignment) {
            Assignment a = (Assignment) st;
            if (a.target instanceof ChainExpr) {
                ChainExpr c = (ChainExpr) a.target;
                Field f = fieldOf(c.base, c.arrow, c.member);
                if (f.embed != null)
                    throw new IllegalStateException("no se puede asignar una estructura entera ('" + describe(c) + "'): asigna campo a campo");
                Expression addr = fieldAddr(c.base, c.arrow, c.member);
                return new Assignment(
                    new DereferenceExpr(new CastExpr(f.rawType + "*", addr)),
                    new CastExpr(f.rawType, expr(a.value)));
            }
            a.target = expr(a.target);
            a.value = expr(a.value);
            return a;
        }

        if (st instanceof ReturnStmt) {
            ReturnStmt r = (ReturnStmt) st;
            r.value = expr(r.value);
            return r;
        }

        if (st instanceof ExpressionStmt) {
            ExpressionStmt x = (ExpressionStmt) st;
            x.expression = expr(x.expression);
            return x;
        }

        if (st instanceof IfStmt) {
            IfStmt i = (IfStmt) st;
            i.condition = expr(i.condition);
            block(i.thenBranch);
            block(i.elseBranch);
            return i;
        }

        if (st instanceof WhileStmt) {
            WhileStmt w = (WhileStmt) st;
            w.condition = expr(w.condition);
            block(w.body);
            return w;
        }

        if (st instanceof ForStmt) {
            ForStmt f = (ForStmt) st;
            f.init = stmt(f.init);
            f.condition = expr(f.condition);
            f.update = stmt(f.update);
            block(f.body);
            return f;
        }

        return st;
    }

    private static Expression expr(Expression e) {

        if (e == null) return null;

        if (e instanceof MemberExpr) {
            MemberExpr m = (MemberExpr) e;
            return chainValue(new ChainExpr(new ValueExpr(m.base, "identifier"), m.member, m.arrow));
        }

        if (e instanceof ChainExpr)
            return chainValue((ChainExpr) e);

        if (e instanceof PtrCallExpr) {
            PtrCallExpr p = (PtrCallExpr) e;
            CallExpr call = new CallExpr("__icall");
            call.arguments.add(expr(p.target));
            for (Expression a : p.arguments)
                call.arguments.add(expr(a));
            return call;
        }

        if (e instanceof BinaryExpr) {
            BinaryExpr b = (BinaryExpr) e;
            b.left = expr(b.left);
            b.right = expr(b.right);
            return b;
        }

        if (e instanceof Ast.UnaryExpr) {
            Ast.UnaryExpr u = (Ast.UnaryExpr) e;
            u.expression = expr(u.expression);
            return u;
        }

        if (e instanceof UnaryExpr) {
            UnaryExpr u = (UnaryExpr) e;
            return new UnaryExpr(u.operator, expr(u.expression), u.postfix);
        }

        if (e instanceof NotExpr)
            return new NotExpr(expr(((NotExpr) e).expression));

        if (e instanceof CastExpr) {
            CastExpr c = (CastExpr) e;
            c.expression = expr(c.expression);
            return c;
        }

        if (e instanceof AddressOfExpr) {
            AddressOfExpr a = (AddressOfExpr) e;
            a.expression = expr(a.expression);
            return a;
        }

        if (e instanceof DereferenceExpr) {
            DereferenceExpr d = (DereferenceExpr) e;
            d.expression = expr(d.expression);
            return d;
        }

        if (e instanceof CallExpr) {
            CallExpr c = (CallExpr) e;
            for (int i = 0; i < c.arguments.size(); i++)
                c.arguments.set(i, expr(c.arguments.get(i)));
            return c;
        }

        if (e instanceof IndexExpr) {
            IndexExpr ix = (IndexExpr) e;
            return new IndexExpr(expr(ix.base), expr(ix.index));
        }

        if (e instanceof LengthExpr)
            return new LengthExpr(expr(((LengthExpr) e).expression));

        if (e instanceof ArrayLiteralExpr) {
            List<Expression> els = new ArrayList<>();
            for (Expression x : ((ArrayLiteralExpr) e).elements)
                els.add(expr(x));
            return new ArrayLiteralExpr(els);
        }

        return e;
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
