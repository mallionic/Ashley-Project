// @naked: funcion SIN prologo, epilogo ni ret. El compilador no genera nada propio:
// solo los bytes de tus asm { } / asm("...") y emit(...). Tu pones el "ret" (o "iretq"
// en un manejador de interrupcion). No hay variables: los argumentos llegan en
// rdi, rsi, rdx, rcx, r8, r9 (64 bits) o en la pila [esp+4], [esp+8]... (32 bits).
// Util para: manejadores de interrupcion, entrada de un kernel/bootloader, codigo exacto.
// ashc 50_naked.ash --elf   -> suma3=6 42=42 doble=42
// los argumentos llegan en rdi, rsi, rdx, rcx, r8, r9 y el resultado va en rax
@naked
func suma3(int64 a, int64 b, int64 c): int64 {
    asm {
        lea rax, [rdi + rsi]
        add rax, rdx
        ret
    }
}

@naked
func cuarenta_y_dos(): int64 {
    emit(0xB8, 0x2A, 0x00, 0x00, 0x00);    // mov eax, 42
    emit(0xC3);                            // ret
}

@naked
func doble(int64 x): int64 {
    asm volatile("lea rax, [rdi + rdi]; ret");
}

@Entry
pub func _start(): int64 {
    printf("suma3=%d 42=%d doble=%d\n", suma3(1, 2, 3), cuarenta_y_dos(), doble(21));
    return 0;
}
