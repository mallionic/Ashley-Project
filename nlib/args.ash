/*
 * args.ash - argumentos de ejecucion (The Ashley Project, GPLv3+)
 *
 * Uso (Linux ELF / System V):
 *
 *   import <args>;
 *   @Entry
 *   pub func _start(int64 argc, int64 argv): int64 {
 *       for (int64 i = 0; i < argc; i++) {
 *           printf("%s\n", arg_at(argv, i));
 *       }
 *       return 0;
 *   }
 *
 *   arg_at(argv, i)      puntero al texto del argumento i (usalo con %s)
 *   arg_equals(argv, i, s)   1 si el argumento i es igual a s
 *   arg_int(argv, i)     argumento i convertido a numero (atoi)
 *   arg_has(argc, argv, s)   1 si algun argumento es igual a s (p.ej. "-v")
 */

extern int64 atoi(string s);
extern int64 strcmp(int64 a, string b);

pub func arg_at(int64 argv, int64 i): int64 {
    int64 r = 0;
    asm {
        mov rax, argv
        mov rcx, i
        mov rax, [rax + rcx*8]
        mov r, rax
    }
    return r;
}

pub func arg_equals(int64 argv, int64 i, string s): int64 {
    if (strcmp(arg_at(argv, i), s) == 0) {
        return 1;
    }
    return 0;
}

pub func arg_int(int64 argv, int64 i): int64 {
    return atoi(arg_at(argv, i));
}

pub func arg_has(int64 argc, int64 argv, string s): int64 {
    for (int64 i = 0; i < argc; i++) {
        if (arg_equals(argv, i, s) == 1) {
            return 1;
        }
    }
    return 0;
}
