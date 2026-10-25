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

package arch;

import java.util.LinkedHashMap;
import java.util.Map;

public final class Sections {

    private Sections() {
    }

    public static final class Placement {
        public final String section;
        public final String flags;

        Placement(String section, String flags) {
            this.section = section;
            this.flags = flags == null ? "" : flags.toLowerCase();
        }

        public boolean write(boolean isCode) {
            return flags.isEmpty() ? !isCode : flags.contains("w");
        }

        public boolean exec(boolean isCode) {
            return flags.isEmpty() ? isCode : flags.contains("x");
        }
    }

    private static final Map<String, Placement> MAP = new LinkedHashMap<>();

    public static void place(String symbol, String section, String flags) {
        if (section == null || section.isEmpty())
            throw new IllegalArgumentException("@section: nombre de seccion vacio para '" + symbol + "'");
        if (section.equals(".text") && (flags == null || flags.isEmpty()))
            return;
        MAP.put(symbol, new Placement(section, flags));
    }

    public static Placement of(String symbol) {
        return MAP.get(symbol);
    }

    public static boolean isEmpty() {
        return MAP.isEmpty();
    }

    public static Map<String, Placement> all() {
        return MAP;
    }

    public static void reset() {
        MAP.clear();
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
