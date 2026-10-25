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

import Builder.ash.Ast.*;

public abstract class Rewriter {

    protected Expression onExpr(Expression e) { return null; }

    protected Statement onStmt(Statement s) { return null; }

    public final void run(Program program) {

        for (Function fn : program.functions) {
            block(fn.statements);
            for (Crack c : fn.cracks)
                block(c.body);
        }
    }

    protected final void block(List<Statement> body) {

        if (body == null) return;

        for (int i = 0; i < body.size(); i++)
            body.set(i, stmt(body.get(i)));
    }

    protected final Statement stmt(Statement st) {

        if (st == null) return null;

        Statement r = onStmt(st);

        if (r != null) return r;

        if (st instanceof VariableDecl) {
            VariableDecl d = (VariableDecl) st;
            d.value = expr(d.value);
            return d;
        }

        if (st instanceof Assignment) {
            Assignment a = (Assignment) st;
            a.target = expr(a.target);
            a.value = expr(a.value);
            return a;
        }

        if (st instanceof ReturnStmt) {
            ReturnStmt x = (ReturnStmt) st;
            x.value = expr(x.value);
            return x;
        }

        if (st instanceof ExpressionStmt) {
            ExpressionStmt x = (ExpressionStmt) st;
            x.expression = expr(x.expression);
            return x;
        }

        if (st instanceof UnreturnableStmt) {
            UnreturnableStmt u = (UnreturnableStmt) st;
            u.value = expr(u.value);
            return u;
        }

        if (st instanceof MemberAssignStmt) {
            MemberAssignStmt m = (MemberAssignStmt) st;
            for (int i = 0; i < m.values.size(); i++)
                m.values.set(i, expr(m.values.get(i)));
            return m;
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

    protected final Expression expr(Expression e) {

        if (e == null) return null;

        Expression r = onExpr(e);

        if (r != null) return r;

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
