// Variables globales: general (visible para C y otros archivos) / ungeneral (solo este script).
// static = se puede cambiar; sin static es constante.
general static uint64 total = 0
ungeneral static uint16 pasos = 0
general uint32 LIMITE = 1000          // constante
ungeneral static int64 saldo = -50

func sumar(uint64 n): void {
    total = total + n;
    pasos = pasos + 1;
    return;
}

@Entry
pub func _start(): int64 {
    for (int64 i = 1; i <= 5; i++) { sumar(i * 10); }
    saldo = saldo * 4;
    uint64* p = &total;              // &global = direccion del dato
    *p = *p + 1;
    printf("total=%llu pasos=%u LIMITE=%u saldo=%lld\n", total, pasos, LIMITE, saldo);
    // LIMITE = 5;                   // error: la global 'LIMITE' no es static (es constante)
    return 0;
}
