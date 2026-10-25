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

import java.io.*;
import java.nio.file.*;
import java.util.*;
import shasm.asm.Assembler;
import shasm.data.Embedded;
import shasm.i18n.I18n;
import shasm.obj.CoffWriter;
import shasm.obj.ElfWriter;
import shasm.obj.ObjBuilder;
import shasm.obj.ObjModel;

public final class Main {
    static final String VERSION = "1.2.0";

        static void usage() {
        System.err.println(I18n.tr("shasm %s THE ASHLEY PROJECT VERSION", VERSION));
        System.err.println(I18n.tr("This file is included by default in the ADK (Ashley Development Kit)."));
        System.err.println();
        System.err.println(I18n.tr("Usage: shasm [options] file.asm"));
        System.err.println("  -o <" + I18n.tr("file") + ">     " + I18n.tr("output file (default: <input>.bin)"));
        System.err.println("  -f <fmt>         " + I18n.tr("format: bin, hex, elf32, elf64, win32, win64"));
        System.err.println("  -l <" + I18n.tr("file") + ">     " + I18n.tr("generate a listing (address, bytes, source)"));
        System.err.println("  -I <dir>         " + I18n.tr("search directory for %include / incbin (repeatable)"));
        System.err.println("  -D <sym>[=<val>] " + I18n.tr("define a preprocessor macro"));
        System.err.println("  -b <16|32|64>    " + I18n.tr("initial BITS mode (default 16; 32/64 in object formats)"));
        System.err.println("  -w               " + I18n.tr("treat warnings as errors"));
        System.err.println("  --lang <xx>      " + I18n.tr("message language (%s)", String.join(", ", I18n.available())));
        System.err.println("  -v, --version    " + I18n.tr("show version"));
        System.err.println("  -h, --help       " + I18n.tr("show this help"));
        }

    private static void def(Map<String, String> defs, String d) {
        int e = d.indexOf('=');
        defs.put(e < 0 ? d : d.substring(0, e), e < 0 ? "1" : d.substring(e + 1));
    }

    public static void main(String[] args) throws Exception {
        System.setOut(new PrintStream(
                new FileOutputStream(FileDescriptor.out),
                true,
                java.nio.charset.StandardCharsets.UTF_8
        ));
        System.setErr(new PrintStream(
                new FileOutputStream(FileDescriptor.err),
                true,
                java.nio.charset.StandardCharsets.UTF_8
        ));

        I18n.setLanguage(I18n.detect());

        Path po = null;
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("--lang")) {
                if (!I18n.setLanguage(args[i + 1])) {
                    System.err.println(I18n.tr(
                            "idioma no disponible: %s (use %s)",
                            args[i + 1],
                            String.join(", ", I18n.available())
                    ));
                }
            } else if (args[i].equals("--po")) {
                po = Paths.get(args[i + 1]);
            }
        }

        if (po != null) {
            try {
                I18n.overlay(po);
            } catch (IOException e) {
                System.err.println(I18n.tr(
                        "shasm: error: no se puede abrir '%s'",
                        po
                ));
                System.exit(1);
            }
        }

        String in = null, out = null, fmt = "bin", lst = null;
        int bits = -1;
        boolean werror = false;

        List<Path> inc = new ArrayList<>();
        Map<String, String> defs = new LinkedHashMap<>();

        for (int i = 0; i < args.length; i++) {
            String a = args[i];

            switch (a) {
                case "-h", "--help" -> {
                    usage();
                    return;
                }

                case "-v", "--version" -> {
                    System.out.println("shasm " + VERSION);
                    return;
                }

                case "--lang", "--po" -> i++;

                case "-o" -> out = args[++i];

                case "-f" -> fmt = args[++i];

                case "-l" -> lst = args[++i];

                case "-I" -> inc.add(Paths.get(args[++i]));

                case "-D" -> def(defs, args[++i]);

                case "-b" -> bits = Integer.parseInt(args[++i]);

                case "-w" -> werror = true;

                default -> {
                    if (a.startsWith("-I") && a.length() > 2) {
                        inc.add(Paths.get(a.substring(2)));
                    } else if (a.startsWith("-D") && a.length() > 2) {
                        def(defs, a.substring(2));
                    } else if (a.startsWith("--lang=")) {
                        I18n.setLanguage(a.substring(7));
                    } else if (a.startsWith("-")) {
                        System.err.println(I18n.tr("opcion desconocida: %s", a));
                        usage();
                        System.exit(2);
                    } else {
                        in = a;
                    }
                }
            }
        }

        if (in == null) {
            usage();
            System.exit(2);
        }

        ObjModel.Fmt of = null;

        for (ObjModel.Fmt f : ObjModel.Fmt.values()) {
            if (f.id.equals(fmt)) {
                of = f;
                break;
            }
        }

        if (of == null && !fmt.equals("bin") && !fmt.equals("hex")) {
            System.err.println(I18n.tr(
                    "formato no soportado: %s (use bin, hex, elf32, elf64, win32 o win64)",
                    fmt
            ));
            System.exit(2);
        }

        if (bits < 0) {
            bits = of == null ? 16 : (of.is64 ? 64 : 32);
        }

        if (out == null) {
            String base =
                    (in.endsWith(".asm") || in.endsWith(".s"))
                            ? in.substring(0, in.lastIndexOf('.'))
                            : in;

            out = base
                    + (of != null
                        ? of.ext()
                        : fmt.equals("hex")
                            ? ".hex"
                            : ".bin");
        }

        Map<String, Reg> regs = Reg.load(Embedded.regs());
        InsnDb db = new InsnDb(Embedded.insns());

        Preproc pp = new Preproc(inc);

        for (var e : defs.entrySet()) {
            pp.define(e.getKey(), e.getValue());
        }

        List<Preproc.Line> lines;

        try {
            lines = pp.run(Paths.get(in));
        } catch (NoSuchFileException e) {
            System.err.println(I18n.tr(
                    "shasm: error: no se puede abrir '%s'",
                    in
            ));
            System.exit(1);
            return;
        } catch (AsmError e) {
            System.err.println(e.getMessage());
            System.exit(1);
            return;
        }

        Assembler as = new Assembler(db, regs, lines, bits, inc);

        as.setObjectMode(of != null);

        byte[] code = as.assemble();

        for (String w : pp.warnings) {
            System.err.println(w);
        }

        for (String w : as.warnings) {
            System.err.println(w);
        }

        if (!as.errors.isEmpty()) {
            for (String e : as.errors) {
                System.err.println(e);
            }
            System.exit(1);
        }

        if (werror && (!as.warnings.isEmpty() || !pp.warnings.isEmpty())) {
            System.err.println(
                    I18n.tr("shasm: avisos tratados como errores")
            );
            System.exit(1);
        }

        if (of != null) {
            try {
                ObjModel om = ObjBuilder.build(
                        as,
                        of,
                        Paths.get(in).getFileName().toString()
                );

                Files.write(
                        Paths.get(out),
                        of.elf()
                                ? ElfWriter.write(om)
                                : CoffWriter.write(om)
                );
            } catch (AsmError e) {
                System.err.println(
                        "shasm: error: " + e.getMessage()
                );
                System.exit(1);
            }
        } else if (fmt.equals("bin")) {
            Files.write(Paths.get(out), code);
        } else {
            Files.writeString(Paths.get(out), intelHex(code));
        }

        if (lst != null) {
            writeListing(Paths.get(lst), as);
        }
    }

    static String intelHex(byte[] d) {
        StringBuilder sb = new StringBuilder();

        for (int off = 0; off < d.length; off += 16) {
            int n = Math.min(16, d.length - off);
            int sum = n
                    + (off >> 8 & 0xFF)
                    + (off & 0xFF);

            sb.append(String.format(
                    ":%02X%04X00",
                    n,
                    off & 0xFFFF
            ));

            for (int i = 0; i < n; i++) {
                sb.append(String.format(
                        "%02X",
                        d[off + i] & 0xFF
                ));
                sum += d[off + i] & 0xFF;
            }

            sb.append(String.format(
                    "%02X\n",
                    (-sum) & 0xFF
            ));
        }

        sb.append(":00000001FF\n");
        return sb.toString();
    }

    static void writeListing(Path p, Assembler as) throws IOException {
        StringBuilder sb = new StringBuilder();

        for (Assembler.ListEntry e : as.listing) {
            StringBuilder hex = new StringBuilder();

            for (byte b : e.bytes) {
                hex.append(String.format(
                        "%02X",
                        b & 0xFF
                ));
            }

            String h = hex.length() > 24
                    ? hex.substring(0, 24) + "-"
                    : hex.toString();

            sb.append(String.format(
                    "%6d %08X %-25s %s%n",
                    e.no,
                    e.addr,
                    h,
                    e.src
            ));
        }

        Files.writeString(p, sb.toString());
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
