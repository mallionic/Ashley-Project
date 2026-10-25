// emit(...): bytes de maquina crudos y FIJOS dentro del codigo compilado.
// emit(b, b, ...) bytes | emit16/emit32/emit64 palabras little-endian | @emit(...) igual.
// ashc 40_emit.ash --elf   -> exit 42
@Entry
pub func _start(): int64 {
    int64 r = 0;
    emit(0x90);                          // nop
    @emit(0x90, 0x90);                   // nop nop (forma atributo)
    emit(0xB8, 0x2A, 0x00, 0x00, 0x00);  // mov eax, 42
    asm volatile("mov {r}, rax");        // recoge rax en la variable r
    return r;
}
