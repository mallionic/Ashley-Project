// ASH llama a una funcion escrita en C (extern)
// ashc 11_ash_calls_c.ash --elf --no-libc ; gcc -m32 -ffreestanding -fno-pic -c 11_c_side.c
@bits(32)
extern int32 c_twice(int32 x);

@Entry
pub func ash_main(): int32 { return c_twice(21); }
