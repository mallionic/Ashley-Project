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

package Builder.ash;

import Builder.Untokenizer.Token;
import Builder.Untokenizer.Type;
import arch.builtin.Env;

public final class Annotations {

    private final Cursor c;
    private final long builderOrg;

    public static final java.util.Map<String, Long> RIGHTS = new java.util.HashMap<>();

    public static void setMsAbi(boolean on) { Generator.MS_ABI = on; }

    public static boolean EPILOGUE_FLAG = false;

    private static boolean boundsExplicit = false;

    private Long pendingOverride;

    private boolean pendingEfiapi;

    public boolean takeEfiapi() {
        boolean v = pendingEfiapi;
        pendingEfiapi = false;
        return v;
    }

    private boolean pendingNaked;

    public boolean takeNaked() {
        boolean v = pendingNaked;
        pendingNaked = false;
        return v;
    }

    private String[] pendingSection;

    public String[] takeSection() {
        String[] v = pendingSection;
        pendingSection = null;
        return v;
    }

    private String readSectionArgs() {
        String name = Builder.ash.StringConv.stripQuotes(c.expect(Type.STRING, "nombre de seccion").text);
        String flags = "";
        if (c.match(Type.COMMA))
            flags = Builder.ash.StringConv.stripQuotes(c.expect(Type.STRING, "flags de seccion").text);
        pendingSection = new String[]{name, flags};
        return name;
    }

    public Long takeOverride() {
        Long v = pendingOverride;
        pendingOverride = null;
        return v;
    }

    private long readRight() {

        boolean neg = c.match(Type.MINUS);

        Token n = c.expect(Type.NUMBER, "valor de override");

        java.math.BigInteger b;

        String t = n.text.replace("_", "");

        try {
            b = t.startsWith("0x") || t.startsWith("0X")
                ? new java.math.BigInteger(t.substring(2), 16)
                : new java.math.BigInteger(t);
        } catch (NumberFormatException e) {
            throw c.error("valor de override invalido: " + n.text);
        }

        if (neg) b = b.negate();

        if (b.bitLength() > 63)
            throw c.error("override fuera del rango int64 con signo: " + b);

        return b.longValue();
    }

    public Annotations(
        Cursor c,
        long builderOrg
    ) {
        this.c = c;
        this.builderOrg = builderOrg;
    }

    public boolean parse(
        Ast.Program program
    ) {
        boolean entry = false;

        while (c.match(Type.AT)) {

            Token attr = c.expect(
                Type.IDENT,
                "atributo"
            );

            if (
                attr.text.equals("Entry") ||
                attr.text.equals("entry")
            ) {
                if (entry) {
                    throw c.error(
                        "El atributo @Entry aparece dos veces"
                    );
                }

                entry = true;
                continue;
            }

            if (
                attr.text.equalsIgnoreCase(
                    "originoffs"
                )
            ) {
                parseOriginDirective(program);
                continue;
            }

            if (
                attr.text.equalsIgnoreCase(
                    "bits"
                )
            ) {
                parseBitsDirective(program);
                continue;
            }

            if (
                attr.text.equalsIgnoreCase(
                    "beautycode"
                )
            ) {
                c.expect(Type.LPAREN, "(");

                String v = c.advance().text.toLowerCase();

                c.expect(Type.RPAREN, ")");

                boolean on =
                    !(v.equals("false") || v.equals("0") || v.equals("off"));

                Generator.BEAUTY = on;
                if (!boundsExplicit)
                    Generator.BOUNDS_CHECKS = on;
                arch.builtin.Vars.emitFrames = !on || EPILOGUE_FLAG;
                arch.Backend32.emitFrames = !on || EPILOGUE_FLAG;
                continue;
            }

            if (attr.text.equals("efiapi") || attr.text.equals("EFIAPI") || attr.text.equals("msabi")) {
                pendingEfiapi = true;
                continue;
            }

            if (attr.text.equals("naked")) {
                pendingNaked = true;
                continue;
            }

            if (attr.text.equals("section")) {
                c.expect(Type.LPAREN, "(");
                readSectionArgs();
                c.expect(Type.RPAREN, ")");
                continue;
            }

            if (attr.text.equals("attribute") || attr.text.equals("__attribute__")) {
                c.expect(Type.LPAREN, "(");
                boolean dbl = c.match(Type.LPAREN);
                do {
                    Token a = c.expect(Type.IDENT, "atributo");
                    if (a.text.equals("section") || a.text.equals("__section__")) {
                        c.expect(Type.LPAREN, "(");
                        readSectionArgs();
                        c.expect(Type.RPAREN, ")");
                    } else {
                        throw c.error("@attribute: atributo desconocido '" + a.text + "' (soportado: section)");
                    }
                } while (c.match(Type.COMMA));
                if (dbl) c.expect(Type.RPAREN, ")");
                c.expect(Type.RPAREN, ")");
                continue;
            }

            if (attr.text.equalsIgnoreCase("bounds") || attr.text.equalsIgnoreCase("boundscheck")) {
                c.expect(Type.LPAREN, "(");
                String v = c.advance().text.toLowerCase();
                c.expect(Type.RPAREN, ")");
                Generator.BOUNDS_CHECKS =
                    !(v.equals("false") || v.equals("0") || v.equals("off"));
                boundsExplicit = true;
                continue;
            }

            if (
                attr.text.equalsIgnoreCase("ovr") ||
                attr.text.equalsIgnoreCase("override")
            ) {

                long v = 1;

                if (c.match(Type.LBRACKET)) {

                    if (!c.check(Type.RBRACKET))
                        v = readRight();

                    c.expect(Type.RBRACKET, "]");

                } else if (c.match(Type.LPAREN)) {

                    if (!c.check(Type.RPAREN))
                        v = readRight();

                    c.expect(Type.RPAREN, ")");
                }

                pendingOverride = v;
                continue;
            }

            if (attr.text.equalsIgnoreCase("nolibc")) {

                boolean on = true;

                if (c.match(Type.LPAREN)) {

                    String v = c.advance().text.toLowerCase();

                    c.expect(Type.RPAREN, ")");

                    on = !(v.equals("false") || v.equals("0") || v.equals("off"));
                }

                Directives.NO_LIBC = on;
                Builder.Parseconv.NO_LIBC = on;
                continue;
            }

            if (attr.text.equalsIgnoreCase("strict")) {

                boolean on = true;

                if (c.match(Type.LPAREN)) {

                    String v = c.advance().text.toLowerCase();

                    c.expect(Type.RPAREN, ")");

                    on = !(v.equals("false") || v.equals("0") || v.equals("off"));
                }

                Generator.STRICT = on;
                continue;
            }

            throw c.error(
                "Atributo desconocido @" +
                attr.text
            );
        }

        return entry;
    }

    private void parseOriginDirective(
        Ast.Program p
    ) {
        if (p.originSpecified) {
            throw c.error(
                "@originoffs(...) solo puede aparecer una vez"
            );
        }

        c.expect(
            Type.LPAREN,
            "("
        );

        long value;

        if (c.check(Type.NUMBER)) {

            value = Nums.parseIntegerLiteral(
                c.advance().text
            );

        } else if (c.check(Type.IDENT)) {

            String name =
                c.advance().text;

            if (
                name.equalsIgnoreCase(
                    "offset"
                ) ||
                name.equalsIgnoreCase(
                    "org"
                )
            ) {
                value = builderOrg;
            } else {
                throw c.error(
                    "Identificador desconocido '" +
                    name +
                    "' en @originoffs(...)"
                );
            }

        } else {

            throw c.error(
                "@originoffs(...) requiere un numero o 'offset'"
            );
        }

        c.expect(
            Type.RPAREN,
            ")"
        );

        c.match(
            Type.SEMICOLON
        );

        p.originOffset = value;
        p.originSpecified = true;
    }

    private void parseBitsDirective(
        Ast.Program p
    ) {
        if (p.bitsSpecified) {
            throw c.error(
                "@bits(...) solo puede aparecer una vez"
            );
        }

        c.expect(
            Type.LPAREN,
            "("
        );

        if (!c.check(Type.NUMBER)) {
            throw c.error(
                "@bits(...) requiere 16, 32 o 64"
            );
        }

        long value =
            Nums.parseIntegerLiteral(
                c.advance().text
            );

        if (
            value != 16 &&
            value != 32 &&
            value != 64
        ) {
            throw c.error(
                "@bits solo acepta 16, 32 o 64"
            );
        }

        c.expect(
            Type.RPAREN,
            ")"
        );

        c.match(
            Type.SEMICOLON
        );
        arch.Diagnostics.log("SET BIT:" + value);
        p.bits = (int) value;
        p.bitsSpecified = true;

        Env.SET_BITS((int) value);
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
