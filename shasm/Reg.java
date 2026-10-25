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

package shasm;

import java.util.*;

public final class Reg {
    public final String name;
    public final String asmClass;
    public final String kind;
    public final int num;
    public final int bits;
    public final boolean high;
    public final boolean rexOnly;

    Reg(String name, String asmClass, String kind, int num) {
        this.name = name; this.asmClass = asmClass; this.kind = kind; this.num = num;
        this.high = asmClass.equals("REG_HIGH");
        this.rexOnly = kind.equals("reg8_rex");
        this.bits = switch (kind.split(",")[0]) {
            case "reg8", "reg8_rex" -> 8;
            case "reg16", "sreg" -> 16;
            case "reg32", "creg", "dreg", "treg" -> 32;
            case "reg64", "mmxreg" -> 64;
            case "xmmreg" -> 128; case "ymmreg" -> 256; case "zmmreg" -> 512;
            default -> 0;
        };
    }

    public String cls() {
        String k = kind.split(",")[0];
        return k.equals("reg8_rex") ? "reg8" : k;
    }

    public int gprBits() {
        return switch (cls()) { case "reg8" -> 8; case "reg16" -> 16; case "reg32" -> 32; case "reg64" -> 64; default -> 0; };
    }

    public boolean isGpr() { return gprBits() != 0; }
    @Override public String toString() { return name; }

    public static Map<String, Reg> load(String compact) {
        Map<String, Reg> m = new LinkedHashMap<>();
        for (String l : compact.split("\n")) {
            if (l.isEmpty()) continue;
            String[] t = l.split("\t");
            m.put(t[0], new Reg(t[0], t[1], t[2], Integer.parseInt(t[3])));
        }
        return m;
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
