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

import Builder.Builder;
import Builder.ash.NlibEmbedded;
import Builder.ash.Annotations;
import Builder.Marked;
import Builder.ash.Directives;
import Builder.Parseconv;
import Builder.Untokenizer;
import Builder.Untokenizer.Token;

import arch.builtin.Backend;
import arch.builtin.Parsing;
import arch.builtin.Tokens;
import arch.formatter.JavaWinOBJ;
import arch.formatter.JavaElfOBJ;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public final class Main {

    private Main() {
    }
    public static final String VERSION_ASHC = "1.0.0";
    public static void main(String[] args) {

        try {
            System.setOut(new java.io.PrintStream(System.out, true, "UTF-8"));
            System.setErr(new java.io.PrintStream(System.err, true, "UTF-8"));
        } catch (java.io.UnsupportedEncodingException ignored) {
        }

        long totalStart = System.nanoTime();

        try {
            execute(args);
        } catch (BuilderException e) {

            Diagnostics.error(e.getMessage());
            Diagnostics.summary();
            System.exit(1);

        } catch (Marked.MarkedException e) {

            Diagnostics.noteError();
            Diagnostics.summary();
            System.exit(1);

        } catch (NullPointerException |
                 IndexOutOfBoundsException |
                 ClassCastException |
                 StackOverflowError |
                 NegativeArraySizeException e) {

            Diagnostics.internal(e);
            Diagnostics.summary();
            System.exit(2);

        } catch (RuntimeException e) {

            Diagnostics.error(e.getMessage());
            Diagnostics.summary();

            if (Diagnostics.TRACE)
                e.printStackTrace();

            System.exit(1);

        } catch (Throwable e) {

            Diagnostics.internal(e);
            Diagnostics.summary();
            System.exit(2);

        } finally {

            long totalEnd = System.nanoTime();

            if (Debug.ENABLED) {
                Debug.line();
                Debug.print(
                    "TOTAL",
                    totalEnd - totalStart
                );
                Debug.line();
            }
        }
    }

    private static void execute(
        String[] args
    ) throws Exception {

        if (args == null || args.length == 0) {
            usage();
            return;
        }

        boolean compile = false;
        boolean win = false;
        boolean elf = false;
        boolean showTokens = false;
        boolean stdout = false;

        String input = null;
        String output = null;

        for (int i = 0; i < args.length; i++) {

            String arg = args[i];

            if ("--compile".equals(arg)) {
                compile = true;
                continue;
            }

            if ("--win".equals(arg)) {

                if (elf) {
                    throw new BuilderException(
                        "--win y --elf son formatos " +
                        "de salida mutuamente excluyentes."
                    );
                }

                win = true;
                compile = true;
                continue;
            }

            if ("--elf".equals(arg)) {

                if (win) {
                    throw new BuilderException(
                        "--win y --elf son formatos " +
                        "de salida mutuamente excluyentes."
                    );
                }

                elf = true;
                compile = true;
                continue;
            }

            if ("--tokens".equals(arg)) {
                showTokens = true;
                continue;
            }

            if ("--stdout".equals(arg)) {
                stdout = true;
                continue;
            }

            if ("--bits".equals(arg)) {

                if (i + 1 >= args.length)
                    throw new BuilderException("missing value after --bits (32 or 64)");

                String v = args[++i];

                if (!v.equals("16") && !v.equals("32") && !v.equals("64"))
                    throw new BuilderException(
                        "unsupported --bits " + v + " (use 16, 32 or 64)"
                    );

                forcedBits = Integer.parseInt(v);
                compile = true;
                continue;
            }

            if ("-s".equals(arg) || "--s".equals(arg) || "--silent".equals(arg)) {
                Diagnostics.SILENT = true;
                continue;
            }

            if ("--msg".equals(arg)) {
                if (i + 1 >= args.length)
                    throw new BuilderException("missing format after --msg");
                Diagnostics.SUCCESS_FORMAT = args[++i];
                continue;
            }

            if ("-e".equals(arg) || "--epilogue".equals(arg)) {
                arch.builtin.Vars.emitFrames = true;
                Backend32.emitFrames = true;
                Annotations.EPILOGUE_FLAG = true;
                continue;
            }

            if ("--no-libc".equals(arg)) {
                noLibc = true;
                continue;
            }

            if ("--exe".equals(arg)) {
                exe32 = true;
                compile = true;
                continue;
            }

            if ("--trace".equals(arg)) {
                Diagnostics.TRACE = true;
                continue;
            }

            if ("--version".equals(arg) || "-v".equals(arg) || "--v".equals(arg)) {
                System.out.println("ashc " + VERSION_ASHC);
                System.out.println("This is The Ashley Project!");
                System.out.println("Licensed under GNU GPL v3.0");
                System.out.println("Copyright (C) 2026 Mallionic Inc.");
                return;
            }
            if ("--licence".equals(arg) || "--license".equals(arg)) {
                System.out.println("The Ashley Project - ashc");
                System.out.println("Copyright (C) 2026 Mallionic Inc.");
                System.out.println();
                System.out.println("This program is free software: you can redistribute it and/or modify");
                System.out.println("it under the terms of the GNU General Public License as published by");
                System.out.println("the Free Software Foundation, either version 3 of the License, or");
                System.out.println("(at your option) any later version.");
                System.out.println();
                System.out.println("This program is distributed in the hope that it will be useful,");
                System.out.println("but WITHOUT ANY WARRANTY; without even the implied warranty of");
                System.out.println("MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.");
                System.out.println("See the GNU General Public License for more details.");
                System.out.println();
                System.out.println("Full license: https://www.gnu.org/licenses/gpl-3.0.html");
                return;
            }
            if ("--debug".equals(arg)) {
                Debug.ENABLED = true;
                Diagnostics.DEBUG = true;
                continue;
            }

            if ("--help".equals(arg) ||
                "-h".equals(arg)) {

                usage();
                return;
            }

            if ("--list-nlib".equals(arg)) {

                System.out.println("nlib embebida en el compilador:");

                for (String n : NlibEmbedded.names())
                    System.out.println("  <" + n.replaceAll("\\.(ash|a)$", "") + ">   (" + n + ")");

                System.exit(0);
            }

            if ("--outdir".equals(arg)) {

                if (i + 1 >= args.length)
                    throw new BuilderException("Falta el directorio despues de --outdir.");

                Diagnostics.OUT_DIR = Paths.get(args[++i]);
                continue;
            }

            if ("--format".equals(arg) || "-f".equals(arg)) {

                if (i + 1 >= args.length)
                    throw new BuilderException("Falta el formato despues de --format.");

                String fmt = args[++i].toLowerCase();

                switch (fmt) {
                    case "elf":
                        if (win) throw new BuilderException("--format elf choca con --win.");
                        elf = true; compile = true; break;
                    case "coff": case "win": case "obj":
                        if (elf) throw new BuilderException("--format coff choca con --elf.");
                        win = true; compile = true; break;
                    case "bin": case "flat": case "raw":
                        compile = true; break;
                    case "exe": case "elf32":
                        exe32 = true; compile = true; break;
                    case "ashir": case "ir":
                        break;
                    default:
                        throw new BuilderException(
                            "Formato desconocido: " + fmt +
                            " (elf, coff, bin, exe, ashir)"
                        );
                }
                continue;
            }

            if ("-o".equals(arg)) {

                if (i + 1 >= args.length) {
                    throw new BuilderException(
                        "Falta el archivo después de -o."
                    );
                }

                output = args[++i];
                continue;
            }

            if (arg.startsWith("-")) {
                throw new BuilderException(
                    "Opción desconocida: " +
                    arg
                );
            }

            if (input == null) {
                input = arg;
                continue;
            }

            if (output == null) {
                output = arg;
                continue;
            }

            throw new BuilderException(
                "So many args."
            );
        }

        if (input == null) {
            throw new BuilderException(
                "No file was specified."
            );
        }

        if (output != null && compile) {

            Path o = Paths.get(output);

            if (output.endsWith("/") || output.endsWith("\\") || Files.isDirectory(o))
                Diagnostics.OUT_DIR = o;
            else
                Diagnostics.OUT_OVERRIDE = o;
        }

        Path inputPath =
            Paths.get(input);

        Parseconv.PROJECT_ROOT =
            inputPath.toAbsolutePath().getParent();

        if (!Files.isRegularFile(inputPath)) {
            throw new BuilderException(
                "The file doesn't exit or is not a file: " +
                inputPath
            );
        }

        String filename =
            inputPath
                .getFileName()
                .toString()
                .toLowerCase();

        Debug.start(
            "INPUT"
        );

        if (filename.endsWith(".ash")) {

            long readStart = System.nanoTime();

            Directives.FORMAT =
                win ? "coff" : elf ? "elf" : exe32 ? "exe" : compile ? "bin" : "ashir";
            Directives.NO_LIBC = noLibc;
            Directives.FORCED_BITS = forcedBits;

            String source =
                Files.readString(
                    inputPath,
                    StandardCharsets.UTF_8
                );

            long readEnd = System.nanoTime();

            Debug.print(
                "Leer .ash",
                readEnd - readStart
            );

            Debug.value(
                "Bytes fuente",
                source.getBytes(
                    StandardCharsets.UTF_8
                ).length
            );

            Debug.value(
                "Caracteres",
                source.length()
            );

            long conversionStart =
                System.nanoTime();

            Annotations.setMsAbi(win);

            String ashir =
                convertAshToAshir(
                    source,
                    showTokens
                );

            long conversionEnd =
                System.nanoTime();

            Debug.print(
                "ASH -> ASHIR",
                conversionEnd - conversionStart
            );

            Debug.value(
                "Bytes ASHIR",
                ashir.getBytes(
                    StandardCharsets.UTF_8
                ).length
            );

            if (!compile) {

                if (stdout) {

                    System.out.print(
                        ashir
                    );

                    return;
                }

                long writeStart =
                    System.nanoTime();

                Path outputPath =
                    determineOutput(
                        inputPath,
                        output
                    );

                write(
                    outputPath,
                    ashir
                );

                long writeEnd =
                    System.nanoTime();

                Debug.print(
                    "Escribir .ashir",
                    writeEnd - writeStart
                );

                Diagnostics.built(outputPath, "ASHIR text");

                return;
            }

            if (stdout) {
                System.out.print(
                    ashir
                );
            }

            compileAshirMemory(
                ashir,
                inputPath,
                win,
                elf
            );

            return;
        }

        if (filename.endsWith(".ashir")) {

            if (!compile) {

                if (stdout) {

                    long readStart =
                        System.nanoTime();

                    String source =
                        Files.readString(
                            inputPath,
                            StandardCharsets.UTF_8
                        );

                    long readEnd =
                        System.nanoTime();

                    Debug.print(
                        "Leer .ashir",
                        readEnd - readStart
                    );

                    System.out.print(
                        source
                    );
                }

                return;
            }

            long readStart =
                System.nanoTime();

            String source =
                Files.readString(
                    inputPath,
                    StandardCharsets.UTF_8
                );

            long readEnd =
                System.nanoTime();

            Debug.print(
                "Leer .ashir",
                readEnd - readStart
            );

            compileAshirMemory(
                source,
                inputPath,
                win,
                elf
            );

            return;
        }

        throw new BuilderException(
            "Extensión no soportada: " +
            inputPath +
            "\nUsa .ash o .ashir."
        );
    }

    private static int forcedBits = 0;
    private static boolean exe32 = false;
    private static boolean noLibc = false;
    private static int sourceBits = 64;
    private static long sourceOrigin = 0;

    private static String convertAshToAshir(
        String source,
        boolean showTokens
    ) throws Exception {

        long start =
            System.nanoTime();

        Untokenizer lexer =
            new Untokenizer(
                source
            );

        long lexerCreate =
            System.nanoTime();

        Debug.print(
            "Crear Untokenizer",
            lexerCreate - start
        );

        List<Token> tokens =
            lexer.tokenize();

        long tokenEnd =
            System.nanoTime();

        Debug.print(
            "Untokenizer.tokenize",
            tokenEnd - lexerCreate
        );

        Debug.value(
            "Tokens ASH",
            tokens.size()
        );

        if (showTokens) {

            long printStart =
                System.nanoTime();

            printTokens(tokens);

            long printEnd =
                System.nanoTime();

            Debug.print(
                "Imprimir tokens",
                printEnd - printStart
            );
        }

        long convertStart =
            System.nanoTime();

        Parseconv.NO_LIBC = noLibc;
        Parseconv.FORCED_BITS = forcedBits;

        Parseconv.Result converted =
            Parseconv.convertWithSettings(
                tokens,
                0
            );

        String ashir =
            converted == null ? null : converted.ashir;

        if (converted != null) {
            sourceBits = converted.bits;
            sourceOrigin = converted.originOffset;
        }

        long convertEnd =
            System.nanoTime();

        Debug.print(
            "Parseconv.convert",
            convertEnd - convertStart
        );

        if (ashir == null) {
            throw new BuilderException(
                "Parseconv devolvió null."
            );
        }

        return ashir;
    }

    private static void compileAshirMemory(
        String source,
        Path input,
        boolean win,
        boolean elf
    ) throws Exception {

        Debug.line();
        Debug.start(
            "COMPILACIÓN ASHIR EN RAM"
        );

        long tokenStart =
            System.nanoTime();

        List<Tokens.Token> tokens =
            Tokens.tokenize(
                source
            );

        long tokenEnd =
            System.nanoTime();

        Debug.print(
            "Tokens.tokenize",
            tokenEnd - tokenStart
        );

        Debug.value(
            "Tokens ASHIR",
            tokens.size()
        );

        long parsingCreateStart =
            System.nanoTime();

        Parsing parsing =
            new Parsing();

        long parsingCreateEnd =
            System.nanoTime();

        Debug.print(
            "new Parsing",
            parsingCreateEnd - parsingCreateStart
        );

        long parseStart =
            System.nanoTime();

        Parsing.Program program =
            parsing.parse(
                tokens
            );

        long parseEnd =
            System.nanoTime();

        Debug.print(
            "Parsing.parse",
            parseEnd - parseStart
        );

        long builderStart =
            System.nanoTime();

        Builder builder =
            new Builder();

        long builderEnd =
            System.nanoTime();

        Debug.print(
            "new Builder",
            builderEnd - builderStart
        );

        int bits = forcedBits != 0 ? forcedBits : sourceBits;

        if (bits == 16 || bits == 32) {

            if ((win || elf) && bits == 16)
                throw new BuilderException(
                    "16-bit output is a flat binary: use --compile without --elf/--win"
                );

            if (win || elf) {
                compileObj32(program, input, win);
                return;
            }

            compileBin32(program, input, bits == 16);
            return;
        }

        if (win) {

            compileWin(
                builder,
                program,
                input
            );

            return;
        }

        if (elf) {

            compileElf(
                builder,
                program,
                input
            );

            return;
        }

        compileBin(
            builder,
            program,
            input
        );
    }

    private static byte[] wrapElf32Exe(byte[] code, long base) {

        java.nio.ByteBuffer b =
            java.nio.ByteBuffer.allocate(84 + code.length)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN);

        b.put(new byte[]{0x7F, 'E', 'L', 'F', 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0});
        b.putShort((short) 2);
        b.putShort((short) 3);
        b.putInt(1);
        b.putInt((int) (base + 84));
        b.putInt(52);
        b.putInt(0);
        b.putInt(0);
        b.putShort((short) 52);
        b.putShort((short) 32);
        b.putShort((short) 1);
        b.putShort((short) 0);
        b.putShort((short) 0);
        b.putShort((short) 0);

        long filesz = 84L + code.length;

        b.putInt(1);
        b.putInt(0);
        b.putInt((int) base);
        b.putInt((int) base);
        b.putInt((int) filesz);
        b.putInt((int) (filesz + (1 << 20)));
        b.putInt(7);
        b.putInt(0x1000);

        b.put(code);

        return b.array();
    }

    private static void compileObj32(
        Parsing.Program program,
        Path input,
        boolean windows
    ) throws Exception {

        Backend32 backend = new Backend32(program, 0);

        backend.objectMode = true;
        backend.noLibc = noLibc;
        backend.windows = windows;

        byte[] code = backend.compile();

        byte[] obj =
            windows
                ? Obj32.coff32(code, backend.symbols, backend.relocs)
                : Obj32.elf32(code, backend.symbols, backend.relocs,
                    backend.dataSize, backend.bssSize);

        Path outputDir = outputDirectory(input);

        Path outputPath = outputDir.resolve(
            stripExtension(input.getFileName().toString()) +
            (windows ? ".obj" : ".o")
        );

        Files.createDirectories(outputDir);
        Files.write(outputPath, obj);

        Diagnostics.built(outputPath, windows ? "i386 COFF object" : "i386 ELF object");
    }

    private static void compileBin32(
        Parsing.Program program,
        Path input,
        boolean sixteen
    ) throws Exception {

        final int HDR = 84;

        Backend32 backend =
            new Backend32(
                program,
                exe32 ? sourceOrigin + HDR : sourceOrigin
            );

        backend.linuxExit = exe32 && !sixteen;
        backend.set16(sixteen);

        byte[] code = backend.compile();

        if (exe32)
            code = wrapElf32Exe(code, sourceOrigin);

        Path outputDir = outputDirectory(input);

        Path outputPath = outputDir.resolve(
            stripExtension(input.getFileName().toString()) +
            (exe32 ? "" : ".bin")
        );

        Files.createDirectories(outputDir);
        Files.write(outputPath, code);

        if (exe32)
            outputPath.toFile().setExecutable(true);

        Diagnostics.built(outputPath, "i386 flat binary");
    }

    private static void compileBin(
        Builder builder,
        Parsing.Program program,
        Path input
    ) throws Exception {

        long backendCreateStart =
            System.nanoTime();

        Backend backend =
            new Backend(
                builder
            );

        long backendCreateEnd =
            System.nanoTime();

        Debug.print(
            "new Backend",
            backendCreateEnd - backendCreateStart
        );

        long backendStart =
            System.nanoTime();

        backend.generate(
            program
        );

        long backendEnd =
            System.nanoTime();

        Debug.print(
            "Backend.generate",
            backendEnd - backendStart
        );

        Path outputDir =
            outputDirectory(
                input
            );

        String outputName =
            stripExtension(
                input
                    .getFileName()
                    .toString()
            ) + ".bin";

        long buildStart =
            System.nanoTime();

        String result =
            builder.BUILD(
                outputDir.toString(),
                outputName
            );

        long buildEnd =
            System.nanoTime();

        Debug.print(
            "Builder.BUILD",
            buildEnd - buildStart
        );

        if (result == null) {

            throw new BuilderException(
                "Builder.BUILD() no generó ningún archivo."
            );
        }

        Path outputPath =
            outputDir.resolve(
                outputName
            );

        long existsStart =
            System.nanoTime();

        boolean exists =
            Files.exists(
                outputPath
            );

        long existsEnd =
            System.nanoTime();

        Debug.print(
            "Files.exists",
            existsEnd - existsStart
        );

        if (!exists) {

            throw new BuilderException(
                "Builder.BUILD() terminó pero el archivo " +
                "no existe:\n" +
                outputPath
            );
        }

        long sizeStart =
            System.nanoTime();

        long size =
            Files.size(
                outputPath
            );

        long sizeEnd =
            System.nanoTime();

        Debug.print(
            "Files.size",
            sizeEnd - sizeStart
        );

        Diagnostics.built(outputPath, "x86-64 flat binary");
    }

    private static void compileWin(
        Builder builder,
        Parsing.Program program,
        Path input
    ) throws Exception {

        long createStart =
            System.nanoTime();

        JavaWinOBJ winObj =
            new JavaWinOBJ(
                builder,
                JavaWinOBJ.IMAGE_FILE_MACHINE_AMD64
            );

        long createEnd =
            System.nanoTime();

        Debug.print(
            "new JavaWinOBJ",
            createEnd - createStart
        );

        long sectionsStart =
            System.nanoTime();

        winObj.addSection(
            ".text",
            JavaWinOBJ.SCN_CODE
        );

        winObj.addSection(
            ".rdata",
            JavaWinOBJ.SCN_RDATA
        );

        winObj.addSection(
            ".data",
            JavaWinOBJ.SCN_DATA
        );

        long sectionsEnd =
            System.nanoTime();

        Debug.print(
            "addSection",
            sectionsEnd - sectionsStart
        );

        long beginStart =
            System.nanoTime();

        winObj.begin();

        long beginEnd =
            System.nanoTime();

        Debug.print(
            "JavaWinOBJ.begin",
            beginEnd - beginStart
        );

        long backendCreateStart =
            System.nanoTime();

        Backend backend =
            new Backend(
                builder,
                winObj
            );

        long backendCreateEnd =
            System.nanoTime();

        Debug.print(
            "new Backend(COFF)",
            backendCreateEnd - backendCreateStart
        );

        long backendStart =
            System.nanoTime();

        backend.generate(
            program
        );

        long backendEnd =
            System.nanoTime();

        Debug.print(
            "Backend.generate(COFF)",
            backendEnd - backendStart
        );

        long metadataStart =
            System.nanoTime();

        winObj.addFileMetadata(
            input
        );

        long metadataEnd =
            System.nanoTime();

        Debug.print(
            "COFF metadata",
            metadataEnd - metadataStart
        );

        Path outputDir =
            outputDirectory(
                input
            );

        String outputName =
            stripExtension(
                input
                    .getFileName()
                    .toString()
            ) + ".obj";

        long endStart =
            System.nanoTime();

        String result =
            winObj.end(
                outputDir.toString(),
                outputName
            );

        long endEnd =
            System.nanoTime();

        Debug.print(
            "JavaWinOBJ.end",
            endEnd - endStart
        );

        if (result == null) {

            throw new BuilderException(
                "JavaWinOBJ.end() no generó ningún archivo."
            );
        }

        Path outputPath =
            outputDir.resolve(
                outputName
            );

        if (!Files.exists(outputPath)) {

            throw new BuilderException(
                "JavaWinOBJ terminó pero el OBJ no existe:\n" +
                outputPath
            );
        }

        Diagnostics.built(outputPath, "x86-64 COFF object");
    }

    private static void compileElf(
        Builder builder,
        Parsing.Program program,
        Path input
    ) throws Exception {

        long createStart =
            System.nanoTime();

        JavaElfOBJ elf =
            new JavaElfOBJ(
                builder
            );

        long createEnd =
            System.nanoTime();

        Debug.print(
            "new JavaElfOBJ",
            createEnd - createStart
        );

        long sectionsStart =
            System.nanoTime();

        elf.addSection(
            ".text",
            JavaElfOBJ.SCN_TEXT
        );

        elf.addSection(
            ".rodata",
            JavaElfOBJ.SCN_RODATA
        );

        elf.addSection(
            ".data",
            JavaElfOBJ.SCN_DATA
        );

        long sectionsEnd =
            System.nanoTime();

        Debug.print(
            "ELF addSection",
            sectionsEnd - sectionsStart
        );

        long beginStart =
            System.nanoTime();

        elf.begin();

        long beginEnd =
            System.nanoTime();

        Debug.print(
            "JavaElfOBJ.begin",
            beginEnd - beginStart
        );

        long backendCreateStart =
            System.nanoTime();

        Backend backend =
            new Backend(
                builder,
                elf
            );

        long backendCreateEnd =
            System.nanoTime();

        Debug.print(
            "new Backend(ELF)",
            backendCreateEnd - backendCreateStart
        );

        long backendStart =
            System.nanoTime();

        backend.generate(
            program
        );

        long backendEnd =
            System.nanoTime();

        Debug.print(
            "Backend.generate(ELF)",
            backendEnd - backendStart
        );

        long metadataStart =
            System.nanoTime();

        elf.addFileMetadata(
            input
        );

        long metadataEnd =
            System.nanoTime();

        Debug.print(
            "ELF metadata",
            metadataEnd - metadataStart
        );

        Path outputDir =
            outputDirectory(
                input
            );

        String outputName =
            stripExtension(
                input
                    .getFileName()
                    .toString()
            ) + ".o";

        long endStart =
            System.nanoTime();

        String result =
            elf.end(
                outputDir.toString(),
                outputName
            );

        long endEnd =
            System.nanoTime();

        Debug.print(
            "JavaElfOBJ.end",
            endEnd - endStart
        );

        if (result == null) {

            throw new BuilderException(
                "JavaElfOBJ.end() no generó ningún archivo."
            );
        }

        Path outputPath =
            outputDir.resolve(
                outputName
            );

        if (!Files.exists(outputPath)) {

            throw new BuilderException(
                "JavaElfOBJ terminó pero el ELF no existe:\n" +
                outputPath
            );
        }

        Diagnostics.built(outputPath, "x86-64 ELF object");
    }

    private static Path outputDirectory(
        Path input
    ) throws IOException {

        long start =
            System.nanoTime();

        Path absolute =
            input.toAbsolutePath();

        Path parent =
            absolute.getParent();

        if (parent == null) {
            parent = Paths.get(".");
        }

        Path output =
            parent.resolve(
                "out"
            );

        Files.createDirectories(
            output
        );

        long end =
            System.nanoTime();

        Debug.print(
            "Preparar output/",
            end - start
        );

        return output;
    }

    private static Path determineOutput(
        Path input,
        String explicit
    ) {

        if (explicit != null) {

            Path path =
                Paths.get(
                    explicit
                );

            createParent(
                path
            );

            return path;
        }

        String name =
            input
                .getFileName()
                .toString();

        int dot =
            name.lastIndexOf('.');

        if (dot >= 0) {

            name =
                name.substring(
                    0,
                    dot
                );
        }

        name += ".ashir";

        Path parent =
            input.getParent();

        if (parent == null) {
            return Paths.get(
                name
            );
        }

        return parent.resolve(
            name
        );
    }

    private static String stripExtension(
        String name
    ) {

        int dot =
            name.lastIndexOf('.');

        if (dot <= 0) {
            return name;
        }

        return name.substring(
            0,
            dot
        );
    }

    private static void write(
        Path path,
        String text
    ) throws IOException {

        createParent(
            path
        );

        Files.writeString(
            path,
            text,
            StandardCharsets.UTF_8
        );
    }

    private static void createParent(
        Path path
    ) {

        Path parent =
            path
                .toAbsolutePath()
                .getParent();

        if (parent == null) {
            return;
        }

        try {

            Files.createDirectories(
                parent
            );

        } catch (IOException e) {

            throw new BuilderException(
                "No se pudo crear el directorio: " +
                parent,
                e
            );
        }
    }

    private static void printTokens(
        List<Token> tokens
    ) {

        for (int i = 0; i < tokens.size(); i++) {

            Token token =
                tokens.get(i);

            System.out.printf(
                "%5d  %-12s  %-20s  %d:%d%n",
                i,
                token.type,
                token.text,
                token.line,
                token.column
            );
        }
    }

    private static void usage() {

        System.out.print(String.join("\n",
            "ashc - the ASH compiler",
            "",
            "USAGE",
            "  ashc <file.ash> [options]",
            "",
            "OUTPUT MODES",
            "  (default)            ASH -> ASHIR text",
            "  --compile            ASH -> ASHIR -> flat binary",
            "  --elf                ASH -> ELF64 relocatable object (Linux)",
            "  --win                ASH -> COFF AMD64 relocatable object (Windows)",
            "  --bits <16|32|64>    target width (also @bits(N)); 32 + --elf/--win gives",
            "                       an i386 ELF32 / COFF object, 16 gives a flat binary",
            "  --exe                32-bit Linux executable (ELF32) from a flat image",
            "  --no-libc            never call libc: built-in heap/exit, no externs",
            "  -e, --epilogue       emit function prologue/epilogue (stack frame) always;",
            "                       default is clean C-like output (frame only if needed)",
            "",
            "OPTIONS",
            "  -o <file|dir/>       write the output to <file> (or into <dir/>), any mode",
            "  --outdir <dir>       output directory instead of <input dir>/out",
            "  --list-nlib          list the libraries embedded in the compiler (import <name>)",
            "  -f, --format <fmt>   elf | coff | bin | exe | ashir  (same as --elf, --win, ...)",
            "  --stdout             print the result instead of writing a file",
            "  --tokens             print the token stream and stop",
            "  --debug              print per-stage timings",
            "  -s, --silent         print nothing on success",
            "  --msg <format>       success message; {file} {size} {mode} are replaced",
            "  --trace              show Java stack traces for internal errors",
            "  -v, --version        print the compiler version",
            "  -h, --help           show this help",
            "",
            "DIAGNOSTICS",
            "  warning   (yellow)   suspicious code, compilation continues",
            "  tip       (green)    a suggestion to fix the problem above it",
            "  error     (purple)   a problem in your program or command line",
            "  internal  (red)      a bug in ashc itself; please report it",
            "",
            "EXAMPLES",
            "  ashc hello.ash --elf -o hello.o",
            "  ashc hello.ash --win -o hello.obj",
            "",
            ""));
    }

    private static final class Debug {

        private static boolean ENABLED = false;

        private static void start(
            String name
        ) {
            if (!ENABLED) {
                return;
            }

            System.err.println();
            System.err.println(
                "[DEBUG] " + name
            );
        }

        private static void line() {
            if (!ENABLED) {
                return;
            }

            System.err.println(
                "--------------------------------------------------"
            );
        }

        private static void print(
            String name,
            long nanos
        ) {
            if (!ENABLED) {
                return;
            }

            double ms =
                nanos / 1_000_000.0;

            System.err.printf(
                "[DEBUG] %-28s %12.3f ms%n",
                name,
                ms
            );
        }

        private static void value(
            String name,
            Object value
        ) {
            if (!ENABLED) {
                return;
            }

            System.err.printf(
                "[DEBUG] %-28s %s%n",
                name,
                value
            );
        }
    }

    private static final class BuilderException
        extends RuntimeException {

        BuilderException(
            String message
        ) {
            super(
                message
            );
        }

        BuilderException(
            String message,
            Throwable cause
        ) {
            super(
                message,
                cause
            );
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
