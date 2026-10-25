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

import java.util.ArrayList;
import java.util.List;

public final class Ast {

    private Ast() {
    }

    public static class Program {
        public long originOffset;
        public int bits = 64;
        public boolean originSpecified;
        public boolean bitsSpecified;

        public final java.util.Map<String, Long> overrides = new java.util.LinkedHashMap<>();

        public final List<GlobalVar> globals = new ArrayList<>();

        public final List<Import> imports = new ArrayList<>();
        public final List<Function> functions = new ArrayList<>();
        public final List<Extern> externs = new ArrayList<>();
    }

    public static class Import {
        public String path;
        public boolean system;
        public boolean wildcard;
        public boolean recursive;

        public Import(String path, boolean system) {
            this(path, system, false, false);
        }

        public Import(
                String path,
                boolean system,
                boolean wildcard,
                boolean recursive
        ) {
            this.path = path;
            this.system = system;
            this.wildcard = wildcard;
            this.recursive = recursive;
        }

        public boolean isSystem() {
            return system;
        }

        public boolean isProject() {
            return !system;
        }

        public boolean isWildcard() {
            return wildcard;
        }

        public boolean isRecursive() {
            return recursive;
        }

        public String normalizedPath() {
            String value = path == null ? "" : path.trim();

            while (value.startsWith(".")) {
                value = value.substring(1);
            }

            while (value.startsWith("/")) {
                value = value.substring(1);
            }

            while (value.endsWith("/")) {
                value = value.substring(0, value.length() - 1);
            }

            return value
                    .replace('\\', '/')
                    .replace('.', '/');
        }

        public String qualifiedName() {
            return path;
        }

        @Override
        public String toString() {
            StringBuilder out = new StringBuilder();

            out.append("import ");

            if (system) {
                out.append("<");
            } else {
                out.append("\"");
            }

            out.append(path);

            if (system) {
                out.append(">");
            } else {
                out.append("\"");
            }

            return out.toString();
        }
    }

    public static class Extern {
        public String name;
        public String alias;
        public String returnType;

        public boolean variadic;

        public final List<Parameter> parameters =
                new ArrayList<>();

        public Extern(
                String name,
                String alias,
                String returnType
        ) {
            this.name = name;
            this.alias = alias;
            this.returnType = returnType;
        }
    }

    public static class Function {
        public String name;
        public String returnType;

        public boolean variadic;
        public int fixedParams;

        public final List<Crack> cracks = new ArrayList<>();

        public long override = 0;
        public boolean overrideSet;

        public boolean overrideExact;

        public String origin;

        public String owner;
        public boolean entry;
        public boolean isPrivate;

        public boolean naked;

        public boolean efiapi;

        public String[] section;

        public final List<Parameter> parameters =
                new ArrayList<>();

        public final List<Statement> statements =
                new ArrayList<>();
    }

    public static class Crack {
        public String name;
        public final List<Parameter> params = new ArrayList<>();
        public final List<Statement> body = new ArrayList<>();
    }

    public static class UnreturnableStmt implements Statement {
        public Expression value;
        public String typeHint;
    }

    public static class MemberAssignStmt implements Statement {
        public String base;
        public String member;
        public boolean arrow;
        public final List<Expression> values = new ArrayList<>();
    }

    public static class GlobalVar {
        public String name;
        public String rawType;

        public String type;
        public boolean isStatic;
        public boolean general;
        public boolean ungeneral;
        public long value;
        public String origin;

        public String[] section;

        public String elemType;
        public java.util.List<java.math.BigInteger> elements;
        public int count;

        public boolean runnable;
    }

    public static class ChainExpr implements Expression {
        public Expression base;
        public final String member;
        public final boolean arrow;

        public ChainExpr(Expression base, String member, boolean arrow) {
            this.base = base;
            this.member = member;
            this.arrow = arrow;
        }
    }

    public static class PtrCallExpr implements Expression {
        public Expression target;
        public final List<Expression> arguments = new ArrayList<>();

        public PtrCallExpr(Expression target) {
            this.target = target;
        }
    }

    public static class MemberExpr implements Expression {
        public final String base;
        public final String member;
        public final boolean arrow;

        public MemberExpr(String base, String member, boolean arrow) {
            this.base = base;
            this.member = member;
            this.arrow = arrow;
        }
    }

    public static class Parameter {

        public String rawType;
        public String type;
        public String name;

        public Parameter(
                String type,
                String name
        ) {
            this.type = type;
            this.name = name;
        }
    }

    public interface Statement {
    }

public static class IncDecStmt
        implements Statement {

    public String operator;
    public String variable;

    public IncDecStmt(
            String operator,
            String variable
    ) {
        this.operator = operator;
        this.variable = variable;
    }
}
    public static class VariableDecl
            implements Statement {

        public String type;

        public String rawType;
        public String name;
        public Expression value;
        public boolean volatileVariable;

        public VariableDecl(
                String type,
                String name,
                Expression value
        ) {
            this(
                    type,
                    name,
                    value,
                    false
            );
        }

        public VariableDecl(
                String type,
                String name,
                Expression value,
                boolean volatileVariable
        ) {
            this.type = type;
            this.name = name;
            this.value = value;
            this.volatileVariable =
                    volatileVariable;
        }
    }

    public static class Assignment
            implements Statement {

        public Expression target;
        public Expression value;
        public String name;

        public Assignment(
                String name,
                Expression value
        ) {
            this.name = name;

            this.target =
                    new ValueExpr(
                            name,
                            "identifier"
                    );

            this.value = value;
        }

        public Assignment(
                Expression target,
                Expression value
        ) {
            this.target = target;
            this.value = value;

            this.name =
                    target instanceof ValueExpr
                            ? ((ValueExpr) target).value
                            : null;
        }
    }

    public static class ReturnStmt
            implements Statement {

        public Expression value;

        public boolean terminate;

        public ReturnStmt(
                Expression value
        ) {
            this.value = value;
        }
    }

    public static class ExpressionStmt
            implements Statement {

        public Expression expression;

        public ExpressionStmt(
                Expression expression
        ) {
            this.expression = expression;
        }
    }

    public static class InjectBytesStmt
            implements Statement {

        public enum Kind {
            BYTES,
            BYTE,
            WORD,
            DWORD,
            QWORD
        }

        public final Kind kind;

        public final List<Expression> bytes =
                new ArrayList<>();

        public final boolean volatileWrite;

        public InjectBytesStmt(
                Kind kind,
                boolean volatileWrite
        ) {
            this.kind = kind;
            this.volatileWrite =
                    volatileWrite;
        }
    }

    public static class IfStmt
            implements Statement {

        public Expression condition;

        public final List<Statement> thenBranch =
                new ArrayList<>();

        public final List<Statement> elseBranch =
                new ArrayList<>();

        public IfStmt(
                Expression condition,
                List<Statement> thenBranch,
                List<Statement> elseBranch
        ) {
            this.condition = condition;

            if (thenBranch != null) {
                this.thenBranch.addAll(
                        thenBranch
                );
            }

            if (elseBranch != null) {
                this.elseBranch.addAll(
                        elseBranch
                );
            }
        }
    }

    public static class WhileStmt
            implements Statement {

        public Expression condition;

        public final List<Statement> body =
                new ArrayList<>();

        public WhileStmt(
                Expression condition,
                List<Statement> body
        ) {
            this.condition = condition;

            if (body != null) {
                this.body.addAll(body);
            }
        }
    }

    public static class ForStmt
            implements Statement {

        public Statement init;
        public Expression condition;
        public Statement update;

        public final List<Statement> body =
                new ArrayList<>();

        public ForStmt(
                Statement init,
                Expression condition,
                Statement update,
                List<Statement> body
        ) {
            this.init = init;
            this.condition = condition;
            this.update = update;

            if (body != null) {
                this.body.addAll(body);
            }
        }
    }

    public static class BreakStmt
            implements Statement {
    }

    public static class ContinueStmt
            implements Statement {
    }

    public interface Expression {
    }

    public static class ValueExpr
            implements Expression {

        public String value;
        public String type;

        public ValueExpr(
                String value,
                String type
        ) {
            this.value = value;
            this.type = type;
        }
    }

    public static class BinaryExpr
            implements Expression {

        public Expression left;
        public Expression right;
        public String operator;

        public BinaryExpr(
                Expression left,
                String operator,
                Expression right
        ) {
            this.left = left;
            this.operator = operator;
            this.right = right;
        }
    }

    public static class UnaryExpr
            implements Expression {

        public String operator;
        public Expression expression;
        public boolean postfix;

        public UnaryExpr(
                String operator,
                Expression expression
        ) {
            this(
                    operator,
                    expression,
                    false
            );
        }

        public UnaryExpr(
                String operator,
                Expression expression,
                boolean postfix
        ) {
            this.operator = operator;
            this.expression = expression;
            this.postfix = postfix;
        }
    }

    public static class AddressOfExpr
            implements Expression {

        public Expression expression;

        public AddressOfExpr(
                Expression expression
        ) {
            this.expression = expression;
        }
    }

    public static class DereferenceExpr
            implements Expression {

        public Expression expression;

        public DereferenceExpr(
                Expression expression
        ) {
            this.expression = expression;
        }
    }

    public static class CastExpr
            implements Expression {

        public String type;
        public Expression expression;

        public CastExpr(
                String type,
                Expression expression
        ) {
            this.type = type;
            this.expression = expression;
        }
    }

    public static class CallExpr
            implements Expression {

        public String name;

        public final List<Expression> arguments =
                new ArrayList<>();

        public CallExpr(String name) {
            this.name = name;
        }
    }

    public static final class NotExpr
            implements Expression {

        public final Expression expression;

        public NotExpr(
                Expression expression
        ) {
            this.expression = expression;
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
