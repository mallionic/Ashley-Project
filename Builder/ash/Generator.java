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

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import Builder.ash.Ast;
import Builder.ash.Ast.*;

public final class Generator {

    private static final java.util.Set<String> ASM_REGISTERS = new java.util.HashSet<>(java.util.Arrays.asList(
        "al","ah","bl","bh","cl","ch","dl","dh","ax","bx","cx","dx","si","di","bp","sp",
        "sil","dil","bpl","spl","eax","ebx","ecx","edx","esi","edi","ebp","esp",
        "rax","rbx","rcx","rdx","rsi","rdi","rbp","rsp","cs","ds","es","fs","gs","ss",
        "r8","r9","r10","r11","r12","r13","r14","r15",
        "byte","word","dword","qword","ptr","short","near","far"));

    private final StringBuilder out = new StringBuilder();

    private int temporary;
    private int stringCounter;
    private int labelCounter;

    private final Map<String, String> strings =
        new LinkedHashMap<>();

    private final Map<String, String> stringVars =
        new HashMap<>();

    private final Map<String, String> variableTypes =
        new LinkedHashMap<>();

    private final Map<String, String> temporaryTypes =
        new HashMap<>();

    private final Map<String, Integer> variadicFixed = new HashMap<>();

    private final Map<String, String> functionReturnTypes =
        new HashMap<>();

    private final Map<String, String> externReturnTypes =
        new HashMap<>();

    private final ArrayDeque<String[]> loops =
        new ArrayDeque<>();

    private final Set<String> mutated = new HashSet<>();
    private boolean usesRuntime = false;
    public static boolean BOUNDS_CHECKS = true;

    public static boolean BEAUTY = true;

    private int targetBits = 64;

    public static boolean MS_ABI = false;

    public static boolean STRICT = false;
    private final Map<String, String> rawTypes = new HashMap<>();

    private static int strictBits(String normType) {
        if (normType == null) return 0;
        switch (normType) {
            case "byte":  case "int8":  return 8;
            case "word":  case "int16": return 16;
            case "dword": case "int32": return 32;
            case "qword": case "uint64": return 64;
            default:      return 0;
        }
    }

    private static java.math.BigInteger strictLiteral(Expression e) {

        boolean neg = false;

        if (
            e instanceof BinaryExpr &&
            "-".equals(((BinaryExpr) e).operator) &&
            ((BinaryExpr) e).left instanceof ValueExpr &&
            "0".equals(((ValueExpr) ((BinaryExpr) e).left).value)
        ) {
            neg = true;
            e = ((BinaryExpr) e).right;
        }

        if (e instanceof UnaryExpr && "-".equals(((UnaryExpr) e).operator)) {
            neg = !neg;
            e = ((UnaryExpr) e).expression;
        }

        if (!(e instanceof ValueExpr)) return null;

        ValueExpr v = (ValueExpr) e;

        if ("identifier".equals(v.type) || "string".equals(v.type)) return null;

        String t = v.value == null ? "" : v.value.replace("_", "").trim();

        try {
            java.math.BigInteger b;
            if (t.startsWith("0x") || t.startsWith("0X")) b = new java.math.BigInteger(t.substring(2), 16);
            else if (t.startsWith("0b") || t.startsWith("0B")) b = new java.math.BigInteger(t.substring(2), 2);
            else b = new java.math.BigInteger(t);
            return neg ? b.negate() : b;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private int strictWidth(Expression e) {

        if (e == null) return 0;

        if (e instanceof ValueExpr) {
            ValueExpr v = (ValueExpr) e;
            if (isIdentifierValue(v)) {
                try { return strictBits(typeOf(identifierName(v))); }
                catch (RuntimeException ex) { return 0; }
            }
            return 0;
        }

        if (e instanceof UnaryExpr)
            return strictWidth(((UnaryExpr) e).expression);

        if (e instanceof BinaryExpr) {
            BinaryExpr b = (BinaryExpr) e;
            switch (b.operator) {
                case "==": case "!=": case "<": case ">": case "<=": case ">=":
                case "&&": case "||":
                    return 0;
                case "<<": case ">>":
                    return strictWidth(b.left);
                default:
                    return Math.max(strictWidth(b.left), strictWidth(b.right));
            }
        }

        return 0;
    }

    private void strictCheckAssign(String name, String normType, String raw, Expression value) {

        if (!STRICT || value == null) return;

        int bits = strictBits(normType);
        if (bits == 0) return;

        String shown = raw != null ? raw : normType;

        java.math.BigInteger lit = strictLiteral(value);

        if (lit != null) {

            boolean unsigned = raw != null && Nums.isUnsigned(raw) && !raw.equals("qword");

            java.math.BigInteger min, max;

            if (unsigned) {
                min = java.math.BigInteger.ZERO;
                max = java.math.BigInteger.ONE.shiftLeft(bits).subtract(java.math.BigInteger.ONE);
            } else if (raw == null) {
                min = java.math.BigInteger.ONE.shiftLeft(bits - 1).negate();
                max = java.math.BigInteger.ONE.shiftLeft(bits).subtract(java.math.BigInteger.ONE);
            } else {
                min = java.math.BigInteger.ONE.shiftLeft(bits - 1).negate();
                max = java.math.BigInteger.ONE.shiftLeft(bits - 1).subtract(java.math.BigInteger.ONE);
            }

            if (lit.compareTo(min) < 0 || lit.compareTo(max) > 0)
                throw new IllegalStateException(
                    "strict: el valor " + lit + " no cabe en '" + name + "' (" + shown +
                    ", rango " + min + " .. " + max + ")"
                );
            return;
        }

        int w = strictWidth(value);

        if (w > bits)
            throw new IllegalStateException(
                "strict: no se puede asignar un valor de " + w + " bits a '" + name +
                "' (" + shown + ", " + bits + " bits); usa un cast explicito"
            );
    }

    private void scanMutatedBlock(java.util.List<Statement> body) {
        if (body == null) return;
        for (Statement st : body) scanMutatedStmt(st);
    }

    private void scanMutatedStmt(Statement st) {
        if (st == null) return;

        if (st instanceof VariableDecl) {
            VariableDecl d = (VariableDecl) st;
            scanMutatedExpr(d.value);
            for (Expression sx : ArrayInfo.get(d)) scanMutatedExpr(sx);
            return;
        }
        if (st instanceof Assignment) {
            Assignment a = (Assignment) st;
            if (a.target instanceof ValueExpr
                && isIdentifierValue((ValueExpr) a.target))
                mutated.add(identifierName((ValueExpr) a.target));
            scanMutatedExpr(a.target);
            scanMutatedExpr(a.value);
            return;
        }
        if (st instanceof ReturnStmt) {
            scanMutatedExpr(((ReturnStmt) st).value);
            return;
        }
        if (st instanceof ExpressionStmt) {
            scanMutatedExpr(((ExpressionStmt) st).expression);
            return;
        }
        if (st instanceof IfStmt) {
            IfStmt i = (IfStmt) st;
            scanMutatedExpr(i.condition);
            scanMutatedBlock(i.thenBranch);
            scanMutatedBlock(i.elseBranch);
            return;
        }
        if (st instanceof WhileStmt) {
            WhileStmt w = (WhileStmt) st;
            scanMutatedExpr(w.condition);
            scanMutatedBlock(w.body);
            return;
        }
        if (st instanceof ForStmt) {
            ForStmt f = (ForStmt) st;
            scanMutatedStmt(f.init);
            scanMutatedExpr(f.condition);
            scanMutatedStmt(f.update);
            scanMutatedBlock(f.body);
        }
    }

    private void scanMutatedExpr(Expression e) {
        if (e == null) return;

        if (e instanceof IndexExpr) {
            IndexExpr ix = (IndexExpr) e;
            if (ix.base instanceof ValueExpr
                && isIdentifierValue((ValueExpr) ix.base))
                mutated.add(identifierName((ValueExpr) ix.base));
            scanMutatedExpr(ix.base);
            scanMutatedExpr(ix.index);
            return;
        }
        if (e instanceof LengthExpr) {
            Expression in = ((LengthExpr) e).expression;
            if (in instanceof ValueExpr
                && isIdentifierValue((ValueExpr) in))
                mutated.add(identifierName((ValueExpr) in));
            scanMutatedExpr(in);
            return;
        }
        if (e instanceof ArrayLiteralExpr) {
            for (Expression x : ((ArrayLiteralExpr) e).elements)
                scanMutatedExpr(x);
            return;
        }
        if (e instanceof BinaryExpr) {
            scanMutatedExpr(((BinaryExpr) e).left);
            scanMutatedExpr(((BinaryExpr) e).right);
            return;
        }
        if (e instanceof UnaryExpr)       { scanMutatedExpr(((UnaryExpr) e).expression); return; }
        if (e instanceof NotExpr)         { scanMutatedExpr(((NotExpr) e).expression); return; }
        if (e instanceof CastExpr)        { scanMutatedExpr(((CastExpr) e).expression); return; }
        if (e instanceof AddressOfExpr)   { scanMutatedExpr(((AddressOfExpr) e).expression); return; }
        if (e instanceof DereferenceExpr) { scanMutatedExpr(((DereferenceExpr) e).expression); return; }
        if (e instanceof CallExpr) {
            for (Expression a : ((CallExpr) e).arguments)
                scanMutatedExpr(a);
        }
    }

    private static boolean isArrayType(String t) {
        return t != null && t.endsWith("[]");
    }

    private static boolean isPtrType(String t) {
        return t != null && (t.equals("ptr") || t.startsWith("ptr:"));
    }

    private static boolean isStringType(String t) {
        return "string".equals(t) || "cstring".equals(t);
    }

    private static String elementType(String t) {
        if (isArrayType(t))
            return t.substring(0, t.length() - 2);
        if (isPtrType(t) && t.startsWith("ptr:"))
            return t.substring(4);
        if (isStringType(t) || "ptr".equals(t))
            return "uint8";
        return "qword";
    }

    private static String baseElementType(String t) {
        while (isArrayType(t))
            t = t.substring(0, t.length() - 2);
        return t;
    }

    private static int elementSize(String t) {
        if (isArrayType(t) || isStringType(t) || isPtrType(t))
            return 8;

        switch (normalizeType(t)) {
            case "int8": case "uint8": case "byte":
                return 1;
            case "int16": case "uint16": case "word":
                return 2;
            case "int32": case "uint32": case "dword": case "float32":
                return 4;
            default:
                return 8;
        }
    }

    private static String irType(String t) {
        t = normalizeType(t);

        if (isArrayType(t) || isStringType(t) || isPtrType(t))
            return "qword";

        return t;
    }

    private static String charLit(String v) {
        final char Q = 39;

        if (v == null || v.length() < 3)
            return v;

        if (v.charAt(0) != Q || v.charAt(v.length() - 1) != Q)
            return v;

        String d = StringConv.decodeEscapes(v.substring(1, v.length() - 1));

        if (d.length() != 1)
            return v;

        return Long.toString((long) d.charAt(0));
    }

    private String tmpFor(String type) {
        return intRank(type) > 0 ? typedTemp(type) : temporary(type);
    }

    private String typedTemp(String type) {
        String n = temporary(type);

        out.append(".type ")
           .append(n)
           .append(" ")
           .append(irType(type))
           .append("\n");

        return n;
    }

    private String qtemp() {
        return typedTemp("qword");
    }

    private static final String RUNTIME = String.join("\n",
        "extern qword calloc(qword n, qword size)",
        "extern qword free(qword p)",
        "extern qword exit(qword code)",
        "",
        "func qword __ash_alloc(qword n, qword esz, qword extra) {",
        "    .type n_ qword",
        "    .type e_ qword",
        "    .type x_ qword",
        "    .type total qword",
        "    .type raw qword",
        "    .type hdr qword",
        "    %n_ mov[n]",
        "    %e_ mov[esz]",
        "    %x_ mov[extra]",
        "    %total mov[n_]",
        "    %total mul[e_]",
        "    %total add[x_]",
        "    %total add[16]",
        "    %raw call[calloc, 1, total]",
        "    store[raw, n_]",
        "    %hdr mov[raw]",
        "    %hdr add[8]",
        "    store[hdr, n_]",
        "    %raw add[16]",
        "    ret qword raw",
        "}",
        "",
        "func qword __ash_free(qword p) {",
        "    .type p_ qword",
        "    .type blk qword",
        "    .type junk qword",
        "    %p_ mov[p]",
        "    %blk mov[p_]",
        "    %blk sub[16]",
        "    %junk call[free, blk]",
        "    ret qword 0",
        "}",
        "",
        "func qword __ash_strlen(qword p) {",
        "    .type p_ qword",
        "    .type n qword",
        "    .type a qword",
        "    .type b qword",
        "    %p_ mov[p]",
        "    %n mov[0]",
        "    %_ label[__ash_sl_top]",
        "    %a mov[p_]",
        "    %a add[n]",
        "    %b load[a]",
        "    %b mod[256]",
        "    %_ brf[b, ne, 0, __ash_sl_end]",
        "    inc n",
        "    %_ jmp[__ash_sl_top]",
        "    %_ label[__ash_sl_end]",
        "    ret qword n",
        "}",
        "",
        "func qword __ash_memcpy(qword d, qword s, qword n) {",
        "    .type d_ qword",
        "    .type s_ qword",
        "    .type n_ qword",
        "    .type i qword",
        "    .type a qword",
        "    .type b qword",
        "    .type v qword",
        "    .type vb uint8",
        "    %d_ mov[d]",
        "    %s_ mov[s]",
        "    %n_ mov[n]",
        "    %i mov[0]",
        "    %_ label[__ash_mc_top]",
        "    %_ brf[i, lt, n_, __ash_mc_end]",
        "    %a mov[s_]",
        "    %a add[i]",
        "    %v load[a]",
        "    %v mod[256]",
        "    %vb mov[v]",
        "    %b mov[d_]",
        "    %b add[i]",
        "    store[b, vb]",
        "    inc i",
        "    %_ jmp[__ash_mc_top]",
        "    %_ label[__ash_mc_end]",
        "    ret qword d_",
        "}",
        "",
        "func qword __ash_str_new(qword src) {",
        "    .type src_ qword",
        "    .type n qword",
        "    .type p qword",
        "    .type junk qword",
        "    %src_ mov[src]",
        "    %n call[__ash_strlen, src_]",
        "    %p call[__ash_alloc, n, 1, 1]",
        "    %junk call[__ash_memcpy, p, src_, n]",
        "    ret qword p",
        "}",
        "",
        "func qword __ash_str_set(qword dst, qword src) {",
        "    .type dst_ qword",
        "    .type src_ qword",
        "    .type n qword",
        "    .type cap qword",
        "    .type hdr qword",
        "    .type res qword",
        "    .type junk qword",
        "    .type endp qword",
        "    .type lenp qword",
        "    .type z uint8",
        "    %dst_ mov[dst]",
        "    %src_ mov[src]",
        "    %n call[__ash_strlen, src_]",
        "    %res mov[dst_]",
        "    %hdr mov[dst_]",
        "    %hdr sub[8]",
        "    %cap load[hdr]",
        "    %_ brt[cap, ge, n, __ash_fit]",
        "    %res call[__ash_alloc, n, 1, 1]",
        "    %junk call[__ash_free, dst_]",
        "    %_ label[__ash_fit]",
        "    %junk call[__ash_memcpy, res, src_, n]",
        "    %endp mov[res]",
        "    %endp add[n]",
        "    %z mov[0]",
        "    store[endp, z]",
        "    %lenp mov[res]",
        "    %lenp sub[16]",
        "    store[lenp, n]",
        "    ret qword res",
        "}",
        "",
        "func qword __ash_matrix(qword rows, qword cols, qword esz) {",
        "    .type r_ qword",
        "    .type c_ qword",
        "    .type e_ qword",
        "    .type m qword",
        "    .type i qword",
        "    .type slot qword",
        "    .type row qword",
        "    %r_ mov[rows]",
        "    %c_ mov[cols]",
        "    %e_ mov[esz]",
        "    %m call[__ash_alloc, r_, 8, 0]",
        "    %i mov[0]",
        "    %_ label[__ash_mx_top]",
        "    %_ brf[i, lt, r_, __ash_mx_end]",
        "    %row call[__ash_alloc, c_, e_, 0]",
        "    %slot mov[i]",
        "    %slot mul[8]",
        "    %slot add[m]",
        "    store[slot, row]",
        "    inc i",
        "    %_ jmp[__ash_mx_top]",
        "    %_ label[__ash_mx_end]",
        "    ret qword m",
        "}",
        "",
        "func qword __ash_shl(qword v, qword n) {",
        "    .type v_ qword",
        "    .type n_ qword",
        "    %v_ mov[v]",
        "    %n_ mov[n]",
        "    %_ label[__ash_shl_top]",
        "    %_ brf[n_, gt, 0, __ash_shl_end]",
        "    %v_ mul[2]",
        "    dec n_",
        "    %_ jmp[__ash_shl_top]",
        "    %_ label[__ash_shl_end]",
        "    ret qword v_",
        "}",
        "",
        "func qword __ash_shr(qword v, qword n) {",
        "    .type v_ qword",
        "    .type n_ qword",
        "    .type r qword",
        "    %v_ mov[v]",
        "    %n_ mov[n]",
        "    %_ label[__ash_shr_top]",
        "    %_ brf[n_, gt, 0, __ash_shr_end]",
        "    %r mov[v_]",
        "    %r mod[2]",
        "    %v_ div[2]",
        "    %_ brt[r, ge, 0, __ash_shr_nx]",
        "    dec v_",
        "    %_ label[__ash_shr_nx]",
        "    dec n_",
        "    %_ jmp[__ash_shr_top]",
        "    %_ label[__ash_shr_end]",
        "    ret qword v_",
        "}",
        "",
        "func qword __ash_bounds(qword base, qword idx) {",
        "    .type b_ qword",
        "    .type i_ qword",
        "    .type hdr qword",
        "    .type len qword",
        "    .type junk qword",
        "    %b_ mov[base]",
        "    %i_ mov[idx]",
        "    %hdr mov[b_]",
        "    %hdr sub[16]",
        "    %len load[hdr]",
        "    %_ brt[i_, lt, len, __ash_ok]",
        "    %junk call[exit, 134]",
        "    %_ label[__ash_ok]",
        "    ret qword i_",
        "}",
        "");

    private String indexAddress(IndexExpr e) {

        if (
            e.base instanceof ValueExpr &&
            "string".equals(((ValueExpr) e.base).type)
        )
            throw new IllegalStateException(
                "No se puede indexar un literal; asignalo a una variable string"
            );

        usesRuntime = true;

        String elem = elementType(expressionType(e.base));
        int size = elementSize(elem);

        String base = expressionValue(e.base);
        String idx = expressionValue(e.index);

        String off = qtemp();
        movTo(off, idx);

        String baseType = expressionType(e.base);

        if (
            BOUNDS_CHECKS &&
            !isPtrType(baseType) &&
            (isArrayType(baseType) || isStringType(baseType))
        ) {

            String ok = qtemp();

            out.append("    %").append(ok)
               .append(" call[__ash_bounds, ")
               .append(base).append(", ").append(off)
               .append("]\n");
        }

        if (size != 1)
            operation(off, "*", String.valueOf(size));

        operation(off, "+", base);

        return off;
    }

    private void generateIndexLoad(String destination, IndexExpr e) {

        String elem = elementType(expressionType(e.base));
        String addr = indexAddress(e);

        boolean narrowInt =
            !isFloatType(elem) &&
            !isArrayType(elem) &&
            !isStringType(elem) &&
            elementSize(elem) < 8;

        if (narrowInt) {

            String q = typedTemp(elem);

            out.append("    %").append(q)
               .append(" load[").append(addr).append("]\n");

            movTo(destination, q);
            return;
        }

        String t = typedTemp(elem);

        out.append("    %").append(t)
           .append(" load[").append(addr).append("]\n");

        movTo(destination, t);
    }

    private void generateLength(String destination, LengthExpr e) {

        usesRuntime = true;

        String p = expressionValue(e.expression);

        String a = qtemp();
        movTo(a, p);
        operation(a, "-", "16");

        out.append("    %").append(destination)
           .append(" load[").append(a).append("]\n");
    }

    private void storeElement(String addr, String elem, Expression value) {

        String v = typedTemp(elem);

        if (isStringType(elem)) {

            usesRuntime = true;

            String src = expressionValue(value);

            out.append("    %").append(v)
               .append(" call[__ash_str_new, ")
               .append(src).append("]\n");

        } else if (value instanceof ArrayLiteralExpr) {

            generateArrayLiteralInto(v, elem, (ArrayLiteralExpr) value);

        } else if (
            value instanceof BinaryExpr &&
            !isFloatType(elem) &&
            elementSize(elem) < elementSize(expressionType(value))
        ) {

            String w = typedTemp(expressionType(value));
            generateExpressionInto(w, value);
            movTo(v, w);

        } else {

            generateExpressionInto(v, value);
        }

        out.append("    store[").append(addr)
           .append(", ").append(v).append("]\n");
    }

    private void generateIndexStore(IndexExpr target, Expression value) {

        String elem = elementType(expressionType(target.base));
        String addr = indexAddress(target);

        storeElement(addr, elem, value);
    }

    private void generateArrayLiteralInto(
        String dest,
        String arrType,
        ArrayLiteralExpr lit
    ) {
        usesRuntime = true;

        String elem = elementType(arrType);
        int size = elementSize(elem);

        out.append("    %").append(dest)
           .append(" call[__ash_alloc, ")
           .append(lit.elements.size()).append(", ")
           .append(size).append(", 0]\n");

        for (int i = 0; i < lit.elements.size(); i++) {

            String a = qtemp();
            movTo(a, dest);

            if (i > 0)
                operation(a, "+", String.valueOf(i * size));

            storeElement(a, elem, lit.elements.get(i));
        }
    }

    private void generateArrayDecl(VariableDecl d) {

        usesRuntime = true;

        String type = normalizeType(d.type);

        if (d.value instanceof ArrayLiteralExpr) {
            generateArrayLiteralInto(d.name, type, (ArrayLiteralExpr) d.value);
            return;
        }

        java.util.List<Expression> sz = ArrayInfo.get(d);

        if (!sz.isEmpty() && sz.get(0) != null) {

            String n = qtemp();
            movTo(n, expressionValue(sz.get(0)));

            if (sz.size() >= 2 && sz.get(1) != null) {

                int esz = elementSize(baseElementType(type));

                String m = qtemp();
                movTo(m, expressionValue(sz.get(1)));

                out.append("    %").append(d.name)
                   .append(" call[__ash_matrix, ")
                   .append(n).append(", ").append(m).append(", ")
                   .append(esz).append("]\n");

            } else {

                int s = elementSize(elementType(type));

                out.append("    %").append(d.name)
                   .append(" call[__ash_alloc, ")
                   .append(n).append(", ").append(s).append(", 0]\n");
            }

            return;
        }

        if (d.value != null)
            generateExpressionInto(d.name, d.value);
        else
            movTo(d.name, "0");
    }

private boolean isIdentifierValue(ValueExpr value) {

    if (value == null)
        return false;

    if ("identifier".equals(value.type))
        return true;

    if (value.type != null &&
        variableTypes.containsKey(value.type))
        return true;

    return false;
}

private String identifierName(ValueExpr value) {

    if (value == null)
        throw new IllegalStateException(
            "Identificador null"
        );

    String name = null;

    if ("identifier".equals(value.type)) {

        name = value.value;

    } else {

        if (
            value.type != null &&
            variableTypes.containsKey(value.type)
        ) {
            name = value.type;
        }
    }

    if (name == null || name.isBlank())
        throw new IllegalStateException(
            "No se pudo resolver el identificador: type=" +
            value.type +
            ", value=" +
            value.value
        );

    name = name.trim();

    if ("identifier".equals(name))
        throw new IllegalStateException(
            "AST invalido: no se pudo recuperar el nombre real " +
            "del identificador"
        );

    return name;
}

private String requireVariable(
    ValueExpr value
) {

    String name =
        identifierName(value);

    String type =
        variableTypes.get(name);

    if (type == null)
        type =
            temporaryTypes.get(name);

    if (type == null)
        throw new IllegalStateException(
            "Variable no declarada: " + name
        );

    return name;
}

    private String stringSymbol(String value) {

        String existing = strings.get(value);

        if (existing != null)
            return existing;

        String symbol =
            "__str" + stringCounter++;

        strings.put(value, symbol);

        return symbol;
    }

    private static boolean isHex(char ch) {

        return
            (ch >= '0' && ch <= '9') ||
            (ch >= 'a' && ch <= 'f') ||
            (ch >= 'A' && ch <= 'F');
    }

    private static String ashirString(String raw) {

        if (raw == null)
            return "\"\"";

        String t = raw;

        if (
            t.length() >= 2 &&
            t.charAt(0) == '"' &&
            t.charAt(t.length() - 1) == '"'
        ) {
            t = t.substring(1, t.length() - 1);
        }

        StringBuilder sb =
            new StringBuilder(t.length() + 2);

        sb.append('"');

        for (int i = 0; i < t.length(); i++) {

            char ch = t.charAt(i);

            switch (ch) {

                case '\n':
                    sb.append("\\n");
                    break;

                case '\r':
                    sb.append("\\r");
                    break;

                case '\t':
                    sb.append("\\t");
                    break;

                case '\0':
                    sb.append("\\0");
                    break;

                case '\b':
                    sb.append("\\b");
                    break;

                case '\f':
                    sb.append("\\f");
                    break;

                case '\u000B':
                    sb.append("\\v");
                    break;

                case '\\':

                    if (i + 1 < t.length()) {

                        char next =
                            t.charAt(i + 1);

                        switch (next) {

                            case 'n':
                            case 'r':
                            case 't':
                            case '0':
                            case 'a':
                            case 'b':
                            case 'f':
                            case 'v':
                            case '\\':
                            case '\'':
                            case '"':

                                sb.append('\\');
                                sb.append(next);
                                i++;
                                break;

                            case 'x':

                                if (
                                    i + 3 < t.length() &&
                                    isHex(t.charAt(i + 2)) &&
                                    isHex(t.charAt(i + 3))
                                ) {

                                    sb.append("\\x");
                                    sb.append(t.charAt(i + 2));
                                    sb.append(t.charAt(i + 3));

                                    i += 3;

                                } else {

                                    sb.append("\\\\");
                                }

                                break;

                            default:

                                sb.append('\\');
                                sb.append(next);
                                i++;
                                break;
                        }

                    } else {

                        sb.append("\\");
                    }

                    break;

                case '"':

                    sb.append("\\\"");
                    break;

                default:

                    sb.append(ch);
                    break;
            }
        }

        sb.append('"');

        return sb.toString();
    }

    private static String normalizeType(String type) {

        if (type == null || type.trim().isEmpty())
            return "qword";

        String t =
            type.trim().toLowerCase();

        if (t.startsWith("ptr:"))
            return t;

        switch (t) {

            case "unsigned char":
                return "uint8";

            case "unsigned short":
                return "uint16";

            case "unsigned int":
                return "uint32";

            case "unsigned long":
                return "uint64";

            case "signed char":
                return "int8";

            case "signed short":
                return "int16";

            case "signed int":
                return "int32";

            case "signed long":
                return "int64";

            case "float":
                return "float32";

            case "double":
                return "float64";

            default:
                return t;
        }
    }

    private void registerType(
        String name,
        String type
    ) {

        if (name == null || name.isEmpty())
            return;

        variableTypes.put(
            name,
            normalizeType(type)
        );
    }

    private void registerTemporaryType(
        String name,
        String type
    ) {

        if (name == null || name.isEmpty())
            return;

        if (variableTypes.containsKey(name) && !name.startsWith("__"))
            return;

        temporaryTypes.put(
            name,
            normalizeType(type)
        );
    }

    private String typeOf(String name) {

        if (name == null)
            return "qword";

        String type =
            temporaryTypes.get(name);

        if (type != null)
            return type;

        type =
            variableTypes.get(name);

        if (type != null)
            return type;

        return "qword";
    }

    private static boolean isFloatType(String type) {

        if (type == null)
            return false;

        String t =
            normalizeType(type);

        return
            t.equals("float32") ||
            t.equals("float64");
    }

    private static boolean isFloat32Type(String type) {

        return
            type != null &&
            normalizeType(type).equals("float32");
    }

    private static boolean isFloat64Type(String type) {

        return
            type != null &&
            normalizeType(type).equals("float64");
    }

    private static boolean isUnsignedType(String type) {

        if (type == null)
            return false;

        String t =
            normalizeType(type);

        return
            t.equals("uint8") ||
            t.equals("uint16") ||
            t.equals("uint32") ||
            t.equals("uint64") ||
            t.equals("byte") ||
            t.equals("word") ||
            t.equals("dword") ||
            t.equals("qword");
    }

    private static boolean is32BitType(String type) {

        if (type == null)
            return false;

        String t =
            normalizeType(type);

        return
            t.equals("uint32") ||
            t.equals("int32") ||
            t.equals("dword") ||
            t.equals("float32");
    }

    private static boolean is64BitType(String type) {

        if (type == null)
            return false;

        String t =
            normalizeType(type);

        return
            t.equals("uint64") ||
            t.equals("int64") ||
            t.equals("qword") ||
            t.equals("float64");
    }

    private static int intRank(String t) {
        if (t == null) return 0;
        switch (t) {
            case "byte":  case "int8":  case "uint8":  return 1;
            case "word":  case "int16": case "uint16": return 2;
            case "dword": case "int32": case "uint32": return 3;
            case "qword": case "int64": case "uint64": return 4;
            default:      return 0;
        }
    }

    private boolean isNumericLiteral(Expression e) {
        if (e instanceof BinaryExpr) {
            BinaryExpr b = (BinaryExpr) e;
            return isNumericLiteral(b.left) && isNumericLiteral(b.right);
        }
        if (!(e instanceof ValueExpr)) return false;
        ValueExpr v = (ValueExpr) e;
        if (isIdentifierValue(v) || "string".equals(v.type)) return false;
        return strictLiteral(e) != null;
    }

    private String expressionType(Expression e) {

        if (e instanceof IndexExpr)
            return elementType(
                expressionType(((IndexExpr) e).base)
            );

        if (e instanceof LengthExpr)
            return "qword";

        if (e instanceof ArrayLiteralExpr)
            return "qword[]";

        if (e == null)
            return "qword";

        if (e instanceof ValueExpr) {

            ValueExpr v =
                (ValueExpr) e;

            if (isIdentifierValue(v))
                return typeOf(
                    identifierName(v)
                );

            if ("string".equals(v.type))
                return "ptr";

            if (
                "float".equalsIgnoreCase(v.type) ||
                "float32".equalsIgnoreCase(v.type)
            )
                return "float32";

            if (
                "double".equalsIgnoreCase(v.type) ||
                "float64".equalsIgnoreCase(v.type)
            )
                return "float64";

            if (looksLikeFloatLiteral(v.value))
                return "float64";

            return "qword";
        }

        if (e instanceof UnaryExpr) {

            UnaryExpr u =
                (UnaryExpr) e;

            return expressionType(
                u.expression
            );
        }

        if (e instanceof NotExpr)
            return "qword";

        if (e instanceof CastExpr) {

            String ct = ((CastExpr) e).type;

            if (ct != null && ct.endsWith("*")) {

                String base = ct.substring(0, ct.length() - 1).trim();

                if (base.equals("void") || base.endsWith("*"))
                    return base.endsWith("*") ? "ptr:qword" : "ptr";

                try {
                    return "ptr:" + Nums.normalizeType(base);
                } catch (RuntimeException ex) {
                    return "ptr";
                }
            }

            return expressionType(
                ((CastExpr) e).expression
            );
        }

        if (e instanceof BinaryExpr) {

            BinaryExpr b =
                (BinaryExpr) e;

            String left =
                expressionType(b.left);

            String right =
                expressionType(b.right);

            if (
                isFloat64Type(left) ||
                isFloat64Type(right)
            )
                return "float64";

            if (
                isFloat32Type(left) ||
                isFloat32Type(right)
            )
                return "float32";

            int lw = isNumericLiteral(b.left)  ? 0 : intRank(left);
            int rw = isNumericLiteral(b.right) ? 0 : intRank(right);

            boolean leftInt  = lw > 0 || isNumericLiteral(b.left);
            boolean rightInt = rw > 0 || isNumericLiteral(b.right);

            if (leftInt && rightInt) {

                boolean shift = "<<".equals(b.operator) || ">>".equals(b.operator);

                int w = shift ? lw : Math.max(lw, rw);

                if (w == 0) return "qword";

                if (w <= 2) return "int32";
                if (w == 3)
                    return (lw == 3 && !"int32".equals(left)) || (rw == 3 && !"int32".equals(right))
                        ? "dword" : "int32";
                return "qword";
            }

            if (!left.equals("qword"))
                return left;

            return right;
        }

        if (e instanceof GlobalAddrExpr)
            return "ptr:" + ((GlobalAddrExpr) e).type;

        if (isFunctionRef(e))
            return "ptr";

        if (e instanceof AddressOfExpr)
            return "ptr";

        if (e instanceof DereferenceExpr) {
            String it = expressionType(((DereferenceExpr) e).expression);
            return it != null && it.startsWith("ptr:")
                ? it.substring(4)
                : "qword";
        }

        if (e instanceof CallExpr) {

            CallExpr call =
                (CallExpr) e;

            String type =
                functionReturnTypes.get(call.name);

            if (type != null)
                return type;

            type =
                externReturnTypes.get(call.name);

            if (type != null)
                return type;

            return "qword";
        }

        return "qword";
    }

    private static boolean looksLikeFloatLiteral(
        String value
    ) {

        if (value == null)
            return false;

        String s =
            value.trim();

        if (s.isEmpty())
            return false;

        if (
            s.equalsIgnoreCase("nan") ||
            s.equalsIgnoreCase("inf") ||
            s.equalsIgnoreCase("+inf") ||
            s.equalsIgnoreCase("-inf") ||
            s.equalsIgnoreCase("ninf")
        )
            return false;

        boolean digit = false;
        boolean dot = false;
        boolean exponent = false;

        for (int i = 0; i < s.length(); i++) {

            char c =
                s.charAt(i);

            if (Character.isDigit(c)) {
                digit = true;
                continue;
            }

            if (c == '.') {

                if (dot)
                    return false;

                dot = true;
                continue;
            }

            if (c == 'e' || c == 'E') {

                if (exponent)
                    return false;

                exponent = true;

                if (
                    i + 1 < s.length() &&
                    (
                        s.charAt(i + 1) == '+' ||
                        s.charAt(i + 1) == '-'
                    )
                )
                    i++;

                continue;
            }

            if (
                (c == 'f' || c == 'F') &&
                i == s.length() - 1
            )
                return digit;

            if (
                (c == '+' || c == '-') &&
                i == 0
            )
                continue;

            return false;
        }

        return digit && (dot || exponent);
    }

    private void emitType(
        String name,
        String type
    ) {

        if (name == null || name.isEmpty())
            return;

        String normalized =
            normalizeType(type);

        registerType(
            name,
            normalized
        );

        out.append(".type ")
           .append(name)
           .append(" ")
           .append(irType(normalized))
           .append("\n");
    }

    private void faddr(
        String destination,
        String symbol
    ) {

        registerTemporaryType(destination, "ptr");

        out.append("    %")
           .append(destination)
           .append(" faddr[")
           .append(symbol)
           .append("]\n");
    }

    private void addr(
        String destination,
        String symbol
    ) {

        if (!variableTypes.containsKey(destination))
            registerTemporaryType(
                destination,
                "ptr"
            );

        out.append("    %")
           .append(destination)
           .append(" addr[")
           .append(symbol)
           .append("]\n");
    }

    private void movTo(
        String destination,
        String value
    ) {
        value = charLit(value);

        out.append("    %")
           .append(destination)
           .append(" mov[")
           .append(value)
           .append("]\n");
    }

    private String temporary() {

        String name =
            "__tmp" + temporary++;

        registerTemporaryType(
            name,
            "qword"
        );

        return name;
    }

    private String temporary(
        String type
    ) {

        String name =
            temporary();

        registerTemporaryType(
            name,
            type
        );

        return name;
    }

    private String value(
        Expression expression
    ) {

        if (!(expression instanceof ValueExpr))
            throw new IllegalArgumentException(
                "Se esperaba ValueExpr"
            );

        ValueExpr v =
            (ValueExpr) expression;

        if (isIdentifierValue(v))
            return identifierName(v);

        return v.value;
    }

    private String foldString(
        Expression e
    ) {

        if (e instanceof ValueExpr) {

            ValueExpr v =
                (ValueExpr) e;

            if ("string".equals(v.type))
                return StringConv.stripQuotes(v.value);

            if (isIdentifierValue(v))
                return stringVars.get(
                    identifierName(v)
                );

            return null;
        }

        if (e instanceof BinaryExpr) {

            BinaryExpr b =
                (BinaryExpr) e;

            if (!"+".equals(b.operator))
                return null;

            String l =
                foldString(b.left);

            if (l == null)
                return null;

            String r =
                foldString(b.right);

            if (r == null)
                return null;

            return l + r;
        }

        return null;
    }

    private void trackStringVar(
        String name,
        Expression value
    ) {

        if (mutated.contains(name)) {
            stringVars.remove(name);
            return;
        }

        String folded =
            value == null
                ? null
                : foldString(value);

        if (folded != null)
            stringVars.put(name, folded);
        else
            stringVars.remove(name);
    }

    private static boolean isString(
        Expression e
    ) {

        return
            e instanceof ValueExpr &&
            "string".equals(
                ((ValueExpr) e).type
            );
    }

    public String generate(
        Program program
    ) {

        out.setLength(0);

        targetBits = program.bits;

        temporary = 0;
        stringCounter = 0;
        labelCounter = 0;

        strings.clear();
        stringVars.clear();
        variableTypes.clear();
        temporaryTypes.clear();
        functionReturnTypes.clear();
        externReturnTypes.clear();
        loops.clear();

        mutated.clear();
        usesRuntime = false;

        for (String f : new String[] {
            "__ash_alloc", "__ash_str_new", "__ash_str_set",
            "__ash_matrix", "__ash_bounds", "__ash_free",
            "__ash_strlen", "__ash_memcpy", "__ash_shl", "__ash_shr"
        })
            functionReturnTypes.put(f, "qword");

        registerFunctionSignatures(program);
        registerExternSignatures(program);
        registerVolatileStrings(program);

        for (Extern ext :
            program.externs)
            generateExtern(ext);

        for (Function fn :
            program.functions)
            generateFunction(fn);

        for (GlobalVar g : program.globals) {

            if (g.section != null)
                out.append(".section ").append(Globals.symbolOf(g))
                   .append(" \"").append(g.section[0]).append("\" \"")
                   .append(g.section[1]).append("\"\n");

            if (g.elemType != null) {
                out.append(".gdata ").append(Globals.symbolOf(g)).append(" x")
                   .append(globalArrayHex(g)).append(" 16\n");
                continue;
            }

            int size = Globals.bits(g.type) / 8;

            out.append(".gvar ")
               .append(Globals.symbolOf(g))
               .append(" ")
               .append(size)
               .append(" ")
               .append(g.value)
               .append("\n");
        }

        for (Map.Entry<String, String> w : WideStrings.all().entrySet())
            out.append(".gdata ").append(w.getValue()).append(" x")
               .append(WideStrings.hex(w.getKey())).append("\n");

        for (
            Map.Entry<String, String> entry :
            strings.entrySet()
        ) {

            out.append(".string ")
               .append(entry.getValue())
               .append(" ")
               .append(
                   ashirString(entry.getKey())
               )
               .append("\n");
        }

        if (usesRuntime) {

            String rt = RUNTIME;
            for (String own : new String[]{"calloc", "free", "exit"})
                if (functionReturnTypes.containsKey(own))
                    rt = rt.replaceAll("(?m)^extern qword " + own + "\\(.*\\)\\R", "");
            out.insert(0, rt);
        }

        return out.toString();
    }

    private static String globalArrayHex(GlobalVar g) {

        int esz = Globals.bits(g.elemType) / 8;
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();

        for (int k = 0; k < 2; k++)
            for (int i = 0; i < 8; i++)
                b.write((int) ((long) g.count >>> (8 * i)) & 0xFF);

        for (int e = 0; e < g.count; e++) {
            long v = e < g.elements.size() ? g.elements.get(e).longValue() : 0;
            for (int i = 0; i < esz; i++)
                b.write((int) (v >>> (8 * i)) & 0xFF);
        }

        if (esz == 1 && (g.elements.isEmpty() || g.elements.get(g.elements.size() - 1).signum() != 0))
            b.write(0);

        StringBuilder hex = new StringBuilder();
        for (byte x : b.toByteArray())
            hex.append(String.format("%02x", x));
        return hex.toString();
    }

    private void registerFunctionSignatures(
        Program program
    ) {

        variadicFixed.clear();

        for (Function fn :
            program.functions) {

            functionReturnTypes.put(
                fn.name,
                normalizeType(fn.returnType)
            );

            if (fn.variadic)
                variadicFixed.put(fn.name, fn.fixedParams);
        }
    }

    private void registerExternSignatures(
        Program program
    ) {

        for (Extern ext :
            program.externs) {

            String type =
                normalizeType(ext.returnType);

            externReturnTypes.put(
                ext.name,
                type
            );

            if (
                ext.alias != null &&
                !ext.alias.isEmpty()
            ) {

                externReturnTypes.put(
                    ext.alias,
                    type
                );
            }
        }
    }

    private void registerVolatileStrings(
        Program program
    ) {

        for (Function fn :
            program.functions) {

            for (Statement statement :
                fn.statements) {

                if (!(statement instanceof VariableDecl))
                    continue;

                VariableDecl variable =
                    (VariableDecl) statement;

                if (!variable.volatileVariable)
                    continue;

                if (!isString(variable.value))
                    continue;

                stringSymbol(
                    ((ValueExpr)
                        variable.value).value
                );
            }
        }
    }

    private void generateExtern(
        Extern ext
    ) {

        out.append("extern ")
           .append(isPtrType(normalizeType(ext.returnType)) ? "qword" : ext.returnType)
           .append(" ")
           .append(ext.name)
           .append("(");

        for (
            int i = 0;
            i < ext.parameters.size();
            i++
        ) {

            if (i != 0)
                out.append(", ");

            Parameter p =
                ext.parameters.get(i);

            out.append(isPtrType(normalizeType(p.type)) ? "qword" : p.type)
               .append(" ")
               .append(p.name);
        }

        if (ext.variadic) {

            if (!ext.parameters.isEmpty())
                out.append(", ");

            out.append("...");
        }

        out.append(")");

        if (
            ext.alias != null &&
            !ext.alias.equals(ext.name)
        ) {

            out.append(" as ")
               .append(ext.alias)
               .append("(");

            for (
                int i = 0;
                i < ext.parameters.size();
                i++
            ) {

                if (i != 0)
                    out.append(", ");

                out.append(
                    ext.parameters.get(i).name
                );
            }

            out.append(")");
        }

        out.append("\n\n");
    }

    private void generateFunction(
        Function fn
    ) {

        if (fn.section != null)
            out.append(".section ").append(fn.name)
               .append(" \"").append(fn.section[0]).append("\" \"")
               .append(fn.section[1]).append("\"\n");

        if (fn.entry)
            out.append("[entry]\n");

        if (fn.efiapi)
            out.append("[efiapi]\n");

        if (fn.naked) {
            generateNakedFunction(fn);
            return;
        }

        if (fn.isPrivate && !fn.entry)
            arch.Visibility.markPrivate(fn.name);

        out.append("func ")
           .append(isPtrType(normalizeType(fn.returnType)) ? "qword" : fn.returnType)
           .append(" ")
           .append(fn.name)
           .append("(");

        for (
            int i = 0;
            i < fn.parameters.size();
            i++
        ) {

            if (i != 0)
                out.append(", ");

            Parameter p =
                fn.parameters.get(i);

            out.append(isPtrType(normalizeType(p.type)) ? "qword" : p.type)
               .append(" ")
               .append(p.name);

            registerType(
                p.name,
                p.type
            );
        }

        out.append(") {\n");

        stringVars.clear();

        registerFunctionTypes(fn);

        mutated.clear();
        scanMutatedBlock(fn.statements);

        Set<String> used =
            collectUsedVariables(fn);

        mutated.clear();
        {
            java.util.Set<String> paramNames = new HashSet<>();

            for (Parameter p0 : fn.parameters)
                paramNames.add(p0.name);

            for (Map.Entry<String, String> en0 : variableTypes.entrySet())
                if (
                    "string".equals(en0.getValue()) &&
                    !paramNames.contains(en0.getKey())
                )
                    mutated.add(en0.getKey());
        }

        emitFunctionTypes(fn);

        for (Statement s :
            fn.statements) {

            generateStatement(
                fn,
                s,
                used
            );
        }

        if (
            normalizeType(fn.returnType)
                .equals("void") &&
            !hasReturn(fn)
        ) {

            out.append("    ret void\n");
        }

        out.append("}\n\n");
    }

    private void generateNakedFunction(Function fn) {

        out.append("[naked]\n")
           .append("func ").append(isPtrType(normalizeType(fn.returnType)) ? "qword" : fn.returnType)
           .append(" ").append(fn.name).append("(");

        for (int i = 0; i < fn.parameters.size(); i++) {
            if (i != 0) out.append(", ");
            Parameter p = fn.parameters.get(i);
            out.append(isPtrType(normalizeType(p.type)) ? "qword" : p.type).append(" ").append(p.name);
        }

        out.append(") {\n");

        for (Statement s : fn.statements) {

            if (s instanceof InjectBytesStmt) {
                generateInject((InjectBytesStmt) s);
                continue;
            }

            if (
                s instanceof ExpressionStmt &&
                ((ExpressionStmt) s).expression instanceof CallExpr &&
                "__asm".equals(((CallExpr) ((ExpressionStmt) s).expression).name)
            ) {
                CallExpr call = (CallExpr) ((ExpressionStmt) s).expression;
                int index = Integer.parseInt(((ValueExpr) call.arguments.get(0)).value);
                AsmBlocks.Block block = AsmBlocks.get(index);

                if (block.placeholders && block.text.indexOf(AsmBlocks.PH_OPEN) >= 0)
                    throw new IllegalStateException(
                        "@naked function '" + fn.name + "': asm placeholders ({x}, %0) need variables, " +
                        "and a naked function has none. Use registers directly (arguments arrive in rdi, rsi, rdx, rcx, r8, r9)."
                    );

                StringBuilder clean = new StringBuilder();
                for (String line : block.text.split("\\r?\\n", -1)) {
                    int c = block.placeholders ? -1 : line.indexOf(';');
                    clean.append(c >= 0 ? line.substring(0, c) : line).append('\n');
                }

                if (clean.toString().trim().isEmpty())
                    continue;

                StringBuilder hex = new StringBuilder();
                for (byte b : clean.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                    hex.append(String.format("%02x", b));

                out.append("    %_ asm[x").append(hex).append("]\n");
                continue;
            }

            throw new IllegalStateException(
                "@naked function '" + fn.name + "' can only contain asm { } / asm(\"...\") and emit(...): " +
                "the compiler adds no prologue, epilogue or return, so variables, calls and 'return' are not allowed. " +
                "End it yourself with 'ret' (or iretq for an interrupt handler)."
            );
        }

        out.append("}\n\n");
    }

    private void registerFunctionTypes(
        Function fn
    ) {

        for (Statement statement :
            fn.statements)
            registerStatementTypes(statement);
    }

    private void registerStatementTypes(
        Statement statement
    ) {

        if (statement instanceof VariableDecl) {

            VariableDecl d =
                (VariableDecl) statement;

            registerType(
                d.name,
                d.type
            );

            return;
        }

        if (statement instanceof IfStmt) {

            IfStmt s =
                (IfStmt) statement;

            registerBlockTypes(s.thenBranch);
            registerBlockTypes(s.elseBranch);

            return;
        }

        if (statement instanceof WhileStmt) {

            registerBlockTypes(
                ((WhileStmt) statement).body
            );

            return;
        }

        if (statement instanceof ForStmt) {

            ForStmt s =
                (ForStmt) statement;

            if (s.init != null)
                registerStatementTypes(s.init);

            if (s.update != null)
                registerStatementTypes(s.update);

            registerBlockTypes(s.body);
        }
    }

    private void registerBlockTypes(
        java.util.List<Statement> body
    ) {

        if (body == null)
            return;

        for (Statement s :
            body)
            registerStatementTypes(s);
    }

    private void emitFunctionTypes(
        Function fn
    ) {

        emitBlockTypes(fn.statements);
    }

    private void emitBlockTypes(
        java.util.List<Statement> body
    ) {

        if (body == null)
            return;

        for (Statement statement :
            body) {

            if (statement instanceof VariableDecl) {

                VariableDecl d =
                    (VariableDecl) statement;

                emitType(
                    d.name,
                    d.type
                );

                continue;
            }

            if (statement instanceof IfStmt) {

                IfStmt s =
                    (IfStmt) statement;

                emitBlockTypes(s.thenBranch);
                emitBlockTypes(s.elseBranch);

                continue;
            }

            if (statement instanceof WhileStmt) {

                emitBlockTypes(
                    ((WhileStmt) statement).body
                );

                continue;
            }

            if (statement instanceof ForStmt) {

                ForStmt s =
                    (ForStmt) statement;

                if (s.init != null)
                    emitStatementType(s.init);

                emitBlockTypes(s.body);
            }
        }
    }

    private void emitStatementType(
        Statement statement
    ) {

        if (statement instanceof VariableDecl) {

            VariableDecl d =
                (VariableDecl) statement;

            emitType(
                d.name,
                d.type
            );
        }
    }

    private boolean hasReturn(
        Function fn
    ) {

        if (
            fn == null ||
            fn.statements == null
        )
            return false;

        for (Statement statement :
            fn.statements) {

            if (statement instanceof ReturnStmt)
                return true;
        }

        return false;
    }

    private Set<String> collectUsedVariables(
        Function fn
    ) {

        Set<String> used =
            new HashSet<>();

        for (Statement statement :
            fn.statements) {

            collectUsedFromStatement(
                statement,
                used
            );
        }

        return used;
    }

    private void collectUsedFromStatement(
        Statement statement,
        Set<String> used
    ) {

        collectUsedControl(
            statement,
            used
        );

        if (statement instanceof VariableDecl) {

            VariableDecl d =
                (VariableDecl) statement;

            if (d.value != null)
                collectUsedFromExpression(
                    d.value,
                    used
                );

            for (Expression sx : ArrayInfo.get(d))
                if (sx != null)
                    collectUsedFromExpression(sx, used);

            return;
        }

        if (statement instanceof Assignment) {

            Assignment a =
                (Assignment) statement;

            collectUsedFromExpression(
                a.target,
                used
            );

            if (
                a.target instanceof ValueExpr &&
                isIdentifierValue((ValueExpr) a.target)
            )
                mutated.add(identifierName((ValueExpr) a.target));

            collectUsedFromExpression(
                a.value,
                used
            );

            return;
        }

        if (statement instanceof ReturnStmt) {

            collectUsedFromExpression(
                ((ReturnStmt) statement).value,
                used
            );

            return;
        }

        if (statement instanceof ExpressionStmt) {

            collectUsedFromExpression(
                ((ExpressionStmt) statement).expression,
                used
            );

            return;
        }

        if (statement instanceof InjectBytesStmt) {

            for (Expression e :
                ((InjectBytesStmt) statement).bytes) {

                collectUsedFromExpression(
                    e,
                    used
                );
            }
        }
    }

    private void collectUsedFromExpression(
        Expression expression,
        Set<String> used
    ) {

        if (
            expression instanceof CallExpr &&
            "__asm".equals(((CallExpr) expression).name)
        ) {
            int blk =
                Integer.parseInt(
                    ((ValueExpr) ((CallExpr) expression).arguments.get(0)).value
                );

            java.util.regex.Matcher w =
                java.util.regex.Pattern.compile("[A-Za-z_]\\w*")
                    .matcher(AsmBlocks.get(blk).text);

            while (w.find())
                used.add(w.group());

            return;
        }

        if (expression instanceof IndexExpr) {

            IndexExpr ix = (IndexExpr) expression;

            if (
                ix.base instanceof ValueExpr &&
                isIdentifierValue((ValueExpr) ix.base)
            )
                mutated.add(identifierName((ValueExpr) ix.base));

            collectUsedFromExpression(ix.base, used);
            collectUsedFromExpression(ix.index, used);

            return;
        }

        if (expression instanceof LengthExpr) {

            Expression inner = ((LengthExpr) expression).expression;

            if (
                inner instanceof ValueExpr &&
                isIdentifierValue((ValueExpr) inner)
            )
                mutated.add(identifierName((ValueExpr) inner));

            collectUsedFromExpression(inner, used);

            return;
        }

        if (expression instanceof ArrayLiteralExpr) {

            for (Expression x : ((ArrayLiteralExpr) expression).elements)
                collectUsedFromExpression(x, used);

            return;
        }

        if (expression == null)
            return;

        if (expression instanceof UnaryExpr) {

            collectUsedFromExpression(
                ((UnaryExpr) expression).expression,
                used
            );

            return;
        }

        if (expression instanceof NotExpr) {

            collectUsedFromExpression(
                ((NotExpr) expression).expression,
                used
            );

            return;
        }

        if (expression instanceof ValueExpr) {

            ValueExpr v =
                (ValueExpr) expression;

            if (isIdentifierValue(v))
                used.add(
                    identifierName(v)
                );

            return;
        }

        if (expression instanceof BinaryExpr) {

            BinaryExpr b =
                (BinaryExpr) expression;

            collectUsedFromExpression(
                b.left,
                used
            );

            collectUsedFromExpression(
                b.right,
                used
            );

            return;
        }

        if (expression instanceof AddressOfExpr) {

            collectUsedFromExpression(
                ((AddressOfExpr) expression).expression,
                used
            );

            return;
        }

        if (expression instanceof DereferenceExpr) {

            collectUsedFromExpression(
                ((DereferenceExpr) expression).expression,
                used
            );

            return;
        }

        if (expression instanceof CastExpr) {

            collectUsedFromExpression(
                ((CastExpr) expression).expression,
                used
            );

            return;
        }

        if (expression instanceof CallExpr) {

            for (Expression argument :
                ((CallExpr) expression).arguments) {

                collectUsedFromExpression(
                    argument,
                    used
                );
            }
        }
    }

    private boolean shouldGenerateVariable(
        VariableDecl variable,
        Set<String> used
    ) {

        if (variable.volatileVariable)
            return true;

        if (used.contains(variable.name))
            return true;

        if (isString(variable.value))
            return false;

        return true;
    }

    private void generateStatement(
        Function fn,
        Statement statement,
        Set<String> used
    ) {

        if (
            generateControlStatement(
                fn,
                statement,
                used
            )
        )
            return;

        if (statement instanceof InjectBytesStmt) {

            generateInject(
                (InjectBytesStmt) statement
            );

            return;
        }

        if (statement instanceof VariableDecl) {

            VariableDecl variable =
                (VariableDecl) statement;

            registerType(
                variable.name,
                variable.type
            );

            if (STRICT) {
                rawTypes.put(variable.name, variable.rawType);
                strictCheckAssign(variable.name, variable.type, variable.rawType, variable.value);
            }

            trackStringVar(
                variable.name,
                variable.value
            );

            if (!shouldGenerateVariable(
                    variable,
                    used
                ))
                return;

            generateVariable(variable);

            return;
        }

        if (statement instanceof Assignment) {

            Assignment a =
                (Assignment) statement;

            if (a.target instanceof ValueExpr) {

                ValueExpr target =
                    (ValueExpr) a.target;

                if (isIdentifierValue(target)) {

                    trackStringVar(
                        identifierName(target),
                        a.value
                    );
                }
            }

            if (
                STRICT &&
                a.target instanceof ValueExpr &&
                isIdentifierValue((ValueExpr) a.target)
            ) {
                String sn = identifierName((ValueExpr) a.target);
                strictCheckAssign(sn, typeOf(sn), rawTypes.get(sn), a.value);
            }

            generateAssignment(a);

            return;
        }

        if (statement instanceof ReturnStmt) {

            generateReturn(
                fn,
                (ReturnStmt) statement
            );

            return;
        }

        if (statement instanceof ExpressionStmt) {

            generateExpressionStatement(
                (ExpressionStmt) statement
            );

            return;
        }

        throw new IllegalStateException(
            "Statement desconocido: " +
            statement.getClass().getName()
        );
    }

    private void generateAsm(CallExpr call) {

        int index =
            Integer.parseInt(
                ((ValueExpr) call.arguments.get(0)).value
            );

        AsmBlocks.Block block = AsmBlocks.get(index);
        String text = block.text;

        if (block.placeholders) {
            generateAsmPlaceholders(text);
            return;
        }

        StringBuilder clean = new StringBuilder();

        for (String line : text.split("\\r?\\n", -1)) {
            int c = line.indexOf(';');
            clean.append(c >= 0 ? line.substring(0, c) : line).append('\n');
        }

        text = clean.toString();

        java.util.regex.Matcher m =
            java.util.regex.Pattern.compile(
                "\\b([A-Za-z_]\\w*)\\s*\\[\\s*([A-Za-z_]\\w*|-?\\d+)\\s*\\]"
            ).matcher(text);

        StringBuffer sb = new StringBuffer();

        while (m.find()) {

            String name = m.group(1);

            if (!variableTypes.containsKey(name)) {
                m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(m.group()));
                continue;
            }

            String idx = m.group(2);

            Expression ie =
                idx.matches("-?\\d+")
                    ? new ValueExpr(idx, "int64")
                    : new ValueExpr(idx, "identifier");

            IndexExpr ix =
                new IndexExpr(new ValueExpr(name, "identifier"), ie);

            String tmp =
                temporary(expressionType(ix));

            generateExpressionInto(tmp, ix);

            m.appendReplacement(
                sb,
                java.util.regex.Matcher.quoteReplacement("@var:" + tmp)
            );
        }

        m.appendTail(sb);
        text = sb.toString();

        m = java.util.regex.Pattern
            .compile("(?<![@\\w:])([A-Za-z_]\\w*)(?![\\w:])")
            .matcher(text);

        sb = new StringBuffer();

        while (m.find()) {

            String name = m.group(1);

            boolean known =
                !ASM_REGISTERS.contains(name.toLowerCase()) &&
                (variableTypes.containsKey(name) ||
                 temporaryTypes.containsKey(name));

            m.appendReplacement(
                sb,
                java.util.regex.Matcher.quoteReplacement(
                    known ? "@var:" + name : name
                )
            );
        }

        m.appendTail(sb);

        StringBuilder hex = new StringBuilder();

        for (byte b : sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))
            hex.append(String.format("%02x", b));

        out.append("    %_ asm[x").append(hex).append("]\n");
    }

    private void generateAsmPlaceholders(String text) {

        java.util.regex.Matcher m =
            java.util.regex.Pattern.compile(
                AsmBlocks.PH_OPEN + "([A-Za-z_]\\w*)(?:\\[([A-Za-z_]\\w*|-?\\d+)\\])?" + AsmBlocks.PH_CLOSE
            ).matcher(text);

        StringBuffer sb = new StringBuffer();

        while (m.find()) {

            String name = m.group(1);

            if (!variableTypes.containsKey(name) && !temporaryTypes.containsKey(name))
                throw new IllegalArgumentException(
                    "asm: '" + name + "' is not a variable visible here"
                );

            String ref = name;

            if (m.group(2) != null) {
                String idx = m.group(2);
                Expression ie =
                    idx.matches("-?\\d+")
                        ? new ValueExpr(idx, "int64")
                        : new ValueExpr(idx, "identifier");
                IndexExpr ix =
                    new IndexExpr(new ValueExpr(name, "identifier"), ie);
                ref = temporary(expressionType(ix));
                generateExpressionInto(ref, ix);
            }

            m.appendReplacement(
                sb,
                java.util.regex.Matcher.quoteReplacement("@var:" + ref)
            );
        }

        m.appendTail(sb);

        if (sb.toString().trim().isEmpty())
            return;

        StringBuilder hex = new StringBuilder();

        for (byte b : sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))
            hex.append(String.format("%02x", b));

        out.append("    %_ asm[x").append(hex).append("]\n");
    }

    private void generateExpressionStatement(
        ExpressionStmt statement
    ) {

        if (
            statement.expression instanceof CallExpr &&
            "__asm".equals(((CallExpr) statement.expression).name)
        ) {
            generateAsm(
                (CallExpr) statement.expression
            );
            return;
        }

        if (
            statement.expression instanceof CallExpr
        ) {

            generateCall(
                null,
                (CallExpr) statement.expression
            );

            return;
        }

        if (
            statement.expression instanceof UnaryExpr
        ) {

            UnaryExpr u =
                (UnaryExpr)
                    statement.expression;

            if (
                "++".equals(u.operator) ||
                "--".equals(u.operator)
            ) {

                generateIncrementStatement(u);
                return;
            }
        }

        throw new IllegalStateException(
            "Expression statement no soportada"
        );
    }

    private void generateIncrementStatement(
        UnaryExpr expression
    ) {

        if (expression == null)
            throw new IllegalStateException(
                "++/-- requiere una variable"
            );

        if (expression.expression == null)
            throw new IllegalStateException(
                expression.operator +
                " requiere una variable"
            );

        if (
            !(expression.expression instanceof ValueExpr)
        )
            throw new IllegalStateException(
                expression.operator +
                " requiere una variable"
            );

        ValueExpr value =
            (ValueExpr)
                expression.expression;

        String name =
            requireVariable(value);

        if ("++".equals(expression.operator)) {

            out.append("    inc ")
               .append(name)
               .append("\n");

            return;
        }

        if ("--".equals(expression.operator)) {

            out.append("    dec ")
               .append(name)
               .append("\n");

            return;
        }

        throw new IllegalStateException(
            "Operador unary no soportado: " +
            expression.operator
        );
    }

    private void generateIncrementInto(
        String destination,
        UnaryExpr expression
    ) {

        if (
            !(expression.expression instanceof ValueExpr)
        )
            throw new IllegalStateException(
                expression.operator +
                " requiere una variable"
            );

        ValueExpr v =
            (ValueExpr)
                expression.expression;

        String name =
            requireVariable(v);

        String type =
            typeOf(name);

        registerTemporaryType(
            destination,
            type
        );

        if (expression.postfix) {

            movTo(
                destination,
                name
            );

            if ("++".equals(expression.operator)) {

                out.append("    inc ")
                   .append(name)
                   .append("\n");

                return;
            }

            if ("--".equals(expression.operator)) {

                out.append("    dec ")
                   .append(name)
                   .append("\n");

                return;
            }
        }

        if ("++".equals(expression.operator)) {

            out.append("    inc ")
               .append(name)
               .append("\n");

            movTo(
                destination,
                name
            );

            return;
        }

        if ("--".equals(expression.operator)) {

            out.append("    dec ")
               .append(name)
               .append("\n");

            movTo(
                destination,
                name
            );

            return;
        }

        throw new IllegalStateException(
            "Operador unary no soportado: " +
            expression.operator
        );
    }

    private void generateInject(
        InjectBytesStmt statement
    ) {

        if (statement.bytes.isEmpty())
            throw new IllegalStateException(
                "InjectBytes vacio"
            );

        String directive;

        switch (statement.kind) {
            case WORD:  directive = ".raw16"; break;
            case DWORD: directive = ".raw32"; break;
            case QWORD: directive = ".raw64"; break;
            default:    directive = ".raw8";  break;
        }

        for (Expression e : statement.bytes)
            appendRaw(directive, e);

        if (statement.volatileWrite)
            out.append("    volatile_barrier\n");
    }

    private void appendRaw(
        String directive,
        Expression expression
    ) {

        out.append("    ")
           .append(directive)
           .append(" ")
           .append(expressionConstant(expression))
           .append("\n");
    }

    private void generateVariable(
        VariableDecl variable
    ) {

        {
            String vt0 = normalizeType(variable.type);

            registerType(variable.name, variable.type);

            if (isArrayType(vt0)) {
                generateArrayDecl(variable);
                return;
            }

            if (
                "string".equals(vt0) &&
                mutated.contains(variable.name) &&
                variable.value != null
            ) {
                usesRuntime = true;

                String src0 = expressionValue(variable.value);

                out.append("    %").append(variable.name)
                   .append(" call[__ash_str_new, ")
                   .append(src0).append("]\n");

                return;
            }
        }

        registerType(
            variable.name,
            variable.type
        );

        if (variable.value == null) {

            movTo(
                variable.name,
                "0"
            );

            return;
        }

        if (variable.value instanceof ValueExpr) {

            ValueExpr v =
                (ValueExpr)
                    variable.value;

            if ("string".equals(v.type)) {

                addr(
                    variable.name,
                    stringSymbol(v.value)
                );

            } else if (isIdentifierValue(v)) {

                movTo(
                    variable.name,
                    identifierName(v)
                );

            } else {

                movTo(
                    variable.name,
                    v.value
                );
            }

            return;
        }

        if (
            variable.value instanceof AddressOfExpr
        ) {

            generateAddressInto(
                variable.name,
                (AddressOfExpr)
                    variable.value
            );

            return;
        }

        generateExpressionInto(
            variable.name,
            variable.value
        );
    }

    private void generateAssignment(
        Assignment assignment
    ) {

        if (assignment.target instanceof IndexExpr) {
            generateIndexStore(
                (IndexExpr) assignment.target,
                assignment.value
            );
            return;
        }

        if (
            assignment.target instanceof
            DereferenceExpr
        ) {

            generateStore(
                (DereferenceExpr)
                    assignment.target,
                assignment.value
            );

            return;
        }

        if (
            !(assignment.target instanceof ValueExpr)
        )
            throw new IllegalStateException(
                "Destino de asignacion no soportado"
            );

        ValueExpr target =
            (ValueExpr)
                assignment.target;

        String name;

        if (isIdentifierValue(target))
            name = requireVariable(target);
        else
            name = target.value;

        String tType0 = variableTypes.get(name);

        if ("string".equals(tType0) && mutated.contains(name)) {

            usesRuntime = true;

            String src0 = expressionValue(assignment.value);

            out.append("    %").append(name)
               .append(" call[__ash_str_set, ")
               .append(name).append(", ").append(src0).append("]\n");

            return;
        }

        if (
            isArrayType(tType0) &&
            assignment.value instanceof ArrayLiteralExpr
        ) {
            generateArrayLiteralInto(
                name,
                tType0,
                (ArrayLiteralExpr) assignment.value
            );
            return;
        }

        if (assignment.value instanceof ValueExpr) {

            ValueExpr v =
                (ValueExpr)
                    assignment.value;

            if ("string".equals(v.type)) {

                addr(
                    name,
                    stringSymbol(v.value)
                );

            } else if (isIdentifierValue(v)) {

                movTo(
                    name,
                    identifierName(v)
                );

            } else {

                movTo(
                    name,
                    v.value
                );
            }

            return;
        }

        if (
            assignment.value instanceof AddressOfExpr
        ) {

            generateAddressInto(
                name,
                (AddressOfExpr)
                    assignment.value
            );

            return;
        }

        if (
            assignment.value instanceof DereferenceExpr
        ) {

            generateLoadInto(
                name,
                (DereferenceExpr)
                    assignment.value
            );

            return;
        }

        if (
            assignment.value instanceof CastExpr
        ) {

            generateCastInto(
                name,
                (CastExpr)
                    assignment.value
            );

            return;
        }

        if (
            assignment.value instanceof CallExpr
        ) {

            generateCall(
                name,
                (CallExpr)
                    assignment.value
            );

            return;
        }

        generateExpressionInto(
            name,
            assignment.value
        );
    }

    private boolean references(
        Ast.Expression expression,
        String name
    ) {

        if (expression instanceof IndexExpr) {
            IndexExpr ix = (IndexExpr) expression;
            return references(ix.base, name) || references(ix.index, name);
        }

        if (expression instanceof LengthExpr)
            return references(((LengthExpr) expression).expression, name);

        if (expression == null || name == null)
            return false;

        if (expression instanceof ValueExpr) {

            ValueExpr value =
                (ValueExpr) expression;

            return
                isIdentifierValue(value) &&
                name.equals(
                    identifierName(value)
                );
        }

        if (expression instanceof UnaryExpr) {

            return references(
                ((UnaryExpr) expression).expression,
                name
            );
        }

        if (expression instanceof BinaryExpr) {

            BinaryExpr binary =
                (BinaryExpr) expression;

            return
                references(binary.left, name) ||
                references(binary.right, name);
        }

        if (expression instanceof NotExpr) {

            return references(
                ((NotExpr) expression).expression,
                name
            );
        }

        if (expression instanceof AddressOfExpr) {

            return references(
                ((AddressOfExpr) expression).expression,
                name
            );
        }

        if (expression instanceof DereferenceExpr) {

            return references(
                ((DereferenceExpr) expression).expression,
                name
            );
        }

        if (expression instanceof CastExpr) {

            return references(
                ((CastExpr) expression).expression,
                name
            );
        }

        if (expression instanceof CallExpr) {

            CallExpr call =
                (CallExpr) expression;

            for (Ast.Expression argument :
                call.arguments) {

                if (references(argument, name))
                    return true;
            }
        }

        return false;
    }

    private void generateCastInto(
        String destination,
        CastExpr expression
    ) {

        String sourceType =
            expressionType(
                expression.expression
            );

        registerTemporaryType(
            destination,
            sourceType
        );

        if (
            expression.expression instanceof
            ValueExpr
        ) {

            ValueExpr v =
                (ValueExpr)
                    expression.expression;

            if ("string".equals(v.type)) {

                addr(
                    destination,
                    stringSymbol(v.value)
                );

            } else if (isIdentifierValue(v)) {

                movTo(
                    destination,
                    identifierName(v)
                );

            } else {

                movTo(
                    destination,
                    v.value
                );
            }

            return;
        }

        if (
            expression.expression instanceof
            AddressOfExpr
        ) {

            generateAddressInto(
                destination,
                (AddressOfExpr)
                    expression.expression
            );

            return;
        }

        if (
            expression.expression instanceof
            DereferenceExpr
        ) {

            generateLoadInto(
                destination,
                (DereferenceExpr)
                    expression.expression
            );

            return;
        }

        generateExpressionInto(
            destination,
            expression.expression
        );
    }

    private boolean isFunctionRef(Expression e) {

        if (!(e instanceof ValueExpr)) return false;

        ValueExpr v = (ValueExpr) e;

        if (!isIdentifierValue(v)) return false;

        String n = identifierName(v);

        if (variableTypes.get(n) != null || temporaryTypes.get(n) != null)
            return false;

        return functionReturnTypes.containsKey(n) || externReturnTypes.containsKey(n);
    }

    private void generateAddressInto(
        String destination,
        AddressOfExpr expression
    ) {

        if (isFunctionRef(expression.expression)) {

            faddr(destination, identifierName((ValueExpr) expression.expression));
            return;
        }

        if (
            !(expression.expression instanceof ValueExpr)
        )
            throw new IllegalStateException(
                "El operador & requiere una variable"
            );

        ValueExpr v =
            (ValueExpr)
                expression.expression;

        String varName = requireVariable(v);

        String vType = variableTypes.get(varName);
        if (vType == null) vType = temporaryTypes.get(varName);

        if (isArrayType(vType)) {

            registerTemporaryType(destination, "ptr");
            movTo(destination, varName);
            return;
        }

        addr(
            destination,
            varName
        );
    }

    private void generateLoadInto(
        String destination,
        DereferenceExpr expression
    ) {

        String source =
            expressionValue(
                expression.expression
            );

        String pt = expressionType(expression.expression);
        String elem = null;

        if (pt != null && pt.startsWith("ptr:")) {
            try { elem = Nums.normalizeType(pt.substring(4)); }
            catch (RuntimeException ignored) { elem = null; }
        }

        String dt = null;
        try { dt = typeOf(destination); } catch (RuntimeException ignored) { }

        if (elem != null && intRank(elem) > 0 && (dt == null || !dt.equals(elem) || destination.startsWith("__tmp"))) {

            String t = typedTemp(elem);

            out.append("    %").append(t).append(" load[").append(source).append("]\n");

            movTo(destination, t);

            return;
        }

        out.append("    %")
           .append(destination)
           .append(" load[")
           .append(source)
           .append("]\n");
    }

    private void generateStore(
        DereferenceExpr target,
        Expression expression
    ) {

        String pointer =
            expressionValue(
                target.expression
            );

        {
            String pt = expressionType(target.expression);
            String elem = null;

            if (pt != null && pt.startsWith("ptr:")) {
                try { elem = Nums.normalizeType(pt.substring(4)); }
                catch (RuntimeException ignored) { elem = null; }
            }

            boolean isStringLit =
                expression instanceof ValueExpr && "string".equals(((ValueExpr) expression).type);

            if (elem != null && intRank(elem) > 0 && !isStringLit) {

                String tt = typedTemp(elem);

                generateExpressionInto(tt, expression);

                out.append("    store[")
                   .append(pointer)
                   .append(", ")
                   .append(tt)
                   .append("]\n");

                return;
            }
        }

        if (expression instanceof ValueExpr) {

            ValueExpr v =
                (ValueExpr)
                    expression;

            if ("string".equals(v.type)) {

                String temp =
                    temporary("ptr");

                addr(
                    temp,
                    stringSymbol(v.value)
                );

                out.append("    store[")
                   .append(pointer)
                   .append(", ")
                   .append(temp)
                   .append("]\n");

                return;
            }

            String value;

            if (isIdentifierValue(v))
                value = identifierName(v);
            else
                value = v.value;

            out.append("    store[")
               .append(pointer)
               .append(", ")
               .append(value)
               .append("]\n");

            return;
        }

        String temp =
            temporary(
                expressionType(expression)
            );

        if (expression instanceof CastExpr) {

            generateCastInto(
                temp,
                (CastExpr)
                    expression
            );

        } else if (
            expression instanceof AddressOfExpr
        ) {

            generateAddressInto(
                temp,
                (AddressOfExpr)
                    expression
            );

        } else {

            generateExpressionInto(
                temp,
                expression
            );
        }

        out.append("    store[")
           .append(pointer)
           .append(", ")
           .append(temp)
           .append("]\n");
    }

    private void generateExpressionInto(
        String destination,
        Expression expression
    ) {

        if (isFunctionRef(expression)) {

            faddr(destination, identifierName((ValueExpr) expression));
            return;
        }

        if (expression instanceof GlobalAddrExpr) {
            addr(destination, ((GlobalAddrExpr) expression).symbol);
            return;
        }

        if (expression instanceof IndexExpr) {
            generateIndexLoad(destination, (IndexExpr) expression);
            return;
        }

        if (expression instanceof LengthExpr) {
            registerTemporaryType(destination, "qword");
            generateLength(destination, (LengthExpr) expression);
            return;
        }

        if (expression instanceof ArrayLiteralExpr) {
            generateArrayLiteralInto(
                destination,
                "qword[]",
                (ArrayLiteralExpr) expression
            );
            return;
        }

        String type =
            expressionType(expression);

        registerTemporaryType(
            destination,
            type
        );

        if (expression instanceof UnaryExpr) {

            UnaryExpr u =
                (UnaryExpr) expression;

            if (
                "++".equals(u.operator) ||
                "--".equals(u.operator)
            ) {

                generateIncrementInto(
                    destination,
                    u
                );

                return;
            }

            throw new IllegalStateException(
                "Operador unary no soportado: " +
                u.operator
            );
        }

        if (expression instanceof ValueExpr) {

            ValueExpr v =
                (ValueExpr)
                    expression;

            if ("string".equals(v.type)) {

                addr(
                    destination,
                    stringSymbol(v.value)
                );

            } else if (isIdentifierValue(v)) {

                movTo(
                    destination,
                    identifierName(v)
                );

            } else {

                movTo(
                    destination,
                    v.value
                );
            }

            return;
        }

        if (expression instanceof CastExpr) {

            generateCastInto(
                destination,
                (CastExpr)
                    expression
            );

            return;
        }

        if (expression instanceof AddressOfExpr) {

            generateAddressInto(
                destination,
                (AddressOfExpr)
                    expression
            );

            return;
        }

        if (expression instanceof DereferenceExpr) {

            generateLoadInto(
                destination,
                (DereferenceExpr)
                    expression
            );

            return;
        }

        if (expression instanceof CallExpr) {

            generateCall(
                destination,
                (CallExpr)
                    expression
            );

            return;
        }

        if (
            expression instanceof BinaryExpr &&
            "+".equals(
                ((BinaryExpr)
                    expression).operator
            )
        ) {

            String folded =
                foldString(expression);

            if (folded != null) {

                addr(
                    destination,
                    stringSymbol(folded)
                );

                return;
            }
        }

        if (!(expression instanceof BinaryExpr))
            throw new IllegalStateException(
                "Expresion no soportada: " +
                expression.getClass().getName()
            );

        BinaryExpr b =
            (BinaryExpr) expression;

        {
            String dt = null;

            try { dt = typeOf(destination); }
            catch (RuntimeException ignored) { }

            String et = expressionType(expression);

            boolean cmp =
                b.operator.equals("==") || b.operator.equals("!=") ||
                b.operator.equals("<")  || b.operator.equals(">")  ||
                b.operator.equals("<=") || b.operator.equals(">=") ||
                b.operator.equals("&&") || b.operator.equals("||");

            if (
                !cmp &&
                intRank(dt) > 0 &&
                intRank(et) > intRank(dt)
            ) {
                String wide = temporary(et);

                generateExpressionInto(wide, expression);

                movTo(destination, wide);

                return;
            }
        }

        if (
            references(
                b.right,
                destination
            ) &&
            !(
                b.left instanceof ValueExpr &&
                destination.equals(
                    isIdentifierValue(
                        (ValueExpr) b.left
                    )
                    ? identifierName(
                        (ValueExpr) b.left
                    )
                    : ((ValueExpr)
                        b.left).value
                )
            )
        ) {

            String t =
                tmpFor(expressionType(b.left));

            generateExpressionInto(
                t,
                b.left
            );

            String rhs =
                tmpFor(expressionType(b.right));

            generateExpressionInto(
                rhs,
                b.right
            );

            operation(
                t,
                b.operator,
                rhs
            );

            movTo(
                destination,
                t
            );

            return;
        }

        if (b.left instanceof ValueExpr) {

            ValueExpr left =
                (ValueExpr) b.left;

            if ("string".equals(left.type)) {

                addr(
                    destination,
                    stringSymbol(left.value)
                );

            } else if (isIdentifierValue(left)) {

                movTo(
                    destination,
                    identifierName(left)
                );

            } else {

                movTo(
                    destination,
                    left.value
                );
            }

        } else {

            String temp =
                tmpFor(expressionType(b.left));

            generateExpressionInto(
                temp,
                b.left
            );

            movTo(
                destination,
                temp
            );
        }

        if (b.right instanceof ValueExpr) {

            ValueExpr right =
                (ValueExpr) b.right;

            if ("string".equals(right.type)) {

                String temp =
                    temporary("ptr");

                addr(
                    temp,
                    stringSymbol(right.value)
                );

                operation(
                    destination,
                    b.operator,
                    temp
                );

            } else if (isIdentifierValue(right)) {

                operation(
                    destination,
                    b.operator,
                    identifierName(right)
                );

            } else {

                operation(
                    destination,
                    b.operator,
                    right.value
                );
            }

        } else {

            String temp =
                tmpFor(expressionType(b.right));

            generateExpressionInto(
                temp,
                b.right
            );

            operation(
                destination,
                b.operator,
                temp
            );
        }
    }

    private boolean isIndirectCall(CallExpr call) {
        if (functionReturnTypes.containsKey(call.name) || externReturnTypes.containsKey(call.name))
            return false;
        String t = variableTypes.get(call.name);
        return t != null && (isArrayType(t) || isPtrType(t) || irType(t).equals("qword") || irType(t).equals("dword"));
    }

    private void generatePointerCall(String destination, Expression target, java.util.List<Expression> callArgs) {

        String wt = targetBits == 64 ? "qword" : "dword";
        java.util.List<String> args = new java.util.ArrayList<>();

        for (Expression e : callArgs) {
            String t = typedTemp(wt);
            generateExpressionInto(t, e);
            args.add(t);
        }

        String fp = typedTemp(wt);
        generateExpressionInto(fp, target);

        StringBuilder a = new StringBuilder();

        if (targetBits == 64 && MS_ABI) {
            String[] regs = {"rcx", "rdx", "r8", "r9"};
            int stackArgs = Math.max(0, args.size() - 4);
            int frame = 32 + 8 * stackArgs;
            frame = (frame + 15) & ~15;
            a.append("sub rsp, ").append(frame).append('\n');
            for (int i = 4; i < args.size(); i++) {
                a.append("mov rax, @var:").append(args.get(i)).append('\n');
                a.append("mov [rsp + ").append(32 + 8 * (i - 4)).append("], rax\n");
            }
            for (int i = 0; i < args.size() && i < 4; i++)
                a.append("mov ").append(regs[i]).append(", @var:").append(args.get(i)).append('\n');
            a.append("mov rax, @var:").append(fp).append('\n');
            a.append("call rax\n");
            a.append("add rsp, ").append(frame).append('\n');
        } else if (targetBits == 64) {
            String[] regs = {"rdi", "rsi", "rdx", "rcx", "r8", "r9"};
            if (args.size() > regs.length)
                throw new IllegalStateException("llamada por puntero: maximo 6 argumentos en 64 bits System V");
            for (int i = 0; i < args.size(); i++)
                a.append("mov ").append(regs[i]).append(", @var:").append(args.get(i)).append('\n');
            a.append("mov rax, @var:").append(fp).append('\n');
            a.append("call rax\n");
        } else {
            for (int i = args.size() - 1; i >= 0; i--)
                a.append("push dword @var:").append(args.get(i)).append('\n');
            a.append("mov eax, @var:").append(fp).append('\n');
            a.append("call eax\n");
            if (!args.isEmpty())
                a.append("add esp, ").append(4 * args.size()).append('\n');
        }

        if (destination != null) {
            registerTemporaryType(destination, wt);
            out.append("    %").append(destination).append(" mov[0]\n");
            a.append("mov @var:").append(destination).append(targetBits == 64 ? ", rax\n" : ", eax\n");
        }

        StringBuilder hex = new StringBuilder();
        for (byte b : a.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))
            hex.append(String.format("%02x", b));

        out.append("    %_ asm[x").append(hex).append("]\n");
    }

    private void generateIndirectCall(String destination, CallExpr call) {

        boolean x64 = targetBits == 64;
        String[] regs = {"rdi", "rsi", "rdx", "rcx", "r8", "r9"};

        if (x64 && call.arguments.size() > regs.length)
            throw new IllegalStateException(
                "llamada indirecta a '" + call.name + "': maximo " + regs.length + " argumentos");

        java.util.List<String> args = new java.util.ArrayList<>();

        for (Expression e : call.arguments) {
            String t = typedTemp(targetBits == 64 ? "qword" : "dword");
            generateExpressionInto(t, e);
            args.add(t);
        }

        String fp = typedTemp(targetBits == 64 ? "qword" : "dword");
        generateExpressionInto(fp, new ValueExpr(call.name, "identifier"));

        StringBuilder a = new StringBuilder();

        if (x64) {
            for (int i = 0; i < args.size(); i++)
                a.append("mov ").append(regs[i]).append(", @var:").append(args.get(i)).append('\n');
            a.append("mov rax, @var:").append(fp).append('\n');
            a.append("call rax\n");
        } else {
            for (int i = args.size() - 1; i >= 0; i--)
                a.append("push dword @var:").append(args.get(i)).append('\n');
            a.append("mov eax, @var:").append(fp).append('\n');
            a.append("call eax\n");
            if (!args.isEmpty())
                a.append("add esp, ").append(4 * args.size()).append('\n');
        }

        if (destination != null) {
            registerTemporaryType(destination, targetBits == 64 ? "qword" : "dword");
            out.append("    %").append(destination).append(" mov[0]\n");
            a.append("mov @var:").append(destination).append(x64 ? ", rax\n" : ", eax\n");
        }

        StringBuilder hex = new StringBuilder();
        for (byte b : a.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))
            hex.append(String.format("%02x", b));

        out.append("    %_ asm[x").append(hex).append("]\n");
    }

    private void generateCall(
        String destination,
        CallExpr call
    ) {

        if ("__icall".equals(call.name)) {
            generatePointerCall(destination, call.arguments.get(0),
                call.arguments.subList(1, call.arguments.size()));
            return;
        }

        if (isIndirectCall(call)) {
            generateIndirectCall(destination, call);
            return;
        }

        String returnType =
            functionReturnTypes.get(call.name);

        if (returnType == null)
            returnType =
                externReturnTypes.get(call.name);

        if (returnType == null)
            returnType = "qword";

        if (destination != null)
            registerTemporaryType(
                destination,
                returnType
            );

        java.util.List<Expression> callArgs = call.arguments;

        Integer vfixed = variadicFixed.get(call.name);

        if (vfixed != null) {

            int extras = call.arguments.size() - vfixed;

            if (extras < 0)
                throw new IllegalStateException(
                    "'" + call.name + "' necesita al menos " + vfixed + " argumento(s)"
                );

            if (extras > Funcs.VARIADIC_MAX)
                throw new IllegalStateException(
                    "'" + call.name + "' admite como maximo " + Funcs.VARIADIC_MAX +
                    " argumentos variables, recibidos " + extras
                );

            callArgs = new java.util.ArrayList<>(call.arguments.subList(0, vfixed));
            callArgs.add(new ValueExpr(Integer.toString(extras), "integer"));
            callArgs.addAll(call.arguments.subList(vfixed, call.arguments.size()));

            for (int z = extras; z < Funcs.VARIADIC_MAX; z++)
                callArgs.add(new ValueExpr("0", "integer"));
        }

        String[] arguments =
            new String[
                callArgs.size()
            ];

        for (
            int i = 0;
            i < callArgs.size();
            i++
        ) {

            Expression expr =
                callArgs.get(i);

            String argument =
                temporary(
                    expressionType(expr)
                );

            generateExpressionInto(
                argument,
                expr
            );

            arguments[i] =
                argument;
        }

        out.append("    ");

        if (destination != null) {

            out.append("%")
               .append(destination)
               .append(" ");
        }

        out.append("call[")
           .append(call.name);

        for (String argument :
            arguments) {

            out.append(", ")
               .append(argument);
        }

        out.append("]\n");
    }

    private void operation(
        String destination,
        String operator,
        String argument
    ) {
        argument = charLit(argument);

        if (
            ("<<".equals(operator) || ">>".equals(operator)) &&
            !argument.matches("-?(0[xX][0-9a-fA-F]+|[0-9]+)")
        ) {
            usesRuntime = true;

            out.append("    %")
               .append(destination)
               .append(" call[")
               .append("<<".equals(operator) ? "__ash_shl" : "__ash_shr")
               .append(", ")
               .append(destination)
               .append(", ")
               .append(argument)
               .append("]\n");

            return;
        }

        out.append("    %")
           .append(destination)
           .append(" ")
           .append(
               Exprs.opcode(operator)
           )
           .append("[")
           .append(argument)
           .append("]\n");
    }

    private void generateReturn(
        Function fn,
        ReturnStmt statement
    ) {

        if (statement.terminate && statement.value == null && !"void".equals(normalizeType(fn.returnType)))
            statement.value = new ValueExpr("0", "integer");

        if (STRICT) {

            boolean isVoid = "void".equals(fn.returnType);

            if (isVoid && statement.value != null)
                throw new IllegalStateException(
                    "strict: la funcion '" + fn.name + "' es void y no puede devolver un valor"
                );

            if (!isVoid && statement.value == null)
                throw new IllegalStateException(
                    "strict: la funcion '" + fn.name + "' devuelve " + fn.returnType +
                    " y 'return;' necesita un valor"
                );
        }

        if (statement.value == null) {

            out.append("    ret void\n");
            return;
        }

        if (statement.value instanceof ValueExpr) {

            ValueExpr v =
                (ValueExpr)
                    statement.value;

            if ("string".equals(v.type)) {

                addr(
                    "__ret",
                    stringSymbol(v.value)
                );

                registerTemporaryType(
                    "__ret",
                    "ptr"
                );

                out.append("    ret ")
                   .append(irType(fn.returnType))
                   .append(" __ret\n");

                return;
            }

            String returnValue;

            if (isIdentifierValue(v))
                returnValue =
                    identifierName(v);
            else
                returnValue =
                    v.value;

            registerTemporaryType(
                "__ret",
                fn.returnType
            );

            out.append("    ret ")
               .append(irType(fn.returnType))
               .append(" ")
               .append(returnValue)
               .append("\n");

            return;
        }

        registerTemporaryType(
            "__ret",
            fn.returnType
        );

        generateExpressionInto(
            "__ret",
            statement.value
        );

        out.append("    ret ")
           .append(irType(fn.returnType))
           .append(" __ret\n");
    }

    private String expressionValue(
        Expression expression
    ) {

        if (expression instanceof ValueExpr) {

            ValueExpr v =
                (ValueExpr)
                    expression;

            if ("string".equals(v.type)) {

                String temp =
                    temporary("ptr");

                addr(
                    temp,
                    stringSymbol(v.value)
                );

                return temp;
            }

            if (isIdentifierValue(v))
                return identifierName(v);

            return v.value;
        }

        String temp =
            temporary(
                expressionType(expression)
            );

        generateExpressionInto(
            temp,
            expression
        );

        return temp;
    }

    private String expressionConstant(
        Expression expression
    ) {

        if (expression instanceof ValueExpr) {

            ValueExpr v =
                (ValueExpr)
                    expression;

            if ("string".equals(v.type)) {

                return "addr[" +
                    stringSymbol(v.value) +
                    "]";
            }

            if (isIdentifierValue(v))
                return identifierName(v);

            return v.value;
        }

        return expressionValue(expression);
    }

    private String newLabel() {

        return "__L" +
            labelCounter++;
    }

    private void place(
        String label
    ) {

        out.append("    %_ label[")
           .append(label)
           .append("]\n");
    }

    private void jmp(
        String label
    ) {

        out.append("    %_ jmp[")
           .append(label)
           .append("]\n");
    }

    private void block(
        Function fn,
        java.util.List<Statement> body,
        Set<String> used
    ) {

        if (body == null)
            return;

        for (Statement x :
            body) {

            generateStatement(
                fn,
                x,
                used
            );
        }
    }

    private boolean generateControlStatement(
        Function fn,
        Statement st,
        Set<String> used
    ) {

        if (st instanceof IfStmt) {

            IfStmt s =
                (IfStmt) st;

            stringVars.clear();

            String els =
                newLabel();

            String end =
                newLabel();

            jumpIf(
                s.condition,
                false,
                els
            );

            block(
                fn,
                s.thenBranch,
                used
            );

            if (
                s.elseBranch != null &&
                !s.elseBranch.isEmpty()
            ) {

                jmp(end);

                place(els);

                block(
                    fn,
                    s.elseBranch,
                    used
                );

                place(end);

            } else {

                place(els);
            }

            stringVars.clear();

            return true;
        }

        if (st instanceof WhileStmt) {

            WhileStmt s =
                (WhileStmt) st;

            stringVars.clear();

            String top =
                newLabel();

            String end =
                newLabel();

            place(top);

            jumpIf(
                s.condition,
                false,
                end
            );

            loops.push(
                new String[] {
                    top,
                    end
                }
            );

            block(
                fn,
                s.body,
                used
            );

            loops.pop();

            jmp(top);
            place(end);

            stringVars.clear();

            return true;
        }

        if (st instanceof ForStmt) {

            ForStmt s =
                (ForStmt) st;

            stringVars.clear();

            if (s.init != null)
                generateStatement(
                    fn,
                    s.init,
                    used
                );

            String top =
                newLabel();

            String cont =
                newLabel();

            String end =
                newLabel();

            place(top);

            if (s.condition != null)
                jumpIf(
                    s.condition,
                    false,
                    end
                );

            loops.push(
                new String[] {
                    cont,
                    end
                }
            );

            block(
                fn,
                s.body,
                used
            );

            loops.pop();

            place(cont);

            if (s.update != null)
                generateStatement(
                    fn,
                    s.update,
                    used
                );

            jmp(top);
            place(end);

            stringVars.clear();

            return true;
        }

        if (st instanceof BreakStmt) {

            if (loops.isEmpty())
                throw new IllegalStateException(
                    "break fuera de un loop"
                );

            jmp(
                loops.peek()[1]
            );

            return true;
        }

        if (st instanceof ContinueStmt) {

            if (loops.isEmpty())
                throw new IllegalStateException(
                    "continue fuera de un loop"
                );

            jmp(
                loops.peek()[0]
            );

            return true;
        }

        return false;
    }

    private void collectUsedControl(
        Statement st,
        Set<String> used
    ) {

        if (st instanceof IfStmt) {

            IfStmt s =
                (IfStmt) st;

            collectUsedFromExpression(
                s.condition,
                used
            );

            collectUsedBlock(
                s.thenBranch,
                used
            );

            collectUsedBlock(
                s.elseBranch,
                used
            );

        } else if (st instanceof WhileStmt) {

            WhileStmt s =
                (WhileStmt) st;

            collectUsedFromExpression(
                s.condition,
                used
            );

            collectUsedBlock(
                s.body,
                used
            );

        } else if (st instanceof ForStmt) {

            ForStmt s =
                (ForStmt) st;

            if (s.init != null)
                collectUsedFromStatement(
                    s.init,
                    used
                );

            collectUsedFromExpression(
                s.condition,
                used
            );

            if (s.update != null)
                collectUsedFromStatement(
                    s.update,
                    used
                );

            collectUsedBlock(
                s.body,
                used
            );
        }
    }

    private void collectUsedBlock(
        java.util.List<Statement> body,
        Set<String> used
    ) {

        if (body == null)
            return;

        for (Statement x :
            body)
            collectUsedFromStatement(
                x,
                used
            );
    }

    private static String cmpWord(
        String op
    ) {

        switch (op) {

            case "==":
                return "eq";

            case "!=":
                return "ne";

            case "<":
                return "lt";

            case "<=":
                return "le";

            case ">":
                return "gt";

            case ">=":
                return "ge";

            default:
                return null;
        }
    }

    private String condOperand(
        Expression e
    ) {

        if (
            e instanceof ValueExpr &&
            !"string".equals(
                ((ValueExpr) e).type
            )
        ) {

            ValueExpr v =
                (ValueExpr) e;

            if (isIdentifierValue(v))
                return identifierName(v);

            return v.value;
        }

        String t =
            temporary(
                expressionType(e)
            );

        generateExpressionInto(
            t,
            e
        );

        return t;
    }

    private void branch(
        boolean when,
        String l,
        String op,
        String r,
        String target
    ) {
        l = charLit(l); r = charLit(r);

        out.append("    %_ ")
           .append(
               when ? "brt" : "brf"
           )
           .append("[")
           .append(l)
           .append(", ")
           .append(op)
           .append(", ")
           .append(r)
           .append(", ")
           .append(target)
           .append("]\n");
    }

    private void jumpIf(
        Expression e,
        boolean when,
        String target
    ) {

        if (e instanceof NotExpr) {

            jumpIf(
                ((NotExpr) e).expression,
                !when,
                target
            );

            return;
        }

        if (e instanceof UnaryExpr) {

            branch(
                when,
                condOperand(e),
                "ne",
                "0",
                target
            );

            return;
        }

        if (e instanceof BinaryExpr) {

            BinaryExpr b =
                (BinaryExpr) e;

            String op =
                b.operator;

            if ("&&".equals(op)) {

                if (!when) {

                    jumpIf(
                        b.left,
                        false,
                        target
                    );

                    jumpIf(
                        b.right,
                        false,
                        target
                    );

                } else {

                    String skip =
                        newLabel();

                    jumpIf(
                        b.left,
                        false,
                        skip
                    );

                    jumpIf(
                        b.right,
                        true,
                        target
                    );

                    place(skip);
                }

                return;
            }

            if ("||".equals(op)) {

                if (when) {

                    jumpIf(
                        b.left,
                        true,
                        target
                    );

                    jumpIf(
                        b.right,
                        true,
                        target
                    );

                } else {

                    String ok =
                        newLabel();

                    jumpIf(
                        b.left,
                        true,
                        ok
                    );

                    jumpIf(
                        b.right,
                        false,
                        target
                    );

                    place(ok);
                }

                return;
            }

            String w =
                cmpWord(op);

            if (w != null) {

                String l =
                    condOperand(b.left);

                String r =
                    condOperand(b.right);

                branch(
                    when,
                    l,
                    w,
                    r,
                    target
                );

                return;
            }
        }

        branch(
            when,
            condOperand(e),
            "ne",
            "0",
            target
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
