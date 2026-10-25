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
import static arch.builtin.backd.BackendUtil.typeOf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import arch.formatter.JavaElfOBJ;
import arch.formatter.JavaWinOBJ;
import arch.builtin.Vars;

public final class BackendExterns {

    private final BackendContext ctx;
    private final Vars vars;

    public BackendExterns(BackendContext ctx) {
        this.ctx = ctx;
        this.vars = ctx.vars;
    }

    private void checkConflicts(String symbol) {

        if (ctx.generatedFunctions.containsKey(symbol)) {
            throw new IllegalArgumentException(
                "extern '" + symbol +
                "' entra en conflicto con una función local"
            );
        }

        if (ctx.generatedLabels.containsKey(symbol)) {
            throw new IllegalArgumentException(
                "extern '" + symbol +
                "' entra en conflicto con un label global"
            );
        }

        if (ctx.globalStrings.containsKey(symbol)) {
            throw new IllegalArgumentException(
                "extern '" + symbol +
                "' entra en conflicto con un string global"
            );
        }
    }

    private List<String> cleanParams(
        String owner,
        String symbol,
        String[] parameters
    ) {

        List<String> params = new ArrayList<>();

        if (parameters != null) {

            for (String parameter : parameters) {

                if (
                    parameter == null ||
                    parameter.trim().isEmpty()
                ) {
                    throw new IllegalArgumentException(
                        owner + " '" + symbol +
                        "': parámetro vacío"
                    );
                }

                params.add(parameter.trim());
            }
        }

        return params;
    }

    private String cleanReturn(String returnType) {

        return returnType == null ||
               returnType.isEmpty()
            ? "void"
            : returnType.trim();
    }

    public void extern(
        String name,
        String returnType,
        String... parameters
    ) {

        String symbol = cleanSymbol(name);

        if (
            symbol == null ||
            symbol.isEmpty()
        ) {
            throw new IllegalArgumentException(
                "extern: nombre vacío"
            );
        }

        checkConflicts(symbol);

        String ret = cleanReturn(returnType);

        List<String> params =
            cleanParams(
                "extern",
                symbol,
                parameters
            );

        ExternFunction fn =
            new ExternFunction(
                symbol,
                ret,
                params,
                false
            );

        ctx.externFunctions.put(
            symbol,
            fn
        );

        registerExternSymbol(fn);
    }

    public void externVariadic(
        String name,
        String returnType,
        String... fixedParameters
    ) {

        String symbol = cleanSymbol(name);

        if (
            symbol == null ||
            symbol.isEmpty()
        ) {
            throw new IllegalArgumentException(
                "externVariadic: nombre vacío"
            );
        }

        checkConflicts(symbol);

        List<String> params =
            cleanParams(
                "externVariadic",
                symbol,
                fixedParameters
            );

        String ret = cleanReturn(returnType);

        ExternFunction fn =
            new ExternFunction(
                symbol,
                ret,
                params,
                true
            );

        ctx.externFunctions.put(
            symbol,
            fn
        );

        registerExternSymbol(fn);
    }

    public void externExit() {

        extern(
            "exit",
            "void",
            "uint64"
        );
    }

    public boolean hasExtern(String name) {

        if (name == null)
            return false;

        return ctx.externFunctions.containsKey(
            cleanSymbol(name)
        );
    }

    public ExternFunction externFunction(String name) {

        if (name == null)
            return null;

        return ctx.externFunctions.get(
            cleanSymbol(name)
        );
    }

    public Map<String, ExternFunction> externFunctions() {

        return Collections.unmodifiableMap(
            ctx.externFunctions
        );
    }

    public void declare(
        arch.builtin.Parsing.ExternDecl d,
        java.util.Set<String> internalFunctions
    ) {

        if (d == null)
            return;

        String symbol = cleanSymbol(d.name);

        if (symbol == null || symbol.isEmpty())
            return;

        if (hasExtern(symbol))
            return;

        if (internalFunctions != null && internalFunctions.contains(symbol))
            return;

        if (ctx.hasGlobalFunction(symbol))
            return;

        String[] types = new String[d.params.size()];

        for (int i = 0; i < types.length; i++)
            types[i] = d.params.get(i).type;

        if (d.variadic)
            externVariadic(symbol, d.returnType, types);
        else
            extern(symbol, d.returnType, types);
    }

    public void ensureImplicitExtern(
        String functionName
    ) {

        String name =
            cleanSymbol(functionName);

        if (
            name == null ||
            name.isEmpty()
        )
            return;

        if (hasExtern(name))
            return;

        if (ctx.hasGlobalFunction(name))
            return;

if ("exit".equals(name)) {

    extern(
        name,
        "void",
        "uint64"
    );

} else if ("printf".equals(name)) {

    externVariadic(
        name,
        "int32",
        "qword"
    );

} else {

    extern(
        name,
        "qword"
    );
}
    }

    public void registerExternSymbol(
        ExternFunction fn
    ) {

        if (fn == null)
            return;

        if (ctx.symbolTarget == null)
            return;

        if (
            ctx.symbolTarget instanceof JavaElfOBJ
        ) {

            JavaElfOBJ elf =
                (JavaElfOBJ) ctx.symbolTarget;

            JavaElfOBJ.ExternType ret =
                elfExternType(
                    fn.returnType
                );

            JavaElfOBJ.ExternType[] params =
                new JavaElfOBJ.ExternType[
                    fn.parameters.size()
                ];

            for (
                int i = 0;
                i < fn.parameters.size();
                i++
            ) {

                params[i] =
                    elfExternType(
                        fn.parameters.get(i)
                    );
            }

            if (fn.variadic) {

                elf.externVariadic(
                    fn.name,
                    ret,
                    params
                );

            } else {

                elf.externFunc(
                    fn.name,
                    ret,
                    params
                );
            }

            ctx.registeredSymbols.add(
                fn.name
            );

            return;
        }

        if (
            ctx.symbolTarget instanceof JavaWinOBJ
        ) {

            JavaWinOBJ win =
                (JavaWinOBJ) ctx.symbolTarget;

            win.extern(
                fn.name
            );

            ctx.registeredSymbols.add(
                fn.name
            );
        }
    }

    private JavaElfOBJ.ExternType elfExternType(
        String type
    ) {

        if (type == null)
            return JavaElfOBJ.ExternType.PTR;

        String t =
            type.trim().toLowerCase();

        if (t.startsWith("ptr"))
            return JavaElfOBJ.ExternType.PTR;

        switch (t) {

            case "void":
                return JavaElfOBJ.ExternType.VOID;

            case "bool":
            case "boolean":
                return JavaElfOBJ.ExternType.BOOL;

            case "byte":
            case "int8":
                return JavaElfOBJ.ExternType.BYTE;

            case "ubyte":
            case "uint8":
                return JavaElfOBJ.ExternType.UBYTE;

            case "short":
            case "int16":
                return JavaElfOBJ.ExternType.SHORT;

            case "ushort":
            case "uint16":
                return JavaElfOBJ.ExternType.USHORT;

            case "int":
            case "int32":
                return JavaElfOBJ.ExternType.INT;

            case "uint":
            case "uint32":
            case "dword":
                return JavaElfOBJ.ExternType.UINT;

            case "word":
                return JavaElfOBJ.ExternType.USHORT;

            case "long":
            case "int64":
                return JavaElfOBJ.ExternType.LONG;

            case "ulong":
            case "uint64":
            case "qword":
                return JavaElfOBJ.ExternType.ULONG;

            case "float":
            case "float32":
                return JavaElfOBJ.ExternType.FLOAT;

            case "double":
            case "float64":
                return JavaElfOBJ.ExternType.DOUBLE;

            case "string":
            case "cstring":
            case "char*":
            case "char *":
                return JavaElfOBJ.ExternType.STRING;

            default:
                throw new IllegalArgumentException(
                    "Tipo extern desconocido: " +
                    type
                );
        }
    }

    private boolean isFloatType(int type) {

        return type == Vars.FLOAT32 ||
               type == Vars.FLOAT64;
    }
private int argumentType(
    ExternFunction ext,
    int index,
    Object argument
) {

    int fixed =
        ext.parameters.size();

    if (index < fixed) {

        String typeName =
            ext.parameters.get(index);

        return typeOf(typeName);
    }

    if (argument instanceof Double)
        return Vars.FLOAT64;

    if (argument instanceof Float)
        return Vars.FLOAT32;

    if (argument != null) {

        String name =
            cleanSymbol(
                String.valueOf(argument)
            );

        if (
            name != null &&
            !name.isEmpty()
        ) {

            if (vars.exists(name)) {

                int type =
                    vars.TypeOf(name);

                if (
                    type == Vars.FLOAT32 ||
                    type == Vars.FLOAT64
                ) {
                    return type;
                }
            }

            if (ctx.hasType(name)) {

                int type =
                    ctx.varsTypeOf(name);

                if (
                    type == Vars.FLOAT32 ||
                    type == Vars.FLOAT64
                ) {
                    return type;
                }
            }
        }
    }

    return Vars.QWORD;
}

    private void loadFloatingConstant(
        String temporary,
        int type,
        Number value,
        String register
    ) {

        if (
            type != Vars.FLOAT32 &&
            type != Vars.FLOAT64
        ) {

            throw new IllegalArgumentException(
                "loadFloatingConstant requiere FLOAT32/FLOAT64"
            );
        }

        if (
            !ctx.localVariables.contains(temporary) &&
            !vars.exists(temporary)
        ) {

            if (type == Vars.FLOAT32) {

                ctx.declareLocal(
                    temporary,
                    Vars.FLOAT32,
                    value.floatValue()
                );

            } else {

                ctx.declareLocal(
                    temporary,
                    Vars.FLOAT64,
                    value.doubleValue()
                );
            }

        } else {

            ctx.localVariables.add(
                temporary
            );
        }

        if (
            !ctx.bridge.loadFloat(
                temporary,
                register
            )
        ) {

            throw new IllegalStateException(
                "No se pudo cargar constante floating " +
                temporary +
                " en " +
                register
            );
        }
    }

    private void loadIntegerConstant(
        String temporary,
        int type,
        Number value,
        String register
    ) {

        if (
            !ctx.localVariables.contains(temporary) &&
            !vars.exists(temporary)
        ) {

            ctx.declareLocal(
                temporary,
                type,
                value.longValue()
            );

        } else {

            ctx.localVariables.add(
                temporary
            );
        }

        if (
            !ctx.bridge.loadLocal(
                temporary,
                register
            )
        ) {

            throw new IllegalStateException(
                "No se pudo cargar constante entera " +
                temporary +
                " en " +
                register
            );
        }
    }

    public void emitExternalCall(
        ExternFunction ext,
        String destination,
        Object[] arguments
    ) {

        if (ext == null) {

            throw new IllegalArgumentException(
                "Extern null"
            );
        }

        String name =
            cleanSymbol(ext.name);

        if (
            name == null ||
            name.isEmpty()
        ) {

            throw new IllegalArgumentException(
                "Extern sin nombre"
            );
        }

        final boolean win =
            ctx.symbolTarget instanceof JavaWinOBJ;

        if (
            !(ctx.symbolTarget instanceof JavaElfOBJ) &&
            !win
        ) {

            throw new IllegalStateException(
                "Llamada a extern '" +
                name +
                "' requiere JavaElfOBJ"
            );
        }

        if (arguments == null)
            arguments = new Object[0];

        int fixed =
            ext.parameters.size();

        if (
            !ext.variadic &&
            fixed != 0 &&
            arguments.length != fixed
        ) {

            throw new IllegalArgumentException(
                "El extern '" +
                name +
                "' requiere " +
                fixed +
                " argumentos, recibidos " +
                arguments.length
            );
        }

        int count =
            ext.variadic
                ? Math.max(
                    fixed,
                    arguments.length
                )
                : arguments.length;

        final int maxInt = win ? 4 : ctx.bridge.abiIntegerRegisterCount();
        final int maxFloat = win ? 4 : 8;

        final boolean[] onStack = new boolean[count];
        final int[] argTypes = new int[count];

        int integerCount = 0;
        int floatCount = 0;
        int stackCount = 0;

        for (
            int i = 0;
            i < count;
            i++
        ) {

            Object arg =
                arguments[i];

            int type =
                argumentType(
                    ext,
                    i,
                    arg
                );

            argTypes[i] = type;

            if (win) {

                if (i >= 4) {
                    onStack[i] = true;
                    stackCount++;
                }

            } else if (isFloatType(type)) {

                if (floatCount < maxFloat)
                    floatCount++;
                else {
                    onStack[i] = true;
                    stackCount++;
                }

            } else {

                if (integerCount < maxInt)
                    integerCount++;
                else {
                    onStack[i] = true;
                    stackCount++;
                }
            }
        }

        final String retDst =
            cleanSymbol(destination);

        final boolean hasRet =
            !"void".equalsIgnoreCase(ext.returnType) &&
            retDst != null &&
            !retDst.isEmpty();

        final int retType =
            hasRet ? ctx.varsTypeFor(ext.returnType) : 0;

        if (hasRet)
            ctx.ensureTemporary(retDst, retType);

        int stackBytes = 0;

        if (stackCount > 0) {

            if ((stackCount & 1) != 0) {
                ctx.bridge.adjustRsp(-8);
                stackBytes += 8;
            }

            for (
                int i = count - 1;
                i >= 0;
                i--
            ) {

                if (!onStack[i])
                    continue;

                pushStackArgument(
                    name,
                    i,
                    arguments[i],
                    argTypes[i]
                );

                stackBytes += 8;
            }
        }

        if (win) {

            ctx.bridge.adjustRsp(-32);
            stackBytes += 32;
        }

        int integerIndex = 0;
        int floatIndex = 0;

        for (
            int i = 0;
            i < count;
            i++
        ) {

            if (onStack[i])
                continue;

            Object arg =
                arguments[i];

            int type =
                argTypes[i];

            if (isFloatType(type)) {

                loadFloatArgument(
                    name,
                    i,
                    arg,
                    type,
                    "XMM" + (win ? i : floatIndex)
                );

                floatIndex++;

            } else {

                int regType =
                    type < Vars.DWORD
                        ? Vars.DWORD
                        : type;

                loadIntegerArgument(
                    name,
                    i,
                    arg,
                    win
                        ? winAbiRegister(i, regType)
                        : ctx.bridge.abiRegister(
                            integerIndex,
                            regType
                        )
                );

                integerIndex++;
            }
        }

        if (win) {

        } else if (ext.variadic) {

            ctx.bridge.movImm(
                "EAX",
                (long) floatCount
            );

        } else {

            ctx.bridge.movImm(
                "EAX",
                0L
            );
        }

        if (win) {

            ((JavaWinOBJ) ctx.symbolTarget).callExtern(name);

        } else {

            ((JavaElfOBJ) ctx.symbolTarget).callExtern(name);
        }

        if (stackBytes > 0)
            ctx.bridge.adjustRsp(stackBytes);

        if (
            "void".equalsIgnoreCase(
                ext.returnType
            )
        ) {

            return;
        }

        String dst =
            cleanSymbol(destination);

        if (
            dst == null ||
            dst.isEmpty()
        ) {

            return;
        }

        int returnType = retType;

        ctx.clearConstant(dst);

        if (isFloatType(returnType)) {

            vars.Set(
                dst,
                "XMM0"
            );

        } else {

            if (
                !ctx.bridge.storeLocalFromRax(
                    dst
                )
            ) {

                throw new IllegalStateException(
                    "No se pudo crear el destino del retorno extern: " +
                    dst
                );
            }
        }
    }

    private static String winAbiRegister(
        int index,
        int type
    ) {

        String[][] t = {
            {"RCX", "ECX", "CX",  "CL"},
            {"RDX", "EDX", "DX",  "DL"},
            {"R8",  "R8D", "R8W", "R8B"},
            {"R9",  "R9D", "R9W", "R9B"}
        };

        int w =
            type >= Vars.QWORD ? 0 :
            type >= Vars.DWORD ? 1 :
            type >= Vars.WORD  ? 2 : 3;

        return t[index][w];
    }

    private void loadIntegerArgument(
        String name,
        int i,
        Object arg,
        String register
    ) {

        if (arg instanceof Number) {

            ctx.bridge.movImm(
                register,
                ((Number) arg).longValue()
            );

            return;
        }

        if (arg == null) {

            throw new IllegalArgumentException(
                "Argumento null para extern '" +
                name +
                "'"
            );
        }

        String argName =
            cleanSymbol(
                String.valueOf(arg)
            );

        if (ctx.hasGlobalString(argName)) {

            loadSymbolAddress(
                "__strptr_" + i,
                argName,
                wide(register)
            );

            return;
        }

        if (
            ctx.hasGlobalLabel(argName) &&
            !ctx.localVariables.contains(argName)
        ) {

            loadSymbolAddress(
                "__ptr_" + i,
                argName,
                wide(register)
            );

            return;
        }

        if (ctx.bridge.loadLocal(argName, register))
            return;

        if (ctx.bridge.loadParameter(argName, register))
            return;

        if (ctx.knownConstants.contains(argName)) {

            Object value =
                ctx.constantValues.get(argName);

            if (!(value instanceof Number)) {

                throw new IllegalArgumentException(
                    "Constante numérica inválida: " +
                    argName
                );
            }

            ctx.bridge.movImm(
                register,
                ((Number) value).longValue()
            );

            return;
        }

        throw new IllegalArgumentException(
            "Argumento extern no encontrado: " +
            argName
        );
    }

    private static String wide(String reg) {

        switch (reg.toUpperCase()) {
            case "EAX": case "AX": case "AL": return "RAX";
            case "ECX": case "CX": case "CL": return "RCX";
            case "EDX": case "DX": case "DL": return "RDX";
            case "EBX": case "BX": case "BL": return "RBX";
            case "ESI": case "SI": case "SIL": return "RSI";
            case "EDI": case "DI": case "DIL": return "RDI";
            case "R8D": case "R8W": case "R8B": return "R8";
            case "R9D": case "R9W": case "R9B": return "R9";
            case "R10D": case "R10W": case "R10B": return "R10";
            case "R11D": case "R11W": case "R11B": return "R11";
            default: return reg;
        }
    }

    private void loadFloatArgument(
        String name,
        int i,
        Object arg,
        int type,
        String register
    ) {

        if (arg instanceof Number) {

            loadFloatingConstant(
                "__extern_fconst_" + i,
                type,
                (Number) arg,
                register
            );

            return;
        }

        if (arg == null) {

            throw new IllegalArgumentException(
                "Argumento null para extern '" +
                name +
                "'"
            );
        }

        String argName =
            cleanSymbol(
                String.valueOf(arg)
            );

        if (ctx.bridge.loadFloat(argName, register))
            return;

        if (ctx.bridge.loadFloatParameter(argName, register))
            return;

        if (ctx.knownConstants.contains(argName)) {

            Object value =
                ctx.constantValues.get(argName);

            if (!(value instanceof Number)) {

                throw new IllegalArgumentException(
                    "Constante floating inválida: " +
                    argName
                );
            }

            loadFloatingConstant(
                "__extern_const_" + i,
                type,
                (Number) value,
                register
            );

            return;
        }

        throw new IllegalArgumentException(
            "Argumento floating extern no encontrado: " +
            argName
        );
    }

    private void pushStackArgument(
        String name,
        int i,
        Object arg,
        int type
    ) {

        if (isFloatType(type)) {

            loadFloatArgument(name, i, arg, type, "XMM0");

            ctx.bridge.pushXmm0(type == Vars.FLOAT64);

            return;
        }

        loadIntegerArgument(name, i, arg, "RAX");

        ctx.bridge.pushRax();
    }

    private void loadSymbolAddress(
        String pointer,
        String symbol,
        String register
    ) {

        if (
            !ctx.localVariables.contains(pointer) &&
            !vars.exists(pointer)
        ) {

            ctx.declareLocal(
                pointer,
                Vars.QWORD,
                0L
            );

        } else {

            ctx.localVariables.add(
                pointer
            );
        }

        vars.AddressOfSymbol(
            pointer,
            symbol
        );

        ctx.bridge.loadLocal(
            pointer,
            register
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
