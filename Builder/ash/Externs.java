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

import java.util.ArrayList;
import java.util.List;

import Builder.Untokenizer.Type;
import Builder.ash.Ast.Extern;
import Builder.ash.Ast.Parameter;

public final class Externs {

    private final Cursor c;

    public Externs(Cursor c) {
        this.c = c;
    }

    public Extern parse() {

        c.expectKeyword("extern");

        String returnType;

        if (!c.check(Type.EOF) && "func".equals(c.peek().text)) {
            c.advance();
            returnType = "void";
        } else {
            returnType =
                Nums.valueType(
                    c.parseTypeWithPointer()
                );
        }

        String name =
            c.expect(
                Type.IDENT,
                "nombre del simbolo externo"
            ).text;

        c.expect(Type.LPAREN, "(");

        List<Parameter> params =
            new ArrayList<>();

        boolean variadic = false;

        if (!c.check(Type.RPAREN)) {

            do {

                if (c.check(Type.DOT)) {

                    c.expect(Type.DOT, "...");
                    c.expect(Type.DOT, "...");
                    c.expect(Type.DOT, "...");

                    variadic = true;
                    break;
                }

                String paramType =
                    Nums.valueType(
                        c.parseTypeWithPointer()
                    );

                String paramName;

                if (c.check(Type.IDENT)) {
                    paramName = c.advance().text;
                } else {
                    paramName = "__p" + params.size();
                }

                params.add(
                    new Parameter(
                        paramType,
                        paramName
                    )
                );

            } while (c.match(Type.COMMA));
        }

        c.expect(Type.RPAREN, ")");

        c.match(Type.SEMICOLON);

        String alias = name;

        Extern ext =
            new Extern(
                name,
                alias,
                returnType
            );

        ext.parameters.addAll(params);
        ext.variadic = variadic;

        return ext;
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
