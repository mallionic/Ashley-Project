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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

public final class EmbedNlib {

    static final int CHUNK = 12000;

    public static void main(String[] args) throws IOException {

        Path nlib = Paths.get(args.length > 0 ? args[0] : "nlib");
        Path out  = Paths.get(args.length > 1 ? args[1] : "Builder/ash/NlibEmbedded.java");

        List<Path> files;

        if (Files.isDirectory(nlib)) {
            try (Stream<Path> s = Files.walk(nlib)) {
                files = s.filter(Files::isRegularFile)
                         .filter(p -> {
                             String n = p.getFileName().toString();
                             return n.endsWith(".ash") || n.endsWith(".a");
                         })
                         .sorted()
                         .collect(Collectors.toList());
            }
        } else {
            files = new ArrayList<>();
        }

        StringBuilder sb = new StringBuilder();

        sb.append("package Builder.ash;\n\n")
          .append("import java.io.IOException;\n")
          .append("import java.nio.file.*;\n")
          .append("import java.util.*;\n\n")
          .append("/**\n * GENERADO por tools/EmbedNlib.java a partir de nlib/ -- NO EDITAR.\n")
          .append(" * Contiene las bibliotecas del sistema dentro del compilador (jar / native-image).\n */\n")
          .append("public final class NlibEmbedded {\n\n")
          .append("    private NlibEmbedded() { }\n\n")
          .append("    private static final Map<String, String[]> DATA = new LinkedHashMap<>();\n\n")
          .append("    static {\n");

        for (int i = 0; i < files.size(); i++) {
            String rel = nlib.relativize(files.get(i)).toString().replace('\\', '/');
            sb.append("        DATA.put(\"").append(rel).append("\", f").append(i).append("());\n");
        }

        sb.append("    }\n\n");

        for (int i = 0; i < files.size(); i++) {

            byte[] raw = Files.readAllBytes(files.get(i));
            String b64 = Base64.getEncoder().encodeToString(raw);

            sb.append("    private static String[] f").append(i).append("() {\n")
              .append("        return new String[] {\n");

            for (int p = 0; p < b64.length(); p += CHUNK)
                sb.append("            \"").append(b64, p, Math.min(b64.length(), p + CHUNK)).append("\",\n");

            if (b64.isEmpty())
                sb.append("            \"\"\n");

            sb.append("        };\n    }\n\n");
        }

        sb.append(
            "    /** nombres embebidos, p. ej. \"args.ash\". */\n" +
            "    public static List<String> names() {\n" +
            "        return new ArrayList<>(DATA.keySet());\n" +
            "    }\n\n" +
            "    public static boolean has(String name) {\n" +
            "        return DATA.containsKey(name);\n" +
            "    }\n\n" +
            "    public static byte[] bytes(String name) {\n" +
            "        String[] parts = DATA.get(name);\n" +
            "        if (parts == null) return null;\n" +
            "        StringBuilder b = new StringBuilder();\n" +
            "        for (String p : parts) b.append(p);\n" +
            "        return Base64.getDecoder().decode(b.toString());\n" +
            "    }\n\n" +
            "    public static String text(String name) {\n" +
            "        byte[] b = bytes(name);\n" +
            "        return b == null ? null : new String(b, java.nio.charset.StandardCharsets.UTF_8);\n" +
            "    }\n\n" +
            "    private static Path dir;\n\n" +
            "    /** carpeta ya extraida (o null si aun no se uso la nlib embebida). */\n" +
            "    public static synchronized Path currentDir() {\n" +
            "        return dir;\n" +
            "    }\n\n" +
            "    /** Extrae las bibliotecas a una carpeta temporal (una vez) y la devuelve. */\n" +
            "    public static synchronized Path materialize() {\n\n" +
            "        if (dir != null && Files.isDirectory(dir))\n" +
            "            return dir;\n\n" +
            "        try {\n" +
            "            final Path d = Files.createTempDirectory(\"ashc-nlib-\");\n\n" +
            "            for (String n : DATA.keySet()) {\n" +
            "                Path f = d.resolve(n);\n" +
            "                if (f.getParent() != null) Files.createDirectories(f.getParent());\n" +
            "                Files.write(f, bytes(n));\n" +
            "            }\n\n" +
            "            Runtime.getRuntime().addShutdownHook(new Thread(() -> {\n" +
            "                try (java.util.stream.Stream<Path> s = Files.walk(d)) {\n" +
            "                    s.sorted(Comparator.reverseOrder()).forEach(p -> {\n" +
            "                        try { Files.deleteIfExists(p); } catch (IOException ignored) { }\n" +
            "                    });\n" +
            "                } catch (IOException ignored) { }\n" +
            "            }));\n\n" +
            "            dir = d;\n" +
            "            return d;\n\n" +
            "        } catch (IOException e) {\n" +
            "            throw new IllegalStateException(\"no se pudo extraer nlib embebida: \" + e.getMessage());\n" +
            "        }\n" +
            "    }\n" +
            "}\n");

        if (out.getParent() != null) Files.createDirectories(out.getParent());
        Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);

        System.out.println("nlib embebida: " + files.size() + " archivo(s) -> " + out);
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
