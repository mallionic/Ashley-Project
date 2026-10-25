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

package Builder;

public class Exception extends RuntimeException {

    private final int line;
    private final int column;
    private final String source;
    private final String kind;

    public Exception(
        String message
    ) {
        this(
            message,
            -1,
            -1,
            null,
            "error"
        );
    }

    public Exception(
        String message,
        int line,
        int column
    ) {
        this(
            message,
            line,
            column,
            null,
            "error"
        );
    }

    public Exception(
        String message,
        int line,
        int column,
        String source
    ) {
        this(
            message,
            line,
            column,
            source,
            "error"
        );
    }

    public Exception(
        String message,
        int line,
        int column,
        String source,
        String kind
    ) {
        super(message);

        this.line = line;
        this.column = column;
        this.source = source;
        this.kind = kind == null
            ? "error"
            : kind;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    public String getSource() {
        return source;
    }

    public String getKind() {
        return kind;
    }

    private static final String RESET =
        "\u001B[0m";

    private static final String BOLD =
        "\u001B[1m";

    private static final String RED =
        "\u001B[31m";

    private static final String MAGENTA =
        "\u001B[35m";

    private static final String CYAN =
        "\u001B[36m";

    private static final String YELLOW =
        "\u001B[33m";

    private static final String BLUE =
        "\u001B[34m";

    public String format() {

        StringBuilder out =
            new StringBuilder();

        String color;

        if (kind.equals("warning")) {
            color = YELLOW;
        } else if (kind.equals("note")) {
            color = CYAN;
        } else {
            color = RED;
        }

        out
            .append(BOLD)
            .append(MAGENTA)
            .append("ASH")
            .append(RESET)
            .append(" ");

        out
            .append(color)
            .append(BOLD)
            .append(kind)
            .append(":")
            .append(RESET)
            .append(" ");

        if (source != null && !source.isEmpty()) {

            out
                .append(CYAN)
                .append(source)
                .append(RESET);

            if (line >= 0) {
                out
                    .append(":")
                    .append(line);

                if (column >= 0) {
                    out
                        .append(":")
                        .append(column);
                }
            }

            out.append(": ");
        } else if (line >= 0) {

            out
                .append(CYAN)
                .append(line);

            if (column >= 0) {
                out
                    .append(":")
                    .append(column);
            }

            out
                .append(RESET)
                .append(": ");
        }

        out
            .append(MAGENTA)
            .append(getMessage())
            .append(RESET);

        return out.toString();
    }

    public void print() {
        System.err.println(format());
    }

    public static Exception error(
        String message,
        int line,
        int column
    ) {
        return new Exception(
            message,
            line,
            column
        );
    }

    public static Exception warning(
        String message,
        int line,
        int column
    ) {
        return new Exception(
            message,
            line,
            column,
            null,
            "warning"
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
