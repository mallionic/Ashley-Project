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

public final class Cracks {

    private Cracks() { }

    public static String crackName(String fn, String crack) {
        return "__crack_" + fn + "_" + crack;
    }

    public static void rewrite(Program program) {

        List<Function> originals = new ArrayList<>(program.functions);

        for (Function f : originals) {

            boolean uses = containsUnret(f.statements);

            for (Crack c : f.cracks)
                uses |= containsUnret(c.body);

            String ret = "__ret_" + f.name;

            if (uses) {

                GlobalVar g = new GlobalVar();
                g.name = ret;
                g.rawType = "qword";
                g.type = "qword";
                g.isStatic = true;
                g.value = 0;
                program.globals.add(g);

                replaceUnret(f.statements, ret);

                for (Crack c : f.cracks)
                    replaceUnret(c.body, ret);

                if (!"void".equals(f.returnType))
                    addImplicitReturn(f, ret);
            }

            for (Crack c : f.cracks) {

                Function cf = new Function();
                cf.name = crackName(f.name, c.name);
                cf.returnType = "void";
                cf.origin = f.origin;
                cf.parameters.addAll(c.params);
                cf.statements.addAll(c.body);
                cf.statements.add(new ReturnStmt(null));

                program.functions.add(cf);
            }
        }

        for (Function f : new ArrayList<>(program.functions))
            resolveMembers(program, f.statements);
    }

    private static boolean containsUnret(List<Statement> body) {

        if (body == null) return false;

        for (Statement st : body) {
            if (st instanceof UnreturnableStmt) return true;
            if (st instanceof IfStmt
                && (containsUnret(((IfStmt) st).thenBranch) || containsUnret(((IfStmt) st).elseBranch))) return true;
            if (st instanceof WhileStmt && containsUnret(((WhileStmt) st).body)) return true;
            if (st instanceof ForStmt && containsUnret(((ForStmt) st).body)) return true;
        }

        return false;
    }

    private static void replaceUnret(List<Statement> body, String ret) {

        if (body == null) return;

        for (int i = 0; i < body.size(); i++) {

            Statement st = body.get(i);

            if (st instanceof UnreturnableStmt) {

                UnreturnableStmt u = (UnreturnableStmt) st;

                if (u.value == null) {
                    body.remove(i);
                    i--;
                } else {
                    body.set(i, new Assignment(ret, u.value));
                }

            } else if (st instanceof IfStmt) {
                replaceUnret(((IfStmt) st).thenBranch, ret);
                replaceUnret(((IfStmt) st).elseBranch, ret);
            } else if (st instanceof WhileStmt) {
                replaceUnret(((WhileStmt) st).body, ret);
            } else if (st instanceof ForStmt) {
                replaceUnret(((ForStmt) st).body, ret);
            }
        }
    }

    private static void addImplicitReturn(Function f, String ret) {

        retValue(f.statements, ret);

        boolean endsWithReturn =
            !f.statements.isEmpty()
            && f.statements.get(f.statements.size() - 1) instanceof ReturnStmt;

        if (!endsWithReturn)
            f.statements.add(
                new ReturnStmt(new ValueExpr(ret, "identifier"))
            );
    }

    private static void retValue(List<Statement> body, String ret) {

        if (body == null) return;

        for (Statement st : body) {

            if (st instanceof ReturnStmt && ((ReturnStmt) st).value == null)
                ((ReturnStmt) st).value = new ValueExpr(ret, "identifier");
            else if (st instanceof IfStmt) {
                retValue(((IfStmt) st).thenBranch, ret);
                retValue(((IfStmt) st).elseBranch, ret);
            } else if (st instanceof WhileStmt)
                retValue(((WhileStmt) st).body, ret);
            else if (st instanceof ForStmt)
                retValue(((ForStmt) st).body, ret);
        }
    }

    private static void resolveMembers(Program program, List<Statement> body) {

        if (body == null) return;

        for (int i = 0; i < body.size(); i++) {

            Statement st = body.get(i);

            if (st instanceof MemberAssignStmt) {

                MemberAssignStmt m = (MemberAssignStmt) st;

                Function target = null;

                for (Function f : program.functions)
                    if (f.name.equals(m.base)) { target = f; break; }

                Crack crack = null;

                if (target != null)
                    for (Crack c : target.cracks)
                        if (c.name.equals(m.member)) { crack = c; break; }

                if (crack == null)
                    throw new IllegalStateException(
                        target == null
                            ? "'" + m.base + "." + m.member + " = ...': '" + m.base
                                + "' no es una funcion con cracks (ni una estructura conocida)"
                            : "la funcion '" + m.base + "' no tiene el crack '" + m.member + "'"
                    );

                if (crack.params.size() != m.values.size())
                    throw new IllegalStateException(
                        "el crack '" + m.base + "." + m.member + "' espera "
                        + crack.params.size() + " valor(es) y recibio " + m.values.size()
                    );

                CallExpr call = new CallExpr(crackName(m.base, m.member));
                call.arguments.addAll(m.values);

                body.set(i, new ExpressionStmt(call));

            } else if (st instanceof IfStmt) {
                resolveMembers(program, ((IfStmt) st).thenBranch);
                resolveMembers(program, ((IfStmt) st).elseBranch);
            } else if (st instanceof WhileStmt) {
                resolveMembers(program, ((WhileStmt) st).body);
            } else if (st instanceof ForStmt) {
                resolveMembers(program, ((ForStmt) st).body);
            }
        }
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
