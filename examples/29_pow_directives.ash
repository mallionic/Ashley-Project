// Potencia **, @nolibc y directivas con aritmetica (solo cambian valores en compilacion).
@nolibc(false)

%define BASE 2
%define BITS (BASE ** 5) - 1              // 31
%define MASK (1 << 4) | 3                 // 19
%define TOTAL BITS + MASK * 2             // 69
%define TOTAL 100                         // los %define se pueden redefinir cuando quieras

%if ASH_TARGET == windows
%error "este ejemplo es para elf/raw"
%elif TOTAL == 100 && (MASK & 16) != 0
%define MODO 1
%else
%define MODO 2
%endif

@Entry
pub func _start(): int64 {
    int64 b = 3;
    int64 e = 4;
    printf("2**10=%d  3**4=%d  2**3**2=%d  5*2**2=%d\n", 2 ** 10, b ** e, 2 ** 3 ** 2, 5 * 2 ** 2);
    return 0;
}
