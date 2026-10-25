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

package shasm;

import java.util.*;
import java.util.regex.*;

public final class InsnDb {
    public final Map<String, List<Tpl>> byMnem = new HashMap<>();
    public int count;

    static final Map<String, Integer> CONDS = new LinkedHashMap<>();
    static {
        String[] n = {"o","no","c","nc","z","nz","na","a","s","ns","pe","po","l","nl","ng","g"};
        for (int i = 0; i < 16; i++) CONDS.put(n[i], i);
        String[][] al = {{"ae","3"},{"b","2"},{"be","6"},{"e","4"},{"ge","13"},{"le","14"},{"nae","2"},{"nb","3"},
            {"nbe","7"},{"ne","5"},{"nge","12"},{"nle","15"},{"np","11"},{"p","10"}};
        for (String[] a : al) CONDS.put(a[0], Integer.parseInt(a[1]));
    }
    private static final Pattern CCNAME = Pattern.compile("^(J|SET|CMOV)CC$", Pattern.CASE_INSENSITIVE);
    private static final Pattern NOT_LEGACY = Pattern.compile("\\b(e?vex|xop|rex2)\\b|\\bevex\\.|\\bvex[.+]|\\bxop\\.");

    private final List<Tpl> raw = new ArrayList<>();

    public InsnDb(String compact) {
        for (String l : compact.split("\n")) if (!l.isEmpty()) process(l);
        expandAll();
    }

    private void process(String line) {
        String[] f = line.split("\t", -1);
        String name = f[0], operands = f[1], letters = f[2], enc = f[3], flags = f[4];
        if (NOT_LEGACY.matcher(enc).find()) return;
        Tpl t = new Tpl();
        t.src = name + " " + operands + " [" + letters + ": " + enc + "] " + flags;
        t.mnem = name;
        for (String fl : flags.split(",")) if (!fl.isEmpty()) t.flags.add(fl);
        if (t.flags.contains("KILL") || t.flags.contains("APX") || t.flags.contains("FUTURE") || t.flags.contains("NEVER")) return;
        if (operands.contains("?")) return;
        if (operands.isEmpty()) { t.ops = new String[0]; t.colon = new boolean[0]; t.star = new boolean[0]; }
        else {
            List<String> ol = new ArrayList<>(); List<Boolean> cl = new ArrayList<>();
            StringBuilder cur = new StringBuilder();
            for (char c : operands.toCharArray()) {
                if (c == ',' || c == ':') { ol.add(cur.toString()); cl.add(c == ':'); cur.setLength(0); } else cur.append(c);
            }
            ol.add(cur.toString()); cl.add(false);
            t.ops = ol.toArray(new String[0]);
            t.colon = new boolean[t.ops.length];
            t.star = new boolean[t.ops.length];
            for (int i = 0; i < t.ops.length; i++) {
                t.colon[i] = cl.get(i);
                if (t.ops[i].endsWith("*")) { t.star[i] = true; t.ops[i] = t.ops[i].substring(0, t.ops[i].length() - 1); }
            }
        }
        for (String tk : enc.trim().split("\\s+")) if (!tk.isEmpty()) t.tokens.add(tk);
        t.slots = new String[t.ops.length];
        Arrays.fill(t.slots, "");
        int k = -1;
        for (int i = 0; i < letters.length(); i++) {
            char c = letters.charAt(i);
            if (c == '+') { if (i + 1 < letters.length() && k >= 0 && k < t.slots.length) t.slots[k] += letters.charAt(++i); }
            else { k++; if (k < t.slots.length) t.slots[k] = c == '-' ? "" : String.valueOf(c); }
        }

        if (t.tok("a16") || t.tok("rex.l")) t.flags.add("NOLONG");
        else if (t.tok("o64") || t.tok("o64nw") || t.tok("a64")) t.flags.add("LONG");
        if (t.flags.contains("NOLONG") && t.flags.contains("LONG")) return;
        raw.add(t);
    }

    private void expandAll() {
        for (Tpl t : raw) {
            List<Tpl> v = new ArrayList<>();
            Matcher cc = CCNAME.matcher(t.mnem);
            if (cc.matches()) {
                for (Map.Entry<String, Integer> e : CONDS.entrySet()) {
                    Tpl c = copy(t);
                    c.mnem = cc.group(1).toUpperCase() + e.getKey().toUpperCase();
                    boolean ok = false;
                    for (int i = 0; i < c.tokens.size(); i++) {
                        String tk = c.tokens.get(i);
                        if (tk.matches("[0-9a-fA-F]{2}\\+c")) {
                            c.tokens.set(i, String.format("%02x", Integer.parseInt(tk.substring(0, 2), 16) ^ e.getValue()));
                            ok = true;
                        }
                    }
                    if (!ok) continue;
                    if (isAlias(e.getKey())) c.flags.add("ND");
                    v.add(c);
                }
            } else v.add(t);
            for (Tpl x : v) {
                boolean any = false;
                for (boolean b : x.star) any |= b;
                add(x);
                if (any) {
                    for (int i = 1; i < x.ops.length; i++) if (x.star[i]) {
                        Tpl r = copy(x);
                        r.ops = remove(x.ops, i); r.star = new boolean[r.ops.length]; r.colon = Arrays.copyOf(x.colon, r.ops.length);
                        r.slots = remove(x.slots, i);
                        r.dropLetter = x.slots[i].isEmpty() ? 0 : x.slots[i].charAt(0);
                        r.srcLetter = x.slots[i - 1].isEmpty() ? 0 : x.slots[i - 1].charAt(0);
                        r.flags.add("ND");
                        add(r);
                        break;
                    }
                }
            }
        }
        raw.clear();
    }

    private static boolean isAlias(String cc) {
        return Set.of("ae","b","be","e","ge","le","nae","nb","nbe","ne","nge","nle","np","p").contains(cc);
    }
    private static String[] remove(String[] a, int i) {
        List<String> l = new ArrayList<>(Arrays.asList(a)); l.remove(i); return l.toArray(new String[0]);
    }
    private Tpl copy(Tpl t) {
        Tpl c = new Tpl();
        c.mnem = t.mnem; c.ops = t.ops.clone(); c.colon = t.colon.clone(); c.star = t.star.clone(); c.slots = t.slots.clone();
        c.tokens = new ArrayList<>(t.tokens); c.flags = new HashSet<>(t.flags); c.src = t.src;
        c.dropLetter = t.dropLetter; c.srcLetter = t.srcLetter;
        return c;
    }
    private void add(Tpl t) {
        t.id = count++;
        byMnem.computeIfAbsent(t.mnem.toUpperCase(), k -> new ArrayList<>()).add(t);
    }

    public List<Tpl> get(String mnem) { return byMnem.get(mnem.toUpperCase()); }
    public boolean has(String mnem) { return byMnem.containsKey(mnem.toUpperCase()); }
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
