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

public final class ParamCopies {

    private ParamCopies() { }

    public static void rewrite(Program program) {

        for (Function fn : program.functions) {

            Set<String> params = new LinkedHashSet<>();

            for (Parameter p : fn.parameters)
                params.add(p.name);

            Set<String> written = new LinkedHashSet<>();

            scan(fn.statements, params, written);

            for (Crack c : fn.cracks)
                scan(c.body, new HashSet<>(), new HashSet<>());

            if (written.isEmpty())
                continue;

            List<Statement> prologue = new ArrayList<>();

            for (Parameter p : fn.parameters) {

                if (!written.contains(p.name))
                    continue;

                String local = p.name;

                p.name = "__arg_" + local;

                String type = p.rawType != null && !p.rawType.endsWith("*") ? p.rawType : p.type;

                VariableDecl d = new VariableDecl(
                    p.type, local, new ValueExpr(p.name, "identifier"), false
                );
                d.rawType = type;

                prologue.add(d);
            }

            fn.statements.addAll(0, prologue);
        }
    }

    private static void scan(List<Statement> body, Set<String> params, Set<String> written) {

        if (body == null) return;

        for (Statement st : body) {

            if (st instanceof Assignment) {

                Assignment a = (Assignment) st;

                if (a.target instanceof ValueExpr) {
                    ValueExpr v = (ValueExpr) a.target;
                    if ("identifier".equals(v.type) && params.contains(v.value))
                        written.add(v.value);
                } else if (a.name != null && params.contains(a.name)) {
                    written.add(a.name);
                }

            } else if (st instanceof ExpressionStmt && incDecTarget(((ExpressionStmt) st).expression) != null) {

                String n = incDecTarget(((ExpressionStmt) st).expression);
                if (params.contains(n)) written.add(n);
            } else if (st instanceof IncDecStmt) {
                if (params.contains(((IncDecStmt) st).variable)) written.add(((IncDecStmt) st).variable);
            } else if (st instanceof IfStmt) {
                scan(((IfStmt) st).thenBranch, params, written);
                scan(((IfStmt) st).elseBranch, params, written);
            } else if (st instanceof WhileStmt) {
                scan(((WhileStmt) st).body, params, written);
            } else if (st instanceof ForStmt) {
                ForStmt f = (ForStmt) st;
                if (f.init instanceof Assignment)
                    scan(Collections.singletonList(f.init), params, written);
                if (f.update instanceof Assignment || f.update instanceof ExpressionStmt || f.update instanceof IncDecStmt)
                    scan(Collections.singletonList(f.update), params, written);
                scan(f.body, params, written);
            }
        }
    }

    private static String incDecTarget(Expression e) {
        String op = null;
        Expression x = null;
        if (e instanceof UnaryExpr) { op = ((UnaryExpr) e).operator; x = ((UnaryExpr) e).expression; }
        else if (e instanceof Ast.UnaryExpr) { op = ((Ast.UnaryExpr) e).operator; x = ((Ast.UnaryExpr) e).expression; }
        if (op == null || !(op.equals("++") || op.equals("--")) || !(x instanceof ValueExpr)) return null;
        ValueExpr v = (ValueExpr) x;
        if ("identifier".equals(v.type)) return v.value;
        if ("identifier".equals(v.value)) return v.type;
        return null;
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
