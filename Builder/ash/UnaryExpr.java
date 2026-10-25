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

public final class UnaryExpr implements Ast.Expression {

    public final String operator;
    public final Ast.Expression expression;
    public final boolean postfix;

    public UnaryExpr(
            String operator,
            Ast.Expression expression,
            boolean postfix
    ) {
        if (operator == null) {
            throw new IllegalArgumentException(
                "Unary operator no puede ser null"
            );
        }

        if (expression == null) {
            throw new IllegalArgumentException(
                "Unary expression no puede ser null"
            );
        }

        if (
            !"++".equals(operator) &&
            !"--".equals(operator)
        ) {
            throw new IllegalArgumentException(
                "Operador unary no soportado: " +
                operator
            );
        }

        this.operator = operator;
        this.expression = expression;
        this.postfix = postfix;
    }

    public UnaryExpr(
            String operator,
            Ast.Expression expression
    ) {
        this(
            operator,
            expression,
            false
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
