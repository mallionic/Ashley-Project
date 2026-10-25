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

import java.util.LinkedHashMap;
import java.util.Map;

public final class Generator {

    private final StringBuilder out =
        new StringBuilder();

    private final Map<String, String> strings =
        new LinkedHashMap<>();

    private int stringCounter = 0;

    public Generator() {
    }

    private String internString(String value) {

        String existing =
            strings.get(value);

        if (existing != null) {
            return existing;
        }

        String symbol =
            "__str" + stringCounter++;

        strings.put(value, symbol);

        return symbol;
    }

    private String stringSymbol(String value) {
        return internString(value);
    }

    public String generateString(String value) {

        String symbol =
            stringSymbol(value);

        return "addr[" + symbol + "]";
    }

    public String generateStrings() {

        StringBuilder result =
            new StringBuilder();

        for (Map.Entry<String, String> entry :
                strings.entrySet()) {

            result
                .append(".string ")
                .append(entry.getValue())
                .append(" ")
                .append(entry.getKey())
                .append("\n");
        }

        return result.toString();
    }

    public String getOutput() {
        return out.toString();
    }

    public void append(String value) {
        out.append(value);
    }

    public void appendLine(String value) {
        out
            .append(value)
            .append('\n');
    }

    public String generate() {

        StringBuilder result =
            new StringBuilder();

        result.append(out);

        if (out.length() > 0 &&
            out.charAt(out.length() - 1) != '\n') {

            result.append('\n');
        }

        result.append(
            generateStrings()
        );

        return result.toString();
    }

    public void clear() {

        out.setLength(0);
        strings.clear();
        stringCounter = 0;
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
