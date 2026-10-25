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

public final class Marked {

    private static final String RESET = "\033[0m";
    private static final String BOLD = "\033[1m";

    private static final String RED = "\033[31m";
    private static final String YELLOW = "\033[33m";
    private static final String BLUE = "\033[34m";
    private static final String PURPLE = "\033[35m";

    private Marked() {
    }

    public static void error(
        String file,
        int line,
        int column,
        String source,
        int length,
        String message
    ) {
        String output = format(
            PURPLE,
            "error",
            file,
            line,
            column,
            source,
            length,
            arch.Diagnostics.translate(message),
            true
        );

        System.err.print(output);

        throw new MarkedException(
            file,
            line,
            column,
            message
        );
    }

    public static void warning(
        String file,
        int line,
        int column,
        String source,
        int length,
        String message
    ) {
        System.err.print(
            format(
                YELLOW,
                "warning",
                file,
                line,
                column,
                source,
                length,
                message,
                false
            )
        );
    }

    public static void info(
        String file,
        int line,
        int column,
        String message
    ) {
        System.err.println(
            formatSimple(
                BLUE,
                "info",
                file,
                line,
                column,
                message
            )
        );
    }

    public static void hint(
        String file,
        int line,
        int column,
        String source,
        int length,
        String message
    ) {
        System.err.print(
            format(
                PURPLE,
                "hint",
                file,
                line,
                column,
                source,
                length,
                message,
                false
            )
        );
    }

    private static String format(
        String color,
        String type,
        String file,
        int line,
        int column,
        String source,
        int length,
        String message,
        boolean terminate
    ) {
        if (source == null) {
            source = "";
        }

        if (length < 1) {
            length = 1;
        }

        StringBuilder out =
            new StringBuilder();

        out.append(
            BOLD
        );

        out.append(
            file
        );

        out.append(
            ":"
        );

        out.append(
            line
        );

        out.append(
            ":"
        );

        out.append(
            column
        );

        out.append(
            ":"
        );

        out.append(
            RESET
        );

        out.append(
            " "
        );

        out.append(
            color
        );

        out.append(
            BOLD
        );

        out.append(
            type
        );

        out.append(
            ": "
        );

        out.append(
            RESET
        );

        out.append(
            message
        );

        out.append(
            "\n"
        );

        out.append(
            "    "
        );

        out.append(
            line
        );

        out.append(
            " | "
        );

        out.append(
            source
        );

        out.append(
            "\n"
        );

        out.append(
            "      | "
        );

        int spaces =
            Math.max(
                0,
                column - 1
            );

        for (
            int i = 0;
            i < spaces;
            i++
        ) {
            char c =
                i < source.length()
                    ? source.charAt(i)
                    : ' ';

            if (c == '\t') {
                out.append('\t');
            } else {
                out.append(' ');
            }
        }

        out.append(
            color
        );

        out.append(
            BOLD
        );

        for (
            int i = 0;
            i < length;
            i++
        ) {
            out.append('^');
        }

        out.append(
            RESET
        );

        out.append(
            "\n"
        );

        if (terminate) {
            out.append(
                ""
            );
        }

        return out.toString();
    }

    private static String formatSimple(
        String color,
        String type,
        String file,
        int line,
        int column,
        String message
    ) {
        return
            BOLD +
            file +
            ":" +
            line +
            ":" +
            column +
            ":" +
            RESET +
            " " +
            color +
            BOLD +
            type +
            ": " +
            RESET +
            message +
            "\n";
    }

    public static final class MarkedException
        extends RuntimeException {

        private final String file;
        private final int line;
        private final int column;

        public MarkedException(
            String file,
            int line,
            int column,
            String message
        ) {
            super(message);

            this.file = file;
            this.line = line;
            this.column = column;
        }

        public String getFile() {
            return file;
        }

        public int getLine() {
            return line;
        }

        public int getColumn() {
            return column;
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
