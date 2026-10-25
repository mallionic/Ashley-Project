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

public final class Visibility extends Rewriter {

    private final Map<String, Function> byName = new HashMap<>();
    private Function current;

    public static void check(Program program) {

        Visibility v = new Visibility();

        for (Function f : program.functions)
            v.byName.put(f.name, f);

        for (Function f : program.functions) {

            v.current = f;

            v.block(f.statements);

            for (Crack c : f.cracks)
                v.block(c.body);
        }
    }

    @Override
    protected Expression onExpr(Expression e) {

        if (e instanceof CallExpr) {

            CallExpr c = (CallExpr) e;
            Function g = byName.get(c.name);

            if (g != null && g.isPrivate && !g.entry && g != current) {

                boolean sameClass =
                    g.owner != null && g.owner.equals(current.owner);

                boolean sameFile =
                    Objects.equals(g.origin, current.origin);

                if (g.owner != null && !sameClass)
                    throw new IllegalStateException(
                        "'" + c.name.replaceFirst("_", ".") + "' is private: only methods of class "
                        + g.owner + " can call it (called from '" + current.name
                        + "'). Make it 'pub' to expose it."
                    );

                if (g.owner == null && !sameFile)
                    throw new IllegalStateException(
                        "'" + c.name + "' is private to " + g.origin
                        + " and cannot be called from " + (current.origin == null ? "the main script" : current.origin)
                        + ". Make it 'pub' to use it from other files."
                    );
            }
        }

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
