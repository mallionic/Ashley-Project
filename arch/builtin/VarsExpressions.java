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

import java.util.*;

final class VarsExpressions {

    private final Vars core;

    private static final int[] INTEGER_TEMP_CODES = {
        0,
        1,
        2
    };

    private static final int[] INTEGER_PERSISTENT_CODES = {
        8,
        9,
        10,
        11,
        12,
        13,
        14,
        15
    };

    VarsExpressions(Vars core) {
        this.core = core;
    }

    void setFromExpression(
        Vars.Variable destination,
        String expression
    ) {

        if (expression == null)
            throw new IllegalArgumentException(
                "Expresión null"
            );

        expression = expression.trim();

        if (expression.isEmpty())
            throw new IllegalArgumentException(
                "Expresión vacía"
            );

        if (destination.isFloat()) {

            evaluateFloatExpression(
                destination,
                expression
            );

        } else {

            evaluateIntegerExpression(
                destination,
                expression
            );
        }
    }

    private static final class IntegerValue {

        final String location;
        final Long constant;

        IntegerValue(String location) {
            this(location, null);
        }

        IntegerValue(
            String location,
            Long constant
        ) {

            this.location = location;
            this.constant = constant;
        }

        boolean isRegister() {
            return isIntegerRegister(location);
        }

        boolean isTemporary() {
            return isIntegerTemporary(location);
        }

        boolean isPersistent() {
            return isIntegerPersistent(location);
        }

        boolean isStack() {
            return location.equalsIgnoreCase("STACK");
        }

        boolean isConstant() {
            return constant != null;
        }
    }

    private static final class ConstantState {

        final boolean known;
        final long value;

        private ConstantState(
            boolean known,
            long value
        ) {

            this.known = known;
            this.value = value;
        }

        static ConstantState known(long value) {
            return new ConstantState(true, value);
        }

        static ConstantState unknown() {
            return UNKNOWN;
        }
    }

    private static final ConstantState UNKNOWN =
        new ConstantState(false, 0L);

    private final class IntegerExpressionStack {

        private final List<IntegerValue> values =
            new ArrayList<>();

        void pushRegister(String register) {
            values.add(
                new IntegerValue(register)
            );
        }

        void pushRegister(
            String register,
            Long constant
        ) {

            values.add(
                new IntegerValue(
                    register,
                    constant
                )
            );
        }

        void pushConstant(
            String register,
            long constant
        ) {

            values.add(
                new IntegerValue(
                    register,
                    constant
                )
            );
        }

        void pushStack() {

            values.add(
                new IntegerValue("STACK")
            );
        }

        void replaceTop(IntegerValue value) {

            if (values.isEmpty())
                throw new IllegalStateException(
                    "Stack de expresión vacío"
                );

            values.set(
                values.size() - 1,
                value
            );
        }

        IntegerValue pop() {

            if (values.isEmpty())
                throw new IllegalStateException(
                    "Stack de expresión vacío"
                );

            return values.remove(
                values.size() - 1
            );
        }

        IntegerValue peek() {

            if (values.isEmpty())
                throw new IllegalStateException(
                    "Stack de expresión vacío"
                );

            return values.get(
                values.size() - 1
            );
        }

        int size() {
            return values.size();
        }

        boolean containsRegister(
            String register
        ) {

            for (IntegerValue value : values) {

                if (value.isRegister() &&
                    sameRegisterFamily(
                        value.location,
                        register
                    )) {

                    return true;
                }
            }

            return false;
        }

        void spillTopRegister() {

            if (values.isEmpty())
                throw new IllegalStateException(
                    "No hay valor para hacer spill"
                );

            IntegerValue top = peek();

            if (!top.isRegister())
                throw new IllegalStateException(
                    "El valor superior no es un registro"
                );

            core.alu.PUSH(top.location);

            values.set(
                values.size() - 1,
                new IntegerValue(
                    "STACK",
                    top.constant
                )
            );
        }
    }

    private void evaluateIntegerExpression(
        Vars.Variable destination,
        String expression
    ) {

        final int bits =
            integerBits(destination.type);

        List<String> tokens =
            tokenize(expression);

        List<String> postfix =
            toPostfix(tokens);

        postfix =
            optimizeConstantPostfix(postfix);

        IntegerExpressionStack stack =
            new IntegerExpressionStack();

        for (String token : postfix) {

            if (!isOperator(token)) {

                String target =
                    chooseValueRegister(
                        stack,
                        bits
                    );

                if (target == null) {

                    spillTopRegister(stack);

                    target =
                        chooseValueRegister(
                            stack,
                            bits
                        );

                    if (target == null)
                        throw new IllegalStateException(
                            "No hay registro temporal disponible"
                        );
                }

                Long constant =
                    tryParseIntegerConstant(token);

                expressionIntegerToRegister(
                    token,
                    target,
                    bits
                );

                if (constant != null) {

                    stack.pushConstant(
                        target,
                        truncateToBits(
                            constant,
                            bits
                        )
                    );

                } else {

                    stack.pushRegister(target);
                }

                continue;
            }

            if (token.equals("u-")) {

                IntegerValue value =
                    stack.pop();

                String reg =
                    materializeValue(
                        value,
                        temporaryRegister(0, bits)
                    );

                if (value.isConstant()) {

                    long result =
                        truncateToBits(
                            -value.constant,
                            bits
                        );

                    core.mov.MOV(
                        reg,
                        result
                    );

                    stack.pushRegister(
                        reg,
                        result
                    );

                } else {

                    core.alu.NEG(reg);

                    stack.pushRegister(reg);
                }

                continue;
            }

            if (stack.size() < 2)
                throw new IllegalArgumentException(
                    "Expresión inválida"
                );

            IntegerValue right =
                stack.pop();

            IntegerValue left =
                stack.pop();

            if (left.isConstant() &&
                right.isConstant()) {

                Long folded =
                    foldConstants(
                        token,
                        left.constant,
                        right.constant,
                        bits
                    );

                if (folded != null) {

                    String reg =
                        temporaryRegister(
                            0,
                            bits
                        );

                    core.mov.MOV(
                        reg,
                        folded
                    );

                    stack.pushRegister(
                        reg,
                        folded
                    );

                    continue;
                }
            }

            if (left.isConstant() &&
                !right.isConstant() &&
                isCommutative(token)) {

                IntegerValue tmp = left;
                left = right;
                right = tmp;
            }

            if (right.isConstant()) {

                String leftReg =
                    materializeValue(
                        left,
                        temporaryRegister(
                            0,
                            bits
                        )
                    );

                long c =
                    truncateToBits(
                        right.constant,
                        bits
                    );

                if (token.equals("+") &&
                    c == 0L) {

                    stack.pushRegister(
                        leftReg
                    );

                    continue;
                }

                if (token.equals("-") &&
                    c == 0L) {

                    stack.pushRegister(
                        leftReg
                    );

                    continue;
                }

                if (token.equals("*") &&
                    c == 0L) {

                    core.mov.MOV(
                        leftReg,
                        0L
                    );

                    stack.pushRegister(
                        leftReg,
                        0L
                    );

                    continue;
                }

                if (token.equals("*") &&
                    c == 1L) {

                    stack.pushRegister(
                        leftReg
                    );

                    continue;
                }

                if (token.equals("*") &&
                    c > 0L &&
                    isPowerOfTwo(c, bits)) {

                    int shift =
                        Long.numberOfTrailingZeros(c);

                    emitShiftLeft(
                        leftReg,
                        shift,
                        bits
                    );

                    stack.pushRegister(leftReg);

                    continue;
                }

                if (token.equals("/") &&
                    c == 1L) {

                    stack.pushRegister(
                        leftReg
                    );

                    continue;
                }

                boolean canUseImmediate =
                    bits != 8 ||
                    (c >= -128L && c <= 127L);

                if (canUseImmediate &&
                    token.equals("+") &&
                    fitsSignedImm32(c)) {

                    emitAddImmediate(
                        leftReg,
                        c,
                        bits
                    );

                    stack.pushRegister(leftReg);

                    continue;
                }

                if (canUseImmediate &&
                    token.equals("-") &&
                    fitsSignedImm32(c)) {

                    emitSubImmediate(
                        leftReg,
                        c,
                        bits
                    );

                    stack.pushRegister(leftReg);

                    continue;
                }

                String rightReg =
                    chooseRightRegister(
                        leftReg,
                        bits
                    );

                rightReg =
                    materializeValue(
                        right,
                        rightReg
                    );

                switch (token) {

                    case "+":

                        if (!sameRegister(
                            leftReg,
                            rightReg
                        )) {

                            core.alu.ADD(
                                leftReg,
                                rightReg
                            );
                        }

                        break;

                    case "-":

                        if (sameRegister(
                            leftReg,
                            rightReg
                        )) {

                            core.mov.MOV(
                                leftReg,
                                0L
                            );

                        } else {

                            core.alu.SUB(
                                leftReg,
                                rightReg
                            );
                        }

                        break;

                    case "*":

                        core.alu.IMUL(
                            leftReg,
                            rightReg
                        );

                        break;

                    case "/":

                        leftReg =
                            emitIntegerDivision(
                                leftReg,
                                rightReg,
                                bits
                            );

                        break;

                    default:

                        throw new IllegalArgumentException(
                            "Operador desconocido: " +
                            token
                        );
                }

                stack.pushRegister(leftReg);

                continue;
            }

            String leftReg =
                materializeValue(
                    left,
                    temporaryRegister(
                        0,
                        bits
                    )
                );

            String rightReg =
                chooseRightRegister(
                    leftReg,
                    bits
                );

            rightReg =
                materializeValue(
                    right,
                    rightReg
                );

            switch (token) {

                case "+":

                    if (!sameRegister(
                        leftReg,
                        rightReg
                    )) {

                        core.alu.ADD(
                            leftReg,
                            rightReg
                        );
                    }

                    break;

                case "-":

                    if (sameRegister(
                        leftReg,
                        rightReg
                    )) {

                        core.mov.MOV(
                            leftReg,
                            0L
                        );

                    } else {

                        core.alu.SUB(
                            leftReg,
                            rightReg
                        );
                    }

                    break;

                case "*":

                    core.alu.IMUL(
                        leftReg,
                        rightReg
                    );

                    break;

                case "/":

                    leftReg =
                        emitIntegerDivision(
                            leftReg,
                            rightReg,
                            bits
                        );

                    break;

                default:

                    throw new IllegalArgumentException(
                        "Operador desconocido: " +
                        token
                    );
            }

            stack.pushRegister(leftReg);
        }

        if (stack.size() != 1)
            throw new IllegalArgumentException(
                "Expresión inválida"
            );

        IntegerValue result =
            stack.pop();

        if (result.isStack()) {

            String popRegister =
                temporaryRegister(
                    0,
                    bits
                );

            core.alu.POP(
                popRegister
            );

            core.storeInteger(
                destination,
                popRegister
            );

            return;
        }

        String finalRegister =
            materializeValue(
                result,
                temporaryRegister(
                    0,
                    bits
                )
            );

        core.storeInteger(
            destination,
            finalRegister
        );
    }

    private String emitIntegerDivision(
        String leftReg,
        String rightReg,
        int bits
    ) {

        String accumulator =
            temporaryRegister(
                0,
                bits
            );

        String high =
            temporaryRegister(
                2,
                bits
            );

        if (sameRegister(
                rightReg,
                accumulator
            ) ||
            sameRegister(
                rightReg,
                high
            )) {

            String replacement =
                temporaryRegister(
                    1,
                    bits
                );

            emitMoveIfNeeded(
                replacement,
                rightReg
            );

            rightReg =
                replacement;
        }

        emitMoveIfNeeded(
            accumulator,
            leftReg
        );

        emitSignedDividePreparation(bits);

        emitIDIV(
            rightReg,
            bits
        );

        return accumulator;
    }

    private void emitSignedDividePreparation(
        int bits
    ) {

        switch (bits) {

            case 8:

                core.builder.file_write(
                    0x66,
                    0x98
                );

                break;

            case 16:

                core.builder.file_write(
                    0x66,
                    0x99
                );

                break;

            case 32:

                core.builder.file_write(
                    0x99
                );

                break;

            case 64:

                core.builder.file_write(
                    0x48,
                    0x99
                );

                break;

            default:

                throw new IllegalArgumentException(
                    "Ancho entero inválido para división: " +
                    bits
                );
        }
    }

    private void emitIDIV(
        String register,
        int bits
    ) {

        RegisterInfo info =
            registerInfo(register);

        int rex =
            info.rexR;

        if (bits == 64)
            rex |= 0x48;

        if (bits == 8) {

            emitWithOptionalRex(
                rex,
                0xF6,
                0xF8 | info.code
            );

            return;
        }

        if (bits == 16) {

            if (rex != 0) {

                emitWithOptionalRex(
                    0x66,
                    rex,
                    0xF7,
                    0xF8 | info.code
                );

            } else {

                core.builder.file_write(
                    0x66,
                    0xF7,
                    0xF8 | info.code
                );
            }

            return;
        }

        emitWithOptionalRex(
            rex,
            0xF7,
            0xF8 | info.code
        );
    }

    private List<String> optimizeConstantPostfix(
        List<String> postfix
    ) {

        List<String> output =
            new ArrayList<>();

        Deque<ConstantState> constants =
            new ArrayDeque<>();

        for (String token : postfix) {

            if (!isOperator(token)) {

                output.add(token);

                Long value =
                    tryParseIntegerConstant(token);

                constants.push(
                    value != null
                        ? ConstantState.known(value)
                        : ConstantState.unknown()
                );

                continue;
            }

            if (token.equals("u-")) {

                if (constants.isEmpty()) {

                    output.add(token);

                    constants.push(
                        ConstantState.unknown()
                    );

                    continue;
                }

                ConstantState value =
                    constants.pop();

                if (value.known) {

                    if (!output.isEmpty())
                        output.remove(
                            output.size() - 1
                        );

                    long result =
                        -value.value;

                    output.add(
                        Long.toString(result)
                    );

                    constants.push(
                        ConstantState.known(result)
                    );

                } else {

                    output.add(token);

                    constants.push(
                        ConstantState.unknown()
                    );
                }

                continue;
            }

            if (constants.size() < 2) {

                output.add(token);

                constants.push(
                    ConstantState.unknown()
                );

                continue;
            }

            ConstantState right =
                constants.pop();

            ConstantState left =
                constants.pop();

            if (left.known &&
                right.known) {

                Long folded =
                    foldConstants(
                        token,
                        left.value,
                        right.value,
                        64
                    );

                if (folded != null) {

                    if (output.size() >= 2) {

                        output.remove(
                            output.size() - 1
                        );

                        output.remove(
                            output.size() - 1
                        );
                    }

                    output.add(
                        Long.toString(folded)
                    );

                    constants.push(
                        ConstantState.known(
                            folded
                        )
                    );

                    continue;
                }
            }

            output.add(token);

            constants.push(
                ConstantState.unknown()
            );
        }

        return output;
    }

    private Long foldConstants(
        String operator,
        long left,
        long right,
        int bits
    ) {

        long l =
            truncateToBits(
                left,
                bits
            );

        long r =
            truncateToBits(
                right,
                bits
            );

        switch (operator) {

            case "+":

                return truncateToBits(
                    l + r,
                    bits
                );

            case "-":

                return truncateToBits(
                    l - r,
                    bits
                );

            case "*":

                return truncateToBits(
                    l * r,
                    bits
                );

            case "/":

                if (r == 0L)
                    return null;

                if (l == Long.MIN_VALUE &&
                    r == -1L)

                    return null;

                return truncateToBits(
                    l / r,
                    bits
                );

            default:

                return null;
        }
    }

    private boolean isCommutative(
        String operator
    ) {

        return operator.equals("+") ||
               operator.equals("*");
    }

    private boolean isPowerOfTwo(
        long value,
        int bits
    ) {

        if (value <= 0L)
            return false;

        if (bits < 64)
            value &= (1L << bits) - 1L;

        return (value &
                (value - 1L)) == 0L;
    }

    private boolean fitsSignedImm32(
        long value
    ) {

        return value >= Integer.MIN_VALUE &&
               value <= Integer.MAX_VALUE;
    }

    private void emitShiftLeft(
        String register,
        int amount,
        int bits
    ) {

        if (amount <= 0)
            return;

        RegisterInfo info =
            registerInfo(register);

        int rex =
            info.rexR;

        if (bits == 64)
            rex |= 0x48;

        if (bits == 16) {

            if (amount == 1) {

                emit16WithOptionalRex(
                    rex,
                    0xD1,
                    0xE0 | info.code
                );

            } else {

                emit16WithOptionalRex(
                    rex,
                    0xC1,
                    0xE0 | info.code,
                    amount & 0xFF
                );
            }

            return;
        }

        if (bits == 8) {

            if (amount == 1) {

                emitWithOptionalRex(
                    rex,
                    0xD0,
                    0xE0 | info.code
                );

            } else {

                emitWithOptionalRex(
                    rex,
                    0xC0,
                    0xE0 | info.code,
                    amount & 0xFF
                );
            }

            return;
        }

        if (amount == 1) {

            emitWithOptionalRex(
                rex,
                0xD1,
                0xE0 | info.code
            );

        } else {

            emitWithOptionalRex(
                rex,
                0xC1,
                0xE0 | info.code,
                amount & 0xFF
            );
        }
    }

    private void emitAddImmediate(
        String register,
        long value,
        int bits
    ) {

        RegisterInfo info =
            registerInfo(register);

        int rex =
            info.rexR;

        if (bits == 64)
            rex |= 0x48;

        if (bits == 8) {

            emitWithOptionalRex(
                rex,
                0x80,
                0xC0 | info.code,
                ((int)value) & 0xFF
            );

            return;
        }

        if (value >= -128L &&
            value <= 127L) {

            if (bits == 16) {

                emit16WithOptionalRex(
                    rex,
                    0x83,
                    0xC0 | info.code,
                    ((int)value) & 0xFF
                );

            } else {

                emitWithOptionalRex(
                    rex,
                    0x83,
                    0xC0 | info.code,
                    ((int)value) & 0xFF
                );
            }

            return;
        }

        if (bits == 16) {

            emit16WithOptionalRex(
                rex,
                0x81,
                0xC0 | info.code,
                (int)value & 0xFF,
                ((int)value >>> 8) & 0xFF
            );

        } else {

            writeImm32(
                rex,
                0x81,
                0xC0 | info.code,
                (int)value
            );
        }
    }

    private void emitSubImmediate(
        String register,
        long value,
        int bits
    ) {

        RegisterInfo info =
            registerInfo(register);

        int rex =
            info.rexR;

        if (bits == 64)
            rex |= 0x48;

        if (bits == 8) {

            emitWithOptionalRex(
                rex,
                0x80,
                0xE8 | info.code,
                ((int)value) & 0xFF
            );

            return;
        }

        if (value >= -128L &&
            value <= 127L) {

            if (bits == 16) {

                emit16WithOptionalRex(
                    rex,
                    0x83,
                    0xE8 | info.code,
                    ((int)value) & 0xFF
                );

            } else {

                emitWithOptionalRex(
                    rex,
                    0x83,
                    0xE8 | info.code,
                    ((int)value) & 0xFF
                );
            }

            return;
        }

        if (bits == 16) {

            emit16WithOptionalRex(
                rex,
                0x81,
                0xE8 | info.code,
                (int)value & 0xFF,
                ((int)value >>> 8) & 0xFF
            );

        } else {

            writeImm32(
                rex,
                0x81,
                0xE8 | info.code,
                (int)value
            );
        }
    }

    private static final class RegisterInfo {

        final int code;
        final int rexR;

        RegisterInfo(
            int code,
            int rexR
        ) {

            this.code = code;
            this.rexR = rexR;
        }
    }

    private RegisterInfo registerInfo(
        String register
    ) {

        String r =
            baseRegister(register);

        switch (r) {

            case "RAX":
                return new RegisterInfo(0, 0);

            case "RCX":
                return new RegisterInfo(1, 0);

            case "RDX":
                return new RegisterInfo(2, 0);

            case "RBX":
                return new RegisterInfo(3, 0);

            case "RSP":
                return new RegisterInfo(4, 0);

            case "RBP":
                return new RegisterInfo(5, 0);

            case "RSI":
                return new RegisterInfo(6, 0);

            case "RDI":
                return new RegisterInfo(7, 0);

            case "R8":
                return new RegisterInfo(0, 0x04);

            case "R9":
                return new RegisterInfo(1, 0x04);

            case "R10":
                return new RegisterInfo(2, 0x04);

            case "R11":
                return new RegisterInfo(3, 0x04);

            case "R12":
                return new RegisterInfo(4, 0x04);

            case "R13":
                return new RegisterInfo(5, 0x04);

            case "R14":
                return new RegisterInfo(6, 0x04);

            case "R15":
                return new RegisterInfo(7, 0x04);

            default:

                throw new IllegalArgumentException(
                    "Registro no soportado: " +
                    register
                );
        }
    }

    private String baseRegister(
        String register
    ) {

        if (register == null)
            throw new IllegalArgumentException(
                "Registro null"
            );

        String r =
            register.toUpperCase(Locale.ROOT);

        switch (r) {

            case "AL":
            case "AH":
            case "AX":
            case "EAX":
            case "RAX":
                return "RAX";

            case "CL":
            case "CH":
            case "CX":
            case "ECX":
            case "RCX":
                return "RCX";

            case "DL":
            case "DH":
            case "DX":
            case "EDX":
            case "RDX":
                return "RDX";

            case "BL":
            case "BH":
            case "BX":
            case "EBX":
            case "RBX":
                return "RBX";

            case "SPL":
            case "SP":
            case "ESP":
            case "RSP":
                return "RSP";

            case "BPL":
            case "BP":
            case "EBP":
            case "RBP":
                return "RBP";

            case "SIL":
            case "SI":
            case "ESI":
            case "RSI":
                return "RSI";

            case "DIL":
            case "DI":
            case "EDI":
            case "RDI":
                return "RDI";

            default:

                for (int i = 8; i <= 15; i++) {

                    String n = "R" + i;

                    if (r.equals(n) ||
                        r.equals(n + "B") ||
                        r.equals(n + "W") ||
                        r.equals(n + "D")) {

                        return n;
                    }
                }
        }

        throw new IllegalArgumentException(
            "Registro no soportado: " +
            register
        );
    }

    private void writeImm32(
        int rex,
        int opcode,
        int modrm,
        int value
    ) {

        emitWithOptionalRex(
            rex,
            opcode,
            modrm,
            value & 0xFF,
            (value >>> 8) & 0xFF,
            (value >>> 16) & 0xFF,
            (value >>> 24) & 0xFF
        );
    }

    private void emitWithOptionalRex(
        int rex,
        int... bytes
    ) {

        if (rex != 0) {

            int[] output =
                new int[bytes.length + 1];

            output[0] =
                rex & 0xFF;

            System.arraycopy(
                bytes,
                0,
                output,
                1,
                bytes.length
            );

            core.builder.file_write(
                output
            );

        } else {

            core.builder.file_write(
                bytes
            );
        }
    }

    private void emit16WithOptionalRex(
        int rex,
        int... bytes
    ) {

        if (rex != 0) {

            int[] output =
                new int[bytes.length + 2];

            output[0] =
                0x66;

            output[1] =
                rex & 0xFF;

            System.arraycopy(
                bytes,
                0,
                output,
                2,
                bytes.length
            );

            core.builder.file_write(
                output
            );

        } else {

            int[] output =
                new int[bytes.length + 1];

            output[0] =
                0x66;

            System.arraycopy(
                bytes,
                0,
                output,
                1,
                bytes.length
            );

            core.builder.file_write(
                output
            );
        }
    }

    private void spillTopRegister(
        IntegerExpressionStack stack
    ) {

        stack.spillTopRegister();
    }

    private String chooseValueRegister(
        IntegerExpressionStack stack,
        int bits
    ) {

        for (int code :
             INTEGER_TEMP_CODES) {

            String register =
                temporaryRegister(
                    code,
                    bits
                );

            if (!stack.containsRegister(register))
                return register;
        }

        return null;
    }

    private String chooseRightRegister(
        String left,
        int bits
    ) {

        int[] preferred = {
            1,
            2,
            0
        };

        for (int code : preferred) {

            String register =
                temporaryRegister(
                    code,
                    bits
                );

            if (!sameRegister(
                register,
                left
            )) {

                return register;
            }
        }

        return temporaryRegister(
            1,
            bits
        );
    }

    private String materializeValue(
        IntegerValue value,
        String preferredRegister
    ) {

        if (value.isRegister())
            return value.location;

        if (value.isStack()) {

            core.alu.POP(
                preferredRegister
            );

            return preferredRegister;
        }

        throw new IllegalStateException(
            "Ubicación de expresión desconocida: " +
            value.location
        );
    }

private Long tryParseIntegerConstant(String text) {
    if (text == null) {
        return null;
    }

    String s = text.trim();

    if (s.isEmpty()) {
        return null;
    }

    try {
        boolean negative = s.startsWith("-");

        String value = negative ? s.substring(1) : s;

        if (value.isEmpty()) {
            return null;
        }

        int radix = 10;

        if (value.startsWith("0x") || value.startsWith("0X")) {
            radix = 16;
            value = value.substring(2);
        } else if (value.startsWith("0b") || value.startsWith("0B")) {
            radix = 2;
            value = value.substring(2);
        } else if (value.startsWith("0o") || value.startsWith("0O")) {
            radix = 8;
            value = value.substring(2);
        }

        if (value.isEmpty()) {
            return null;
        }

        long result = Long.parseUnsignedLong(value, radix);

        return negative ? -result : result;

    } catch (NumberFormatException e) {
        return null;
    }
}
    private void expressionIntegerToRegister(
        String token,
        String destination
    ) {

        int destinationBits =
            bitsOfRegister(destination);

        expressionIntegerToRegister(
            token,
            destination,
            destinationBits
        );
    }

    private void expressionIntegerToRegister(
        String token,
        String destination,
        int destinationBits
    ) {

        Long constant =
            tryParseIntegerConstant(token);

        if (constant != null) {

            core.mov.MOV(
                destination,
                truncateToBits(
                    constant,
                    destinationBits
                )
            );

            return;
        }

        Vars.Variable v =
            core.localVariable(token);

        if (v != null) {

            core.ensureInteger(v);

            int sourceBits =
                integerBits(v.type);

            loadIntegerConverted(
                v,
                sourceBits,
                destination,
                destinationBits
            );

            return;
        }

        Vars.Parameter p =
            core.parameter(token);

        if (p != null) {

            if (!p.isInteger())
                throw new IllegalArgumentException(
                    "El parámetro no es entero: " +
                    token
                );

            int sourceBits =
                integerBits(p.type);

            loadIntegerParameterConverted(
                p,
                sourceBits,
                destination,
                destinationBits
            );

            return;
        }

        throw new IllegalArgumentException(
            "Símbolo no existe: " +
            token
        );
    }

    private void loadIntegerConverted(
        Vars.Variable source,
        int sourceBits,
        String destination,
        int destinationBits
    ) {

        if (sourceBits == destinationBits) {

            core.loadInteger(
                source,
                destination
            );

            return;
        }

        if (destinationBits < sourceBits) {

            String sourceRegister =
                persistentRegisterForVariable(
                    source,
                    sourceBits
                );

            String temporary =
                temporaryRegister(
                    0,
                    sourceBits
                );

            core.loadInteger(
                source,
                temporary
            );

            String narrowed =
                temporaryRegister(
                    0,
                    destinationBits
                );

            if (!sameRegister(
                narrowed,
                destination
            )) {

                core.mov.MOV(
                    destination,
                    narrowed
                );
            }

            return;
        }

        String sourceTemp =
            temporaryRegister(
                0,
                sourceBits
            );

        core.loadInteger(
            source,
            sourceTemp
        );

        if (sourceBits == 8)
            core.builder.file_write(0x0F, 0xB6, 0xC0);
        else if (sourceBits == 16)
            core.builder.file_write(0x0F, 0xB7, 0xC0);

        String widened =
            temporaryRegister(
                0,
                destinationBits
            );

        if (core.signedLookup.test(source.name))
            core.signExtendInPlace(widened, sourceBits);

        if (!sameRegister(
            widened,
            destination
        )) {

            if (!sameRegister(
                sourceTemp,
                widened
            )) {

                core.mov.MOV(
                    destination,
                    widened
                );
            }
        }
    }

    private void loadIntegerParameterConverted(
        Vars.Parameter source,
        int sourceBits,
        String destination,
        int destinationBits
    ) {

        if (sourceBits == destinationBits) {

            core.loadIntegerParameter(
                source,
                destination
            );

            return;
        }

        String sourceTemp =
            temporaryRegister(
                0,
                sourceBits
            );

        core.loadIntegerParameter(
            source,
            sourceTemp
        );

        if (destinationBits < sourceBits) {

            String destinationSub =
                temporaryRegister(
                    0,
                    destinationBits
                );

            if (!sameRegister(
                destinationSub,
                destination
            )) {

                core.mov.MOV(
                    destination,
                    destinationSub
                );
            }

            return;
        }

        String widened =
            temporaryRegister(
                0,
                destinationBits
            );

        if (!sameRegister(
            widened,
            destination
        )) {

            if (!sameRegister(
                sourceTemp,
                widened
            )) {

                core.mov.MOV(
                    destination,
                    widened
                );
            }
        }
    }

    private String persistentRegisterForVariable(
        Vars.Variable variable,
        int bits
    ) {

        return temporaryRegister(
            0,
            bits
        );
    }

    private int bitsOfRegister(
        String register
    ) {

        String r =
            register.toUpperCase(
                Locale.ROOT
            );

        if (r.endsWith("B"))
            return 8;

        if (r.endsWith("W"))
            return 16;

        if (r.endsWith("D"))
            return 32;

        switch (r) {

            case "AL":
            case "CL":
            case "DL":
            case "BL":
                return 8;

            case "AX":
            case "CX":
            case "DX":
            case "BX":
                return 16;

            case "EAX":
            case "ECX":
            case "EDX":
            case "EBX":
            case "ESP":
            case "EBP":
            case "ESI":
            case "EDI":
                return 32;

            default:
                return 64;
        }
    }

    void expressionIntegerToRAX(
        String token
    ) {

        expressionIntegerToRegister(
            token,
            "RAX",
            64
        );
    }

    private void emitMoveIfNeeded(
        String destination,
        String source
    ) {

        if (destination == null ||
            source == null)

            return;

        if (sameRegister(
            destination,
            source
        ))

            return;

        core.mov.MOV(
            destination,
            source
        );
    }

    private static boolean isIntegerTemporary(
        String register
    ) {

        if (register == null)
            return false;

        String base =
            baseRegisterStatic(register);

        return base.equals("RAX") ||
               base.equals("RCX") ||
               base.equals("RDX");
    }

    private static boolean isIntegerPersistent(
        String register
    ) {

        if (register == null)
            return false;

        String base =
            baseRegisterStatic(register);

        for (int code :
             INTEGER_PERSISTENT_CODES) {

            if (base.equals(
                "R" + code
            )) {

                return true;
            }
        }

        return false;
    }

    private static boolean isIntegerRegister(
        String register
    ) {

        return isIntegerTemporary(register) ||
               isIntegerPersistent(register);
    }

    private void evaluateFloatExpression(
        Vars.Variable destination,
        String expression
    ) {

        List<String> postfix =
            toPostfix(
                tokenizeFloat(expression)
            );

        int values = 0;

        for (String token : postfix) {

            if (!isOperator(token)) {

                expressionFloatToXMM0(
                    token,
                    destination.type
                );

                pushXmm(0);

                values++;

                continue;
            }

            if (token.equals("u-")) {

                popXmm(0);

                core.loadFloatConstant(
                    core.newConstantFloat(
                        destination.type,
                        0.0
                    ),
                    1
                );

                core.emitSseArithmetic(
                    "SUB",
                    1,
                    0,
                    destination.type
                );

                core.emitSseMoveRegReg(
                    0,
                    1,
                    destination.type
                );

                pushXmm(0);

                continue;
            }

            popXmm(1);
            popXmm(0);

            String op;

            switch (token) {

                case "+":
                    op = "ADD";
                    break;

                case "-":
                    op = "SUB";
                    break;

                case "*":
                    op = "MUL";
                    break;

                case "/":
                    op = "DIV";
                    break;

                default:

                    throw new IllegalArgumentException(
                        "Operador floating inválido"
                    );
            }

            core.emitSseArithmetic(
                op,
                0,
                1,
                destination.type
            );

            pushXmm(0);

            values--;
        }

        if (values != 1)
            throw new IllegalArgumentException(
                "Expresión floating inválida"
            );

        popXmm(0);

        core.storeFloat(
            destination,
            "XMM0"
        );
    }

    private void pushXmm(
        int xmm
    ) {

        core.startStackFrame();

        int offset =
            core.nextStackOffset +
            core.expressionStackDepth * 8;

        String mem =
            "[RBP-" +
            offset +
            "]";

        core.emitSseStore(
            mem,
            xmm,
            Vars.FLOAT64
        );

        core.expressionStackDepth++;
    }

    private void popXmm(
        int xmm
    ) {

        if (core.expressionStackDepth <= 0)
            throw new IllegalStateException(
                "Stack floating vacío"
            );

        core.expressionStackDepth--;

        int offset =
            core.nextStackOffset +
            core.expressionStackDepth * 8;

        String mem =
            "[RBP-" +
            offset +
            "]";

        core.emitSseLoad(
            xmm,
            mem,
            Vars.FLOAT64
        );
    }

    private void expressionFloatToXMM0(
        String token,
        int destinationType
    ) {

        Double number =
            tryParseFloat(token);

        if (number != null) {

            if (destinationType ==
                Vars.FLOAT32) {

                int bits =
                    Float.floatToRawIntBits(
                        number.floatValue()
                    );

                core.mov.MOV(
                    "EAX",
                    bits & 0xFFFFFFFFL
                );

                core.emitMovD_XmmFromEax(0);

            } else {

                long bits =
                    Double.doubleToRawLongBits(
                        number
                    );

                core.mov.MOV(
                    "RAX",
                    bits
                );

                core.emitMovQ_XmmFromRax(0);
            }

            return;
        }

        Vars.Variable v =
            core.localVariable(token);

        if (v != null) {

            core.ensureFloat(v);

            if (v.type != destinationType)
                throw new IllegalArgumentException(
                    "Tipos floating incompatibles"
                );

            core.loadFloat(
                v,
                "XMM0"
            );

            return;
        }

        Vars.Parameter p =
            core.parameter(token);

        if (p != null) {

            if (!p.isFloat())
                throw new IllegalArgumentException(
                    "El parámetro no es floating"
                );

            if (p.type != destinationType)
                throw new IllegalArgumentException(
                    "Tipos floating incompatibles"
                );

            core.loadFloatParameter(
                p,
                "XMM0"
            );

            return;
        }

        throw new IllegalArgumentException(
            "Símbolo no existe: " +
            token
        );
    }

    private List<String> tokenize(
        String expression
    ) {

        List<String> out =
            new ArrayList<>();

        StringBuilder cur =
            new StringBuilder();

        for (int i = 0;
             i < expression.length();
             i++) {

            char c =
                expression.charAt(i);

            if (Character.isWhitespace(c))
                continue;

            if (Character.isLetterOrDigit(c) ||
                c == '_' ||
                c == '$') {

                cur.append(c);

                continue;
            }

            if (cur.length() != 0) {

                out.add(
                    cur.toString()
                );

                cur.setLength(0);
            }

            if (c == '+' ||
                c == '-' ||
                c == '*' ||
                c == '/' ||
                c == '(' ||
                c == ')') {

                out.add(
                    String.valueOf(c)
                );

                continue;
            }

            throw new IllegalArgumentException(
                "Carácter inválido: " +
                c
            );
        }

        if (cur.length() != 0)
            out.add(cur.toString());

        return out;
    }

    private List<String> tokenizeFloat(
        String expression
    ) {

        List<String> out =
            new ArrayList<>();

        StringBuilder cur =
            new StringBuilder();

        boolean exponent = false;

        for (int i = 0;
             i < expression.length();
             i++) {

            char c =
                expression.charAt(i);

            if (Character.isWhitespace(c))
                continue;

            if (Character.isLetterOrDigit(c) ||
                c == '_' ||
                c == '$' ||
                c == '.') {

                cur.append(c);

                if (c == 'e' ||
                    c == 'E') {

                    exponent = true;
                }

                continue;
            }

            if ((c == '+' ||
                 c == '-') &&
                exponent &&
                cur.length() > 0 &&
                (cur.charAt(
                    cur.length() - 1
                ) == 'e' ||
                 cur.charAt(
                    cur.length() - 1
                ) == 'E')) {

                cur.append(c);

                exponent = false;

                continue;
            }

            if (cur.length() != 0) {

                out.add(
                    cur.toString()
                );

                cur.setLength(0);

                exponent = false;
            }

            if (c == '+' ||
                c == '-' ||
                c == '*' ||
                c == '/' ||
                c == '(' ||
                c == ')') {

                out.add(
                    String.valueOf(c)
                );

                continue;
            }

            throw new IllegalArgumentException(
                "Carácter inválido floating: " +
                c
            );
        }

        if (cur.length() != 0)
            out.add(cur.toString());

        return out;
    }

    private List<String> toPostfix(
        List<String> tokens
    ) {

        List<String> output =
            new ArrayList<>();

        Deque<String> operators =
            new ArrayDeque<>();

        String previous = null;

        for (String token : tokens) {

            if (isValueToken(token)) {

                output.add(token);

                previous = token;

                continue;
            }

            if (token.equals("(")) {

                operators.push(token);

                previous = token;

                continue;
            }

            if (token.equals(")")) {

                while (!operators.isEmpty() &&
                       !operators.peek().equals("(")) {

                    output.add(
                        operators.pop()
                    );
                }

                if (operators.isEmpty())
                    throw new IllegalArgumentException(
                        "Paréntesis incorrectos"
                    );

                operators.pop();

                previous = token;

                continue;
            }

            if (token.equals("-") &&
                (previous == null ||
                 previous.equals("(") ||
                 isOperator(previous))) {

                operators.push("u-");

                previous = token;

                continue;
            }

            while (!operators.isEmpty() &&
                   !operators.peek().equals("(") &&
                   precedence(
                       operators.peek()
                   ) >=
                   precedence(token)) {

                output.add(
                    operators.pop()
                );
            }

            operators.push(token);

            previous = token;
        }

        while (!operators.isEmpty()) {

            String op =
                operators.pop();

            if (op.equals("("))
                throw new IllegalArgumentException(
                    "Paréntesis incorrectos"
                );

            output.add(op);
        }

        return output;
    }

    private boolean isValueToken(
        String token
    ) {

        return !token.equals("(") &&
               !token.equals(")") &&
               !isOperator(token);
    }

    private boolean isOperator(
        String token
    ) {

        return token.equals("+") ||
               token.equals("-") ||
               token.equals("*") ||
               token.equals("/") ||
               token.equals("u-");
    }

    private int precedence(
        String op
    ) {

        switch (op) {

            case "u-":
                return 3;

            case "*":
            case "/":
                return 2;

            case "+":
            case "-":
                return 1;

            default:
                return 0;
        }
    }

    private long parseInteger(
        String value
    ) {

        String s =
            value.trim();

        boolean negative = false;

        if (s.startsWith("-")) {

            negative = true;
            s = s.substring(1);
        }

        long result;

        if (s.startsWith("0x") ||
            s.startsWith("0X")) {

            result =
                Long.parseUnsignedLong(
                    s.substring(2),
                    16
                );

        } else if (
            s.startsWith("0b") ||
            s.startsWith("0B")
        ) {

            result =
                Long.parseUnsignedLong(
                    s.substring(2),
                    2
                );

        } else {

            result =
                new java.math.BigInteger(s).longValue();
        }

        return negative
            ? -result
            : result;
    }

    private Double tryParseFloat(
        String value
    ) {

        String s =
            value.trim();

        if (!s.contains(".") &&
            !s.contains("e") &&
            !s.contains("E"))

            return null;

        try {

            return Double.parseDouble(s);

        } catch (
            NumberFormatException e
        ) {

            return null;
        }
    }

    private int integerBits(
        int type
    ) {

        switch (type) {

            case Vars.BYTE:
                return 8;

            case Vars.WORD:
                return 16;

            case Vars.DWORD:
                return 32;

            case Vars.QWORD:
                return 64;

            default:

                throw new IllegalArgumentException(
                    "Tipo entero no soportado: " +
                    type
                );
        }
    }

    private String temporaryRegister(
        int code,
        int bits
    ) {

        switch (bits) {

            case 8:

                switch (code) {
                    case 0: return "AL";
                    case 1: return "CL";
                    case 2: return "DL";
                    default:
                        throw new IllegalArgumentException(
                            "Temporal inválido: " +
                            code
                        );
                }

            case 16:

                switch (code) {
                    case 0: return "AX";
                    case 1: return "CX";
                    case 2: return "DX";
                    default:
                        throw new IllegalArgumentException(
                            "Temporal inválido: " +
                            code
                        );
                }

            case 32:

                switch (code) {
                    case 0: return "EAX";
                    case 1: return "ECX";
                    case 2: return "EDX";
                    default:
                        throw new IllegalArgumentException(
                            "Temporal inválido: " +
                            code
                        );
                }

            case 64:

                switch (code) {
                    case 0: return "RAX";
                    case 1: return "RCX";
                    case 2: return "RDX";
                    default:
                        throw new IllegalArgumentException(
                            "Temporal inválido: " +
                            code
                        );
                }

            default:

                throw new IllegalArgumentException(
                    "Bits enteros inválidos: " +
                    bits
                );
        }
    }

    private long truncateToBits(
        long value,
        int bits
    ) {

        switch (bits) {

            case 8:
                return value & 0xFFL;

            case 16:
                return value & 0xFFFFL;

            case 32:
                return value & 0xFFFFFFFFL;

            case 64:
                return value;

            default:

                throw new IllegalArgumentException(
                    "Bits inválidos: " +
                    bits
                );
        }
    }

    private boolean sameRegister(
        String a,
        String b
    ) {

        if (a == null ||
            b == null)

            return false;

        return baseRegister(a)
            .equalsIgnoreCase(
                baseRegister(b)
            );
    }

    private static boolean sameRegisterFamily(
        String a,
        String b
    ) {

        if (a == null ||
            b == null)

            return false;

        return baseRegisterStatic(a)
            .equalsIgnoreCase(
                baseRegisterStatic(b)
            );
    }

    private static String baseRegisterStatic(
        String register
    ) {

        String r =
            register.toUpperCase(
                Locale.ROOT
            );

        switch (r) {

            case "AL":
            case "AH":
            case "AX":
            case "EAX":
            case "RAX":
                return "RAX";

            case "CL":
            case "CH":
            case "CX":
            case "ECX":
            case "RCX":
                return "RCX";

            case "DL":
            case "DH":
            case "DX":
            case "EDX":
            case "RDX":
                return "RDX";

            case "BL":
            case "BH":
            case "BX":
            case "EBX":
            case "RBX":
                return "RBX";

            case "SPL":
            case "SP":
            case "ESP":
            case "RSP":
                return "RSP";

            case "BPL":
            case "BP":
            case "EBP":
            case "RBP":
                return "RBP";

            case "SIL":
            case "SI":
            case "ESI":
            case "RSI":
                return "RSI";

            case "DIL":
            case "DI":
            case "EDI":
            case "RDI":
                return "RDI";

            default:

                for (int i = 8;
                     i <= 15;
                     i++) {

                    String n =
                        "R" + i;

                    if (r.equals(n) ||
                        r.equals(n + "B") ||
                        r.equals(n + "W") ||
                        r.equals(n + "D")) {

                        return n;
                    }
                }
        }

        return r;
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
