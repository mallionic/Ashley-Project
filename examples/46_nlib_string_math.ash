// nlib sin libc: <string> (cadenas/memoria con uint8*) y <math> (enteros y punto
// fijo Q16.16, SIN FPU). printf es solo para mostrar el resultado.
// ashc 46_nlib_string_math.ash --elf
import <string>;
import <math>;

@Entry
pub func _start(): int64 {
    uint8[64] s;
    uint8[32] n;
    strcpy(&s, "hola");
    strcat(&s, " ASH");
    str_upper(&s);
    printf("%s len=%d strstr=%d atoi=%d\n", &s, strlen(&s), strstr(&s, "ASH"), atoi("-250"));
    printf("gcd=%d isqrt=%d primo97=%d 3^13=%d\n", gcd(84, 36), isqrt(99980001), is_prime(97), ipow(3, 13));
    printf("sqrt(2)=%s ", fx_print_buf(fx_sqrt(fx(2)), &n));
    printf("sin(30)=%s\n", fx_print_buf(fx_sin(fx_deg(30)), &n));
    return 0;
}
