// libreria ASH de 32 bits que C puede llamar (objeto ELF32, sin libc)
// ashc 09_lib32.ash --elf --no-libc       ->  out/09_lib32.o
@bits(32)
pub func add3(int32 a, int32 b, int32 c): int32 {
    int32[] t = {a, b, c};
    return t[0] + t[1] + t[2];
}
priv func hidden(): int32 { return 1; }

@Entry
pub func ash_main(): int32 { return add3(1, 2, 3) + hidden(); }
