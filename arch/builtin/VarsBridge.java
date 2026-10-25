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

public final class VarsBridge {

    private final Vars vars;

    public VarsBridge(Vars vars) {
        this.vars = vars;
    }

    public int abiIntegerRegisterCount() {
        return Vars.ABI_INTEGER_REGISTERS.length;
    }

    public String abiRegister(
        int index,
        int type
    ) {
        return vars.registerName(
            Vars.ABI_INTEGER_REGISTERS[index],
            type
        );
    }

    public void movImm(
        String register,
        long value
    ) {
        vars.mov.MOV(
            register,
            value
        );
    }

    public void adjustRsp(long delta) {

        if (delta > 0)
            vars.alu.ADD("RSP", delta);
        else if (delta < 0)
            vars.alu.SUB("RSP", -delta);
    }

    public void pushRax() {
        vars.alu.PUSH("RAX");
    }

    public void pushXmm0(boolean isDouble) {

        adjustRsp(-8);

        vars.builder.file_write(
            isDouble ? 0xF2 : 0xF3,
            0x0F,
            0x11,
            0x04,
            0x24
        );
    }

    public boolean loadLocal(
        String name,
        String register
    ) {

        if (name == null)
            return false;

        Vars.Variable variable =
            vars.localVariable(name);

        if (variable == null)
            return false;

        if (
            variable.type == Vars.FLOAT32 ||
            variable.type == Vars.FLOAT64
        ) {
            return false;
        }

        String target = register;

        if (variable.bits() == 32) {
            String alias = alias32(register);
            if (alias != null)
                target = alias;
        }

        vars.loadInteger(
            variable,
            target
        );

        return true;
    }

    private static String alias32(String reg) {

        if (reg == null)
            return null;

        switch (reg.toUpperCase()) {
            case "RAX": return "EAX";
            case "RCX": return "ECX";
            case "RDX": return "EDX";
            case "RBX": return "EBX";
            case "RSI": return "ESI";
            case "RDI": return "EDI";
            case "R8":  return "R8D";
            case "R9":  return "R9D";
            case "R10": return "R10D";
            case "R11": return "R11D";
            default:    return null;
        }
    }

    public boolean loadParameter(
        String name,
        String register
    ) {

        if (name == null)
            return false;

        Vars.Parameter parameter =
            vars.parameter(name);

        if (parameter == null)
            return false;

        if (
            parameter.type == Vars.FLOAT32 ||
            parameter.type == Vars.FLOAT64
        ) {
            return false;
        }

        vars.loadParameterInteger(
            parameter,
            register
        );

        return true;
    }

    public boolean loadFloat(
        String name,
        String register
    ) {

        if (name == null)
            return false;

        Vars.Variable variable =
            vars.localVariable(name);

        if (variable == null) {

            variable =
                vars.variable(name);
        }

        if (variable == null)
            return false;

        if (
            variable.type != Vars.FLOAT32 &&
            variable.type != Vars.FLOAT64
        ) {
            return false;
        }

        vars.loadFloat(
            variable,
            register
        );

        return true;
    }

    public boolean loadFloatParameter(
        String name,
        String register
    ) {

        if (name == null)
            return false;

        Vars.Parameter parameter =
            vars.parameter(name);

        if (parameter == null)
            return false;

        if (
            parameter.type != Vars.FLOAT32 &&
            parameter.type != Vars.FLOAT64
        ) {
            return false;
        }

        vars.loadParameterFloat(
            parameter,
            register
        );

        return true;
    }

    public boolean storeLocalFromRax(
        String name
    ) {

        if (name == null)
            return false;

        Vars.Variable variable =
            vars.localVariable(name);

        if (variable == null)
            return false;

        if (
            variable.type == Vars.FLOAT32 ||
            variable.type == Vars.FLOAT64
        ) {
            return false;
        }

        vars.ensureInteger(variable);

        vars.storeInteger(
            variable,
            vars.temporaryInteger(
                variable.bits()
            )
        );

        return true;
    }

    public void If(
        String cond
    ) {
        vars.control.If(cond);
    }

    public void ElseIf(
        String cond
    ) {
        vars.control.ElseIf(cond);
    }

    public void Else() {
        vars.control.Else();
    }

    public void EndIf() {
        vars.control.EndIf();
    }

    public void While(
        String cond
    ) {
        vars.control.While(cond);
    }

    public void EndWhile() {
        vars.control.EndWhile();
    }

    public void Break() {
        vars.control.Break();
    }

    public void Continue() {
        vars.control.Continue();
    }

    public void EndFor() {
        vars.control.EndFor();
    }

    public void labelNamed(
        String name
    ) {
        vars.control.labelNamed(name);
    }

    public void jmpNamed(
        String name
    ) {
        vars.control.jmpNamed(name);
    }

    public void branch(
        String lhs,
        String op,
        String rhs,
        String label,
        boolean when
    ) {
        vars.control.branch(
            lhs,
            op,
            rhs,
            label,
            when
        );
    }

    public void For(
        String variable,
        String start,
        String end,
        String step
    ) {
        vars.control.For(
            variable,
            start,
            end,
            step
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
