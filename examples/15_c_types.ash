@Entry
pub func _start(): int64 {
    unsigned long long big = 18446744073709551615ULL;
    unsigned int a = 4000000000U;
    unsigned short s = 65535;
    long long l = 5000000000LL;
    unsigned char c = 200;
    short sh = 1000;
    unsigned long ul = 0xFFUL;
    int64 r = 'A';
    printf("%llu %u %u %lld %u %d %lu %d\n", big, a, s, l, c, sh, ul, r);
    return 0;
}
