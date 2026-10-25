// asm y volatile asm en 64 bits, usando variables dentro del bloque
// ashc 02_asm64_volatile.ash --elf   -> devuelve 84
@Entry
pub func _start(): int64 {
    int64 x = 40;
    volatile asm {
        add x, 2
    }
    asm {
        mov rax, x
        shl rax, 1
        mov x, rax
    }
    return x;
}
