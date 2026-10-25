// Punteros a funcion (&f o solo f) para callbacks de C, redefine (alias) y else/elif.
extern int64 atexit(int64 fn);
extern void qsort(int64 base, int64 n, int64 size, int64 cmp);

redefine uint64 printf(char[], ...) uint64 out(char[]...)

func adios(): void {
    out("adios (callback de atexit)\n");
    return;
}

func comparar(int64 a, int64 b): int64 {
    int64 x = *(int64*)a;
    int64 y = *(int64*)b;
    if (x < y) { return -1; }
    elif (x > y) { return 1; }
    else { return 0; }
}

@Entry
pub func _start(): int64 {
    atexit(&adios);
    int64 v[5];
    v[0] = 5; v[1] = 2; v[2] = 9; v[3] = 1; v[4] = 7;
    qsort(&v, 5, 8, comparar);
    out("%d %d %d %d %d\n", v[0], v[1], v[2], v[3], v[4]);
    return 0;
}
