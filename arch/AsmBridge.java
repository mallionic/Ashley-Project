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

import shasm.AsmError;
import shasm.InsnDb;
import shasm.Preproc;
import shasm.Reg;
import shasm.asm.Assembler;
import shasm.data.Embedded;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;

public final class AsmBridge {

    private AsmBridge() {
    }

    private static Map<String, Reg> regs;
    private static InsnDb db;

    public static synchronized byte[] assemble(
        String text,
        int bits,
        long org
    ) {

        try {

            if (db == null) {
                regs = Reg.load(Embedded.regs());
                db = new InsnDb(Embedded.insns());
            }

            Path f = Files.createTempFile("ash_asm", ".asm");

            try {
                Files.writeString(
                    f,
                    "bits " + bits + "\norg " + org + "\n" + text + "\n"
                );

                Preproc pp = new Preproc(new ArrayList<>());

                Assembler as =
                    new Assembler(db, regs, pp.run(f), bits, new ArrayList<>());

                as.setObjectMode(false);

                return as.assemble();

            } finally {
                Files.deleteIfExists(f);
            }

        } catch (AsmError e) {
            throw new IllegalArgumentException("asm: " + e.getMessage());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("asm: " + e.getMessage());
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
