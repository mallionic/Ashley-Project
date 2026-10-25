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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Builder {

    public static boolean COMPILATION_FLOW_IGNORE_ERRS = false;

    private static final DateTimeFormatter TS_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final ByteArrayOutputStream buffer;

    private int origin = 0x0000;

    private int org = 0x0000;

    private volatile boolean fileLoggingEnabled = true;

    private volatile String logFilePath = "logs/build.log";

    private final Map<String, Integer> labels =
        new LinkedHashMap<>();

    private final Map<String, List<Integer>> references =
        new LinkedHashMap<>();

    private final ByteArrayOutputStream dataBuffer =
        new ByteArrayOutputStream();

    private final Map<String, StringData> strings =
        new LinkedHashMap<>();

    private int stringCounter = 0;

    private static final class PendingData {

        final int patchPos;
        final int dataOffset;

        PendingData(int patchPos, int dataOffset) {
            this.patchPos = patchPos;
            this.dataOffset = dataOffset;
        }
    }

    private final List<PendingData> pendingData =
        new ArrayList<>();

    public static final class StringData {

        private final String symbol;
        private final String value;
        private final byte[] bytes;
        private final int dataOffset;

        private StringData(
            String symbol,
            String value,
            byte[] bytes,
            int dataOffset
        ) {
            this.symbol = symbol;
            this.value = value;
            this.bytes = bytes.clone();
            this.dataOffset = dataOffset;
        }

        public String getSymbol() {
            return symbol;
        }

        public String getValue() {
            return value;
        }

        public byte[] getBytes() {
            return bytes.clone();
        }

        public int getDataOffset() {
            return dataOffset;
        }

        public int size() {
            return bytes.length;
        }

        @Override
        public String toString() {
            return symbol +
                   " offset=" +
                   dataOffset +
                   " size=" +
                   bytes.length;
        }
    }

    public Builder() {
        this.buffer = new ByteArrayOutputStream();
    }

    public Builder(String logFilePath) {
        this();

        if (logFilePath != null &&
            !logFilePath.isBlank()) {

            this.logFilePath = logFilePath;
        }
    }

    public Builder(Class<?> sourceClass) {
        this();
    }

    public synchronized void disableFileLogging() {

        appendToLogFile(
            "[CONFIG] Logging a archivo DESACTIVADO."
        );

        this.fileLoggingEnabled = false;
    }

    public synchronized void enableFileLogging() {

        this.fileLoggingEnabled = true;

        appendToLogFile(
            "[CONFIG] Logging a archivo ACTIVADO."
        );
    }

    public synchronized boolean isFileLoggingEnabled() {
        return fileLoggingEnabled;
    }

    public synchronized void setLogLocation(String path) {

        Objects.requireNonNull(
            path,
            "path no puede ser null"
        );

        String old = this.logFilePath;

        this.logFilePath = path;

        appendToLogFile(
            "[CONFIG] Ubicación de log cambiada: " +
            old +
            " -> " +
            path
        );
    }

    public synchronized String getLogLocation() {
        return logFilePath;
    }

    public synchronized void logBufferSnapshot(
        String label
    ) {

        byte[] data = toByteArray();

        appendBlockToLogFile(
            "[SNAPSHOT] " +
            (label == null
                ? "(sin etiqueta)"
                : label) +
            " - tamaño=" +
            data.length +
            " bytes",
            hexDump(data)
        );
    }

    private synchronized void appendToLogFile(
        String line
    ) {

        if (!fileLoggingEnabled) {
            return;
        }

        writeRaw(
            "[" +
            LocalDateTime.now().format(TS_FMT) +
            "] " +
            line +
            System.lineSeparator()
        );
    }

    private synchronized void appendBlockToLogFile(
        String header,
        String block
    ) {

        if (!fileLoggingEnabled) {
            return;
        }

        StringBuilder sb =
            new StringBuilder();

        sb.append('[')
          .append(
              LocalDateTime.now().format(TS_FMT)
          )
          .append("] ")
          .append(header)
          .append(System.lineSeparator());

        sb.append(block)
          .append(System.lineSeparator());

        writeRaw(sb.toString());
    }

    private synchronized void writeRaw(
        String content
    ) {

        try {

            File logFile =
                new File(logFilePath);

            File parent =
                logFile.getParentFile();

            if (parent != null &&
                !parent.exists()) {

                parent.mkdirs();
            }

            try (
                FileWriter fw =
                    new FileWriter(
                        logFile,
                        true
                    )
            ) {

                fw.write(content);
            }

        } catch (IOException e) {

            System.err.println(
                "[jbios] No se pudo escribir en el log: " +
                e.getMessage()
            );
        }
    }

    private static String stackTraceToString(
        Throwable t
    ) {

        StringWriter sw =
            new StringWriter();

        t.printStackTrace(
            new PrintWriter(sw)
        );

        return sw.toString();
    }

    public synchronized String hexDump(
        byte[] data
    ) {

        if (data == null ||
            data.length == 0) {

            return "(buffer vacío, 0 bytes)";
        }

        StringBuilder sb =
            new StringBuilder();

        int len = data.length;

        for (
            int offset = 0;
            offset < len;
            offset += 16
        ) {

            sb.append(
                String.format(
                    "%08X  ",
                    offset
                )
            );

            StringBuilder ascii =
                new StringBuilder();

            for (
                int i = 0;
                i < 16;
                i++
            ) {

                int idx =
                    offset + i;

                if (idx < len) {

                    int b =
                        data[idx] & 0xFF;

                    sb.append(
                        String.format(
                            "%02X ",
                            b
                        )
                    );

                    ascii.append(
                        (b >= 32 && b < 127)
                            ? (char) b
                            : '.'
                    );

                } else {

                    sb.append("   ");
                }

                if (i == 7) {
                    sb.append(' ');
                }
            }

            sb.append(" |")
              .append(ascii)
              .append('|')
              .append(
                  System.lineSeparator()
              );
        }

        sb.append(
            String.format(
                "%08X  (fin, %d bytes totales)",
                len,
                len
            )
        );

        return sb.toString();
    }

    public synchronized String hexDump() {
        return hexDump(toByteArray());
    }

    public synchronized Builder file_write(
        byte... bytes
    ) {

        if (bytes == null ||
            bytes.length == 0) {

            return this;
        }

        buffer.write(
            bytes,
            0,
            bytes.length
        );

        return this;
    }

    public synchronized Builder emit_bytes(
        byte... bytes
    ) {

        return file_write(bytes);
    }

    public synchronized Builder file_write(
        int... values
    ) {

        if (values == null ||
            values.length == 0) {

            return this;
        }

        byte[] bytes =
            new byte[values.length];

        for (
            int i = 0;
            i < values.length;
            i++
        ) {

            bytes[i] =
                (byte)(
                    values[i] & 0xFF
                );
        }

        buffer.write(
            bytes,
            0,
            bytes.length
        );

        return this;
    }

    public synchronized Builder file_write_shortLE(
        short value
    ) {

        byte low =
            (byte)(value & 0xFF);

        byte high =
            (byte)(
                (value >> 8) & 0xFF
            );

        return file_write(
            low,
            high
        );
    }

    public synchronized Builder file_write_intLE(
        int value
    ) {

        byte b0 =
            (byte)(value & 0xFF);

        byte b1 =
            (byte)((value >> 8) & 0xFF);

        byte b2 =
            (byte)((value >> 16) & 0xFF);

        byte b3 =
            (byte)((value >> 24) & 0xFF);

        return file_write(
            b0,
            b1,
            b2,
            b3
        );
    }

    public synchronized byte[] toByteArray() {
        return buffer.toByteArray();
    }

    public synchronized int size() {
        return buffer.size();
    }

    public synchronized int offset() {
        return size();
    }

    public synchronized int getOrigin() {
        return origin;
    }

    public synchronized void setOrigin(
        int origin
    ) {

        this.origin = origin;
        this.org = origin;
    }

    public synchronized int currentAddress() {
        return origin + size();
    }

    public synchronized int markLabel() {
        return size();
    }

    public synchronized int markLabel(
        String name
    ) {

        Objects.requireNonNull(
            name,
            "name no puede ser null"
        );

        if (labels.containsKey(name)) {

            throw new IllegalArgumentException(
                "Símbolo duplicado: " +
                name
            );
        }

        int pos = size();

        labels.put(
            name,
            pos
        );

        return pos;
    }

    public synchronized int defineLabel(
        String name
    ) {

        return markLabel(name);
    }

    public synchronized boolean hasLabel(
        String name
    ) {

        return labels.containsKey(name);
    }

    public synchronized Integer getLabelOffset(
        String name
    ) {

        return labels.get(name);
    }

    public synchronized Integer getLabelAddress(
        String name
    ) {

        Integer off =
            labels.get(name);

        if (off == null) {
            return null;
        }

        return origin + off;
    }

    public synchronized Map<String, Integer>
    getLabels() {

        return Collections.unmodifiableMap(
            new LinkedHashMap<>(labels)
        );
    }

    public synchronized String defineString(
        String value
    ) {

        Objects.requireNonNull(
            value,
            "value no puede ser null"
        );

        for (StringData existing : strings.values()) {

            if (existing.value.equals(value)) {
                return existing.symbol;
            }
        }

        String symbol =
            "__str_" +
            stringCounter++;

        byte[] utf8 =
            value.getBytes(
                StandardCharsets.UTF_8
            );

        byte[] terminated =
            new byte[utf8.length + 1];

        System.arraycopy(
            utf8,
            0,
            terminated,
            0,
            utf8.length
        );

        terminated[
            terminated.length - 1
        ] = 0;

        int dataOffset =
            dataBuffer.size();

        try {

            dataBuffer.write(
                terminated
            );

        } catch (IOException e) {

            throw new RuntimeException(e);
        }

        StringData info =
            new StringData(
                symbol,
                value,
                terminated,
                dataOffset
            );

        strings.put(
            symbol,
            info
        );

        appendToLogFile(
            "[STRING] " +
            symbol +
            " offset=" +
            dataOffset +
            " size=" +
            terminated.length +
            " value=\"" +
            value +
            "\""
        );

        return symbol;
    }

    public synchronized String emitString(
        String value
    ) {

        return defineString(value);
    }

    public synchronized boolean hasString(
        String symbol
    ) {

        return strings.containsKey(symbol);
    }

    public synchronized StringData getString(
        String symbol
    ) {

        return strings.get(symbol);
    }

    public synchronized byte[] getStringData(
        String symbol
    ) {

        StringData data =
            strings.get(symbol);

        if (data == null) {
            return null;
        }

        return data.getBytes();
    }

    public synchronized Integer getStringOffset(
        String symbol
    ) {

        StringData data =
            strings.get(symbol);

        if (data == null) {
            return null;
        }

        return data.dataOffset;
    }

    public synchronized int getDataSize() {
        return dataBuffer.size();
    }

    public synchronized byte[] getDataBuffer() {
        return dataBuffer.toByteArray();
    }

    public synchronized List<StringData>
    getStrings() {

        return Collections.unmodifiableList(
            new ArrayList<>(
                strings.values()
            )
        );
    }

    public synchronized List<String>
    getStringSymbols() {

        return Collections.unmodifiableList(
            new ArrayList<>(
                strings.keySet()
            )
        );
    }

    public synchronized Integer
    getStringFileOffset(
        String symbol
    ) {

        StringData data =
            strings.get(symbol);

        if (data == null) {
            return null;
        }

        return size() + data.dataOffset;
    }

    public synchronized Integer
    getStringAddress(
        String symbol
    ) {

        Integer fileOffset =
            getStringFileOffset(symbol);

        if (fileOffset == null) {
            return null;
        }

        return origin + fileOffset;
    }

    private final List<Object[]> symbolFixups = new ArrayList<>();

    public synchronized void noteSymbolFixup(int pos, String symbol) {
        symbolFixups.add(new Object[]{pos, symbol});
    }

    public synchronized List<Object[]> symbolFixups() {
        return symbolFixups;
    }

    public synchronized void truncate(int size) {
        if (size < 0 || size > this.size())
            throw new IllegalArgumentException("truncate fuera de rango: " + size);
        truncateTo(size);
    }

    private void truncateTo(int size) {
        byte[] all = buffer.toByteArray();
        buffer.reset();
        buffer.write(all, 0, size);
    }

    public synchronized void addReference(
        String symbol,
        int patchOffset
    ) {

        Objects.requireNonNull(
            symbol,
            "symbol no puede ser null"
        );

        if (patchOffset < 0) {

            throw new IllegalArgumentException(
                "patchOffset < 0"
            );
        }

        references
            .computeIfAbsent(
                symbol,
                k -> new ArrayList<>()
            )
            .add(patchOffset);
    }

    public synchronized List<Integer>
    getReferences(
        String symbol
    ) {

        List<Integer> refs =
            references.get(symbol);

        if (refs == null) {
            return Collections.emptyList();
        }

        return Collections.unmodifiableList(
            new ArrayList<>(refs)
        );
    }

    public synchronized Map<String, List<Integer>>
    getReferences() {

        Map<String, List<Integer>> copy =
            new LinkedHashMap<>();

        for (
            Map.Entry<String, List<Integer>> e :
            references.entrySet()
        ) {

            copy.put(
                e.getKey(),
                Collections.unmodifiableList(
                    new ArrayList<>(e.getValue())
                )
            );
        }

        return Collections.unmodifiableMap(
            copy
        );
    }

    public synchronized byte getByteAt(
        int offset
    ) {

        byte[] data =
            buffer.toByteArray();

        if (
            offset < 0 ||
            offset >= data.length
        ) {

            throw new IndexOutOfBoundsException(
                "getByteAt: offset fuera de rango: " +
                offset
            );
        }

        return data[offset];
    }

    public synchronized void patchByte(
        int pos,
        byte value
    ) {

        byte[] data =
            buffer.toByteArray();

        if (
            pos < 0 ||
            pos >= data.length
        ) {

            throw new IndexOutOfBoundsException(
                "patchByte: pos fuera de rango: " +
                pos
            );
        }

        byte oldValue =
            data[pos];

        data[pos] = value;

        buffer.reset();

        try {

            buffer.write(data);

        } catch (IOException e) {

            throw new RuntimeException(e);
        }

        appendToLogFile(
            String.format(
                "[BUFFER COPY] patchByte: " +
                "%d bytes, pos=%d, %02X -> %02X",
                data.length,
                pos,
                oldValue & 0xFF,
                value & 0xFF
            )
        );
    }

    public synchronized void patchBytes(
        int pos,
        byte[] values
    ) {

        Objects.requireNonNull(
            values,
            "values no puede ser null"
        );

        byte[] data =
            buffer.toByteArray();

        if (
            pos < 0 ||
            pos + values.length > data.length
        ) {

            throw new IndexOutOfBoundsException(
                "patchBytes: pos fuera de rango: " +
                pos
            );
        }

        System.arraycopy(
            values,
            0,
            data,
            pos,
            values.length
        );

        buffer.reset();

        try {

            buffer.write(data);

        } catch (IOException e) {

            throw new RuntimeException(e);
        }

        appendToLogFile(
            String.format(
                "[BUFFER COPY] patchBytes: " +
                "%d bytes, escribió %d bytes en pos=%d",
                data.length,
                values.length,
                pos
            )
        );
    }

    public synchronized void file_write_at(
        int pos,
        byte value
    ) {

        patchByte(
            pos,
            value
        );
    }

    public synchronized void patchShortLE(
        int pos,
        short value
    ) {

        patchByte(
            pos,
            (byte)(value & 0xFF)
        );

        patchByte(
            pos + 1,
            (byte)((value >> 8) & 0xFF)
        );
    }

    public synchronized void patchIntLE(
        int pos,
        int value
    ) {

        patchByte(
            pos,
            (byte)(value & 0xFF)
        );

        patchByte(
            pos + 1,
            (byte)((value >> 8) & 0xFF)
        );

        patchByte(
            pos + 2,
            (byte)((value >> 16) & 0xFF)
        );

        patchByte(
            pos + 3,
            (byte)((value >> 24) & 0xFF)
        );
    }

    public synchronized int queueData(
        byte[] data
    ) {

        Objects.requireNonNull(
            data,
            "data no puede ser null"
        );

        int offset =
            dataBuffer.size();

        try {

            dataBuffer.write(data);

        } catch (IOException e) {

            throw new RuntimeException(e);
        }

        return offset;
    }

    public synchronized void registerDataReference(
        int patchPos,
        int dataOffset
    ) {

        pendingData.add(
            new PendingData(
                patchPos,
                dataOffset
            )
        );
    }

    private synchronized void finalizePendingData() {

        if (dataBuffer.size() == 0) {
            return;
        }

        int dataBaseOffset =
            buffer.size();

        byte[] data =
            dataBuffer.toByteArray();

        file_write(data);

        for (
            PendingData ref :
            pendingData
        ) {

            int fileOffset =
                dataBaseOffset +
                ref.dataOffset;

            int runtimeAddress =
                origin +
                fileOffset;

            patchShortLE(
                ref.patchPos,
                (short)runtimeAddress
            );
        }

        for (
            StringData string :
            strings.values()
        ) {

            int fileOffset =
                dataBaseOffset +
                string.dataOffset;

            labels.put(
                string.symbol,
                fileOffset
            );
        }

        dataBuffer.reset();
        pendingData.clear();
    }

    public synchronized void finalizeData() {
        finalizePendingData();
    }

    public synchronized int
    emit_jmp_near_placeholder_16() {

        file_write(0xE9);

        int pos =
            size();

        file_write_shortLE(
            (short)0
        );

        return pos;
    }

    public synchronized int
    emit_call_placeholder_16() {

        file_write(0xE8);

        int pos =
            size();

        file_write_shortLE(
            (short)0
        );

        return pos;
    }

    public synchronized int
    emit_jmp_near_placeholder() {

        file_write(0xE9);

        int pos =
            size();

        file_write_intLE(0);

        return pos;
    }

    public synchronized int
    emit_jmp_short_placeholder() {

        file_write(
            0xEB,
            0x00
        );

        return size() - 1;
    }

    public synchronized void
    patch_call_16(
        int callRelPos,
        int targetPos
    ) {

        int rel =
            targetPos -
            (callRelPos + 2);

        patchShortLE(
            callRelPos,
            (short)rel
        );
    }

    public synchronized void
    emit_mov_ax_imm16(
        short imm
    ) {

        file_write(0xB8);

        file_write_shortLE(
            imm
        );
    }

    public synchronized void
    emit_mov_bx_imm16(
        short imm
    ) {

        file_write(0xBB);

        file_write_shortLE(
            imm
        );
    }

    public synchronized int
    emit_mov_dx_placeholder_16() {

        file_write(0xBA);

        int pos =
            size();

        file_write_shortLE(
            (short)0
        );

        return pos;
    }

    public synchronized void
    emit_and_ax_imm16(
        short imm
    ) {

        file_write(0x25);

        file_write_shortLE(
            imm
        );
    }

    public synchronized void
    emit_cmp_al_imm8(
        byte value
    ) {

        file_write(0x3C);

        file_write(
            value & 0xFF
        );
    }

    public synchronized int
    emit_mov_di_placeholder_16() {

        file_write(0xBF);

        int pos =
            size();

        file_write_shortLE(
            (short)0
        );

        return pos;
    }

    public synchronized void
    emit_mov_bx_ax() {

        file_write(
            0x89,
            0xC3
        );
    }

    public synchronized int
    emit_jz_short_placeholder() {

        file_write(
            0x74,
            0x00
        );

        return size() - 1;
    }

    public synchronized void
    emit_jmp_short_rel(
        int relSigned
    ) {

        if (
            relSigned < -128 ||
            relSigned > 127
        ) {

            throw new IllegalArgumentException(
                "JMP short fuera de rango: " +
                relSigned
            );
        }

        file_write(
            0xEB,
            relSigned & 0xFF
        );
    }

    public synchronized void
    patch_jmp_short(
        int jumpPos,
        int targetPos
    ) {

        int rel =
            targetPos -
            (jumpPos + 1);

        if (
            rel < -128 ||
            rel > 127
        ) {

            throw new IllegalArgumentException(
                "JMP short fuera de rango: " +
                rel
            );
        }

        patchByte(
            jumpPos,
            (byte)(rel & 0xFF)
        );
    }

    public synchronized void
    patch_jz_short(
        int jumpPos,
        int targetPos
    ) {

        int rel =
            targetPos -
            (jumpPos + 1);

        if (
            rel < -128 ||
            rel > 127
        ) {

            throw new IllegalArgumentException(
                "JZ short fuera de rango: " +
                rel
            );
        }

        patchByte(
            jumpPos,
            (byte)(rel & 0xFF)
        );
    }

    public synchronized Builder
    fill_with_until(
        byte fill,
        int targetSize
    ) {

        int current =
            buffer.size();

        if (current >= targetSize) {

            appendToLogFile(
                "[FILL] tamaño actual (" +
                current +
                ") >= objetivo (" +
                targetSize +
                ")"
            );

            return this;
        }

        int missing =
            targetSize -
            current;

        byte[] padding =
            new byte[missing];

        for (
            int i = 0;
            i < missing;
            i++
        ) {

            padding[i] =
                fill;
        }

        buffer.write(
            padding,
            0,
            padding.length
        );

        appendToLogFile(
            "[FILL] Rellenados " +
            missing +
            " bytes"
        );

        return this;
    }

    public synchronized String BUILD(
        String outputDir
    ) {

        return BUILD(
            outputDir,
            "output.bin"
        );
    }

    public synchronized String BUILD(
        String outputDir,
        String filename
    ) {

        return BUILD(
            outputDir,
            filename,
            null
        );
    }

    public synchronized String BUILD(
        String outputDir,
        String filename,
        String postBuildCmd
    ) {

        Instant startInstant =
            Instant.now();

        long startNanos =
            System.nanoTime();

        appendToLogFile(
            "==================== INICIO DE BUILD ===================="
        );

        appendToLogFile(
            "outputDir=\"" +
            outputDir +
            "\" filename=\"" +
            filename +
            "\" postBuildCmd=" +
            (
                postBuildCmd == null
                    ? "null"
                    : "\"" +
                      postBuildCmd +
                      "\""
            )
        );

        boolean buildSuccess =
            false;

        String resultPath =
            null;

        String failureDetail =
            null;

        try {

            if (
                outputDir == null ||
                outputDir.isBlank()
            ) {

                throw new IOException(
                    "El directorio de salida está vacío."
                );
            }

            if (
                filename == null ||
                filename.isBlank()
            ) {

                throw new IOException(
                    "El nombre del archivo está vacío."
                );
            }

            File dir =
                new File(outputDir);

            if (!dir.exists()) {

                if (
                    !dir.mkdirs() &&
                    !dir.exists()
                ) {

                    throw new IOException(
                        "No se pudo crear el directorio: " +
                        dir.getAbsolutePath()
                    );
                }
            }

            if (!dir.isDirectory()) {

                throw new IOException(
                    "La ruta no es un directorio: " +
                    dir.getAbsolutePath()
                );
            }

            finalizePendingData();

            byte[] data =
                buffer.toByteArray();

            File outFile =
                new File(
                    dir,
                    filename
                );

            appendBlockToLogFile(
                "[FASE 3] Buffer completo (" +
                data.length +
                " bytes)",
                hexDump(data)
            );

            try (
                FileOutputStream fos =
                    new FileOutputStream(
                        outFile
                    )
            ) {

                fos.write(data);
                fos.flush();
                fos.getFD().sync();
            }

            if (!outFile.exists()) {

                throw new IOException(
                    "El archivo no existe después de escribirlo."
                );
            }

            long writtenSize =
                outFile.length();

            if (
                writtenSize !=
                data.length
            ) {

                throw new IOException(
                    "Tamaño incorrecto. " +
                    "Esperado=" +
                    data.length +
                    " obtenido=" +
                    writtenSize
                );
            }

            resultPath =
                outFile.getAbsolutePath();

            buildSuccess =
                true;

        } catch (IOException e) {

            failureDetail =
                e.getMessage();

            appendToLogFile(
                "[ERROR] IOException: " +
                e.getMessage()
            );

            appendBlockToLogFile(
                "[ERROR] Stack trace:",
                stackTraceToString(e)
            );

        } catch (RuntimeException e) {

            failureDetail =
                e.getMessage();

            appendToLogFile(
                "[ERROR] RuntimeException: " +
                e.getMessage()
            );

            appendBlockToLogFile(
                "[ERROR] Stack trace:",
                stackTraceToString(e)
            );
        }

        long durationMs =
            (
                System.nanoTime() -
                startNanos
            ) / 1_000_000L;

        if (buildSuccess) {

            appendToLogFile(
                "[RESUMEN] SUCCESS " +
                "duración=" +
                durationMs +
                "ms bytes=" +
                size() +
                " ruta=" +
                resultPath
            );

            if (
                postBuildCmd != null &&
                !postBuildCmd.isBlank()
            ) {

                MAKE_FILE_FLOW_EXEC_CMD(
                    postBuildCmd
                );
            }

        } else {

            appendToLogFile(
                "[RESUMEN] FAILURE " +
                "duración=" +
                durationMs +
                "ms detalle=" +
                failureDetail
            );
        }

        appendToLogFile(
            "Fecha/hora fin: " +
            LocalDateTime.now().format(TS_FMT)
        );

        appendToLogFile(
            "==================== FIN DE BUILD ====================" +
            System.lineSeparator()
        );

        return resultPath;
    }

    public void COMPILATION_FLOW_IGNORE_ERRS(
        boolean act
    ) {

        COMPILATION_FLOW_IGNORE_ERRS =
            act;
    }

    public synchronized void clear() {

        int bytesDescartados =
            buffer.size();

        buffer.reset();

        dataBuffer.reset();

        labels.clear();

        references.clear();

        strings.clear();

        pendingData.clear();

        stringCounter =
            0;

        appendToLogFile(
            "[CLEAR] Builder reiniciado. " +
            "Bytes descartados=" +
            bytesDescartados
        );
    }

    private String toHexString(
        byte[] data
    ) {

        if (
            data == null ||
            data.length == 0
        ) {

            return "";
        }

        StringBuilder sb =
            new StringBuilder();

        for (
            int i = 0;
            i < data.length;
            i++
        ) {

            sb.append(
                String.format(
                    "%02X",
                    data[i]
                )
            );

            if (
                i + 1 <
                data.length
            ) {

                sb.append(",");
            }
        }

        return sb.toString();
    }

    @Override
    public synchronized String toString() {

        return toHexString(
            toByteArray()
        );
    }

    public static boolean
    MAKE_FILE_FLOW_EXEC_CMD(
        String cmd
    ) {

        final String RESET =
            "\u001B[0m";

        final String BOLD =
            "\u001B[1m";

        final String GREEN =
            "\u001B[32m";

        final String RED =
            "\u001B[31m";

        try {

            ProcessBuilder pb;

            if (
                System
                    .getProperty("os.name")
                    .toLowerCase()
                    .contains("win")
            ) {

                pb =
                    new ProcessBuilder(
                        "cmd.exe",
                        "/c",
                        cmd
                    );

            } else {

                pb =
                    new ProcessBuilder(
                        "bash",
                        "-c",
                        cmd
                    );
            }

            pb.inheritIO();

            Process p =
                pb.start();

            int exitCode =
                p.waitFor();

            if (exitCode == 0) {

                System.out.println(
                    BOLD +
                    GREEN +
                    "[SUCCESS] " +
                    RESET +
                    "Comando ejecutado correctamente: " +
                    cmd
                );

                return true;
            }

            System.out.println(
                BOLD +
                RED +
                "[FAILED] " +
                RESET +
                "The command terminated with code " +
                exitCode +
                ": " +
                cmd
            );

            return false;

        } catch (
            IOException |
            InterruptedException e
        ) {

            System.out.println(
                BOLD +
                RED +
                "[FAILED] " +
                RESET +
                "Error ejecutando comando: " +
                e.getMessage()
            );

            if (
                e instanceof InterruptedException
            ) {

                Thread.currentThread()
                    .interrupt();
            }

            return false;
        }
    }

    public static void main(
        String[] args
    ) {

        Builder builder =
            new Builder();

        String hello =
            builder.defineString(
                "Hola"
            );

        String world =
            builder.defineString(
                "Mundo"
            );

        System.out.println(
            "hello = " +
            hello
        );

        System.out.println(
            "world = " +
            world
        );

        System.out.println(
            "hello offset = " +
            builder.getStringOffset(
                hello
            )
        );

        System.out.println(
            "data size = " +
            builder.getDataSize()
        );

        System.out.println(
            "hello bytes:"
        );

        System.out.println(
            builder.hexDump(
                builder.getStringData(
                    hello
                )
            )
        );
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
