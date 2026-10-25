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

public final class Inject {

    private final Cursor c;
    private final Exprs exprs;
    private final Nums nums;

    public Inject(Cursor c, Exprs exprs, Nums nums) {
        this.c = c;
        this.exprs = exprs;
        this.nums = nums;
    }

    private static boolean isEmitName(String n) {
        return n.equals("emit") || n.equals("emit8") || n.equals("emit16")
            || n.equals("emit32") || n.equals("emit64");
    }

    private boolean isEmitAhead() {
        int k = c.check(Type.AT) ? 1 : 0;
        return c.checkAhead(k, Type.IDENT)
            && isEmitName(c.peekAt(k).text)
            && c.checkAhead(k + 1, Type.LPAREN);
    }

    public boolean isAhead() {
        if (isEmitAhead())
            return true;

        if (!c.check(Type.IDENT) || !"ashley".equals(c.peek().text))
            return false;

        if (!c.text(c.peekAt(1)).equals("."))
            return false;

        if (!c.checkAhead(2, Type.IDENT))
            return false;

        String n = c.peekAt(2).text;

        return n.equals("InjectByte") || n.equals("InjectBytes")
            || n.equals("InjectWord") || n.equals("InjectDword")
            || n.equals("InjectQword");
    }

    public Statement parse(boolean volatileWrite) {
        String function;
        boolean emit = isEmitAhead();

        if (emit) {
            c.match(Type.AT);
            String n = c.expect(Type.IDENT, "emit").text;
            switch (n) {
                case "emit16": function = "InjectWord";  break;
                case "emit32": function = "InjectDword"; break;
                case "emit64": function = "InjectQword"; break;
                default:       function = "InjectBytes"; break;
            }
        } else {
            c.expect(Type.IDENT, "ashley");
            c.expectText(".", ".");
            function = c.expect(Type.IDENT, "nombre del intrinsic").text;
        }

        c.expect(Type.LPAREN, "(");

        InjectBytesStmt.Kind kind;

        switch (function) {
            case "InjectByte":  kind = InjectBytesStmt.Kind.BYTE;  break;
            case "InjectBytes": kind = InjectBytesStmt.Kind.BYTES; break;
            case "InjectWord":  kind = InjectBytesStmt.Kind.WORD;  break;
            case "InjectDword": kind = InjectBytesStmt.Kind.DWORD; break;
            case "InjectQword": kind = InjectBytesStmt.Kind.QWORD; break;
            default:
                throw c.error("Intrinsic ashley desconocido: " + function);
        }

        InjectBytesStmt stmt = new InjectBytesStmt(kind, volatileWrite);

        if (!c.check(Type.RPAREN)) {
            do {
                stmt.bytes.add(exprs.parseExpression());
            } while (c.match(Type.COMMA));
        }

        c.expect(Type.RPAREN, ")");
        c.match(Type.SEMICOLON);

        if (emit && stmt.bytes.isEmpty())
            throw c.error("emit(...) requiere al menos un valor");

        if (!emit && kind != InjectBytesStmt.Kind.BYTES && stmt.bytes.size() != 1)
            throw c.error(function + " requiere exactamente un valor");

        if (kind == InjectBytesStmt.Kind.BYTES && stmt.bytes.isEmpty())
            throw c.error("ashley.InjectBytes requiere al menos un byte");

        for (Expression e : stmt.bytes) {
            if (!(e instanceof ValueExpr))
                throw c.error(function + " solo acepta valores enteros constantes");

            ValueExpr v = (ValueExpr) e;

            if (!"integer".equals(v.type) && !"char".equals(v.type))
                throw c.error(function + " solo acepta valores enteros constantes");

            long n = Nums.parseIntegerLiteral(v.value);

            switch (kind) {
                case BYTE:
                case BYTES:
                    nums.checkUnsignedRange(n, 8, "byte");
                    break;
                case WORD:
                    nums.checkUnsignedRange(n, 16, "word");
                    break;
                case DWORD:
                    nums.checkUnsignedRange(n, 32, "dword");
                    break;
                case QWORD:
                    break;
            }
        }

        return stmt;
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
