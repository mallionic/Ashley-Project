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

package shasm.i18n;

import java.util.*;

public final class PoParser {
    private PoParser() {}

    public static Map<String, String> parse(String text) {
        Map<String, String> out = new HashMap<>();
        StringBuilder id = null, str = null, cur = null;
        boolean fuzzy = false, nextFuzzy = false;
        for (String raw : text.split("\r?\n")) {
            String l = raw.strip();
            if (l.startsWith("#")) {
                if (l.startsWith("#,") && l.contains("fuzzy")) nextFuzzy = true;
                continue;
            }
            if (l.isEmpty()) { flush(out, id, str, fuzzy); id = null; str = null; cur = null; fuzzy = false; nextFuzzy = false; continue; }
            if (l.startsWith("msgid ") || l.equals("msgid")) {
                flush(out, id, str, fuzzy);
                id = new StringBuilder(); str = null; cur = id; fuzzy = nextFuzzy; nextFuzzy = false;
                cur.append(unquote(l.substring(5).strip()));
            } else if (l.startsWith("msgstr ") || l.equals("msgstr")) {
                str = new StringBuilder(); cur = str;
                cur.append(unquote(l.substring(6).strip()));
            } else if (l.startsWith("\"") && cur != null) {
                cur.append(unquote(l));
            }
        }
        flush(out, id, str, fuzzy);
        return out;
    }

    private static void flush(Map<String, String> out, StringBuilder id, StringBuilder str, boolean fuzzy) {
        if (id == null || str == null || fuzzy) return;
        if (id.length() == 0 || str.length() == 0) return;
        out.put(id.toString(), str.toString());
    }

    private static String unquote(String s) {
        s = s.strip();
        if (s.length() < 2 || s.charAt(0) != '"' || s.charAt(s.length() - 1) != '"') return "";
        StringBuilder b = new StringBuilder();
        for (int i = 1; i < s.length() - 1; i++) {
            char c = s.charAt(i);
            if (c != '\\' || i + 1 >= s.length() - 1) { b.append(c); continue; }
            char e = s.charAt(++i);
            switch (e) {
                case 'n' -> b.append('\n'); case 't' -> b.append('\t'); case 'r' -> b.append('\r');
                case '"' -> b.append('"'); case '\\' -> b.append('\\');
                default -> b.append('\\').append(e);
            }
        }
        return b.toString();
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
