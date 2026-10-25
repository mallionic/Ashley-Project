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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import shasm.data.Embedded;

public final class I18n {
    private I18n() {}

    private static volatile Map<String, String> catalog = Map.of();
    private static volatile String lang = "es";

    public static String tr(String msg, Object... args) {
        String t = catalog.getOrDefault(msg, msg);
        if (args == null || args.length == 0) return t;
        try { return String.format(Locale.ROOT, t, args); }
        catch (IllegalFormatException e) { return String.format(Locale.ROOT, msg, args); }
    }

    public static String language() { return lang; }

    public static List<String> available() { return Arrays.asList(Embedded.langs()); }

    public static String normalize(String code) {
        if (code == null) return null;
        String c = code.strip().toLowerCase(Locale.ROOT);
        int i = 0;
        while (i < c.length() && Character.isLetter(c.charAt(i))) i++;
        c = c.substring(0, i);
        return c.isEmpty() || c.equals("c") || c.equals("posix") ? null : c;
    }

    public static String detect() {
        for (String v : new String[]{"SHASM_LANG", "LC_ALL", "LC_MESSAGES", "LANGUAGE", "LANG"}) {
            String e = System.getenv(v);
            if (e != null && !e.isBlank()) {
                String n = normalize(e.split(":")[0]);
                if (n != null) return n;
            }
        }
        String n = normalize(Locale.getDefault().getLanguage());
        return n == null ? "es" : n;
    }

    public static boolean setLanguage(String code) {
        String c = normalize(code);
        if (c == null) c = "es";
        String po = Embedded.po(c);
        if (po == null) { catalog = Map.of(); lang = "es"; return false; }
        catalog = c.equals("es") ? Map.of() : PoParser.parse(po);
        lang = c;
        return true;
    }

    public static void overlay(Path poFile) throws IOException {
        Map<String, String> m = new HashMap<>(catalog);
        m.putAll(PoParser.parse(Files.readString(poFile, StandardCharsets.UTF_8)));
        catalog = m;
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
