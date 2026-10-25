// Flags de permisos con operadores bit a bit: | & ^ ~ y compuestos.
@Entry
pub func _start(): int64 {
    uint32 READ  = 1;
    uint32 WRITE = 2;
    uint32 EXEC  = 4;

    uint32 perms = READ | WRITE;          // 3
    uint32 canWrite = perms & WRITE;      // 2
    perms |= EXEC;                        // 7
    perms &= ~WRITE;                      // 5  (quita WRITE)
    perms ^= READ;                        // 4  (conmuta READ)

    printf("canWrite=%u perms=%u\n", canWrite, perms);
    printf("1 | 2 ^ 3 & 7 = %d\n", 1 | 2 ^ 3 & 7);   // precedencia de C -> 1
    return 0;
}
