// FNV-1a de 64 bits: literales ULL, xor y multiplicacion sin signo.
@Entry
pub func _start(): int64 {
    int64[] data = {104, 101, 108, 108, 111};     // "hello"
    unsigned long long h = 14695981039346656037ULL;
    unsigned long long prime = 1099511628211ULL;

    for (int64 i = 0; i < 5; i++) {
        h ^= data[i];
        h = h * prime;
    }

    printf("fnv1a64(hello) = %llu\n", h);
    return 0;
}
