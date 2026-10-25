// @beautycode(true) (por defecto): cada acceso a un array lleva comprobacion de limites
// (__ash_bounds) y el programa avisa en vez de corromper memoria.
// Compara con 27_beautycode_off.ash (mismo codigo, sin comprobaciones) -> ver beautycode_compare.txt
@beautycode(true)

func llenar(int64 n): int64 {
    int64[8] v;
    int64 total = 0;
    for (int64 i = 0; i < n; i++) {
        v[i] = i * 3;
    }
    for (int64 j = 0; j < n; j++) {
        total = total + v[j];
    }
    return total;
}

@Entry
pub func _start(): int64 {
    printf("n=8 -> %d\n", llenar(8));     // correcto: 84
    printf("n=9 -> %d\n", llenar(9));     // 9 > 8: con beautycode(true) el programa ABORTA aqui (SIGABRT, codigo 134)
    return 0;
}
