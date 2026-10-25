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

package arch.builtin;

final class VarsFunctions {

    private final Vars core;

    VarsFunctions(Vars core) {
        if (core == null) {
            throw new IllegalArgumentException(
                "VarsFunctions: core no puede ser null"
            );
        }

        this.core = core;
    }

    public void Function(
        String name,
        int returnType,
        Vars.Parameter... params
    ) {

        Vars.checkType(returnType);
        core.validateName(name);

        if (core.currentFunction != null) {
            throw new IllegalStateException(
                "Ya existe una función abierta: " +
                core.currentFunction.name
            );
        }

        if (core.variables.containsKey(name)) {
            throw new IllegalArgumentException(
                "El nombre '" + name +
                "' ya pertenece a una variable"
            );
        }

        if (core.functionTable.containsKey(name)) {
            throw new IllegalArgumentException(
                "Duplicated Function : " + name
            );
        }

        Vars.Function f =
            new Vars.Function(
                name,
                returnType
            );

        if (params != null) {

            for (Vars.Parameter p : params) {

                if (p == null) {
                    throw new IllegalArgumentException(
                        "Parámetro null"
                    );
                }

                core.validateName(p.name);

                for (Vars.Parameter old : f.parameters) {

                    if (old.name.equals(p.name)) {
                        throw new IllegalArgumentException(
                            "Parámetro duplicado: " +
                            p.name
                        );
                    }
                }

                if (f.locals.containsKey(p.name)) {
                    throw new IllegalArgumentException(
                        "Colisión de parámetro: " +
                        p.name
                    );
                }

                f.parameters.add(p);
            }
        }

        allocateParameters(f);

        int functionAddress =
            core.builder.markLabel();

        core.functions.defineFunctionAt(
            name,
            functionAddress
        );

        core.functions.EXPORT_FUNCTION(name);

        core.functionTable.put(name, f);

        core.currentFunction = f;

        core.nextStackOffset = Vars.FIRST_STACK_OFFSET;
        core.stackFrameStarted = false;
        core.framePatch = -1;

        if (core.wantFrameAtEntry())
            core.startStackFrame();

        Vars.frameUsed = false;

        if (Vars.msAbiFunction) {
            core.startStackFrame();
            Vars.noteFrameUse();
            core.msAbiRsiSlot = core.nextStackOffset;
            core.nextStackOffset += 8;
            core.msAbiRdiSlot = core.nextStackOffset;
            core.nextStackOffset += 8;
            core.storeIntegerMemory("[RBP-" + core.msAbiRsiSlot + "]", "RSI", 64);
            core.storeIntegerMemory("[RBP-" + core.msAbiRdiSlot + "]", "RDI", 64);
        }

        emitParameterEntry(f);
    }

    public void DeclareNaked(
        String name,
        int returnType,
        Vars.Parameter... params
    ) {

        Vars.checkType(returnType);
        core.validateName(name);

        if (core.functionTable.containsKey(name))
            throw new IllegalArgumentException("Duplicated Function : " + name);

        Vars.Function f = new Vars.Function(name, returnType);

        if (params != null)
            for (Vars.Parameter p : params)
                f.parameters.add(p);

        allocateParameters(f);

        core.functions.defineFunctionAt(name, core.builder.markLabel());
        core.functions.EXPORT_FUNCTION(name);
        core.functionTable.put(name, f);
    }

    public void GlobalFunction(
        String name,
        int returnType,
        Vars.Parameter... params
    ) {

        Function(
            name,
            returnType,
            params
        );
    }

    private void allocateParameters(Vars.Function f) {

        if (Vars.msAbiFunction) {
            allocateParametersMs(f);
            return;
        }

        int integerIndex = 0;
        int floatIndex = 0;

        for (Vars.Parameter p : f.parameters) {

            if (p.isInteger()) {

                if (
                    integerIndex <
                    Vars.ABI_INTEGER_REGISTERS.length
                ) {

                    p.integerRegister =
                        Vars.ABI_INTEGER_REGISTERS[
                            integerIndex++
                        ];

                } else {

                    p.stackOffset =
                        16 +
                        (
                            (
                                integerIndex -
                                Vars.ABI_INTEGER_REGISTERS.length
                            ) * 8
                        );

                    integerIndex++;
                }

            } else {

                if (
                    floatIndex <
                    Vars.ABI_FLOAT_REGISTERS.length
                ) {

                    p.xmmRegister =
                        Vars.ABI_FLOAT_REGISTERS[
                            floatIndex++
                        ];

                } else {

                    p.stackOffset =
                        16 +
                        (
                            (
                                floatIndex -
                                Vars.ABI_FLOAT_REGISTERS.length
                            ) * 8
                        );

                    floatIndex++;
                }
            }
        }
    }

    private void allocateParametersMs(Vars.Function f) {

        final int[] INT = {1, 2, 8, 9};
        int pos = 0;

        for (Vars.Parameter p : f.parameters) {

            if (pos < 4) {
                if (p.isInteger()) p.integerRegister = INT[pos];
                else p.xmmRegister = pos;
            } else {
                p.stackOffset = 16 + 32 + (pos - 4) * 8;
            }

            pos++;
        }
    }

    private void emitParameterEntry(Vars.Function f) {

        for (Vars.Parameter p : f.parameters) {

            boolean isInt = p.integerRegister >= 0;
            boolean isXmm = p.xmmRegister >= 0;

            if (!isInt && !isXmm)
                continue;

            if (Vars.leafFunction && isInt && (
                    p.integerRegister == 7 || p.integerRegister == 6 ||
                    p.integerRegister == 8 || p.integerRegister == 9))
                continue;

            Vars.noteFrameUse();

            int slot = core.nextStackOffset;
            core.nextStackOffset += 8;

            String memory = "[RBP-" + slot + "]";

            if (isInt) {

                core.storeIntegerMemory(
                    memory,
                    core.registerName(p.integerRegister, p.bits()),
                    p.bits()
                );

                p.integerRegister = -1;

            } else {

                core.emitSseStore(memory, p.xmmRegister, p.type);

                p.xmmRegister = -1;
            }

            p.stackOffset = -slot;
        }
    }

    public void EndFunction() {

        if (core.currentFunction == null) {
            throw new IllegalStateException(
                "No hay función abierta"
            );
        }

        core.currentFunction = null;
    }

    public void Return() {

        requireReturn();

        int type =
            core.currentFunction.returnType;

        if (
            type != Vars.BYTE &&
            type != Vars.WORD &&
            type != Vars.DWORD &&
            type != Vars.QWORD &&
            type != Vars.FLOAT32 &&
            type != Vars.FLOAT64
        ) {

            throw new IllegalStateException(
                "Tipo de retorno inválido"
            );
        }

        core.emitRet();

        core.currentFunction.returning = true;
    }

private void emitFloat64RawBitsReturn(
    long bits
) {

    core.mov.MOV(
        "RAX",
        bits
    );

    core.emitMovQ_XmmFromRax(0);
}

private void emitFloat32RawBitsReturn(
    int bits
) {

    core.mov.MOV(
        "EAX",
        bits & 0xFFFFFFFFL
    );

    core.emitMovD_XmmFromEax(0);
}

public void Return(long value) {

    requireReturn();

    int type =
        core.currentFunction.returnType;

    if (type == Vars.FLOAT64) {

        emitFloat64RawBitsReturn(value);

        core.emitRet();

        core.currentFunction.returning = true;

        return;
    }

    if (type == Vars.FLOAT32) {

        emitFloat32RawBitsReturn(
            (int)value
        );

        core.emitRet();

        core.currentFunction.returning = true;

        return;
    }

    if (!Vars.isIntegerType(type)) {

        throw new IllegalArgumentException(
            "La función no devuelve entero"
        );
    }

    emitIntegerReturn(
        type,
        value
    );

    core.emitRet();

    core.currentFunction.returning = true;
}

    public void Return(float value) {

        requireReturn();

        if (
            core.currentFunction.returnType !=
            Vars.FLOAT32
        ) {

            throw new IllegalArgumentException(
                "La función requiere FLOAT32"
            );
        }

        emitFloatReturn(
            Vars.FLOAT32,
            value
        );

        core.emitRet();

        core.currentFunction.returning = true;
    }

    public void Return(double value) {

        requireReturn();

        if (
            core.currentFunction.returnType !=
            Vars.FLOAT64
        ) {

            throw new IllegalArgumentException(
                "La función requiere FLOAT64"
            );
        }

        emitFloatReturn(
            Vars.FLOAT64,
            value
        );

        core.emitRet();

        core.currentFunction.returning = true;
    }

    public void Return(String symbol) {

        requireReturn();

        Vars.Variable v =
            core.localVariable(symbol);

        if (v != null) {

            if (!returnCompatible(v)) {

                throw new IllegalArgumentException(
                    "Tipo de retorno incorrecto: " +
                    Vars.typeName(v.type) +
                    " != " +
                    Vars.typeName(
                        core.currentFunction.returnType
                    )
                );
            }

            if (v.isInteger()) {

                String returnRegister;

                switch (v.bits()) {

                    case 8:
                        returnRegister = "AL";
                        break;

                    case 16:
                        returnRegister = "AX";
                        break;

                    case 32:
                        returnRegister = "EAX";
                        break;

                    case 64:
                        returnRegister = "RAX";
                        break;

                    default:
                        throw new IllegalArgumentException(
                            "Bits inválidos"
                        );
                }

                if (v.isRegister()) {

                    String variableRegister =
                        core.registerName(
                            v.register,
                            v.bits()
                        );

                    if (
                        !variableRegister.equalsIgnoreCase(
                            returnRegister
                        )
                    ) {

                        core.mov.MOV(
                            returnRegister,
                            variableRegister
                        );
                    }

                } else if (v.constant) {

                    emitIntegerReturn(
                        v.type,
                        v.integerValue
                    );

                } else {

                    core.loadInteger(
                        v,
                        returnRegister
                    );
                }

            } else if (
                v.type == Vars.FLOAT32 ||
                v.type == Vars.FLOAT64
            ) {

                core.loadFloat(
                    v,
                    "XMM0"
                );

            } else {

                throw new IllegalArgumentException(
                    "Tipo de retorno no soportado"
                );
            }

            core.emitRet();

            core.currentFunction.returning = true;

            return;
        }

        Vars.Parameter p =
            core.parameter(symbol);

        if (p != null) {

            returnParameter(p);

            core.emitRet();

            core.currentFunction.returning = true;

            return;
        }

        if (
            Vars.isFloatType(
                core.currentFunction.returnType
            )
        ) {

            Vars.Variable tmp =
                new Vars.Variable(
                    "$return",
                    core.currentFunction.returnType,
                    false
                );

            core.expr.setFromExpression(
                tmp,
                symbol
            );

            core.loadFloat(
                tmp,
                "XMM0"
            );

        } else {

            core.expr.expressionIntegerToRAX(
                symbol
            );

            emitIntegerReturnRegister(
                core.currentFunction.returnType,
                "RAX"
            );
        }

        core.emitRet();

        core.currentFunction.returning = true;
    }

    private void requireReturn() {

        if (core.currentFunction == null) {
            throw new IllegalStateException(
                "RETURN fuera de función"
            );
        }
    }

    private void emitIntegerReturn(
        int type,
        long value
    ) {

        if (value == 0) {

            switch (type) {

                case Vars.BYTE:

                    core.builder.file_write(
                        0x31,
                        0xC0
                    );

                    return;

                case Vars.WORD:

                    core.builder.file_write(
                        0x66,
                        0x31,
                        0xC0
                    );

                    return;

                case Vars.DWORD:

                    core.builder.file_write(
                        0x31,
                        0xC0
                    );

                    return;

                case Vars.QWORD:

                    core.builder.file_write(
                        0x48,
                        0x31,
                        0xC0
                    );

                    return;

                default:
                    break;
            }
        }

        switch (type) {

            case Vars.BYTE:

                core.mov.MOV(
                    "AL",
                    value
                );

                break;

            case Vars.WORD:

                core.mov.MOV(
                    "AX",
                    value
                );

                break;

            case Vars.DWORD:

                core.mov.MOV(
                    "EAX",
                    value
                );

                break;

            case Vars.QWORD:

                core.mov.MOV(
                    "RAX",
                    value
                );

                break;

            default:

                throw new IllegalArgumentException(
                    "Retorno entero inválido"
                );
        }
    }

    private void emitIntegerReturnRegister(
        int type,
        String source
    ) {

        String destination;

        switch (type) {

            case Vars.BYTE:
                destination = "AL";
                break;

            case Vars.WORD:
                destination = "AX";
                break;

            case Vars.DWORD:
                destination = "EAX";
                break;

            case Vars.QWORD:
                destination = "RAX";
                break;

            default:

                throw new IllegalArgumentException(
                    "Tipo entero inválido"
                );
        }

        if (
            !destination.equalsIgnoreCase(source)
        ) {

            core.mov.MOV(
                destination,
                source
            );
        }
    }

    private void emitFloatReturn(
        int type,
        double value
    ) {

        if (type == Vars.FLOAT32) {

            int bits =
                Float.floatToRawIntBits(
                    (float)value
                );

            core.mov.MOV(
                "EAX",
                bits & 0xFFFFFFFFL
            );

            core.emitMovD_XmmFromEax(0);

            return;
        }

        long bits =
            Double.doubleToRawLongBits(value);

        core.mov.MOV(
            "RAX",
            bits
        );

        core.emitMovQ_XmmFromRax(0);
    }

    private void returnParameter(
        Vars.Parameter p
    ) {

        if (
            p.type !=
            core.currentFunction.returnType
        ) {

            throw new IllegalArgumentException(
                "Tipo del parámetro '" +
                p.name +
                "' incompatible con retorno"
            );
        }

        if (p.isInteger()) {

            String destination;

            switch (p.bits()) {

                case 8:
                    destination = "AL";
                    break;

                case 16:
                    destination = "AX";
                    break;

                case 32:
                    destination = "EAX";
                    break;

                case 64:
                    destination = "RAX";
                    break;

                default:

                    throw new IllegalArgumentException(
                        "Bits inválidos"
                    );
            }

            core.loadParameterInteger(
                p,
                destination
            );

            return;
        }

        core.loadParameterFloat(
            p,
            "XMM0"
        );
    }

    public void Call(
        String functionName,
        Object... arguments
    ) {

        core.validateName(functionName);

        Vars.Function f =
            core.functionTable.get(functionName);

        if (f == null) {
            throw new IllegalArgumentException(
                "Función no existe: " +
                functionName
            );
        }

        if (arguments == null) {
            arguments = new Object[0];
        }

        if (
            arguments.length !=
            f.parameters.size()
        ) {

            throw new IllegalArgumentException(
                "La función '" +
                functionName +
                "' requiere " +
                f.parameters.size() +
                " argumentos, recibidos " +
                arguments.length
            );
        }

        int integerIndex = 0;
        int floatIndex = 0;

        java.util.List<Integer> onStack = new java.util.ArrayList<>();
        {
            int n = 0;
            for (int i = 0; i < arguments.length; i++) {
                Vars.Parameter q = f.parameters.get(i);
                if (q.isInteger()) {
                    if (n >= Vars.ABI_INTEGER_REGISTERS.length)
                        onStack.add(i);
                    n++;
                }
            }
        }

        int stackBytes = 0;

        if (!onStack.isEmpty()) {

            if ((onStack.size() & 1) != 0) {
                core.alu.SUB("RSP", 8);
                stackBytes += 8;
            }

            for (int j = onStack.size() - 1; j >= 0; j--) {

                int idx = onStack.get(j);
                Vars.Parameter q = f.parameters.get(idx);

                emitCallIntegerArgument(
                    q.type,
                    arguments[idx],
                    core.temporaryInteger(q.bits())
                );

                core.alu.PUSH("RAX");
                stackBytes += 8;
            }
        }

        for (
            int i = 0;
            i < arguments.length;
            i++
        ) {

            Vars.Parameter p =
                f.parameters.get(i);

            Object arg =
                arguments[i];

            if (p.isInteger()) {

                if (
                    integerIndex >=
                    Vars.ABI_INTEGER_REGISTERS.length
                ) {
                    integerIndex++;
                    continue;
                }

                int reg =
                    Vars.ABI_INTEGER_REGISTERS[
                        integerIndex++
                    ];

                String dst =
                    core.registerName(
                        reg,
                        p.bits()
                    );

                emitCallIntegerArgument(
                    p.type,
                    arg,
                    dst
                );

            } else {

                if (
                    floatIndex >=
                    Vars.ABI_FLOAT_REGISTERS.length
                ) {

                    throw new IllegalArgumentException(
                        "Demasiados argumentos floating"
                    );
                }

                int xmm =
                    Vars.ABI_FLOAT_REGISTERS[
                        floatIndex++
                    ];

                emitCallFloatArgument(
                    p.type,
                    arg,
                    xmm
                );
            }
        }

        Vars.noteFrameUse();

        core.functions.CALL_FUNCTION(
            functionName
        );

        if (stackBytes > 0)
            core.alu.ADD("RSP", stackBytes);
    }

    private void emitCallIntegerArgument(
        int type,
        Object arg,
        String destination
    ) {

        if (
            arg instanceof Byte ||
            arg instanceof Short ||
            arg instanceof Integer ||
            arg instanceof Long
        ) {

            Vars.checkIntegerRange(
                type,
                ((Number)arg).longValue()
            );

            core.mov.MOV(
                destination,
                ((Number)arg).longValue()
            );

            return;
        }

        if (!(arg instanceof String)) {

            throw new IllegalArgumentException(
                "Argumento entero inválido: " +
                arg
            );
        }

        String name =
            (String)arg;

        Vars.Variable v =
            core.localVariable(name);

        if (v != null) {

            if (!v.isInteger()) {

                throw new IllegalArgumentException(
                    "El argumento '" +
                    name +
                    "' no es entero"
                );
            }

            core.loadInteger(
                v,
                destination
            );

            return;
        }

        Vars.Parameter p =
            core.parameter(name);

        if (p != null) {

            if (!p.isInteger()) {

                throw new IllegalArgumentException(
                    "El parámetro '" +
                    name +
                    "' no es entero"
                );
            }

            core.loadParameterInteger(
                p,
                destination
            );

            return;
        }

        throw new IllegalArgumentException(
            "Variable/parámetro no existe: " +
            name
        );
    }

    private void emitCallFloatArgument(
        int type,
        Object arg,
        int xmm
    ) {

        if (
            arg instanceof Float ||
            arg instanceof Double
        ) {

            double value =
                ((Number)arg).doubleValue();

            if (type == Vars.FLOAT32) {

                int bits =
                    Float.floatToRawIntBits(
                        (float)value
                    );

                core.mov.MOV(
                    "EAX",
                    bits & 0xFFFFFFFFL
                );

                core.emitMovD_XmmFromEax(xmm);

            } else {

                long bits =
                    Double.doubleToRawLongBits(value);

                core.mov.MOV(
                    "RAX",
                    bits
                );

                core.emitMovQ_XmmFromRax(xmm);
            }

            return;
        }

        if (!(arg instanceof String)) {

            throw new IllegalArgumentException(
                "Argumento floating inválido"
            );
        }

        String name =
            (String)arg;

        Vars.Variable v =
            core.localVariable(name);

        if (v != null) {

            if (!v.isFloat()) {

                throw new IllegalArgumentException(
                    "El argumento '" +
                    name +
                    "' no es floating"
                );
            }

            if (v.type != type) {

                throw new IllegalArgumentException(
                    "Tipo floating incorrecto"
                );
            }

            if (v.isXMM()) {

                core.emitSseMoveRegReg(
                    xmm,
                    v.xmm,
                    v.type
                );

            } else {

                core.emitSseLoad(
                    xmm,
                    core.memoryOf(v),
                    v.type
                );
            }

            return;
        }

        Vars.Parameter p =
            core.parameter(name);

        if (p != null) {

            if (!p.isFloat()) {

                throw new IllegalArgumentException(
                    "El parámetro no es floating"
                );
            }

            if (p.type != type) {

                throw new IllegalArgumentException(
                    "Tipo floating incorrecto"
                );
            }

            core.loadParameterFloat(
                p,
                core.xmmName(xmm)
            );

            return;
        }

        throw new IllegalArgumentException(
            "Variable/parámetro no existe: " +
            name
        );
    }

    public String ReturnRegister() {

        if (core.currentFunction == null) {

            throw new IllegalStateException(
                "No hay función"
            );
        }

        int type =
            core.currentFunction.returnType;

        if (type == Vars.BYTE)
            return "AL";

        if (type == Vars.WORD)
            return "AX";

        if (type == Vars.DWORD)
            return "EAX";

        if (type == Vars.QWORD)
            return "RAX";

        if (
            type == Vars.FLOAT32 ||
            type == Vars.FLOAT64
        ) {
            return "XMM0";
        }

        throw new IllegalStateException(
            "Tipo de retorno inválido: " +
            Vars.typeName(type)
        );
    }

    private static String intRegisterByBits(int bits) {

        switch (bits) {
            case 8:  return "AL";
            case 16: return "AX";
            case 32: return "EAX";
            default: return "RAX";
        }
    }

    public void Call(
        String functionName,
        String destination,
        Object... arguments
    ) {

        core.validateName(functionName);

        if (destination == null) {

            throw new IllegalArgumentException(
                "Variable de destino null"
            );
        }

        core.validateName(destination);

        Vars.Function f =
            core.functionTable.get(functionName);

        if (f == null) {

            throw new IllegalArgumentException(
                "Función no existe: " +
                functionName
            );
        }

        Vars.Variable dst =
            core.variable(destination);

        if (dst == null) {

            throw new IllegalArgumentException(
                "Variable de destino no existe: " +
                destination
            );
        }

        boolean intWidthMismatch =
            dst.type != f.returnType &&
            Vars.isIntegerType(dst.type) &&
            Vars.isIntegerType(f.returnType);

        if (
            dst.type !=
            f.returnType &&
            !intWidthMismatch
        ) {

            throw new IllegalArgumentException(
                "El retorno de '" +
                functionName +
                "' no coincide con '" +
                destination +
                "': " +
                Vars.typeName(
                    f.returnType
                ) +
                " -> " +
                Vars.typeName(
                    dst.type
                )
            );
        }

        Call(
            functionName,
            arguments
        );

        if (
            Vars.isIntegerType(
                f.returnType
            )
        ) {

            String source;

            switch (f.returnType) {

                case Vars.BYTE:
                    source = "AL";
                    break;

                case Vars.WORD:
                    source = "AX";
                    break;

                case Vars.DWORD:
                    source = "EAX";
                    break;

                case Vars.QWORD:
                    source = "RAX";
                    break;

                default:

                    throw new IllegalArgumentException(
                        "Tipo entero de retorno inválido: " +
                        Vars.typeName(
                            f.returnType
                        )
                    );
            }

            if (intWidthMismatch) {

                int db = dst.bits();

                if (db < f.returnType) {

                    source = intRegisterByBits(db);

                } else {

                    String wide = intRegisterByBits(db);

                    core.mov.MOV(
                        wide,
                        source
                    );

                    source = wide;
                }
            }

            core.storeInteger(
                dst,
                source
            );

            return;
        }

        if (
            f.returnType == Vars.FLOAT32 ||
            f.returnType == Vars.FLOAT64
        ) {

            core.storeFloat(
                dst,
                "XMM0"
            );

            return;
        }

        throw new IllegalArgumentException(
            "Tipo de retorno no soportado: " +
            Vars.typeName(
                f.returnType
            )
        );
    }

    public int GlobalLabel(String name) {

        core.validateName(name);

        return core.builder.markLabel();
    }

    public int GlobalLabel(
        String name,
        int address
    ) {

        core.validateName(name);

        if (address < 0) {

            throw new IllegalArgumentException(
                "Dirección de label inválida: " +
                address
            );
        }

        return address;
    }

    public int globalLabel(String name) {

        return GlobalLabel(name);
    }

    public int globalLabel(
        String name,
        int address
    ) {

        return GlobalLabel(
            name,
            address
        );
    }

    private boolean returnCompatible(
        Vars.Variable v
    ) {

        int rt =
            core.currentFunction.returnType;

        if (v.type == rt)
            return true;

        if (
            !v.isInteger() ||
            !Vars.isIntegerType(rt)
        )
            return false;

        if (v.bits() >= rt)
            return true;

        return v.bits() == 32 &&
               rt == 64;
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
