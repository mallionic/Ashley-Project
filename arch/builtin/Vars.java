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

import Builder.ash.Generator;
import java.util.*;

import Builder.Builder;
import arch.ALU;
import arch.Registers.Movement;
import arch.Registers.Ptr;
import arch.Func.Functions;

public class Vars {

    public static final int BYTE  = 8;
    public static final int WORD  = 16;
    public static final int DWORD = 32;
    public static final int QWORD = 64;

    public static final int INT8  = BYTE;
    public static final int INT16 = WORD;
    public static final int INT32 = DWORD;
    public static final int INT64 = QWORD;

    public static final int FLOAT32 = 132;
    public static final int FLOAT64 = 164;

    public static final int FLOAT  = FLOAT32;
    public static final int DOUBLE = FLOAT64;

    static final int[] VARIABLE_REGISTERS = {12,13,14,15};

    static final int[] LEAF_VARIABLE_REGISTERS = {10, 11, 8, 9};

    boolean parameterHoldsRegister(int r) {
        if (currentFunction == null) return false;
        for (Parameter p : currentFunction.parameters)
            if (p.integerRegister == r) return true;
        return false;
    }

    static final int[] XMM_VARIABLE_REGISTERS = {
        8, 9, 10, 11, 12, 13, 14, 15
    };

    static final int[] ABI_INTEGER_REGISTERS = {
        7, 6, 2, 1, 8, 9
    };

    static final int[] ABI_FLOAT_REGISTERS = {
        0, 1, 2, 3, 4, 5, 6, 7
    };

    final Builder builder;
    final Movement mov;
    final ALU alu;
    final Ptr ptr;
    final Functions functions;

    final VarsArithmetic arithmetic;
    final VarsExpressions expr;
    final VarsFunctions funcOps;
    final VarsControl control;

    final Map<String, Variable> variables =
        new LinkedHashMap<>();

    final Map<String, Function> functionTable =
        new LinkedHashMap<>();

    Function currentFunction = null;

    final boolean[] registerUsed =
        new boolean[16];

    final boolean[] xmmUsed =
        new boolean[16];

    static final int FIRST_STACK_OFFSET = 8 + 32;

    int nextStackOffset = FIRST_STACK_OFFSET;

    boolean stackFrameStarted = false;

    boolean entryPointFunction = false;

    int expressionStackDepth = 0;

    public static final class Function {

        final String name;
        final int returnType;

        final List<Parameter> parameters =
            new ArrayList<>();

        final Map<String, Variable> locals =
            new LinkedHashMap<>();

        boolean returning;

        Function(
            String name,
            int returnType
        ) {
            this.name = name;
            this.returnType = returnType;
        }
    }

    public static final class Parameter {

        public final String name;
        public final int type;

        int integerRegister = -1;
        int xmmRegister = -1;

        int stackOffset = 0;

        public Parameter(
            String name,
            int type
        ) {
            this.name = name;
            this.type = type;
        }

        public boolean isInteger() {
            return isIntegerType(type);
        }

        public boolean isFloat() {
            return isFloatType(type);
        }

        public int bits() {

            if (type == FLOAT32)
                return 32;

            if (type == FLOAT64)
                return 64;

            return type;
        }
    }

    static final class Variable {

        final String name;
        final int type;
        final boolean constant;

        long integerValue;
        double floatingValue;

        int register = -1;
        int xmm = -1;

        int stackOffset = 0;

        boolean initialized;

        Variable(
            String name,
            int type,
            boolean constant
        ) {
            this.name = name;
            this.type = type;
            this.constant = constant;
        }

        boolean isInteger() {
            return isIntegerType(type);
        }

        boolean isFloat() {
            return isFloatType(type);
        }

        boolean isRegister() {
            return register >= 0;
        }

        boolean isXMM() {
            return xmm >= 0;
        }

        boolean isStack() {
            return stackOffset != 0;
        }

        int bits() {

            if (type == FLOAT32)
                return 32;

            if (type == FLOAT64)
                return 64;

            return type;
        }
    }

    public Vars(Builder builder) {

        if (builder == null)
            throw new IllegalArgumentException(
                "Builder no puede ser null"
            );

        this.builder = builder;

        this.mov = new Movement(builder);
        this.alu = new ALU(builder);
        this.ptr = new Ptr(builder);
        this.functions = new Functions(builder);

        this.arithmetic = new VarsArithmetic(this);
        this.expr = new VarsExpressions(this);
        this.funcOps = new VarsFunctions(this);
        this.control = new VarsControl(this);
    }

    static void checkType(int type) {

        if (type != BYTE &&
            type != WORD &&
            type != DWORD &&
            type != QWORD &&
            type != FLOAT32 &&
            type != FLOAT64) {

            throw new IllegalArgumentException(
                "Tipo no soportado: " + type
            );
        }
    }

    static boolean isIntegerType(int type) {

        return type == BYTE ||
               type == WORD ||
               type == DWORD ||
               type == QWORD;
    }

    static boolean isFloatType(int type) {

        return type == FLOAT32 ||
               type == FLOAT64;
    }

    static String typeName(int type) {

        switch (type) {

            case BYTE:
                return "BYTE";

            case WORD:
                return "WORD";

            case DWORD:
                return "DWORD";

            case QWORD:
                return "QWORD";

            case FLOAT32:
                return "FLOAT32";

            case FLOAT64:
                return "FLOAT64";

            default:
                return "UNKNOWN";
        }
    }

    static void checkIntegerRange(
        int bits,
        long value
    ) {

        if (bits != BYTE &&
            bits != WORD &&
            bits != DWORD) {

            return;
        }

        long signedMin =
            -(1L << (bits - 1));

        long signedMax =
            (1L << (bits - 1)) - 1;

        long unsignedMax =
            (1L << bits) - 1;

        if (value >= signedMin &&
            value <= signedMax)
            return;

        if (value >= 0 &&
            value <= unsignedMax)
            return;

        throw new IllegalArgumentException(
            "El valor " +
            value +
            " no cabe en " +
            bits +
            " bits (rango permitido: " +
            signedMin +
            ".." +
            unsignedMax +
            ")"
        );
    }

    void validateName(String name) {

        if (name == null ||
            name.trim().isEmpty()) {

            throw new IllegalArgumentException(
                "Nombre inválido"
            );
        }

        if (!Character.isLetter(name.charAt(0)) &&
            name.charAt(0) != '_') {

            throw new IllegalArgumentException(
                "Nombre inválido: " + name
            );
        }

        for (int i = 1; i < name.length(); i++) {

            char c = name.charAt(i);

            if (!Character.isLetterOrDigit(c) &&
                c != '_') {

                throw new IllegalArgumentException(
                    "Nombre inválido: " + name
                );
            }
        }
    }

    String cleanName(String name) {

        if (name == null)
            throw new IllegalArgumentException(
                "Nombre null"
            );

        String n =
            name.trim();

        while (
            n.startsWith("$") ||
            n.startsWith("%")
        ) {
            n = n.substring(1);
        }

        if (n.isEmpty())
            throw new IllegalArgumentException(
                "Nombre vacío"
            );

        return n;
    }

    void ensureGlobalNameFree(String name) {

        if (variables.containsKey(name))
            throw new IllegalArgumentException(
                "Ya existe una variable llamada: " +
                name
            );

        if (functionTable.containsKey(name))
            throw new IllegalArgumentException(
                "Ya existe una función llamada: " +
                name
            );
    }

    void ensureLocalNameFree(String name) {

        if (currentFunction == null)
            return;

        if (currentFunction.locals.containsKey(name))
            throw new IllegalArgumentException(
                "Ya existe una variable local llamada: " +
                name
            );

        for (
            Parameter p :
            currentFunction.parameters
        ) {

            if (p.name.equals(name)) {

                throw new IllegalArgumentException(
                    "El nombre '" +
                    name +
                    "' ya pertenece a un parámetro"
                );
            }
        }
    }

    Variable globalVariable(
        String name
    ) {

        String n =
            cleanName(name);

        Variable v =
            variables.get(n);

        if (v == null)
            throw new IllegalArgumentException(
                "Variable no existe: " +
                n
            );

        return v;
    }

    Variable localVariable(
        String name
    ) {

        String n =
            cleanName(name);

        if (currentFunction != null) {

            Variable local =
                currentFunction.locals.get(n);

            if (local != null)
                return local;
        }

        return variables.get(n);
    }

    Parameter parameter(
        String name
    ) {

        String n =
            cleanName(name);

        if (currentFunction == null)
            return null;

        for (
            Parameter p :
            currentFunction.parameters
        ) {

            if (p.name.equals(n))
                return p;
        }

        return null;
    }

    boolean symbolExists(
        String name
    ) {

        return localVariable(name) != null ||
               parameter(name) != null;
    }

    Variable variable(
        String name
    ) {

        String n =
            cleanName(name);

        Variable v =
            localVariable(n);

        if (v != null)
            return v;

        throw new IllegalArgumentException(
            "Variable no existe: " +
            n
        );
    }

    Variable get(
        String name
    ) {
        return variable(name);
    }

    public boolean exists(
        String name
    ) {
        return symbolExists(name);
    }

    public boolean functionExists(
        String name
    ) {
        return functionTable.containsKey(
            cleanName(name)
        );
    }

    public int TypeOf(
        String name
    ) {

        Variable v =
            localVariable(name);

        if (v != null)
            return v.type;

        Parameter p =
            parameter(name);

        if (p != null)
            return p.type;

        throw new IllegalArgumentException(
            "Símbolo no existe: " +
            name
        );
    }

    public boolean IsConst(
        String name
    ) {

        Variable v =
            variable(name);

        return v.constant;
    }

    public boolean IsFloat(
        String name
    ) {

        return isFloatType(
            TypeOf(name)
        );
    }

    public boolean IsInteger(
        String name
    ) {

        return isIntegerType(
            TypeOf(name)
        );
    }

    void ensureMutable(
        Variable v
    ) {

        if (v.constant)
            throw new IllegalStateException(
                "La constante '" +
                v.name +
                "' no puede modificarse"
            );
    }

    void ensureSameType(
        Variable a,
        Variable b
    ) {

        if (a.type != b.type)

            throw new IllegalArgumentException(
                "Tipos incompatibles: " +
                a.name +
                " (" +
                typeName(a.type) +
                ") y " +
                b.name +
                " (" +
                typeName(b.type) +
                ")"
            );
    }

    void ensureInteger(
        Variable v
    ) {

        if (!v.isInteger())

            throw new IllegalArgumentException(
                "Se esperaba entero: " +
                v.name
            );
    }

    void ensureFloat(
        Variable v
    ) {

        if (!v.isFloat())

            throw new IllegalArgumentException(
                "Se esperaba floating-point: " +
                v.name
            );
    }

    public void Const(
        String name,
        int bits,
        long value
    ) {

        checkType(bits);

        if (!isIntegerType(bits))
            throw new IllegalArgumentException(
                "Este overload requiere entero"
            );

        checkIntegerRange(
            bits,
            value
        );

        validateName(name);
        ensureGlobalNameFree(name);

        Variable v =
            new Variable(
                name,
                bits,
                true
            );

        v.integerValue = value;
        v.initialized = true;

        variables.put(
            name,
            v
        );
    }

    public void Const(
        String name,
        long value
    ) {
        Const(name, QWORD, value);
    }

    public void Const(
        String name,
        int type,
        double value
    ) {

        checkType(type);

        if (!isFloatType(type))
            throw new IllegalArgumentException(
                "Este overload requiere FLOAT32/FLOAT64"
            );

        validateName(name);
        ensureGlobalNameFree(name);

        Variable v =
            new Variable(
                name,
                type,
                true
            );

        v.floatingValue = value;
        v.initialized = true;

        variables.put(
            name,
            v
        );
    }

    public void Const(
        String name,
        float value
    ) {
        Const(name, FLOAT32, value);
    }

    public void Const(
        String name,
        double value
    ) {
        Const(name, FLOAT64, value);
    }

    public void Let(
        String name,
        int bits,
        long value
    ) {

        checkType(bits);

        if (!isIntegerType(bits))
            throw new IllegalArgumentException(
                "Este overload requiere entero"
            );

        checkIntegerRange(bits, value);

        validateName(name);

        if (currentFunction == null)
            ensureGlobalNameFree(name);
        else
            ensureLocalNameFree(name);

        Variable v =
            new Variable(
                name,
                bits,
                false
            );

        if (currentFunction == null)
            variables.put(name, v);
        else
            currentFunction.locals.put(name, v);

        allocate(v);

        writeIntegerImmediate(v, value);

        v.integerValue = value;
        v.initialized = true;
    }

    public void Let(
        String name,
        long value
    ) {
        Let(name, QWORD, value);
    }

public int functionReturnType(
    String functionName
) {
    if (
        functionName == null ||
        functionName.isEmpty()
    ) {
        throw new IllegalArgumentException(
            "Nombre de función vacío"
        );
    }

    String name =
        functionName.trim();

    Function f =
        functionTable.get(name);

    if (f == null) {

        throw new IllegalArgumentException(
            "Función no existe: " +
            name
        );
    }

    return f.returnType;
}
    public void Let(
        String name,
        int type,
        double value
    ) {

        checkType(type);

        if (!isFloatType(type))
            throw new IllegalArgumentException(
                "Este overload requiere floating-point"
            );

        validateName(name);

        if (currentFunction == null)
            ensureGlobalNameFree(name);
        else
            ensureLocalNameFree(name);

        Variable v =
            new Variable(
                name,
                type,
                false
            );

        if (currentFunction == null)
            variables.put(name, v);
        else
            currentFunction.locals.put(name, v);

        allocate(v);

        writeFloatImmediate(v, value);

        v.floatingValue = value;
        v.initialized = true;
    }

    public void Let(
        String name,
        float value
    ) {
        Let(name, FLOAT32, value);
    }

    public void Let(
        String name,
        double value
    ) {
        Let(name, FLOAT64, value);
    }

    public void Let(
        String name,
        int type,
        Object value
    ) {

        if (value instanceof Byte ||
            value instanceof Short ||
            value instanceof Integer ||
            value instanceof Long) {

            Let(
                name,
                type,
                ((Number)value).longValue()
            );

            return;
        }

        if (value instanceof Float ||
            value instanceof Double) {

            Let(
                name,
                type,
                ((Number)value).doubleValue()
            );

            return;
        }

        if (value instanceof String) {

            Let(
                name,
                type,
                (String)value
            );

            return;
        }

        throw new IllegalArgumentException(
            "Inicializador no soportado: " +
            value
        );
    }

public void Let(
    String name,
    int type,
    String expression
) {

    checkType(type);
    validateName(name);

    if (expression == null)
        throw new IllegalArgumentException(
            "Inicializador null"
        );

    String e = expression.trim();

    if (e.isEmpty())
        throw new IllegalArgumentException(
            "Inicializador vacío"
        );

    if (isFloatType(type)) {

        String numeric = e;

        if (numeric.length() > 1) {

            char last =
                numeric.charAt(
                    numeric.length() - 1
                );

            if (last == 'f' ||
                last == 'F' ||
                last == 'd' ||
                last == 'D') {

                numeric =
                    numeric.substring(
                        0,
                        numeric.length() - 1
                    );
            }
        }

        boolean floatingLiteral =
            numeric.indexOf('.') >= 0 ||
            numeric.indexOf('e') >= 0 ||
            numeric.indexOf('E') >= 0;

        if (floatingLiteral) {

            try {

                double value =
                    Double.parseDouble(
                        numeric
                    );

                if (type == FLOAT32) {

                    Let(
                        name,
                        FLOAT32,
                        (float)value
                    );

                } else {

                    Let(
                        name,
                        FLOAT64,
                        value
                    );
                }

                return;

            } catch (
                NumberFormatException ex
            ) {

                throw new IllegalArgumentException(
                    "Literal floating-point inválido: " +
                    expression,
                    ex
                );
            }
        }
    }

    if (isIntegerType(type)) {

        try {

            String n = e;

            boolean signed =
                n.startsWith("+") ||
                n.startsWith("-");

            String unsigned =
                signed
                    ? n.substring(1)
                    : n;

            if (unsigned.startsWith("0x") ||
                unsigned.startsWith("0X")) {

                long value =
                    Long.parseUnsignedLong(
                        unsigned.substring(2),
                        16
                    );

                if (n.startsWith("-"))
                    value = -value;

                Let(
                    name,
                    type,
                    value
                );

                return;
            }

            if (unsigned.startsWith("0b") ||
                unsigned.startsWith("0B")) {

                long value =
                    Long.parseUnsignedLong(
                        unsigned.substring(2),
                        2
                    );

                if (n.startsWith("-"))
                    value = -value;

                Let(
                    name,
                    type,
                    value
                );

                return;
            }

            boolean integerLiteral = true;

            for (int i = 0; i < unsigned.length(); i++) {

                if (!Character.isDigit(
                        unsigned.charAt(i)
                    )) {

                    integerLiteral = false;
                    break;
                }
            }

            if (integerLiteral &&
                !unsigned.isEmpty()) {

                long value =
                    new java.math.BigInteger(n).longValue();

                Let(
                    name,
                    type,
                    value
                );

                return;
            }

        } catch (
            NumberFormatException ex
        ) {

            throw new IllegalArgumentException(
                "Literal entero inválido: " +
                expression,
                ex
            );
        }
    }

    Variable v =
        new Variable(
            name,
            type,
            false
        );

    if (currentFunction == null)
        ensureGlobalNameFree(name);
    else
        ensureLocalNameFree(name);

    if (currentFunction == null)
        variables.put(name, v);
    else
        currentFunction.locals.put(name, v);

    allocate(v);

    expr.setFromExpression(
        v,
        expression
    );

    v.initialized = true;
}

    void allocate(
        Variable v
    ) {

        if (v.isInteger()) {

            if (leafFunction) {
                for (int r : LEAF_VARIABLE_REGISTERS) {
                    if (!registerUsed[r] && !parameterHoldsRegister(r)) {
                        registerUsed[r] = true;
                        v.register = r;
                        return;
                    }
                }
            }

            for (int r : VARIABLE_REGISTERS) {

                if (!registerUsed[r]) {

                    registerUsed[r] = true;
                    everUsed[r] = true;
                    v.register = r;

                    return;
                }
            }
        }

        if (v.isFloat()) {

            for (int r : XMM_VARIABLE_REGISTERS) {

                if (!xmmUsed[r]) {

                    xmmUsed[r] = true;
                    v.xmm = r;

                    return;
                }
            }
        }

        startStackFrame();

        v.stackOffset =
            nextStackOffset;

        nextStackOffset += 8;
    }

    int framePatch = -1;

    public static boolean measuring = false;
    public static java.util.Map<String,Integer> measured = null;

    public static java.util.Map<String,Integer> calleeMask = null;

    public static boolean emitFrames = false;

    public static boolean frameUsed = false;

    public static java.util.Map<String,Boolean> frameNeeded = null;
    public static java.util.Map<String,Boolean> frameMeasured = null;

    public static void noteFrameUse() { frameUsed = true; }

    public static boolean leafFunction = false;

    public static boolean msAbiFunction = false;

    int msAbiRsiSlot = -1;
    int msAbiRdiSlot = -1;

    boolean wantFrameAtEntry() {
        if (emitFrames || measuring || entryPointFunction || msAbiFunction) return true;
        if (frameNeeded == null || curFn == null) return true;
        Boolean n = frameNeeded.get(curFn);
        return n == null || n;
    }

    boolean[] everUsed = new boolean[16];
    String curFn = null;
    int activeMask = 0xF;

    boolean compact() {
        return calleeMask != null && !measuring;
    }

    public void forceSaveAll() {
        frameUsed = true;
        for (int r = 12; r <= 15; r++) everUsed[r] = true;
    }

    int maskForFunction() {
        if (!compact() || curFn == null) return 0xF;
        Integer m = calleeMask.get(curFn);
        return m == null ? 0xF : m;
    }

    void startStackFrame() {

        if (stackFrameStarted)
            return;

        frameUsed = true;

        alu.PUSH("RBP");

        mov.MOV(
            "RBP",
            "RSP"
        );

        if (entryPointFunction) {

            builder.file_write(
                0x48,
                0x83,
                0xE4,
                0xF0
            );
        }

        builder.file_write(0x48, 0x81, 0xEC);
        framePatch = builder.size();
        builder.file_write(0, 0, 0, 0);

        activeMask = maskForFunction();
        if ((activeMask & 1) != 0) builder.file_write(0x4C, 0x89, 0x65, 0xF8);
        if ((activeMask & 2) != 0) builder.file_write(0x4C, 0x89, 0x6D, 0xF0);
        if ((activeMask & 4) != 0) builder.file_write(0x4C, 0x89, 0x75, 0xE8);
        if ((activeMask & 8) != 0) builder.file_write(0x4C, 0x89, 0x7D, 0xE0);

        stackFrameStarted = true;
    }

    void restoreCalleeSaved() {

        if ((activeMask & 1) != 0) builder.file_write(0x4C, 0x8B, 0x65, 0xF8);
        if ((activeMask & 2) != 0) builder.file_write(0x4C, 0x8B, 0x6D, 0xF0);
        if ((activeMask & 4) != 0) builder.file_write(0x4C, 0x8B, 0x75, 0xE8);
        if ((activeMask & 8) != 0) builder.file_write(0x4C, 0x8B, 0x7D, 0xE0);
    }

    void emitFrameTeardown() {
        if (compact()) {
            builder.file_write(0xC9);
        } else {
            mov.MOV("RSP", "RBP");
            alu.POP("RBP");
        }
    }

    void emitRet() {

        if (msAbiRsiSlot > 0) {
            loadIntegerMemory("RSI", "[RBP-" + msAbiRsiSlot + "]", 64);
            loadIntegerMemory("RDI", "[RBP-" + msAbiRdiSlot + "]", 64);
        }

        if (stackFrameStarted) {
            restoreCalleeSaved();
            emitFrameTeardown();
        }

        builder.file_write(0xC3);
    }

    void finishFunction() {

        if (measuring && measured != null && curFn != null) {
            int m = 0;
            for (int i = 0; i < 4; i++)
                if (everUsed[12 + i]) m |= (1 << i);
            Integer old = measured.get(curFn);
            measured.put(curFn, old == null ? m : (old | m));
            if (frameMeasured != null) {
                Boolean oldF = frameMeasured.get(curFn);
                boolean nf = frameUsed || m != 0;
                frameMeasured.put(curFn, oldF == null ? nf : (oldF || nf));
            }
        }
        frameUsed = false;
        java.util.Arrays.fill(everUsed, false);

        if (stackFrameStarted && framePatch >= 0) {

            int size =
                ((nextStackOffset - 8) + 15) & ~15;

            builder.patchIntLE(
                framePatch,
                size
            );
        }

        stackFrameStarted = false;
        framePatch = -1;
        msAbiRsiSlot = -1;
        msAbiRdiSlot = -1;
        nextStackOffset = FIRST_STACK_OFFSET;
        expressionStackDepth = 0;

        java.util.Arrays.fill(registerUsed, false);
        java.util.Arrays.fill(xmmUsed, false);
    }

    String memoryOf(
        Variable v
    ) {

        if (!v.isStack())

            throw new IllegalArgumentException(
                "La variable no está en memoria: " +
                v.name
            );

        return "[RBP-" +
               v.stackOffset +
               "]";
    }

    String registerName(
        int code,
        int bits
    ) {

        if (code < 0 || code > 15)
            throw new IllegalArgumentException(
                "Registro inválido: " + code
            );

        if (bits == 64)
            return "R" + codeName(code);

        if (bits == 32) {

            if (code < 8)
                return new String[] {
                    "EAX","ECX","EDX","EBX",
                    "ESP","EBP","ESI","EDI"
                }[code];

            return "R" + codeName(code) + "D";
        }

        if (bits == 16) {

            if (code < 8)
                return new String[] {
                    "AX","CX","DX","BX",
                    "SP","BP","SI","DI"
                }[code];

            return "R" + codeName(code) + "W";
        }

        if (bits == 8) {

            if (code < 4)
                return new String[] {
                    "AL","CL","DL","BL"
                }[code];

            if (code < 8)
                return new String[] {
                    "SPL","BPL","SIL","DIL"
                }[code];

            return "R" + codeName(code) + "B";
        }

        throw new IllegalArgumentException(
            "Bits inválidos: " + bits
        );
    }

    String codeName(int code) {

        if (code < 8)
            return new String[] {
                "AX","CX","DX","BX",
                "SP","BP","SI","DI"
            }[code];

        return Integer.toString(code);
    }

    int registerCode(String name) {

        String r =
            name.toUpperCase(Locale.ROOT);

        switch (r) {

            case "AL":
            case "AX":
            case "EAX":
            case "RAX":
                return 0;

            case "CL":
            case "CX":
            case "ECX":
            case "RCX":
                return 1;

            case "DL":
            case "DX":
            case "EDX":
            case "RDX":
                return 2;

            case "BL":
            case "BX":
            case "EBX":
            case "RBX":
                return 3;

            case "SPL":
            case "SP":
            case "ESP":
            case "RSP":
                return 4;

            case "BPL":
            case "BP":
            case "EBP":
            case "RBP":
                return 5;

            case "SIL":
            case "SI":
            case "ESI":
            case "RSI":
                return 6;

            case "DIL":
            case "DI":
            case "EDI":
            case "RDI":
                return 7;

            case "R8":
            case "R8B":
            case "R8W":
            case "R8D":
                return 8;

            case "R9":
            case "R9B":
            case "R9W":
            case "R9D":
                return 9;

            case "R10":
            case "R10B":
            case "R10W":
            case "R10D":
                return 10;

            case "R11":
            case "R11B":
            case "R11W":
            case "R11D":
                return 11;

            case "R12":
            case "R12B":
            case "R12W":
            case "R12D":
                return 12;

            case "R13":
            case "R13B":
            case "R13W":
            case "R13D":
                return 13;

            case "R14":
            case "R14B":
            case "R14W":
            case "R14D":
                return 14;

            case "R15":
            case "R15B":
            case "R15W":
            case "R15D":
                return 15;

            case "AH":
                return 4;

            case "CH":
                return 5;

            case "DH":
                return 6;

            case "BH":
                return 7;

            default:
                throw new IllegalArgumentException(
                    "Registro desconocido: " + name
                );
        }
    }

    String xmmName(int code) {

        if (code < 0 || code > 15)
            throw new IllegalArgumentException(
                "XMM inválido: " + code
            );

        return "XMM" + code;
    }

    int xmmCode(String name) {

        String s =
            name.toUpperCase(Locale.ROOT);

        if (!s.startsWith("XMM"))
            throw new IllegalArgumentException(
                "No es XMM: " + name
            );

        int n =
            Integer.parseInt(
                s.substring(3)
            );

        if (n < 0 || n > 15)
            throw new IllegalArgumentException(
                "XMM inválido: " + name
            );

        return n;
    }

    public String registerOf(String name) {

        Variable v =
            localVariable(name);

        if (v != null) {

            if (v.isRegister())
                return registerName(
                    v.register,
                    v.bits()
                );

            if (v.isXMM())
                return xmmName(v.xmm);
        }

        Parameter p =
            parameter(name);

        if (p != null) {

            if (p.integerRegister < 0 && p.xmmRegister < 0)
                return p.stackOffset < 0
                    ? "[RBP-" + (-p.stackOffset) + "]"
                    : "[RBP+" + p.stackOffset + "]";

            if (p.isInteger())
                return registerName(
                    p.integerRegister,
                    p.bits()
                );

            return xmmName(p.xmmRegister);
        }

        return null;
    }

    public String Location(String name) {

        Variable v =
            localVariable(name);

        if (v != null) {

            if (v.constant)
                return "CONST";

            if (v.isRegister())
                return registerName(
                    v.register,
                    v.bits()
                );

            if (v.isXMM())
                return xmmName(v.xmm);

            return memoryOf(v);
        }

        Parameter p =
            parameter(name);

        if (p != null) {

            if (p.integerRegister < 0 && p.xmmRegister < 0)
                return p.stackOffset < 0
                    ? "[RBP-" + (-p.stackOffset) + "]"
                    : "[RBP+" + p.stackOffset + "]";

            if (p.isInteger())
                return registerName(
                    p.integerRegister,
                    p.bits()
                );

            return xmmName(p.xmmRegister);
        }

        throw new IllegalArgumentException(
            "Símbolo no existe: " + name
        );
    }

    String temporaryInteger(int bits) {

        switch (bits) {

            case 64:
                return "RAX";

            case 32:
                return "EAX";

            case 16:
                return "AX";

            case 8:
                return "AL";

            default:
                throw new IllegalArgumentException(
                    "Temporal entero inválido"
                );
        }
    }

    String temporaryInteger2(int bits) {

        switch (bits) {

            case 64:
                return "RCX";

            case 32:
                return "ECX";

            case 16:
                return "CX";

            case 8:
                return "CL";

            default:
                throw new IllegalArgumentException(
                    "Temporal entero inválido"
                );
        }
    }

    void writeIntegerImmediate(
        Variable v,
        long value
    ) {

        String r =
            temporaryInteger(v.bits());

        mov.MOV(r, value);

        storeInteger(v, r);
    }

    public java.util.function.Predicate<String> unsignedLookup = n -> false;

    public boolean unsignedOp = false;

    public java.util.function.Predicate<String> signedLookup = n -> false;

    void signExtendInPlace(String dst, int fromBits) {

        int dstBits = registerBitsOf(dst);

        if (fromBits >= dstBits || fromBits >= 64 || dstBits < 32)
            return;

        int d = registerCode(dst);
        boolean w = dstBits == 64;
        int rex = (w ? 0x48 : 0x40) | (d >= 8 ? 0x05 : 0);
        int modrm = 0xC0 | ((d & 7) << 3) | (d & 7);

        if (fromBits == 32) {
            if (!w) return;
            builder.file_write(rex, 0x63, modrm);
            return;
        }

        if (w || d >= 8 || (fromBits == 8 && d >= 4))
            builder.file_write(rex);
        builder.file_write(0x0F, fromBits == 8 ? 0xBE : 0xBF, modrm);
    }

    private static int bitsOfType(int type) {
        switch (type) {
            case BYTE:  return 8;
            case WORD:  return 16;
            case DWORD: return 32;
            default:    return 64;
        }
    }

    void loadInteger(
        Variable v,
        String dst
    ) {

        loadIntegerRaw(v, dst);

        if (!v.constant && signedLookup.test(v.name))
            signExtendInPlace(dst, bitsOfType(v.type));
    }

    private void loadIntegerRaw(
        Variable v,
        String dst
    ) {

        if (!v.isInteger())
            throw new IllegalArgumentException(
                "No es entero: " + v.name
            );

        if (v.constant) {

            mov.MOV(
                dst,
                v.integerValue
            );

            return;
        }

        if (v.isRegister()) {

            String src =
                registerName(
                    v.register,
                    v.bits()
                );

            if (!src.equalsIgnoreCase(dst))
                mov.MOV(dst, src);

            return;
        }

        loadIntegerMemory(
            dst,
            memoryOf(v),
            v.bits()
        );
    }

    void loadIntegerParameter(
        Parameter p,
        String dst
    ) {

        loadParameterInteger(p, dst);
    }

    void storeInteger(
        Variable v,
        String src
    ) {

        ensureMutable(v);

        if (v.isRegister()) {

            String dst =
                registerName(
                    v.register,
                    v.bits()
                );

            if (dst.equalsIgnoreCase(src))
                return;

            mov.MOV(dst, src);
            return;
        }

        storeIntegerMemory(
            memoryOf(v),
            src,
            v.bits()
        );
    }

    private void loadIntegerMemory(
        String destination,
        String memory,
        int bits
    ) {

        int d =
            registerCode(destination);

        int dstBits =
            registerBitsOf(destination);

        if (bits < 32 && dstBits > bits) {

            if (dstBits == 16)
                builder.file_write(0x66);

            ptr.emitRexForMemory(
                d,
                memory,
                dstBits == 64 ? 64 : 32
            );

            builder.file_write(0x0F);
            builder.file_write(bits == 8 ? 0xB6 : 0xB7);

            ptr.emitModRM(
                d,
                memory
            );

            return;
        }

        if (dstBits < bits)
            bits = dstBits;

        if (bits == 16)
            builder.file_write(0x66);

        ptr.emitRexForMemory(
            d,
            memory,
            bits
        );

        if (bits == 8)
            builder.file_write(0x8A);
        else
            builder.file_write(0x8B);

        ptr.emitModRM(
            d,
            memory
        );
    }

    int registerBitsOf(String name) {

        String r = name.toUpperCase(Locale.ROOT);

        if (r.startsWith("R") && r.length() > 1 &&
            Character.isDigit(r.charAt(1))) {
            if (r.endsWith("B")) return 8;
            if (r.endsWith("W")) return 16;
            if (r.endsWith("D")) return 32;
            return 64;
        }

        if (r.startsWith("R")) return 64;
        if (r.startsWith("E")) return 32;
        if (r.endsWith("L"))   return 8;
        return 16;
    }

    public void StorePtr(
        String pointer,
        long value
    ) {

        if (pointer == null ||
            pointer.trim().isEmpty())

            throw new IllegalArgumentException(
                "StorePtr: puntero vacío"
            );

        String p =
            cleanName(pointer);

        Variable pv =
            variable(p);

        if (!pv.isInteger() ||
            pv.bits() != QWORD)

            throw new IllegalArgumentException(
                "StorePtr: '" +
                pointer +
                "' no es un puntero QWORD"
            );

        loadInteger(pv, "RAX");

        mov.MOV("RCX", value);

        storeIntegerMemory(
            "[RAX]",
            "RCX",
            QWORD
        );
    }

    public void StorePtr(
        String pointer,
        String value
    ) {

        if (pointer == null ||
            pointer.trim().isEmpty())

            throw new IllegalArgumentException(
                "StorePtr: puntero vacío"
            );

        if (value == null ||
            value.trim().isEmpty())

            throw new IllegalArgumentException(
                "StorePtr: valor vacío"
            );

        String p =
            cleanName(pointer);

        String vName =
            cleanName(value);

        Parameter pp = localVariable(p) == null ? parameter(p) : null;
        Parameter vp = localVariable(vName) == null ? parameter(vName) : null;

        if (pp != null) {
            if (!pp.isInteger() || pp.bits() != QWORD)
                throw new IllegalArgumentException(
                    "StorePtr: '" + pointer + "' no es un puntero QWORD");
            loadParameterInteger(pp, "RAX");
        } else {
            Variable pv = variable(p);
            if (!pv.isInteger() || pv.bits() != QWORD)
                throw new IllegalArgumentException(
                    "StorePtr: '" + pointer + "' no es un puntero QWORD");
            loadInteger(pv, "RAX");
        }

        int bits;

        if (vp != null) {
            if (!vp.isInteger())
                throw new IllegalArgumentException(
                    "StorePtr: '" + value + "' no es un entero");
            loadParameterInteger(vp, "RCX");
            bits = vp.bits();
        } else {
            Variable vv = variable(vName);
            if (!vv.isInteger())
                throw new IllegalArgumentException(
                    "StorePtr: '" + value + "' no es un entero");
            loadInteger(vv, "RCX");
            bits = vv.bits();
        }

        storeIntegerMemory(
            "[RAX]",
            "RCX",
            bits
        );
    }

    void storeIntegerMemory(
        String memory,
        String src,
        int bits
    ) {

        int s =
            registerCode(src);

        if (bits == 16)
            builder.file_write(0x66);

        ptr.emitRexForMemory(
            s,
            memory,
            bits
        );

        builder.file_write(
            bits == 8 ? 0x88 : 0x89
        );

        ptr.emitModRM(
            s,
            memory
        );
    }

    void emitSseMoveRegReg(
        int dst,
        int src,
        int type
    ) {

        builder.file_write(
            type == FLOAT32 ? 0xF3 : 0xF2
        );

        int rex = 0x40;

        if (dst >= 8)
            rex |= 0x04;

        if (src >= 8)
            rex |= 0x01;

        if (rex != 0x40)
            builder.file_write(rex);

        builder.file_write(
            0x0F,
            0x10
        );

        builder.file_write(
            0xC0 |
            ((dst & 7) << 3) |
            (src & 7)
        );
    }

    void emitSseLoad(
        int dst,
        String memory,
        int type
    ) {

        builder.file_write(
            type == FLOAT32 ? 0xF3 : 0xF2
        );

        if (dst >= 8)
            builder.file_write(0x44);

        builder.file_write(
            0x0F,
            0x10
        );

        ptr.emitModRM(
            dst,
            memory
        );
    }

    void emitSseStore(
        String memory,
        int src,
        int type
    ) {

        builder.file_write(
            type == FLOAT32 ? 0xF3 : 0xF2
        );

        if (src >= 8)
            builder.file_write(0x44);

        builder.file_write(
            0x0F,
            0x11
        );

        ptr.emitModRM(
            src,
            memory
        );
    }

    void emitSseArithmetic(
        String operation,
        int dst,
        int src,
        int type
    ) {

        builder.file_write(
            type == FLOAT32 ? 0xF3 : 0xF2
        );

        int rex = 0x40;

        if (dst >= 8)
            rex |= 0x04;

        if (src >= 8)
            rex |= 0x01;

        if (rex != 0x40)
            builder.file_write(rex);

        builder.file_write(0x0F);

        switch (operation) {

            case "ADD":
                builder.file_write(0x58);
                break;

            case "SUB":
                builder.file_write(0x5C);
                break;

            case "MUL":
                builder.file_write(0x59);
                break;

            case "DIV":
                builder.file_write(0x5E);
                break;

            default:
                throw new IllegalArgumentException(
                    "Operación SSE inválida: " +
                    operation
                );
        }

        builder.file_write(
            0xC0 |
            ((dst & 7) << 3) |
            (src & 7)
        );
    }

    void writeFloatImmediate(
        Variable v,
        double value
    ) {

        if (v.type == FLOAT32) {

            int bits =
                Float.floatToRawIntBits(
                    (float)value
                );

            mov.MOV(
                "EAX",
                bits & 0xFFFFFFFFL
            );

            emitMovD_XmmFromEax(0);

        } else {

            long bits =
                Double.doubleToRawLongBits(value);

            mov.MOV("RAX", bits);

            emitMovQ_XmmFromRax(0);
        }

        storeFloat(v, "XMM0");
    }

    void loadFloat(
        Variable v,
        String dst
    ) {

        ensureFloat(v);

        int d =
            xmmCode(dst);

        if (v.constant) {

            loadFloatConstant(v, d);
            return;
        }

        if (v.isXMM()) {

            if (v.xmm != d)
                emitSseMoveRegReg(
                    d,
                    v.xmm,
                    v.type
                );

            return;
        }

        emitSseLoad(
            d,
            memoryOf(v),
            v.type
        );
    }

    void loadFloatParameter(
        Parameter p,
        String dst
    ) {

        int d =
            xmmCode(dst);

        if (p.xmmRegister != d)

            emitSseMoveRegReg(
                d,
                p.xmmRegister,
                p.type
            );
    }

    void storeFloat(
        Variable v,
        String src
    ) {

        ensureMutable(v);

        int s =
            xmmCode(src);

        if (v.isXMM()) {

            if (v.xmm != s)
                emitSseMoveRegReg(
                    v.xmm,
                    s,
                    v.type
                );

            return;
        }

        emitSseStore(
            memoryOf(v),
            s,
            v.type
        );
    }

    void loadFloatConstant(
        Variable v,
        int dst
    ) {

        if (v.type == FLOAT32) {

            int bits =
                Float.floatToRawIntBits(
                    (float)v.floatingValue
                );

            mov.MOV(
                "EAX",
                bits & 0xFFFFFFFFL
            );

            emitMovD_XmmFromEax(dst);

            return;
        }

        long bits =
            Double.doubleToRawLongBits(
                v.floatingValue
            );

        mov.MOV("RAX", bits);

        emitMovQ_XmmFromRax(dst);
    }

    void emitMovD_XmmFromEax(
        int xmm
    ) {

        builder.file_write(0x66);

        if (xmm >= 8)
            builder.file_write(0x44);

        builder.file_write(
            0x0F,
            0x6E
        );

        builder.file_write(
            0xC0 |
            ((xmm & 7) << 3)
        );
    }

    void emitMovQ_XmmFromRax(
        int xmm
    ) {

        builder.file_write(0x66);

        int rex = 0x48;

        if (xmm >= 8)
            rex |= 0x04;

        builder.file_write(rex);

        builder.file_write(
            0x0F,
            0x6E
        );

        builder.file_write(
            0xC0 |
            ((xmm & 7) << 3)
        );
    }

    public void Set(
        String name,
        long value
    ) {

        Variable v =
            variable(name);

        ensureMutable(v);
        ensureInteger(v);

        checkIntegerRange(
            v.bits(),
            value
        );

        writeIntegerImmediate(
            v,
            value
        );

        v.integerValue = value;
        v.initialized = true;
    }

    public void Set(
        String name,
        float value
    ) {

        Variable v =
            variable(name);

        ensureMutable(v);

        if (v.type != FLOAT32)
            throw new IllegalArgumentException(
                "FLOAT32 requerido"
            );

        writeFloatImmediate(v, value);

        v.floatingValue = value;
        v.initialized = true;
    }

    public void Set(
        String name,
        double value
    ) {

        Variable v =
            variable(name);

        ensureMutable(v);

        if (v.type != FLOAT64)
            throw new IllegalArgumentException(
                "FLOAT64 requerido"
            );

        writeFloatImmediate(v, value);

        v.floatingValue = value;
        v.initialized = true;
    }

    private String lastCopyKey = null;
    private int lastCopyEnd = -1;

    public void Set(
        String destination,
        String source
    ) {

        String key = destination + "<-" + source;

        if (key.equals(lastCopyKey) && builder.size() == lastCopyEnd)
            return;

        SetCopy(destination, source);

        lastCopyKey = key;
        lastCopyEnd = builder.size();
    }

    private void SetCopy(
    String destination,
    String source
) {

    if (source == null)
        throw new IllegalArgumentException(
            "Valor null"
        );

    String s = source.trim();

    Variable dst =
        variable(destination);

    ensureMutable(dst);

    if (dst.isFloat()) {

        String numeric = s;

        if (numeric.length() > 1) {

            char last =
                numeric.charAt(
                    numeric.length() - 1
                );

            if (last == 'f' ||
                last == 'F' ||
                last == 'd' ||
                last == 'D') {

                numeric =
                    numeric.substring(
                        0,
                        numeric.length() - 1
                    );
            }
        }

        boolean floatingLiteral =
            numeric.indexOf('.') >= 0 ||
            numeric.indexOf('e') >= 0 ||
            numeric.indexOf('E') >= 0;

        if (floatingLiteral) {

            try {

                double value =
                    Double.parseDouble(
                        numeric
                    );

                if (dst.type == FLOAT32) {

                    Set(
                        destination,
                        (float)value
                    );

                } else {

                    Set(
                        destination,
                        value
                    );
                }

                return;

            } catch (
                NumberFormatException ex
            ) {

                throw new IllegalArgumentException(
                    "Literal floating-point inválido: " +
                    source,
                    ex
                );
            }
        }
    }

    expr.setFromExpression(
        dst,
        source
    );

    dst.initialized = true;
}

    public void Set(
        String destination,
        Object value
    ) {

        if (value instanceof Float) {

            Set(
                destination,
                ((Float)value).floatValue()
            );

            return;
        }

        if (value instanceof Double) {

            Set(
                destination,
                ((Double)value).doubleValue()
            );

            return;
        }

        if (value instanceof Number) {

            Set(
                destination,
                ((Number)value).longValue()
            );

            return;
        }

        if (value instanceof String) {

            Set(
                destination,
                (String)value
            );

            return;
        }

        throw new IllegalArgumentException(
            "Valor no soportado"
        );
    }

    public void And(String name, String source) { arithmetic.Bitwise("AND", name, source); }
    public void And(String name, long value)    { arithmetic.Bitwise("AND", name, value); }
    public void Or(String name, String source)  { arithmetic.Bitwise("OR", name, source); }
    public void Or(String name, long value)     { arithmetic.Bitwise("OR", name, value); }
    public void Xor(String name, String source) { arithmetic.Bitwise("XOR", name, source); }
    public void Xor(String name, long value)    { arithmetic.Bitwise("XOR", name, value); }

    public void Add(String name, String source) {
        arithmetic.Add(name, source);
    }

    public void Add(String name, long value) {
        arithmetic.Add(name, value);
    }

    public void Add(String name, float value) {
        arithmetic.Add(name, value);
    }

    public void Add(String name, double value) {
        arithmetic.Add(name, value);
    }

    public void Sub(String name, String source) {
        arithmetic.Sub(name, source);
    }

    public void Sub(String name, long value) {
        arithmetic.Sub(name, value);
    }

    public void Sub(String name, float value) {
        arithmetic.Sub(name, value);
    }

    public void Sub(String name, double value) {
        arithmetic.Sub(name, value);
    }

    public void Multiply(String name, String source) {
        arithmetic.Multiply(name, source);
    }

    public void ShiftLeft(String name, int count) {
        arithmetic.ShiftLeft(name, count);
    }

    public void ShiftRight(String name, int count) {
        arithmetic.ShiftRight(name, count);
    }

    public void Multiply(String name, long value) {
        arithmetic.Multiply(name, value);
    }

    public void Multiply(String name, float value) {
        arithmetic.Multiply(name, value);
    }

    public void Multiply(String name, double value) {
        arithmetic.Multiply(name, value);
    }

    public void Divide(String name, String source) {
        arithmetic.Divide(name, source);
    }

    public void Divide(String name, long divisor) {
        arithmetic.Divide(name, divisor);
    }

    public void Divide(String name, float divisor) {
        arithmetic.Divide(name, divisor);
    }

    public void Divide(String name, double divisor) {
        arithmetic.Divide(name, divisor);
    }

    public void Mod(String name, String source) {
        arithmetic.Mod(name, source);
    }

    public void Mod(String name, long divisor) {
        arithmetic.Mod(name, divisor);
    }

    public void Negate(String name) {
        arithmetic.Negate(name);
    }

    public void Increment(String name) {
        arithmetic.Increment(name);
    }

    public void Decrement(String name) {
        arithmetic.Decrement(name);
    }

    void emitIDIV(
        String reg,
        int bits
    ) {

        int r =
            registerCode(reg);

        if (unsignedOp && bits >= 16) {

            int n = builder.size();
            int last = n > 0 ? builder.getByteAt(n - 1) & 0xFF : -1;
            int prev = n > 1 ? builder.getByteAt(n - 2) & 0xFF : -1;

            if (last == 0x99) {
                builder.truncate((prev == 0x48 || prev == 0x66) ? n - 2 : n - 1);
                builder.file_write(0x31, 0xD2);
            }

            if (bits == 16)
                builder.file_write(0x66);

            int rex = (bits == 64 ? 0x48 : 0x40) | (r >= 8 ? 0x01 : 0);

            if (rex != 0x40)
                builder.file_write(rex);

            builder.file_write(0xF7, 0xF0 | (r & 7));

            return;
        }

        if (bits == 64 || bits == 32) {

            int rex =
                (bits == 64 ? 0x48 : 0x40) |
                (r >= 8 ? 0x01 : 0);

            if (rex != 0x40)
                builder.file_write(rex);

            builder.file_write(0xF7);

            builder.file_write(
                0xF8 |
                (r & 7)
            );

            return;
        }

        if (bits == 16) {

            builder.file_write(0x66);

            if (r >= 8)
                builder.file_write(0x41);

            builder.file_write(0xF7);

            builder.file_write(
                0xF8 |
                (r & 7)
            );

            return;
        }

        if (bits == 8) {

            if (r >= 8)
                builder.file_write(0x41);

            builder.file_write(0xF6);

            builder.file_write(
                0xF8 |
                (r & 7)
            );

            return;
        }

        throw new IllegalArgumentException(
            "IDIV inválido"
        );
    }

    Variable newConstantFloat(
        int type,
        double value
    ) {

        Variable v =
            new Variable(
                "$tmp",
                type,
                true
            );

        v.floatingValue = value;

        return v;
    }

    public void Copy(
        String destination,
        String source
    ) {
        Set(destination, source);
    }

    public void Delete(
        String name
    ) {

        String n =
            cleanName(name);

        Variable v =
            variables.remove(n);

        if (v == null &&
            currentFunction != null) {

            v =
                currentFunction.locals.remove(n);
        }

        if (v == null)
            throw new IllegalArgumentException(
                "Variable no existe: " + n
            );

        if (v.isRegister())
            registerUsed[v.register] = false;

        if (v.isXMM())
            xmmUsed[v.xmm] = false;
    }

    public void AddressOf(
        String destination,
        String source
    ) {

        if (destination == null ||
            destination.trim().isEmpty())

            throw new IllegalArgumentException(
                "AddressOf: destino vacío"
            );

        if (source == null ||
            source.trim().isEmpty())

            throw new IllegalArgumentException(
                "AddressOf: origen vacío"
            );

        String dstName =
            cleanName(destination);

        String srcName =
            cleanName(source);

        Variable src =
            variable(srcName);

        if (src.constant)
            throw new IllegalArgumentException(
                "La constante '" +
                srcName +
                "' no tiene dirección"
            );

        if (!src.initialized)
            throw new IllegalStateException(
                "La variable '" +
                srcName +
                "' no está inicializada"
            );

        Variable dst =
            variable(dstName);

        ensureMutable(dst);

        if (!dst.isInteger() ||
            dst.bits() != QWORD)

            throw new IllegalArgumentException(
                "AddressOf: '" +
                dstName +
                "' debe ser QWORD"
            );

        if (!src.isStack()) {

            startStackFrame();

            src.stackOffset =
                nextStackOffset;

            nextStackOffset += 8;

            if (src.isRegister()) {

                String srcRegister =
                    registerName(
                        src.register,
                        src.bits()
                    );

                storeIntegerMemory(
                    memoryOf(src),
                    srcRegister,
                    src.bits()
                );

                registerUsed[src.register] =
                    false;

                src.register = -1;

            } else if (src.isXMM()) {

                emitSseStore(
                    memoryOf(src),
                    src.xmm,
                    src.type
                );

                xmmUsed[src.xmm] = false;
                src.xmm = -1;

            } else {

                throw new IllegalArgumentException(
                    "No se puede obtener la dirección de '" +
                    srcName +
                    "'"
                );
            }
        }

        String sourceMemory =
            memoryOf(src);

        if (dst.isRegister()) {

            String dstRegister =
                registerName(
                    dst.register,
                    QWORD
                );

            alu.LEA(
                dstRegister,
                sourceMemory
            );

            dst.integerValue = 0;
            dst.initialized = true;

            return;
        }

        if (dst.isStack()) {

            alu.LEA(
                "RAX",
                sourceMemory
            );

            storeIntegerMemory(
                memoryOf(dst),
                "RAX",
                QWORD
            );

            dst.integerValue = 0;
            dst.initialized = true;

            return;
        }

        throw new IllegalArgumentException(
            "AddressOf: el destino '" +
            dstName +
            "' no tiene ubicación válida"
        );
    }

    private void emitRipRelativeLea(
        String destination,
        String symbol
    ) {

        if (destination == null ||
            destination.trim().isEmpty())

            throw new IllegalArgumentException(
                "emitRipRelativeLea: destino vacío"
            );

        if (symbol == null ||
            symbol.trim().isEmpty())

            throw new IllegalArgumentException(
                "emitRipRelativeLea: símbolo vacío"
            );

        String dst =
            cleanName(destination);

        String sym =
            cleanName(symbol);

        int reg =
            registerCode(dst);

        int rex = 0x48;

        if (reg >= 8)
            rex |= 0x04;

        builder.file_write(
            rex,
            0x8D
        );

        builder.file_write(
            0x05 |
            ((reg & 7) << 3)
        );

        int patchOffset =
            builder.size();

        builder.file_write(
            0x00,
            0x00,
            0x00,
            0x00
        );

        builder.addReference(
            sym,
            patchOffset
        );
    }

    public void AddressOfSymbol(
        String destination,
        String symbol
    ) {

        if (destination == null ||
            destination.trim().isEmpty())

            throw new IllegalArgumentException(
                "AddressOfSymbol: destino vacío"
            );

        if (symbol == null ||
            symbol.trim().isEmpty())

            throw new IllegalArgumentException(
                "AddressOfSymbol: símbolo vacío"
            );

        String dstName =
            cleanName(destination);

        String sym =
            cleanName(symbol);

        Variable dst =
            variable(dstName);

        ensureMutable(dst);

        if (!dst.isInteger() ||
            dst.bits() != QWORD)

            throw new IllegalArgumentException(
                "AddressOfSymbol requiere destino QWORD: " +
                dstName
            );

        if (dst.isRegister()) {

            String dstRegister =
                registerName(
                    dst.register,
                    QWORD
                );

            emitRipRelativeLea(
                dstRegister,
                sym
            );

            dst.integerValue = 0;
            dst.initialized = true;

            return;
        }

        if (dst.isStack()) {

            emitRipRelativeLea(
                "RAX",
                sym
            );

            storeIntegerMemory(
                memoryOf(dst),
                "RAX",
                QWORD
            );

            dst.integerValue = 0;
            dst.initialized = true;

            return;
        }

        throw new IllegalArgumentException(
            "AddressOfSymbol: destino sin almacenamiento válido: " +
            dstName
        );
    }

public void Label(String name) {
    control.labelNamed(name);
}

public void Jmp(String name) {
    control.jmpNamed(name);
}

public void Jcc(String condition, String label) {
    control.jccNamed(condition, label);
}

    public static Parameter Parameter(
        String name,
        int type
    ) {

        checkType(type);

        return new Parameter(
            name,
            type
        );
    }

    public void DeclareNaked(String name, int returnType, Parameter... params) {
        funcOps.DeclareNaked(cleanName(name), returnType, params);
    }

    public void Function(
        String name,
        int returnType,
        Parameter... params
    ) {

        String cleanFunctionName =
            cleanName(name);

        entryPointFunction =
            cleanFunctionName.equals("_start");

        curFn = cleanFunctionName;

        funcOps.Function(
            name,
            returnType,
            params
        );
    }

    public void EndFunction() {

        control.endFunction();

        funcOps.EndFunction();

        finishFunction();

        entryPointFunction = false;
    }

    public void Return() {
        funcOps.Return();
    }

    public void Return(long value) {
        funcOps.Return(value);
    }

    public void Return(float value) {
        funcOps.Return(value);
    }

    public void Return(double value) {
        funcOps.Return(value);
    }

    public void Return(String symbol) {
        funcOps.Return(symbol);
    }

    public void Call(
        String functionName,
        Object... arguments
    ) {

        funcOps.Call(
            functionName,
            arguments
        );
    }

    public void Call(
        String functionName,
        String destination,
        Object... arguments
    ) {

        funcOps.Call(
            functionName,
            destination,
            arguments
        );
    }

    public String ReturnRegister() {
        return funcOps.ReturnRegister();
    }

    void loadParameterInteger(
        Parameter p,
        String destination
    ) {

        loadParameterIntegerRaw(p, destination);

        if (signedLookup.test(p.name))
            signExtendInPlace(destination, bitsOfType(p.type));
    }

    private void loadParameterIntegerRaw(
        Parameter p,
        String destination
    ) {

        if (p.integerRegister >= 0) {

            String source =
                registerName(
                    p.integerRegister,
                    p.bits()
                );

            if (!source.equalsIgnoreCase(
                    destination
                )) {

                mov.MOV(
                    destination,
                    source
                );
            }

            return;
        }

        String memory =
            p.stackOffset < 0
                ? "[RBP-" + (-p.stackOffset) + "]"
                : "[RBP+" + p.stackOffset + "]";

        loadIntegerMemory(
            destination,
            memory,
            p.bits()
        );
    }

    void loadParameterFloat(
        Parameter p,
        String destination
    ) {

        if (p.xmmRegister >= 0) {

            emitSseMoveRegReg(
                xmmCode(destination),
                p.xmmRegister,
                p.type
            );

            return;
        }

        String memory =
            p.stackOffset < 0
                ? "[RBP-" + (-p.stackOffset) + "]"
                : "[RBP+" + p.stackOffset + "]";

        emitSseLoad(
            xmmCode(destination),
            memory,
            p.type
        );
    }

    public void EndFrame() {

        if (!stackFrameStarted)
            return;

        restoreCalleeSaved();

        emitFrameTeardown();

        stackFrameStarted = false;

        expressionStackDepth = 0;
    }

    public void Dump() {

        arch.Diagnostics.log(
            "===== VARS ====="
        );

        for (
            Variable v :
            variables.values()
        ) {

            String location;

            if (v.constant) {

                if (v.isFloat())

                    location =
                        "CONST = " +
                        v.floatingValue;

                else

                    location =
                        "CONST = " +
                        v.integerValue;

            } else if (v.isRegister()) {

                location =
                    registerName(
                        v.register,
                        v.bits()
                    );

            } else if (v.isXMM()) {

                location =
                    xmmName(v.xmm);

            } else {

                location =
                    memoryOf(v);
            }

            arch.Diagnostics.log(
                v.name +
                " : " +
                typeName(v.type) +
                " -> " +
                location
            );
        }

        arch.Diagnostics.log(
            "================"
        );
    }

public void Cmp(
    String name,
    String source
) {
    arithmetic.Cmp(
        name,
        source
    );
}

public void Cmp(
    String name,
    long value
) {
    arithmetic.Cmp(
        name,
        value
    );
}

    public void DumpFunctions() {

        arch.Diagnostics.log(
            "===== FUNCTIONS ====="
        );

        for (
            Function f :
            functionTable.values()
        ) {

            arch.Diagnostics.logn(
                f.name +
                "("
            );

            for (
                int i = 0;
                i < f.parameters.size();
                i++
            ) {

                if (i != 0)
                    arch.Diagnostics.logn(", ");

                Parameter p =
                    f.parameters.get(i);

                arch.Diagnostics.logn(
                    p.name +
                    ":" +
                    typeName(p.type)
                );
            }

            arch.Diagnostics.log(
                ") -> " +
                typeName(
                    f.returnType
                )
            );
        }

        arch.Diagnostics.log(
            "====================="
        );
    }

    public void add(
        String n,
        String v
    ) {
        Add(n, v);
    }

    public void add(
        String n,
        long v
    ) {
        Add(n, v);
    }

    public void add(
        String n,
        float v
    ) {
        Add(n, v);
    }

    public void add(
        String n,
        double v
    ) {
        Add(n, v);
    }

    public void sub(
        String n,
        String v
    ) {
        Sub(n, v);
    }

    public void sub(
        String n,
        long v
    ) {
        Sub(n, v);
    }

    public void sub(
        String n,
        float v
    ) {
        Sub(n, v);
    }

    public void sub(
        String n,
        double v
    ) {
        Sub(n, v);
    }

    public void multiply(
        String n,
        String v
    ) {
        Multiply(n, v);
    }

    public void multiply(
        String n,
        long v
    ) {
        Multiply(n, v);
    }

    public void multiply(
        String n,
        float v
    ) {
        Multiply(n, v);
    }

    public void multiply(
        String n,
        double v
    ) {
        Multiply(n, v);
    }

    public void divide(
        String n,
        String v
    ) {
        Divide(n, v);
    }

    public void divide(
        String n,
        long v
    ) {
        Divide(n, v);
    }

    public void divide(
        String n,
        float v
    ) {
        Divide(n, v);
    }

    public void divide(
        String n,
        double v
    ) {
        Divide(n, v);
    }

    public void set(
        String n,
        String v
    ) {
        Set(n, v);
    }

    public void set(
        String n,
        long v
    ) {
        Set(n, v);
    }

    public void set(
        String n,
        float v
    ) {
        Set(n, v);
    }

    public void set(
        String n,
        double v
    ) {
        Set(n, v);
    }

    public void function(
        String name,
        int returnType,
        Parameter... parameters
    ) {

        Function(
            name,
            returnType,
            parameters
        );
    }

    public void endFunction() {
        EndFunction();
    }

    public void call(
        String name,
        Object... args
    ) {

        Call(
            name,
            args
        );
    }

    public void ReturnValue(
        long value
    ) {
        Return(value);
    }

    public void ReturnValue(
        float value
    ) {
        Return(value);
    }

    public void ReturnValue(
        double value
    ) {
        Return(value);
    }

    public void ReturnValue(
        String value
    ) {
        Return(value);
    }

    public static Parameter param(
        String name,
        int type
    ) {

        return Parameter(
            name,
            type
        );
    }
public void LoadPtr(
    String destination,
    String pointer
) {
    if (destination == null || destination.trim().isEmpty())
        throw new IllegalArgumentException(
            "LoadPtr: destino vacío"
        );

    if (pointer == null || pointer.trim().isEmpty())
        throw new IllegalArgumentException(
            "LoadPtr: puntero vacío"
        );

    String d = cleanName(destination);
    String p = cleanName(pointer);

    Variable pv = variable(p);

    if (!pv.isInteger() || pv.bits() != QWORD)
        throw new IllegalArgumentException(
            "LoadPtr: '" + pointer +
            "' no es un puntero QWORD"
        );

    Variable dv = variable(d);

    if (!dv.isInteger())
        throw new IllegalArgumentException(
            "LoadPtr: '" + destination +
            "' no es un entero"
        );

    loadInteger(
        pv,
        "RAX"
    );

    loadIntegerMemory(
        "RCX",
        "[RAX]",
        dv.bits()
    );

    storeInteger(
        dv,
        "RCX"
    );
}
    public static Parameter parameterOf(
        String name,
        int type
    ) {

        return Parameter(
            name,
            type
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
