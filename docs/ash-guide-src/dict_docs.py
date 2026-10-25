"""
Entries of the ASH Language Dictionary.
word: (category, syntax, meaning, program or None, see-also labels)
A program is a complete ASH file; it is compiled and run.
"""

M = lambda body: '@Entry\npub func main(): int64 {\n' + '\n'.join('    ' + l if l else '' for l in body.split('\n')) + '\n    return 0;\n}'

DICT = [
    ('#', 'operator', '#array',
     'Gives the number of elements of an array (for a two-dimensional array, the number of rows). The length is '
     'read from the array itself at run time, so it is always correct.',
     M('int64[] v = {4, 8, 15};\nprintf("the array has %d elements\\n", #v);'), ['arr-length']),
    ('&', 'operator', '&variable  |  a & b',
     'Before a name, `&` gives the **address** of the variable, array, global or function: the place in memory where '
     'it is stored. Between two values, `&` is the bitwise AND: each bit of the result is 1 only if both bits are 1.',
     M('int64 x = 5;\nint64* p = &x;\n*p = 9;\nprintf("x is now %d; 12 & 10 = %d\\n", x, 12 & 10);'), ['type-ptr', 'expr-bitwise']),
    ('*', 'operator', 'a * b  |  *pointer  |  type*',
     'Between two values, `*` multiplies. Before a pointer, `*` reads (or, on the left of `=`, writes) the value the '
     'pointer points to. After a type name, `*` makes a pointer type: `int64*` is "address of an int64".',
     M('int64 x = 6;\nint64* p = &x;\nprintf("%d %d\\n", x * 7, *p);'), ['type-ptr']),
    ('**', 'operator', 'base ** exponent',
     'Raises a number to a power: `2 ** 10` is 1024. With constants the result is computed by the compiler.',
     M('int64 n = 3;\nprintf("%d %d\\n", 2 ** 10, n ** 4);'), ['expr-pow']),
    ('***', 'operator', 'base *** n',
     'Squares the base *n* times: `2 *** 3` is ((2 squared) squared) squared = 256.',
     M('printf("%d\\n", 2 *** 3);'), ['expr-pow']),
    ('++ and --', 'operator', 'x++  ++x  x--  --x',
     'Add 1 to or subtract 1 from a variable. `x++` gives the old value and then increments; `++x` increments first.',
     M('int64 i = 5;\nint64 a = i++;\nint64 b = ++i;\ni--;\nprintf("a=%d b=%d i=%d\\n", a, b, i);'), ['expr-unary']),
    ('+= -= *= /= %=', 'operator', 'x += value',
     'Shorthand for changing a variable with its own value: `x += 3` means `x = x + 3`. The same exists for '
     '`-`, `*`, `/`, `%`, `<<`, `>>`, `&`, `|` and `^`.',
     M('int64 x = 10;\nx += 5;\nx *= 2;\nx -= 1;\nprintf("%d\\n", x);'), ['lex-compound']),
    ('-> and .', 'operator', 'pointer->field  |  variable.field  |  Class.method()',
     'Reach a field of a structure: `.` on a structure variable, `->` through a pointer. `.` also calls class '
     'methods and invokes cracks.',
     '''typedef struct {
    int64 w;
    int64 h;
} Box;

@Entry
pub func main(): int64 {
    Box b;
    b.w = 3;
    Box* p = &b;
    p->h = 4;
    printf("%d x %d\\n", b.w, p->h);
    return 0;
}''', ['st-access']),
    ('...', 'syntax', 'func name(type first, ...)',
     'At the end of a parameter list, `...` lets a function receive up to eight extra arguments, read with '
     '`va_count()` and `va_arg(i)`.',
     '''func total(int64 first, ...): int64 {
    int64 t = first;
    for (int64 i = 0; i < va_count(); i++) {
        t = t + va_arg(i);
    }
    return t;
}

@Entry
pub func main(): int64 {
    printf("%d\\n", total(1, 2, 3, 4));
    return 0;
}''', ['fn-variadic']),
    ('<< and >>', 'operator', 'value << n  |  value >> n',
     'Shift the bits of a value left or right by *n* positions. Shifting left by one doubles the value; shifting '
     'right by one halves it (dropping the remainder).',
     M('printf("%d %d\\n", 3 << 4, 100 >> 2);'), ['expr-shift']),
    ('== != < <= > >=', 'operator', 'if (a == b) ...',
     'Compare two values: equal, not equal, less, less or equal, greater, greater or equal. They are used in '
     'conditions (`if`, `while`, `for`).',
     M('int64 a = 3;\nif (a != 4) { printf("a is not 4\\n"); }\nif (a <= 3) { printf("a is at most 3\\n"); }'), ['expr-cmp']),
    ('&& || !', 'operator', 'if (a && b) ...  if (a || b) ...  if (!(a)) ...',
     'Combine conditions: `&&` (and) is true when both are true, `||` (or) when at least one is true, `!` (not) '
     'inverts a condition in parentheses.',
     M('int64 age = 30;\nif (age >= 18 && age < 65) { printf("working age\\n"); }\nif (!(age == 0)) { printf("not zero\\n"); }'), ['cf-conditions']),
    ('@', 'syntax', '@Name  |  @Name(arguments)',
     'Starts an annotation: extra information for the compiler written before a function, a global or a file.',
     None, ['ch-annotations']),
    ('asm', 'statement', 'asm { instructions }  |  asm volatile("instructions")',
     'Writes processor instructions directly. ASH variables can be named inside the block.',
     M('int64 x = 20;\nasm {\n    add x, 22\n}\nprintf("%d\\n", x);'), ['ch-asm']),
    ('bool, true, false', 'type and constants', 'bool flag = true;',
     'A type for yes/no values. `true` is 1 and `false` is 0.',
     M('bool ready = true;\nif (ready == true) { printf("ready=%d\\n", ready); }'), ['type-bool']),
    ('break', 'statement', 'break;',
     'Leaves the loop (or `switch`) it is in, immediately.',
     M('for (int64 i = 0; i < 100; i++) {\n    if (i == 3) { break; }\n    printf("%d ", i);\n}\nprintf("\\n");'), ['cf-break']),
    ('case and default', 'statement', 'switch (x) { case 1: ... default: ... }',
     'Labels inside a `switch`. `case value:` marks where to start when the value matches; `default:` is used when '
     'no case matches.',
     M('int64 day = 6;\nswitch (day) {\n    case 6:\n    case 7:\n        printf("weekend\\n");\n        break;\n    default:\n        printf("weekday\\n");\n}'), ['cf-switch']),
    ('char', 'type', "char c = 'A';",
     'A type for one character, stored as its 8-bit code.',
     M("char c = 'A';\nc = c + 2;\nprintf(\"%c\\n\", c);"), ['type-char']),
    ('class', 'declaration', 'class Name { pub func m(...) { ... } }',
     'Groups functions under a name. Methods are called as `Name.method(...)`; `priv` methods can be called only '
     'from the class.',
     '''class Counter {
    pub func next(int64 n): int64 {
        return n + 1;
    }
}

@Entry
pub func main(): int64 {
    printf("%d\\n", Counter.next(41));
    return 0;
}''', ['ch-classes']),
    ('continue', 'statement', 'continue;',
     'Skips the rest of the current loop iteration and goes on with the next one.',
     M('for (int64 i = 0; i < 6; i++) {\n    if (i % 2 == 1) { continue; }\n    printf("%d ", i);\n}\nprintf("\\n");'), ['cf-break']),
    ('define', 'declaration', 'define NAME value;',
     'Gives a name to a value or a piece of code. Every later use of the name is replaced by the value.',
     'define SIZE 8;\n\n' + M('int64[SIZE] v;\nprintf("%d\\n", #v);'), ['pp-define']),
    ('elif and else', 'statement', 'if (c) { } elif (c) { } else { }',
     '`elif` (or `else if`) tests another condition when the previous ones were false; `else` runs when none was true.',
     M('int64 n = 0;\nif (n > 0) { printf("positive\\n"); }\nelif (n < 0) { printf("negative\\n"); }\nelse { printf("zero\\n"); }'), ['cf-if']),
    ('emit', 'statement', 'emit(byte, byte, ...);  emit16(...)  emit32(...)  emit64(...)',
     'Places raw machine-code bytes in the program.',
     M('int64 r = 0;\nemit(0xB8, 0x07, 0x00, 0x00, 0x00);   // mov eax, 7\nasm volatile("mov {r}, rax");\nprintf("%d\\n", r);'), ['asm-emit']),
    ('extern', 'declaration', 'extern type name(parameter types);',
     'Announces a function that exists outside the ASH program, for example in the C library.',
     'extern int64 labs(int64 v);\n\n' + M('printf("%d\\n", labs(-9));'), ['fn-extern']),
    ('for', 'statement', 'for (start; condition; step) { ... }',
     'Repeats a block: runs *start* once, then while *condition* is true runs the block and then *step*.',
     M('for (int64 i = 3; i > 0; i--) {\n    printf("%d... ", i);\n}\nprintf("go!\\n");'), ['cf-for']),
    ('func', 'declaration', 'func name(type param, ...): result { ... }',
     'Defines a function: a named piece of code that receives parameters and may return a result.',
     'func triple(int64 x): int64 {\n    return x * 3;\n}\n\n' + M('printf("%d\\n", triple(14));'), ['fn-decl']),
    ('general and ungeneral', 'declaration', 'general static type name = value',
     'Declare a global variable (outside every function). `general` makes it visible to other files and to C; '
     '`ungeneral` keeps it private. Add `static` to make it changeable.',
     'general static int64 calls = 0\n\nfunc tick(): void {\n    calls = calls + 1;\n}\n\n' + M('tick();\ntick();\nprintf("%d\\n", calls);'), ['var-global']),
    ('if', 'statement', 'if (condition) { ... }',
     'Runs a block only when the condition is true.',
     M('int64 t = 31;\nif (t > 30) {\n    printf("hot\\n");\n}'), ['cf-if']),
    ('import', 'declaration', 'import <library>;  |  import "file.ash";',
     'Brings in the functions and constants of a library or of another file of your project.',
     'import <math>;\n\n' + M('printf("%d\\n", gcd(12, 18));'), ['ch-modules']),
    ('int8 ... uint64', 'types', 'int64 x;  uint8 b;',
     'Integer types. The number is the size in bits (8, 16, 32 or 64); `u` means unsigned (no negative values).',
     M('int8 small = -100;\nuint16 medium = 60000;\nint64 large = 9000000000;\nprintf("%d %d %lld\\n", small, medium, large);'), ['type-int']),
    ('len, length, rows, cols, count', 'built-in functions', 'len(a)  rows(m)  cols(m)  count(m)',
     '`len(a)` and `length(a)` are the same as `#a`. For a matrix, `rows`, `cols` and `count` give its rows, '
     'columns and number of elements.',
     M('matrix m[] []{\n    [int32 1], [int32 2], [int32 3],\n    [int32 4], [int32 5], [int32 6],\n}\nprintf("%d rows, %d cols, %d cells\\n", rows(m), cols(m), count(m));'), ['arr-length']),
    ('loop', 'statement', 'loop { ... }',
     'Repeats a block forever; leave it with `break`, `return` or `terminate`.',
     M('int64 n = 1;\nloop {\n    n = n * 3;\n    if (n > 100) { break; }\n}\nprintf("%d\\n", n);'), ['cf-loop']),
    ('macro', 'declaration', 'macro NAME { statements }',
     'A named block of statements, inserted wherever the name is written.',
     'macro HELLO {\n    printf("hello\\n");\n}\n\n' + M('HELLO\nHELLO'), ['pp-macro']),
    ('matrix', 'statement', 'matrix name[] []{ [type value], ... }',
     'Declares a table of numbers written row by row (each line is a row).',
     M('matrix t[] []{\n    [int32 1], [int32 2],\n    [int32 3], [int32 4],\n}\nprintf("%d\\n", t[1][0]);'), ['arr-matrix']),
    ('pub and priv', 'modifier', 'pub func ...  |  priv func ...',
     '`pub` makes a function visible outside the file (to C and the linker); `priv` restricts it to its file or class.',
     None, ['mod-visibility']),
    ('redefine', 'declaration', 'redefine type old(...) type new(...)',
     'Gives an existing function a new name, or exports a new definition.',
     'redefine uint64 printf(char[], ...) uint64 say(char[]...)\n\n' + M('say("said with %s\\n", "an alias");'), ['mod-redefine']),
    ('repeat', 'statement', 'repeat (count) { ... }',
     'Runs a block a fixed number of times.',
     M('repeat (3) {\n    printf("ha");\n}\nprintf("!\\n");'), ['cf-repeat']),
    ('return', 'statement', 'return value;  |  return;',
     'Ends the function and gives a value back to the caller.',
     'func half(int64 x): int64 {\n    return x / 2;\n}\n\n' + M('printf("%d\\n", half(84));'), ['cf-return']),
    ('runnable', 'modifier', 'runnable byte[] code = { ... }',
     'Declares a global array whose bytes are machine code that can be called like a function.',
     'runnable byte[] answer = {0xB8, 42, 0, 0, 0, 0xC3}\n\n' + M('printf("%d\\n", answer());'), ['var-runnable']),
    ('static', 'modifier', 'general static int64 g = 0',
     'Makes a global variable changeable. Without `static`, a global is a constant.',
     None, ['var-global']),
    ('string', 'type', 'string s = "text";',
     'A type for text: holds the address of characters ending with a 0 byte.',
     M('string s = "ASH";\nprintf("%s starts with %c\\n", s, s[0]);'), ['type-string']),
    ('struct and typedef', 'declaration', 'typedef struct { fields } Name;  |  typedef type Name;',
     '`struct` groups fields into one type; `typedef` gives a new name to a type.',
     'typedef uint8 byte_t;\ntypedef struct {\n    int64 x;\n    int64 y;\n} Point;\n\n' + M('Point p;\np.x = 2;\np.y = 5;\nbyte_t b = 200;\nprintf("(%d, %d) %d\\n", p.x, p.y, b);'), ['ch-structs']),
    ('switch', 'statement', 'switch (value) { case ...: ... }',
     'Chooses among several cases by comparing one value with each `case`.',
     M("char op = '*';\nswitch (op) {\n    case '+': printf(\"plus\\n\"); break;\n    case '*': printf(\"times\\n\"); break;\n    default: printf(\"other\\n\");\n}"), ['cf-switch']),
    ('terminate', 'statement', 'terminate value;  |  terminate;',
     'Ends the function at once, even from inside several loops, and returns the value.',
     'func first_even(int64* v, int64 n): int64 {\n    for (int64 i = 0; i < n; i++) {\n        if (v[i] % 2 == 0) { terminate v[i]; }\n    }\n    return -1;\n}\n\n' + M('int64[] v = {3, 7, 10, 12};\nprintf("%d\\n", first_even(v, #v));'), ['cf-terminate']),
    ('unreturnable', 'statement', 'unreturnable value;',
     'Sets the result a function will return later, without stopping the function.',
     'func work(): int64 {\n    unreturnable 99;\n    printf("still working\\n");\n    return;\n}\n\n' + M('printf("%d\\n", work());'), ['crk-unret']),
    ('"use strict"', 'directive', '"use strict";',
     'At the top of a file, turns on strict mode: extra checks on numbers and returns.',
     '"use strict";\n\n' + M('uint8 small = 255;\nprintf("%d\\n", small);'), ['ch-strict']),
    ('va_count and va_arg', 'built-in functions', 'va_count()  va_arg(i)',
     'Inside a function with `...`: how many extra arguments were passed, and the extra argument number *i*.',
     None, ['fn-variadic']),
    ('void', 'type', 'func f(): void',
     'Means "no value": the result type of a function that returns nothing.',
     'func say_hi(): void {\n    printf("hi\\n");\n}\n\n' + M('say_hi();'), ['type-void']),
    ('volatile', 'modifier', 'volatile type name = value;  |  volatile asm { }',
     'Keeps a variable even if the program never reads it, or marks an `asm` block as having effects.',
     M('volatile int64 marker = 1234;\nprintf("ok\\n");'), ['var-volatile']),
    ('while', 'statement', 'while (condition) { ... }',
     'Repeats a block as long as the condition is true.',
     M('int64 n = 1;\nwhile (n < 1000) {\n    n = n * 2;\n}\nprintf("%d\\n", n);'), ['cf-while']),
    ('%if, %define and other directives', 'directives', '%if CONDITION ... %endif',
     'Lines that start with `%` choose which parts of the file are compiled, depending on the target.',
     '%if ASH_BITS == 64\ndefine MODE 64;\n%else\ndefine MODE 32;\n%endif\n\n' + M('printf("compiled for %d bits\\n", MODE);'), ['ch-pp']),
]
