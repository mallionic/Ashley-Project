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

import java.math.BigInteger;
import java.util.*;

import Builder.ash.Ast.*;

public final class Globals {

    private Globals() { }

    public static BigInteger constEval(Expression e) {

        if (e instanceof ValueExpr) {

            ValueExpr v = (ValueExpr) e;
            String t = v.value == null ? "" : v.value.replace("_", "").trim();

            try {
                if (t.length() >= 3 && t.charAt(0) == '\'' && t.charAt(t.length() - 1) == '\'') {
                    String d = StringConv.decodeEscapes(t.substring(1, t.length() - 1));
                    if (d.length() == 1) return BigInteger.valueOf(d.charAt(0));
                }
                if (t.startsWith("0x") || t.startsWith("0X")) return new BigInteger(t.substring(2), 16);
                if (t.startsWith("0b") || t.startsWith("0B")) return new BigInteger(t.substring(2), 2);
                return new BigInteger(t);
            } catch (NumberFormatException ex) {
                return null;
            }
        }

        if (e instanceof CastExpr)
            return constEval(((CastExpr) e).expression);

        if (e instanceof BinaryExpr) {

            BinaryExpr b = (BinaryExpr) e;
            BigInteger l = constEval(b.left), r = constEval(b.right);

            if (l == null || r == null) return null;

            switch (b.operator) {
                case "+":  return l.add(r);
                case "-":  return l.subtract(r);
                case "*":  return l.multiply(r);
                case "/":  return r.signum() == 0 ? null : l.divide(r);
                case "%":  return r.signum() == 0 ? null : l.mod(r.abs());
                case "<<": return l.shiftLeft(r.intValue());
                case ">>": return l.shiftRight(r.intValue());
                case "&":  return l.and(r);
                case "|":  return l.or(r);
                case "^":  return l.xor(r);
                default:   return null;
            }
        }

        return null;
    }

    public static int bits(String normalized) {
        switch (normalized) {
            case "byte":  case "int8":  return 8;
            case "word":  case "int16": return 16;
            case "dword": case "int32": return 32;
            case "uint64": return 64;
            case "qword": return 64;
            default:      return 0;
        }
    }

    public static void checkRange(String name, String raw, String norm, BigInteger v) {

        int bits = bits(norm);
        boolean unsigned = Nums.isUnsigned(raw) && !raw.equals("qword");

        BigInteger min, max;

        if (unsigned) {
            min = BigInteger.ZERO;
            max = BigInteger.ONE.shiftLeft(bits).subtract(BigInteger.ONE);
        } else {
            min = BigInteger.ONE.shiftLeft(bits - 1).negate();
            max = BigInteger.ONE.shiftLeft(bits).subtract(BigInteger.ONE);
        }

        if (v.compareTo(min) < 0 || v.compareTo(max) > 0)
            throw new IllegalStateException(
                "el valor " + v + " no cabe en la global '" + name + "' (" + raw +
                ", rango " + min + " .. " + max + ")"
            );
    }

    public static String symbolOf(GlobalVar g) {
        return g.general ? g.name : "__g_" + g.name;
    }

    private static Map<String, GlobalVar> table;
    private static Set<String> locals;

    public static void rewrite(Program program) {

        if (program.globals.isEmpty())
            return;

        table = new HashMap<>();

        List<GlobalVar> arrays = new ArrayList<>();

        for (GlobalVar g : program.globals) {
            if (g.elemType != null) arrays.add(g);
            else table.put(g.name, g);
        }

        for (Function fn : program.functions) {

            locals = new HashSet<>();

            for (Parameter p : fn.parameters)
                locals.add(p.name);

            collectLocals(fn.statements);

            rewriteBlock(fn.statements);

            int at = 0;
            for (GlobalVar g : arrays) {
                if (locals.contains(g.name) || !mentions(fn, g.name))
                    continue;
                VariableDecl d = new VariableDecl(
                    g.elemType + "[]", g.name, new GlobalAddrExpr(symbolOf(g), g.elemType));
                d.rawType = g.rawType + "[]";
                fn.statements.add(at++, d);
            }
        }

        table = null;
        locals = null;
    }

    private static boolean mentions(Function fn, String name) {
        StringBuilder sb = new StringBuilder();
        dump(fn.statements, sb, 0);
        if (sb.indexOf("\u0000ALL") >= 0)
            return true;
        return java.util.regex.Pattern.compile("(?<![\\w$])" + java.util.regex.Pattern.quote(name) + "(?![\\w$])")
            .matcher(sb).find();
    }

    private static void dump(Object o, StringBuilder sb, int depth) {
        if (o == null || depth > 60) return;
        if (o instanceof List) {
            for (Object x : (List<?>) o) dump(x, sb, depth + 1);
            return;
        }
        if (o instanceof ValueExpr) { sb.append(' ').append(((ValueExpr) o).value).append(' '); return; }
        if (o instanceof VariableDecl) { VariableDecl d = (VariableDecl) o; sb.append(' ').append(d.name).append(' '); dump(d.value, sb, depth + 1); return; }
        if (o instanceof Assignment) { Assignment a = (Assignment) o; dump(a.target, sb, depth + 1); dump(a.value, sb, depth + 1); return; }
        if (o instanceof ExpressionStmt) { dump(((ExpressionStmt) o).expression, sb, depth + 1); return; }
        if (o instanceof ReturnStmt) { dump(((ReturnStmt) o).value, sb, depth + 1); return; }
        if (o instanceof IfStmt) { IfStmt f = (IfStmt) o; dump(f.condition, sb, depth + 1); dump(f.thenBranch, sb, depth + 1); dump(f.elseBranch, sb, depth + 1); return; }
        if (o instanceof WhileStmt) { WhileStmt w = (WhileStmt) o; dump(w.condition, sb, depth + 1); dump(w.body, sb, depth + 1); return; }
        if (o instanceof ForStmt) { ForStmt f = (ForStmt) o; dump(f.init, sb, depth + 1); dump(f.condition, sb, depth + 1); dump(f.update, sb, depth + 1); dump(f.body, sb, depth + 1); return; }
        if (o instanceof BinaryExpr) { BinaryExpr b = (BinaryExpr) o; dump(b.left, sb, depth + 1); dump(b.right, sb, depth + 1); return; }
        if (o instanceof UnaryExpr) { dump(((UnaryExpr) o).expression, sb, depth + 1); return; }
        if (o instanceof CallExpr) { CallExpr c = (CallExpr) o; sb.append(' ').append(c.name).append(' '); dump(c.arguments, sb, depth + 1); return; }
        if (o instanceof IndexExpr) { IndexExpr x = (IndexExpr) o; dump(x.base, sb, depth + 1); dump(x.index, sb, depth + 1); return; }
        if (o instanceof AddressOfExpr) { dump(((AddressOfExpr) o).expression, sb, depth + 1); return; }
        if (o instanceof DereferenceExpr) { dump(((DereferenceExpr) o).expression, sb, depth + 1); return; }
        if (o instanceof CastExpr) { dump(((CastExpr) o).expression, sb, depth + 1); return; }
        if (o instanceof NotExpr) { dump(((NotExpr) o).expression, sb, depth + 1); return; }
        if (o instanceof IncDecStmt) { sb.append(' ').append(((IncDecStmt) o).variable).append(' '); return; }
        if (o instanceof BreakStmt || o instanceof ContinueStmt) return;

        if (o instanceof GlobalAddrExpr || o instanceof InjectBytesStmt) return;
        if (o instanceof LengthExpr) { dump(((LengthExpr) o).expression, sb, depth + 1); return; }
        sb.append(" \u0000ALL ");
    }

    private static void collectLocals(List<Statement> body) {

        if (body == null) return;

        for (Statement st : body) {

            if (st instanceof VariableDecl) locals.add(((VariableDecl) st).name);
            else if (st instanceof IfStmt) {
                collectLocals(((IfStmt) st).thenBranch);
                collectLocals(((IfStmt) st).elseBranch);
            } else if (st instanceof WhileStmt) {
                collectLocals(((WhileStmt) st).body);
            } else if (st instanceof ForStmt) {
                ForStmt f = (ForStmt) st;
                if (f.init instanceof VariableDecl) locals.add(((VariableDecl) f.init).name);
                collectLocals(f.body);
            }
        }
    }

    private static GlobalVar globalOf(Expression e) {

        if (e instanceof ValueExpr) {

            ValueExpr v = (ValueExpr) e;

            if ("identifier".equals(v.type) && v.value != null
                && table.containsKey(v.value) && !locals.contains(v.value))
                return table.get(v.value);
        }

        return null;
    }

    private static Expression deref(GlobalVar g) {
        return new DereferenceExpr(new GlobalAddrExpr(symbolOf(g), g.type));
    }

    private static void rewriteBlock(List<Statement> body) {

        if (body == null) return;

        for (int i = 0; i < body.size(); i++) {
            Statement r = incDecGlobal(body.get(i));
            if (r != null) body.set(i, r);
            rewriteStmt(body.get(i));
        }
    }

    private static Statement incDecGlobal(Statement st) {

        String op = null;
        String name = null;

        if (st instanceof IncDecStmt) {
            op = ((IncDecStmt) st).operator;
            name = ((IncDecStmt) st).variable;
        } else if (st instanceof ExpressionStmt) {
            Expression e = ((ExpressionStmt) st).expression;
            Expression operand = null;
            if (e instanceof UnaryExpr) { op = ((UnaryExpr) e).operator; operand = ((UnaryExpr) e).expression; }
            else if (e instanceof Ast.UnaryExpr) { op = ((Ast.UnaryExpr) e).operator; operand = ((Ast.UnaryExpr) e).expression; }
            if (operand instanceof ValueExpr) {
                ValueExpr v = (ValueExpr) operand;

                name = "identifier".equals(v.type) ? v.value : "identifier".equals(v.value) ? v.type : null;
            }
        }

        if (op == null || name == null || !(op.equals("++") || op.equals("--"))) return null;
        if (!table.containsKey(name) || locals.contains(name)) return null;

        return new Assignment(name, new BinaryExpr(
            new ValueExpr(name, "identifier"), op.equals("++") ? "+" : "-", new ValueExpr("1", "integer")));
    }

    private static void rewriteStmt(Statement st) {

        if (st == null) return;

        if (st instanceof VariableDecl) {
            VariableDecl d = (VariableDecl) st;
            d.value = rw(d.value);
            return;
        }

        if (st instanceof Assignment) {

            Assignment a = (Assignment) st;
            GlobalVar g = globalOf(a.target);

            if (g != null) {

                if (!g.isStatic)
                    throw new IllegalStateException(
                        "la global '" + g.name + "' no es static (es constante): " +
                        "declarala 'static " + g.rawType + " " + g.name + "' para poder cambiarla"
                    );

                a.target = deref(g);
                a.name = null;
                a.value = new CastExpr(g.rawType, rw(a.value));
                return;
            }

            a.target = rw(a.target);
            a.value = rw(a.value);
            return;
        }

        if (st instanceof ReturnStmt) {
            ReturnStmt r = (ReturnStmt) st;
            r.value = rw(r.value);
            return;
        }

        if (st instanceof ExpressionStmt) {
            ExpressionStmt x = (ExpressionStmt) st;
            x.expression = rw(x.expression);
            return;
        }

        if (st instanceof IfStmt) {
            IfStmt i = (IfStmt) st;
            i.condition = rw(i.condition);
            rewriteBlock(i.thenBranch);
            rewriteBlock(i.elseBranch);
            return;
        }

        if (st instanceof WhileStmt) {
            WhileStmt w = (WhileStmt) st;
            w.condition = rw(w.condition);
            rewriteBlock(w.body);
            return;
        }

        if (st instanceof ForStmt) {
            ForStmt f = (ForStmt) st;
            rewriteStmt(f.init);
            f.condition = rw(f.condition);
            rewriteStmt(f.update);
            rewriteBlock(f.body);
        }
    }

    private static Expression rw(Expression e) {

        if (e == null) return null;

        GlobalVar g = globalOf(e);

        if (g != null)
            return deref(g);

        if (e instanceof AddressOfExpr) {

            AddressOfExpr a = (AddressOfExpr) e;
            GlobalVar t = globalOf(a.expression);

            if (t != null)
                return new GlobalAddrExpr(symbolOf(t), t.type);

            a.expression = rw(a.expression);
            return a;
        }

        if (e instanceof BinaryExpr) {
            BinaryExpr b = (BinaryExpr) e;
            b.left = rw(b.left);
            b.right = rw(b.right);
            return b;
        }

        if (e instanceof Ast.UnaryExpr) {
            Ast.UnaryExpr u = (Ast.UnaryExpr) e;
            GlobalVar t = globalOf(u.expression);
            if (t != null && !t.isStatic)
                throw new IllegalStateException(
                    "la global '" + t.name + "' no es static (es constante)");
            u.expression = rw(u.expression);
            return u;
        }

        if (e instanceof UnaryExpr) {
            UnaryExpr u = (UnaryExpr) e;
            return new UnaryExpr(u.operator, rw(u.expression), u.postfix);
        }

        if (e instanceof NotExpr)
            return new NotExpr(rw(((NotExpr) e).expression));

        if (e instanceof CastExpr) {
            CastExpr c = (CastExpr) e;
            c.expression = rw(c.expression);
            return c;
        }

        if (e instanceof DereferenceExpr) {
            DereferenceExpr d = (DereferenceExpr) e;
            d.expression = rw(d.expression);
            return d;
        }

        if (e instanceof CallExpr) {
            CallExpr c = (CallExpr) e;
            for (int i = 0; i < c.arguments.size(); i++)
                c.arguments.set(i, rw(c.arguments.get(i)));
            return c;
        }

        if (e instanceof IndexExpr) {
            IndexExpr ix = (IndexExpr) e;
            return new IndexExpr(rw(ix.base), rw(ix.index));
        }

        if (e instanceof LengthExpr)
            return new LengthExpr(rw(((LengthExpr) e).expression));

        if (e instanceof ArrayLiteralExpr) {
            List<Expression> els = new ArrayList<>();
            for (Expression x : ((ArrayLiteralExpr) e).elements)
                els.add(rw(x));
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
