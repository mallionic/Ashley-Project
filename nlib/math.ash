/*
 * math.ash - matematicas SIN libc y SIN FPU (The Ashley Project, GPLv3+)
 *
 *   import <math>;
 *
 * Solo enteros (int64). Sirve en bare metal (kernel, bootloader) porque no
 * usa ni libm ni instrucciones x87/SSE.
 *
 * enteros:     abs min max clamp sign gcd lcm ipow isqrt ilog2 ilog10
 *              factorial is_prime mod_pow div_floor mod_floor is_pow2
 *              next_pow2 popcount align_up align_down
 * punto fijo:  Q16.16 (1.0 == FX_ONE == 65536)
 *              fx fx_int fx_round fx_frac fx_mul fx_div fx_sqrt
 *              fx_sin fx_cos fx_tan fx_deg fx_abs
 *              fx_print_buf (texto con 4 decimales)
 */

define FX_ONE 65536;
define FX_HALF 32768;
define FX_PI 205887;
define FX_TWO_PI 411775;
define FX_HALF_PI 102944;

// ------------------------------------------------------------------ enteros

pub func abs(int64 x): int64 {
    if (x < 0) { return 0 - x; }
    return x;
}

pub func min(int64 a, int64 b): int64 {
    if (a < b) { return a; }
    return b;
}

pub func max(int64 a, int64 b): int64 {
    if (a > b) { return a; }
    return b;
}

pub func clamp(int64 x, int64 lo, int64 hi): int64 {
    if (x < lo) { return lo; }
    if (x > hi) { return hi; }
    return x;
}

pub func sign(int64 x): int64 {
    if (x > 0) { return 1; }
    if (x < 0) { return -1; }
    return 0;
}

pub func gcd(int64 a, int64 b): int64 {
    int64 x = abs(a);
    int64 y = abs(b);
    while (y != 0) {
        int64 t = x % y;
        x = y;
        y = t;
    }
    return x;
}

pub func lcm(int64 a, int64 b): int64 {
    if (a == 0 || b == 0) { return 0; }
    return abs(a / gcd(a, b) * b);
}

/* base^exp (exp >= 0), por cuadrados */
pub func ipow(int64 base, int64 exp): int64 {
    int64 r = 1;
    int64 b = base;
    int64 e = exp;
    while (e > 0) {
        if ((e & 1) == 1) { r = r * b; }
        b = b * b;
        e = e >> 1;
    }
    return r;
}

/* raiz cuadrada entera (parte entera de sqrt(n)), n >= 0, metodo de Newton */
pub func isqrt(int64 n): int64 {
    if (n < 2) { return n; }
    int64 x = n;
    int64 y = (x + 1) / 2;
    while (y < x) {
        x = y;
        y = (x + n / x) / 2;
    }
    return x;
}

/* posicion del bit mas alto (floor(log2 n)), -1 si n <= 0 */
pub func ilog2(int64 n): int64 {
    if (n <= 0) { return -1; }
    int64 r = 0;
    int64 v = n;
    while (v > 1) {
        v = v >> 1;
        r++;
    }
    return r;
}

pub func ilog10(int64 n): int64 {
    if (n <= 0) { return -1; }
    int64 r = 0;
    int64 v = n;
    while (v >= 10) {
        v = v / 10;
        r++;
    }
    return r;
}

pub func factorial(int64 n): int64 {
    int64 r = 1;
    for (int64 i = 2; i <= n; i++) {
        r = r * i;
    }
    return r;
}

pub func is_prime(int64 n): int64 {
    if (n < 2) { return 0; }
    if (n < 4) { return 1; }
    if (n % 2 == 0) { return 0; }
    int64 i = 3;
    while (i * i <= n) {
        if (n % i == 0) { return 0; }
        i = i + 2;
    }
    return 1;
}

/* (base^exp) % m sin desbordar mientras m < 2^31 */
pub func mod_pow(int64 base, int64 exp, int64 m): int64 {
    int64 r = 1 % m;
    int64 b = base % m;
    int64 e = exp;
    while (e > 0) {
        if ((e & 1) == 1) { r = r * b % m; }
        b = b * b % m;
        e = e >> 1;
    }
    return r;
}

/* division y modulo redondeando hacia -infinito (C trunca hacia 0) */
pub func div_floor(int64 a, int64 b): int64 {
    int64 q = a / b;
    if (a % b != 0) {
        if (a < 0 && b > 0) { q = q - 1; }
        if (a > 0 && b < 0) { q = q - 1; }
    }
    return q;
}

pub func mod_floor(int64 a, int64 b): int64 {
    int64 r = a % b;
    if (r < 0 && b > 0) { r = r + b; }
    if (r > 0 && b < 0) { r = r + b; }
    return r;
}

pub func is_pow2(int64 n): int64 {
    if (n > 0 && (n & (n - 1)) == 0) { return 1; }
    return 0;
}

pub func next_pow2(int64 n): int64 {
    int64 p = 1;
    while (p < n) {
        p = p * 2;
    }
    return p;
}

pub func popcount(int64 n): int64 {
    int64 c = 0;
    int64 v = n;
    for (int64 i = 0; i < 64; i++) {
        c = c + (v & 1);
        v = v >> 1;
    }
    return c;
}

pub func align_up(int64 x, int64 a): int64 {
    return (x + a - 1) / a * a;
}

pub func align_down(int64 x, int64 a): int64 {
    return x / a * a;
}

// ------------------------------------------------------------------ punto fijo Q16.16

pub func fx(int64 n): int64 {
    return n * 65536;
}

/* parte entera (trunca hacia 0) */
pub func fx_int(int64 a): int64 {
    return a / 65536;
}

pub func fx_round(int64 a): int64 {
    if (a < 0) { return (a - 32768) / 65536; }
    return (a + 32768) / 65536;
}

pub func fx_frac(int64 a): int64 {
    return a % 65536;
}

pub func fx_abs(int64 a): int64 {
    return abs(a);
}

pub func fx_mul(int64 a, int64 b): int64 {
    return a * b / 65536;
}

pub func fx_div(int64 a, int64 b): int64 {
    return a * 65536 / b;
}

/* sqrt en Q16.16: sqrt(a / 2^16) * 2^16 == isqrt(a * 2^16) */
pub func fx_sqrt(int64 a): int64 {
    if (a <= 0) { return 0; }
    return isqrt(a * 65536);
}

/* grados enteros -> radianes Q16.16 */
pub func fx_deg(int64 grados): int64 {
    return grados * 205887 / 180;
}

/*
 * seno en Q16.16 (entrada en radianes Q16.16). Reduce a [-pi/2, pi/2] y usa
 * Taylor hasta x^9: error < 0.0001 en todo el rango.
 */
pub func fx_sin(int64 a): int64 {
    int64 x = mod_floor(a + 205887, 411775) - 205887;    // [-pi, pi)
    if (x > 102944) { x = 205887 - x; }                  // simetria sin(pi - x)
    if (x < -102944) { x = -205887 - x; }
    int64 x2 = x * x / 65536;
    int64 t = x;
    int64 s = x;
    t = 0 - t * x2 / 65536 / 6;    s = s + t;            // - x^3/3!
    t = 0 - t * x2 / 65536 / 20;   s = s + t;            // + x^5/5!
    t = 0 - t * x2 / 65536 / 42;   s = s + t;            // - x^7/7!
    t = 0 - t * x2 / 65536 / 72;   s = s + t;            // + x^9/9!
    return s;
}

pub func fx_cos(int64 a): int64 {
    return fx_sin(a + 102944);
}

pub func fx_tan(int64 a): int64 {
    int64 c = fx_cos(a);
    if (c == 0) { return 9223372036854775807; }
    return fx_div(fx_sin(a), c);
}

/* Q16.16 -> texto "[-]ent.dddd" en buf (min 32 bytes). Devuelve buf. */
pub func fx_print_buf(int64 a, uint8* buf): uint8* {
    int64 v = a;
    int64 n = 0;
    if (v < 0) {
        buf[0] = 45;
        n = 1;
        v = 0 - v;
    }
    int64 ent = v / 65536;
    int64 dec = (v % 65536) * 10000 / 65536;
    uint8[24] tmp;
    int64 k = 0;
    if (ent == 0) {
        tmp[0] = 48;
        k = 1;
    }
    while (ent > 0) {
        tmp[k] = 48 + ent % 10;
        ent = ent / 10;
        k++;
    }
    while (k > 0) {
        k--;
        buf[n] = tmp[k];
        n++;
    }
    buf[n] = 46;
    buf[n + 1] = 48 + dec / 1000;
    buf[n + 2] = 48 + dec / 100 % 10;
    buf[n + 3] = 48 + dec / 10 % 10;
    buf[n + 4] = 48 + dec % 10;
    buf[n + 5] = 0;
    return buf;
}
