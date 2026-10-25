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

package shasm.obj;

import java.util.*;
import shasm.asm.AsmCore;
import shasm.obj.ObjModel.*;

public final class ObjBuilder {
    private ObjBuilder() {}

    public static ObjModel build(AsmCore as, Fmt fmt, String fileName) {
        ObjModel m = new ObjModel(fmt);
        m.fileName = fileName;

        for (AsmCore.ExportSection es : as.exportSections()) {
            Section s = new Section();
            s.name = es.name; s.nobits = es.nobits; s.align = Math.max(1, es.align);
            s.size = es.size; s.data = es.nobits ? new byte[0] : es.data;
            String n = es.name.toLowerCase(Locale.ROOT);
            if (n.startsWith(".note.gnu-stack")) { s.alloc = false; s.read = false; }
            else if (n.startsWith(".text") || n.startsWith(".init") || n.startsWith(".fini")) s.exec = true;
            else if (n.startsWith(".data") || n.startsWith(".bss") || n.startsWith(".tdata")) s.write = true;
            m.sections.add(s);
        }
        if (fmt.elf() && m.section(".note.GNU-stack") == null) {
            Section s = new Section();
            s.name = ".note.GNU-stack"; s.alloc = false; s.read = false;
            m.sections.add(s);
        }

        for (AsmCore.ExportSymbol e : as.exportSymbols()) {
            Symbol s = new Symbol();
            s.name = e.name; s.key = e.name;
            s.bind = e.weak ? Bind.WEAK : e.global ? Bind.GLOBAL : Bind.LOCAL;
            if (e.undef) s.where = Where.UNDEF;
            else if (e.sec == null) { s.where = Where.ABS; s.value = e.value; }
            else {
                s.where = Where.DEFINED; s.sec = e.sec; s.value = e.value;
                Section sc = m.section(e.sec);
                if (sc != null && sc.exec) s.type = Type.FUNC;
            }
            m.symbols.add(s);
        }

        for (AsmCore.ExternalReloc r : as.externalRelocs) {
            Section s = m.section(r.section);
            if (s == null) continue;
            Reloc x = new Reloc();
            x.sec = r.section; x.offset = r.offset; x.size = r.size;
            x.kind = r.rel ? (r.plt ? Kind.PLT : Kind.PCREL) : Kind.ABS;
            x.signed = r.signed; x.addend = r.addend; x.trail = r.trail;
            if (r.symbol != null) x.ext = r.symbol; else x.tsec = r.tsec;
            s.relocs.add(x);
            if (fmt == Fmt.ELF32 && !s.nobits)
                ObjModel.patch(s.data, r.offset, r.addend, r.size);
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
