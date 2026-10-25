// @beautycode(false): sin comprobaciones de limites -> codigo mas corto y rapido,
// pero un indice fuera de rango ya NO se detecta (aqui solo usamos indices validos).
@beautycode(false)

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
    printf("n=8 -> %d\n", llenar(8));     // 84
    return 0;
}
