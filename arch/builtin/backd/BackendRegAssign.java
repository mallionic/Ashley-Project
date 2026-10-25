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

package arch.builtin.backd;

import static arch.builtin.backd.BackendUtil.cleanSymbol;
import static arch.builtin.backd.BackendUtil.isCompilerTemporary;
import static arch.builtin.backd.BackendUtil.isNumber;
import static arch.builtin.backd.BackendUtil.isStringLiteral;
import static arch.builtin.backd.BackendUtil.parseNumber;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import arch.builtin.Parsing;
import arch.builtin.Vars;
import arch.builtin.VarsBridge;

public final class BackendRegAssign {

    private final BackendContext ctx;
    private final Vars vars;
    private final BackendExterns externs;
    private final BackendGlobals globals;

    public BackendRegAssign(
        BackendContext ctx,
        BackendExterns externs,
        BackendGlobals globals
    ) {
        this.ctx = ctx;
        this.vars = ctx.vars;
        this.externs = externs;
        this.globals = globals;
    }

    private int typeOfSymbol(String symbol) {

        String s = cleanSymbol(symbol);

        if (s == null || s.isEmpty())
            return Vars.QWORD;

        if (vars.exists(s))
            return vars.TypeOf(s);

        if (ctx.hasType(s))
            return ctx.varsTypeOf(s);

        return ctx.typeForTemporary(s);
    }

    private int typeForMoveDestination(
        String destination,
        String source
    ) {

        String dest = cleanSymbol(destination);
        String src = cleanSymbol(source);

        if (dest != null && ctx.hasType(dest))
            return ctx.varsTypeOf(dest);

        if (src != null) {

            if (vars.exists(src))
                return vars.TypeOf(src);

            if (ctx.hasType(src))
                return ctx.varsTypeOf(src);
        }

        return ctx.typeForTemporary(dest);
    }

    private boolean isFloatType(int type) {
        return type == Vars.FLOAT;
    }

    private int moveDestinationType(String destination) {

        String dest = cleanSymbol(destination);

        if (dest == null || dest.isEmpty())
            return Vars.QWORD;

        if (ctx.hasType(dest))
            return ctx.varsTypeOf(dest);

        if (vars.exists(dest))
            return vars.TypeOf(dest);

        return ctx.typeForTemporary(dest);
    }

    private boolean isFloatBitPattern(String value) {

        if (value == null)
            return false;

        String s = value.trim();

        if (s.startsWith("+"))
            s = s.substring(1);

        if (
            !s.startsWith("0x") &&
            !s.startsWith("0X")
        ) {
            return false;
        }

        String hex = s.substring(2);

        if (
            hex.endsWith("L") ||
            hex.endsWith("l")
        ) {
            hex = hex.substring(
                0,
                hex.length() - 1
            );
        }

        return hex.length() == 16;
    }

    private double parseFloatLiteral(String value) {

        if (value == null)
            throw new IllegalArgumentException(
                "Literal FLOAT null"
            );

        String s = value.trim();

        if (s.isEmpty())
            throw new NumberFormatException(
                "Literal FLOAT vacío"
            );

        if (isFloatBitPattern(s)) {

            boolean negative =
                s.startsWith("-");

            if (
                s.startsWith("+") ||
                s.startsWith("-")
            ) {
                s = s.substring(1);
            }

            String hex =
                s.substring(2);

            if (
                hex.endsWith("L") ||
                hex.endsWith("l")
            ) {
                hex = hex.substring(
                    0,
                    hex.length() - 1
                );
            }

            long bits =
                Long.parseUnsignedLong(
                    hex,
                    16
                );

            if (negative)
                bits |= 0x8000000000000000L;

            return Double.longBitsToDouble(bits);
        }

        return Double.parseDouble(s);
    }

    private void declareLiteral(
        String destination,
        int type,
        String literal
    ) {

        if (isFloatType(type)) {

            double value =
                parseFloatLiteral(literal);

            ctx.declareLocal(
                destination,
                type,
                value
            );

            return;
        }

        long value =
            parseNumber(literal);

        ctx.declareLocal(
            destination,
            type,
            value
        );
    }

    private void setLiteral(
        String destination,
        int type,
        String literal
    ) {

        if (isFloatType(type)) {

            double value =
                parseFloatLiteral(literal);

            vars.Set(
                destination,
                value
            );

            return;
        }

        long value =
            parseNumber(literal);

        vars.Set(
            destination,
            value
        );
    }

    private boolean generateControl(
        Parsing.RegAssignStmt s,
        String destination,
        String opcode
    ) {

        String cond =
            s.args == null
                ? ""
                : String.join(" ", s.args);

        List<String> a =
            s.args == null
                ? Collections.<String>emptyList()
                : s.args;

        VarsBridge ctrl = ctx.bridge;

        if (
            opcode.equals("label") ||
            opcode.equals("brf") ||
            opcode.equals("brt") ||
            opcode.equals("jmp") ||
            opcode.equals("goto")
        ) {
            ctx.constantValues.clear();
            ctx.knownConstants.clear();
        }

        switch (opcode) {

            case "if":
                ctrl.If(cond);
                return true;

            case "elseif":
            case "elif":
                ctrl.ElseIf(cond);
                return true;

            case "else":
                ctrl.Else();
                return true;

            case "endif":
            case "fi":
                ctrl.EndIf();
                return true;

            case "while":
                ctrl.While(cond);
                return true;

            case "endwhile":
                ctrl.EndWhile();
                return true;

            case "break":
                ctrl.Break();
                return true;

            case "continue":
                ctrl.Continue();
                return true;

            case "endfor":
                ctrl.EndFor();
                return true;

            case "label":

                if (a.isEmpty())
                    throw new IllegalArgumentException(
                        "label sin nombre"
                    );

                ctrl.labelNamed(
                    cleanSymbol(a.get(0))
                );

                return true;

            case "jmp":
            case "goto":

                if (a.isEmpty())
                    throw new IllegalArgumentException(
                        "jmp sin destino"
                    );

                ctrl.jmpNamed(
                    cleanSymbol(a.get(0))
                );

                return true;

            case "brf":
            case "brt": {

                if (a.size() < 4)
                    throw new IllegalArgumentException(
                        "brf/brt: lhs, op, rhs, label"
                    );

                ctrl.branch(
                    cleanSymbol(a.get(0)),
                    cleanSymbol(a.get(1)),
                    cleanSymbol(a.get(2)),
                    cleanSymbol(a.get(3)),
                    opcode.equals("brt")
                );

                return true;
            }

            case "for": {

                List<String> t = new ArrayList<>();

                for (
                    String x :
                    cond.replace(",", " ")
                        .trim()
                        .split("\\s+")
                ) {
                    t.add(cleanSymbol(x));
                }

                if (t.size() < 3)
                    throw new IllegalArgumentException(
                        "for: for var inicio fin [paso]"
                    );

                String v = t.get(0);

                if (
                    !ctx.localVariables.contains(v) &&
                    !vars.exists(v)
                ) {
                    ctx.declareLocal(
                        v,
                        Vars.QWORD,
                        0L
                    );
                } else {
                    ctx.localVariables.add(v);
                }

                ctx.clearConstant(v);

                ctrl.For(
                    v,
                    t.get(1),
                    t.get(2),
                    t.size() > 3
                        ? t.get(3)
                        : null
                );

                return true;
            }

            case "inc":
            case "dec": {

                String t =
                    a.isEmpty()
                        ? destination
                        : cleanSymbol(a.get(0));

                ctx.clearConstant(t);

                if (opcode.equals("inc"))
                    vars.Increment(t);
                else
                    vars.Decrement(t);

                return true;
            }

            default:
                return false;
        }
    }

    private void prepareArithmeticDestination(
        String destination,
        String source
    ) {

        String dest = cleanSymbol(destination);

        if (dest == null || dest.isEmpty()) {
            throw new IllegalArgumentException(
                "Destino aritmético vacío"
            );
        }

        if (vars.exists(dest))
            return;

        if (ctx.hasType(dest)) {

            ctx.ensureTemporary(
                dest,
                ctx.varsTypeOf(dest)
            );

            return;
        }

        if (isCompilerTemporary(dest)) {

            if (!isNumber(source)) {

                String src = cleanSymbol(source);

                if (src != null && vars.exists(src)) {

                    ctx.ensureTemporary(
                        dest,
                        vars.TypeOf(src)
                    );

                    return;
                }

                if (src != null && ctx.hasType(src)) {

                    ctx.ensureTemporary(
                        dest,
                        ctx.varsTypeOf(src)
                    );

                    return;
                }
            }

            ctx.ensureTemporary(dest);
            return;
        }

        ctx.ensureTemporary(dest);
    }

    public void generateRegAssign(
        Parsing.RegAssignStmt s
    ) {

        if (s == null)
            return;

        if (
            s.opcode == null ||
            s.opcode.isEmpty()
        ) {
            throw new IllegalArgumentException(
                "Asignacion sin opcode"
            );
        }

        String destination =
            cleanSymbol(s.name);

        String opcode =
            s.opcode.toLowerCase();

        if (
            generateControl(
                s,
                destination,
                opcode
            )
        ) {
            return;
        }

        if (
            s.name == null ||
            s.name.isEmpty()
        ) {
            throw new IllegalArgumentException(
                "Asignacion sin destino"
            );
        }

        if (
            s.args == null ||
            s.args.isEmpty()
        ) {
            throw new IllegalArgumentException(
                "Opcode '" +
                s.opcode +
                "' sin argumentos"
            );
        }

        String first =
            s.args.get(0) == null
                ? null
                : s.args.get(0).trim();

        if (isJcc(opcode)) {

            generateJcc(
                opcode,
                first
            );

            return;
        }

        switch (opcode) {

            case "load":
                generateLoad(
                    destination,
                    first
                );
                return;

            case "mov":
                generateMov(
                    destination,
                    first
                );
                return;

            case "addr":
                generateAddr(
                    destination,
                    first
                );
                return;

            case "faddr": {

                String fsym = cleanSymbol(first);
                String fdst = cleanSymbol(destination);

                if (!ctx.localVariables.contains(fdst)) {

                    if (!vars.exists(fdst))
                        ctx.declareLocal(fdst, Vars.QWORD, 0L);
                    else
                        ctx.localVariables.add(fdst);
                }

                vars.AddressOfSymbol(fdst, fsym);
                ctx.clearConstant(fdst);
                return;
            }

            case "cmp":
                generateCmp(
                    destination,
                    first
                );
                return;

            case "call":
                generateCallOpcode(
                    s,
                    destination,
                    first
                );
                return;

            default:
                break;
        }

        vars.unsignedOp =
            (opcode.equals("div") || opcode.equals("mod") ||
             opcode.equals("shr") || opcode.equals("sar")) &&
            vars.unsignedLookup.test(destination);

        try {
            generateArithmeticOpcode(s, destination, opcode, first);
        } finally {
            vars.unsignedOp = false;
        }
    }

    private void generateArithmeticOpcode(
        Parsing.RegAssignStmt s,
        String destination,
        String opcode,
        String first
    ) {

        if (!opcode.equals("asm"))
            prepareArithmeticDestination(
                destination,
                first
            );

        switch (opcode) {

            case "add":

                generateBinaryArithmetic(
                    destination,
                    first,
                    "add"
                );

                break;

            case "sub":

                generateBinaryArithmetic(
                    destination,
                    first,
                    "sub"
                );

                break;

            case "mul":
            case "multiply":
            case "imul":

                generateBinaryArithmetic(
                    destination,
                    first,
                    "mul"
                );

                break;

            case "div":

                generateBinaryArithmetic(
                    destination,
                    first,
                    "div"
                );

                break;

            case "and":
            case "or":
            case "xor": {

                if (isNumber(first)) {

                    long bv = parseNumber(first);

                    if (opcode.equals("and")) vars.And(destination, bv);
                    else if (opcode.equals("or")) vars.Or(destination, bv);
                    else vars.Xor(destination, bv);

                } else {

                    String bc = cleanSymbol(first);

                    if (
                        vars.exists(destination) &&
                        vars.exists(bc) &&
                        vars.TypeOf(destination) != vars.TypeOf(bc)
                    ) {
                        String conv = "__cv" + (convCounter++);

                        ctx.ensureTemporary(conv, vars.TypeOf(destination));
                        vars.Set(conv, bc);

                        bc = conv;

                        if (opcode.equals("and")) vars.And(destination, bc);
                        else if (opcode.equals("or")) vars.Or(destination, bc);
                        else vars.Xor(destination, bc);

                        vars.Delete(conv);

                    } else {

                        if (opcode.equals("and")) vars.And(destination, bc);
                        else if (opcode.equals("or")) vars.Or(destination, bc);
                        else vars.Xor(destination, bc);
                    }
                }

                break;
            }

            case "asm": {

                String hex = first.substring(1);
                byte[] raw = new byte[hex.length() / 2];

                for (int k = 0; k < raw.length; k++)
                    raw[k] = (byte) Integer.parseInt(hex.substring(2 * k, 2 * k + 2), 16);

                String text = new String(raw, java.nio.charset.StandardCharsets.UTF_8);

                java.util.regex.Matcher am =
                    java.util.regex.Pattern.compile("@var:(\\w+)").matcher(text);

                StringBuffer asb = new StringBuffer();

                while (am.find()) {

                    String vn = am.group(1);

                    if (!vars.exists(vn))
                        throw new IllegalArgumentException(
                            "unknown variable '" + vn + "' in asm block"
                        );

                    String loc = vars.Location(vn);

                    if (loc.startsWith("[")) {
                        int t = vars.TypeOf(vn);
                        String w = t == Vars.QWORD ? "qword "
                            : t == Vars.DWORD ? "dword "
                            : t == Vars.WORD ? "word " : "byte ";
                        loc = w + loc;
                    }

                    am.appendReplacement(asb, java.util.regex.Matcher.quoteReplacement(loc));
                }

                am.appendTail(asb);

                byte[] code =
                    arch.AsmBridge.assemble(asb.toString(), 64, 0);

                for (byte x : code)
                    ctx.builder.file_write(x & 0xFF);

                vars.forceSaveAll();

                if (ctx.constantValues != null) ctx.constantValues.clear();
                if (ctx.knownConstants != null) ctx.knownConstants.clear();

                break;
            }

            case "mod":

                generateBinaryArithmetic(
                    destination,
                    first,
                    "mod"
                );

                break;

            case "shl":
            case "shr":
            case "sar": {

                if (!isNumber(first)) {
                    throw new IllegalArgumentException(
                        "shift requiere cuenta constante: " + first
                    );
                }

                long count = parseNumber(first);

                if (count < 0 || count > 63) {
                    throw new IllegalArgumentException(
                        "Shift fuera de rango: " + count
                    );
                }

                if (opcode.equals("shl"))
                    vars.ShiftLeft(destination, (int) count);
                else
                    vars.ShiftRight(destination, (int) count);

                break;
            }

            default:

                throw new IllegalArgumentException(
                    "Operacion ASHIR desconocida: " +
                    s.opcode
                );
        }

        ctx.clearConstant(destination);
    }

    private boolean isJcc(
        String opcode
    ) {

        return
            opcode.equals("je") ||
            opcode.equals("jz") ||
            opcode.equals("jne") ||
            opcode.equals("jnz") ||
            opcode.equals("jg") ||
            opcode.equals("jge") ||
            opcode.equals("jl") ||
            opcode.equals("jle") ||
            opcode.equals("ja") ||
            opcode.equals("jae") ||
            opcode.equals("jb") ||
            opcode.equals("jbe");
    }

    private void generateJcc(
        String opcode,
        String first
    ) {

        String label =
            cleanSymbol(first);

        if (
            label == null ||
            label.isEmpty()
        ) {
            throw new IllegalArgumentException(
                "Salto '" +
                opcode +
                "' sin destino"
            );
        }

        vars.Jcc(
            opcode,
            label
        );
    }

    private void generateLoad(
        String destination,
        String first
    ) {

        String pointer =
            cleanSymbol(first);

        if (
            pointer == null ||
            pointer.isEmpty()
        ) {
            throw new IllegalArgumentException(
                "load: puntero vacío"
            );
        }

        ctx.ensureTemporary(
            destination
        );

        ctx.clearConstant(
            destination
        );

        vars.LoadPtr(
            destination,
            pointer
        );
    }

    private void generateMov(
        String destination,
        String first
    ) {

        if (
            first == null ||
            first.isEmpty()
        ) {
            throw new IllegalArgumentException(
                "mov sin fuente"
            );
        }

        if (isStringLiteral(first)) {

            String stringSymbol =
                globals.internStringLiteral(first);

            boolean temporary =
                isCompilerTemporary(destination);

            if (
                ctx.localVariables.contains(destination)
            ) {

                if (!temporary) {

                    throw new IllegalArgumentException(
                        "La variable '" +
                        destination +
                        "' ya existe"
                    );
                }

            } else if (!vars.exists(destination)) {

                ctx.declareLocal(
                    destination,
                    Vars.QWORD,
                    0L
                );

            } else {

                ctx.localVariables.add(
                    destination
                );
            }

            vars.AddressOfSymbol(
                destination,
                stringSymbol
            );

            ctx.clearConstant(
                destination
            );

            return;
        }

        boolean temporary =
            isCompilerTemporary(destination);

        if (
            !isNumber(first) &&
            destination.equals(
                cleanSymbol(first)
            )
        ) {
            return;
        }

        if (temporary) {

            boolean initialized = false;

            if (
                !ctx.localVariables.contains(
                    destination
                )
            ) {

                if (!vars.exists(destination)) {

                    initialized = true;

                    if (isNumber(first)) {

                        int destinationType =
                            moveDestinationType(
                                destination
                            );

                        declareLiteral(
                            destination,
                            destinationType,
                            first
                        );

                    } else {

                        String srcText = ctx.typeOf(cleanSymbol(first));
                        if (ctx.typeOf(destination) == null && srcText != null)
                            ctx.declareType(destination, srcText);

                        int sourceType =
                            typeForMoveDestination(
                                destination,
                                first
                            );

                        ctx.declareLocal(
                            destination,
                            sourceType,
                            first
                        );
                    }

                } else {

                    ctx.localVariables.add(
                        destination
                    );
                }
            }

            if (isNumber(first)) {

                int destinationType =
                    moveDestinationType(
                        destination
                    );

                if (!initialized)
                    setLiteral(
                        destination,
                        destinationType,
                        first
                    );

                if (!isFloatType(destinationType)) {

                    long value =
                        parseNumber(first);

                    ctx.constantValues.put(
                        destination,
                        value
                    );

                    if (!ctx.addressTaken.contains(destination)) ctx.knownConstants.add(
                        destination
                    );

                } else {

                    ctx.clearConstant(
                        destination
                    );
                }

            } else {

                String source =
                    cleanSymbol(first);

                if (!initialized)
                    vars.Set(
                        destination,
                        source
                    );

                if (
                    ctx.knownConstants.contains(
                        source
                    )
                ) {

                    ctx.constantValues.put(
                        destination,
                        ctx.constantValues.get(
                            source
                        )
                    );

                    if (!ctx.addressTaken.contains(destination)) ctx.knownConstants.add(
                        destination
                    );

                } else {

                    ctx.clearConstant(
                        destination
                    );
                }
            }

            return;
        }

        if (
            ctx.localVariables.contains(
                destination
            )
        ) {

            ctx.clearConstant(
                destination
            );

            if (isNumber(first)) {

                int destinationType =
                    typeOfSymbol(destination);

                setLiteral(
                    destination,
                    destinationType,
                    first
                );

            } else {

                vars.Set(
                    destination,
                    cleanSymbol(first)
                );
            }

            return;
        }

        if (isNumber(first)) {

            int destinationType =
                moveDestinationType(
                    destination
                );

            declareLiteral(
                destination,
                destinationType,
                first
            );

            if (!isFloatType(destinationType)) {

                long value =
                    parseNumber(first);

                ctx.constantValues.put(
                    destination,
                    value
                );

                if (!ctx.addressTaken.contains(destination)) ctx.knownConstants.add(
                    destination
                );

            } else {

                ctx.clearConstant(
                    destination
                );
            }

            return;
        }

        String source =
            cleanSymbol(first);

        int sourceType =
            typeForMoveDestination(
                destination,
                source
            );

        ctx.declareLocal(
            destination,
            sourceType,
            source
        );

        if (
            ctx.knownConstants.contains(
                source
            )
        ) {

            ctx.constantValues.put(
                destination,
                ctx.constantValues.get(
                    source
                )
            );

            if (!ctx.addressTaken.contains(destination)) ctx.knownConstants.add(
                destination
            );
        }
    }

    private void generateAddr(
        String destination,
        String first
    ) {

        String source =
            cleanSymbol(first);

        ctx.addressTaken.add(source);
        ctx.clearConstant(source);

        boolean globalSource =
            ctx.hasGlobalString(source) ||
            ctx.hasGlobalLabel(source);

        if (
            !globalSource &&
            !ctx.localVariables.contains(
                source
            ) &&
            !vars.exists(source)
        ) {

            throw new IllegalArgumentException(
                "addr: variable no encontrada: " +
                source
            );
        }

        if (
            !ctx.localVariables.contains(
                destination
            )
        ) {

            if (!vars.exists(destination)) {

                ctx.declareLocal(
                    destination,
                    Vars.QWORD,
                    0L
                );

            } else {

                ctx.localVariables.add(
                    destination
                );
            }
        }

        if (globalSource) {

            vars.AddressOfSymbol(
                destination,
                source
            );

        } else {

            vars.AddressOf(
                destination,
                source
            );
        }

        ctx.clearConstant(
            destination
        );
    }

    private void generateCmp(
        String destination,
        String first
    ) {

        if (
            first == null ||
            first.isEmpty()
        ) {
            throw new IllegalArgumentException(
                "cmp sin operando"
            );
        }

        String lhs =
            cleanSymbol(destination);

        if (
            lhs == null ||
            lhs.isEmpty()
        ) {
            throw new IllegalArgumentException(
                "cmp sin operando izquierdo"
            );
        }

        if (isNumber(first)) {

            vars.Cmp(
                lhs,
                parseNumber(first)
            );

            return;
        }

        String rhs =
            cleanSymbol(first);

        if (
            rhs == null ||
            rhs.isEmpty()
        ) {
            throw new IllegalArgumentException(
                "cmp sin operando derecho"
            );
        }

        vars.Cmp(
            lhs,
            rhs
        );
    }

    private void generateCallOpcode(
        Parsing.RegAssignStmt s,
        String destination,
        String first
    ) {

        String functionName =
            cleanSymbol(first);

        if (
            functionName == null ||
            functionName.isEmpty()
        ) {
            throw new IllegalArgumentException(
                "call sin función"
            );
        }

        Object[] arguments =
            new Object[
                Math.max(
                    0,
                    s.args.size() - 1
                )
            ];

        for (
            int i = 1;
            i < s.args.size();
            i++
        ) {

            String rawArg =
                s.args.get(i);

            if (rawArg == null) {

                throw new IllegalArgumentException(
                    "Argumento null en call a '" +
                    functionName +
                    "'"
                );
            }

            rawArg =
                rawArg.trim();

            if (
                isStringLiteral(
                    rawArg
                )
            ) {

                arguments[i - 1] =
                    globals.internStringLiteral(
                        rawArg
                    );

                continue;
            }

            String arg =
                cleanSymbol(rawArg);

            arguments[i - 1] =
                isNumber(arg)
                    ? (Object) parseNumber(arg)
                    : arg;
        }

        if (
            !externs.hasExtern(
                functionName
            )
        ) {
            externs.ensureImplicitExtern(
                functionName
            );
        }

        externs.emitExternalCall(
            externs.externFunction(
                functionName
            ),
            destination,
            arguments
        );
    }

    private int convCounter = 0;

    private void generateBinaryArithmetic(
        String destination,
        String source,
        String opcode
    ) {

        if (isNumber(source)) {

            long value =
                parseNumber(source);

            switch (opcode) {

                case "add":

                    vars.Add(
                        destination,
                        value
                    );

                    return;

                case "sub":

                    vars.Sub(
                        destination,
                        value
                    );

                    return;

                case "mul":

                    vars.Multiply(
                        destination,
                        value
                    );

                    return;

                case "div":

                    if (value == 0) {

                        throw new ArithmeticException(
                            "División por cero"
                        );
                    }

                    vars.Divide(
                        destination,
                        value
                    );

                    return;

                case "mod":

                    if (value == 0) {

                        throw new ArithmeticException(
                            "Módulo por cero"
                        );
                    }

                    vars.Mod(
                        destination,
                        value
                    );

                    return;

                default:

                    throw new IllegalArgumentException(
                        "Operación inválida: " +
                        opcode
                    );
            }
        }

        String clean =
            cleanSymbol(source);

        if (
            clean == null ||
            clean.isEmpty()
        ) {
            throw new IllegalArgumentException(
                "Operando aritmético vacío"
            );
        }

        String convertedTemp = null;

        if (
            vars.exists(destination) &&
            vars.exists(clean)
        ) {

            int destType =
                vars.TypeOf(destination);

            int sourceType =
                vars.TypeOf(clean);

            if (destType != sourceType) {

                boolean bothInteger =
                    destType != Vars.FLOAT32 && destType != Vars.FLOAT64 &&
                    sourceType != Vars.FLOAT32 && sourceType != Vars.FLOAT64;

                if (!bothInteger) {
                    throw new IllegalArgumentException(
                        "incompatible types in " + opcode.toUpperCase() +
                        ": " + destination + " (" + typeName(destType) +
                        ") and " + clean + " (" + typeName(sourceType) + ")"
                    );
                }

                String conv = "__cv" + (convCounter++);

                ctx.ensureTemporary(conv, destType);
                vars.Set(conv, clean);

                clean = conv;
                convertedTemp = conv;
            }
        }

        switch (opcode) {

            case "add":

                vars.Add(
                    destination,
                    clean
                );

                break;

            case "sub":

                vars.Sub(
                    destination,
                    clean
                );

                break;

            case "mul":

                vars.Multiply(
                    destination,
                    clean
                );

                break;

            case "div":

                vars.Divide(
                    destination,
                    clean
                );

                break;

            case "mod":

                vars.Mod(
                    destination,
                    clean
                );

                break;

            default:

                throw new IllegalArgumentException(
                    "Operación inválida: " +
                    opcode
                );
        }

        if (convertedTemp != null)
            vars.Delete(convertedTemp);
    }

    private String typeName(int type) {

        if (type == Vars.BYTE)
            return "BYTE";

        if (type == Vars.WORD)
            return "WORD";

        if (type == Vars.DWORD)
            return "DWORD";

        if (type == Vars.QWORD)
            return "QWORD";

        if (type == Vars.FLOAT)
            return "FLOAT";

        return "TYPE(" + type + ")";
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
