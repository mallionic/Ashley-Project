# ashc 1.0.0 — bugs found while writing the ASH guide

Each item was reproduced while the manual was built. The manual documents the current
behaviour (warnings / workarounds); when a bug is fixed, update the matching warning.

1. Binary literals `0b1011` inside function bodies -> ASHIR parse error "expected ] found IDENT(b1011)".
2. Negative constants with magnitude > 2^31-1 (`-5000000000`, `0 - 2147483648`) computed wrong (imm32 subtraction).
3. Variable right shift `u >> n` with unsigned `u` is arithmetic (`__ash_shr` uses sar).
4. Parameter signedness leaks between functions that use the same parameter names
   (`scmp(int64 a, int64 b)` then `pcmp(uint64 a, uint64 b)` -> pcmp compares signed).
5. `continue` inside a `switch` inside a loop -> infinite loop (switch is lowered to a while).
6. Function that reads `*p` and then writes `*p` through a pointer parameter -> "Variable does not exist: p".
7. String `+` concatenation segfaults; writing to a string literal segfaults; `#s` on a string variable returns garbage.
8. Decimal float literals rejected ("El literal es de punto flotante"); double arithmetic unsupported ("expected entero").
9. Comparisons and `&&`/`||` cannot be used as values (`int64 x = a < b;` -> "operator '<' is not supported").
10. A cast to a narrow type does not truncate inside expressions (`(uint8)300` is 300).
11. ELF objects embed host information: FILE symbols (OS, kernel version, user name, home directory)
    and `.comment` with the absolute path of the source file.
12. Several diagnostics are still in Spanish (strict mode, constant globals, override ties, matrix rows, `Variable no declarada`, `Llamada a extern ... requires JavaElfOBJ`, `--debug` output, logs/build.log).
13. Redeclaring a local with the same name and type silently re-initializes it.
14. Locals are function-scoped (visible after the end of their block).
15. Parameter written `int64 v[]` becomes an untyped pointer: `v[i]` indexes bytes. `int64* v` works.
16. Local `char[] c = "abc"` has no header: `#c` is garbage and `c[i] = x` aborts in the bounds check. Global `char[]` works.
17. A `uint8` parameter stored into a `uint8` structure field (`cell->ch = c`) -> internal ArrayIndexOutOfBoundsException.
18. An `asm` block that shasm cannot assemble (e.g. `mvo rax, 1`, `[rbx+rcx*3]`) is silently dropped: no bytes, no error.
19. `--no-libc`: `exit()` exists only when the runtime block is emitted (program uses arrays/strings);
    otherwise `ld` reports "undefined reference to exit".
20. `import <null>;` fails: the library's own macro `null` rewrites the import name to `<(void*)0>`.
21. `<stdlib>` cannot be linked: its wrappers call `__stlib_*` symbols that no library defines, and all its externs stay undefined.
22. `<types>`: assigning the result of `inf()` / `nan()` (double) to an integer -> "expected entero".
23. 16-bit flat binaries: reading a global array (`general uint16[] table = {...}`) crashes in the 8086 emulator (CPU exception); local arrays work.
24. A non-void function whose body ends without `return` (e.g. `func f(): int64 { int64 x = 5; }`) crashes with SIGSEGV when called (no `ret` is emitted).
25. Bitwise operators with a 64-bit immediate that does not fit in 32 bits (`a | 0x8000000000000000`) lose the constant (imm32 truncation). Workaround: load the constant into a variable first.
26. 32-bit objects (ELF32/COFF i386): `@section` is ignored; everything goes to .text/.data.
