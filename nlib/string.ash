/*
 * string.ash - cadenas y memoria SIN libc (The Ashley Project, GPLv3+)
 *
 *   import <string>;
 *
 * Todo trabaja con punteros crudos uint8* (sin cabecera ni comprobacion de
 * limites), asi sirve igual para literales "..." , buffers uint8[N] (&buf),
 * memoria de C y bare metal (kernel, bootloader).
 *
 * memoria:   memset memcpy memmove memcmp memchr
 * longitud:  strlen strnlen
 * copia:     strcpy strncpy strcat strncat
 * compara:   strcmp strncmp strcasecmp
 * busca:     strchr strrchr strstr strspn strcspn
 * chars:     isdigit isalpha isalnum isspace isupper islower isxdigit
 *            toupper tolower
 * convierte: str_to_int (base 2..36, signo, 0x / 0b)  int_to_str  uint_to_str
 * util:      str_reverse str_upper str_lower str_equals str_starts str_ends
 */

// ------------------------------------------------------------------ memoria

pub func memset(uint8* dst, int64 value, uint64 n): uint8* {
    uint64 i = 0;
    while (i < n) {
        dst[i] = value;
        i++;
    }
    return dst;
}

pub func memcpy(uint8* dst, uint8* src, uint64 n): uint8* {
    uint64 i = 0;
    while (i < n) {
        dst[i] = src[i];
        i++;
    }
    return dst;
}

/* como memcpy pero admite zonas solapadas */
pub func memmove(uint8* dst, uint8* src, uint64 n): uint8* {
    if (dst < src) {
        uint64 i = 0;
        while (i < n) {
            dst[i] = src[i];
            i++;
        }
        return dst;
    }
    uint64 j = n;
    while (j > 0) {
        j--;
        dst[j] = src[j];
    }
    return dst;
}

pub func memcmp(uint8* a, uint8* b, uint64 n): int64 {
    uint64 i = 0;
    while (i < n) {
        int64 x = a[i];
        int64 y = b[i];
        if (x != y) {
            return x - y;
        }
        i++;
    }
    return 0;
}

/* indice del primer byte igual a c, o -1 */
pub func memchr(uint8* p, int64 c, uint64 n): int64 {
    uint64 i = 0;
    int64 want = c & 255;
    while (i < n) {
        if (p[i] == want) {
            return i;
        }
        i++;
    }
    return -1;
}

// ------------------------------------------------------------------ longitud

pub func strlen(uint8* s): uint64 {
    uint64 n = 0;
    while (s[n] != 0) {
        n++;
    }
    return n;
}

pub func strnlen(uint8* s, uint64 max): uint64 {
    uint64 n = 0;
    while (n < max) {
        if (s[n] == 0) {
            return n;
        }
        n++;
    }
    return n;
}

// ------------------------------------------------------------------ copia

pub func strcpy(uint8* dst, uint8* src): uint8* {
    uint64 i = 0;
    while (src[i] != 0) {
        dst[i] = src[i];
        i++;
    }
    dst[i] = 0;
    return dst;
}

/* copia como mucho n bytes y rellena con 0 hasta n (como C) */
pub func strncpy(uint8* dst, uint8* src, uint64 n): uint8* {
    uint64 i = 0;
    while (i < n) {
        if (src[i] == 0) {
            break;
        }
        dst[i] = src[i];
        i++;
    }
    while (i < n) {
        dst[i] = 0;
        i++;
    }
    return dst;
}

pub func strcat(uint8* dst, uint8* src): uint8* {
    uint64 d = strlen(dst);
    uint64 i = 0;
    while (src[i] != 0) {
        dst[d + i] = src[i];
        i++;
    }
    dst[d + i] = 0;
    return dst;
}

pub func strncat(uint8* dst, uint8* src, uint64 n): uint8* {
    uint64 d = strlen(dst);
    uint64 i = 0;
    while (i < n) {
        if (src[i] == 0) {
            break;
        }
        dst[d + i] = src[i];
        i++;
    }
    dst[d + i] = 0;
    return dst;
}

// ------------------------------------------------------------------ compara

pub func strcmp(uint8* a, uint8* b): int64 {
    uint64 i = 0;
    while (true) {
        int64 x = a[i];
        int64 y = b[i];
        if (x != y) {
            return x - y;
        }
        if (x == 0) {
            return 0;
        }
        i++;
    }
    return 0;
}

pub func strncmp(uint8* a, uint8* b, uint64 n): int64 {
    uint64 i = 0;
    while (i < n) {
        int64 x = a[i];
        int64 y = b[i];
        if (x != y) {
            return x - y;
        }
        if (x == 0) {
            return 0;
        }
        i++;
    }
    return 0;
}

pub func strcasecmp(uint8* a, uint8* b): int64 {
    uint64 i = 0;
    while (true) {
        int64 x = tolower(a[i]);
        int64 y = tolower(b[i]);
        if (x != y) {
            return x - y;
        }
        if (x == 0) {
            return 0;
        }
        i++;
    }
    return 0;
}

// ------------------------------------------------------------------ busca

/* indice del primer c en s, o -1 (c == 0 encuentra el NUL final) */
pub func strchr(uint8* s, int64 c): int64 {
    uint64 i = 0;
    int64 want = c & 255;
    while (true) {
        int64 x = s[i];
        if (x == want) {
            return i;
        }
        if (x == 0) {
            return -1;
        }
        i++;
    }
    return -1;
}

/* indice del ultimo c en s, o -1 */
pub func strrchr(uint8* s, int64 c): int64 {
    uint64 i = 0;
    int64 last = -1;
    int64 want = c & 255;
    while (true) {
        int64 x = s[i];
        if (x == want) {
            last = i;
        }
        if (x == 0) {
            return last;
        }
        i++;
    }
    return last;
}

/* indice de la primera aparicion de aguja en pajar, o -1 */
pub func strstr(uint8* pajar, uint8* aguja): int64 {
    uint64 n = strlen(aguja);
    if (n == 0) {
        return 0;
    }
    uint64 i = 0;
    while (pajar[i] != 0) {
        if (strncmp(pajar + i, aguja, n) == 0) {
            return i;
        }
        i++;
    }
    return -1;
}

/* longitud del prefijo de s formado solo por bytes de acepta */
pub func strspn(uint8* s, uint8* acepta): uint64 {
    uint64 i = 0;
    while (s[i] != 0) {
        if (strchr(acepta, s[i]) < 0) {
            return i;
        }
        i++;
    }
    return i;
}

/* longitud del prefijo de s sin ningun byte de rechaza */
pub func strcspn(uint8* s, uint8* rechaza): uint64 {
    uint64 i = 0;
    while (s[i] != 0) {
        if (strchr(rechaza, s[i]) >= 0) {
            return i;
        }
        i++;
    }
    return i;
}

// ------------------------------------------------------------------ caracteres

pub func isdigit(int64 c): int64 {
    if (c >= 48 && c <= 57) { return 1; }
    return 0;
}

pub func isupper(int64 c): int64 {
    if (c >= 65 && c <= 90) { return 1; }
    return 0;
}

pub func islower(int64 c): int64 {
    if (c >= 97 && c <= 122) { return 1; }
    return 0;
}

pub func isalpha(int64 c): int64 {
    if (isupper(c) == 1 || islower(c) == 1) { return 1; }
    return 0;
}

pub func isalnum(int64 c): int64 {
    if (isalpha(c) == 1 || isdigit(c) == 1) { return 1; }
    return 0;
}

pub func isspace(int64 c): int64 {
    if (c == 32 || (c >= 9 && c <= 13)) { return 1; }
    return 0;
}

pub func isxdigit(int64 c): int64 {
    if (isdigit(c) == 1) { return 1; }
    if (c >= 97 && c <= 102) { return 1; }
    if (c >= 65 && c <= 70) { return 1; }
    return 0;
}

pub func toupper(int64 c): int64 {
    if (islower(c) == 1) { return c - 32; }
    return c;
}

pub func tolower(int64 c): int64 {
    if (isupper(c) == 1) { return c + 32; }
    return c;
}

// ------------------------------------------------------------------ conversion

/* valor de un digito en base 36, o 99 si no es digito */
func __str_digit(int64 c): int64 {
    if (c >= 48 && c <= 57) { return c - 48; }
    if (c >= 97 && c <= 122) { return c - 87; }
    if (c >= 65 && c <= 90) { return c - 55; }
    return 99;
}

/*
 * texto -> entero. base 0 = automatica (0x.. hex, 0b.. binario, si no decimal).
 * Acepta espacios delante y signo + / -.
 */
pub func str_to_int(uint8* s, int64 base): int64 {
    uint64 i = 0;
    while (isspace(s[i]) == 1) {
        i++;
    }
    int64 neg = 0;
    if (s[i] == 45) {
        neg = 1;
        i++;
    } elif (s[i] == 43) {
        i++;
    }
    int64 b = base;
    if (s[i] == 48) {
        int64 x = tolower(s[i + 1]);
        if (x == 120) {
            if (b == 0 || b == 16) {
                b = 16;
                i = i + 2;
            }
        } elif (x == 98) {
            if (b == 0 || b == 2) {
                b = 2;
                i = i + 2;
            }
        }
    }
    if (b == 0) {
        b = 10;
    }
    int64 v = 0;
    while (true) {
        int64 d = __str_digit(s[i]);
        if (d >= b) {
            break;
        }
        v = v * b + d;
        i++;
    }
    if (neg == 1) {
        return 0 - v;
    }
    return v;
}

pub func atoi(uint8* s): int64 {
    return str_to_int(s, 10);
}

/* entero sin signo -> texto en base 2..36 dentro de buf (min 65 bytes). Devuelve buf. */
pub func uint_to_str(uint64 value, uint8* buf, int64 base): uint8* {
    uint8* digits = "0123456789abcdefghijklmnopqrstuvwxyz";
    uint64 v = value;
    uint64 b = base;
    uint64 n = 0;
    if (v == 0) {
        buf[0] = 48;
        buf[1] = 0;
        return buf;
    }
    while (v != 0) {
        buf[n] = digits[v % b];
        v = v / b;
        n++;
    }
    buf[n] = 0;
    str_reverse(buf);
    return buf;
}

/* entero con signo -> texto (base 10 con '-'; otras bases como unsigned) */
pub func int_to_str(int64 value, uint8* buf, int64 base): uint8* {
    if (value < 0 && base == 10) {
        buf[0] = 45;
        uint_to_str(0 - value, buf + 1, 10);
        return buf;
    }
    return uint_to_str(value, buf, base);
}

// ------------------------------------------------------------------ utilidades

pub func str_reverse(uint8* s): uint8* {
    uint64 n = strlen(s);
    if (n < 2) {
        return s;
    }
    uint64 i = 0;
    uint64 j = n - 1;
    while (i < j) {
        int64 t = s[i];
        s[i] = s[j];
        s[j] = t;
        i++;
        j--;
    }
    return s;
}

pub func str_upper(uint8* s): uint8* {
    uint64 i = 0;
    while (s[i] != 0) {
        s[i] = toupper(s[i]);
        i++;
    }
    return s;
}

pub func str_lower(uint8* s): uint8* {
    uint64 i = 0;
    while (s[i] != 0) {
        s[i] = tolower(s[i]);
        i++;
    }
    return s;
}

pub func str_equals(uint8* a, uint8* b): int64 {
    if (strcmp(a, b) == 0) { return 1; }
    return 0;
}

pub func str_starts(uint8* s, uint8* prefijo): int64 {
    if (strncmp(s, prefijo, strlen(prefijo)) == 0) { return 1; }
    return 0;
}

pub func str_ends(uint8* s, uint8* sufijo): int64 {
    uint64 n = strlen(s);
    uint64 m = strlen(sufijo);
    if (m > n) { return 0; }
    if (strcmp(s + (n - m), sufijo) == 0) { return 1; }
    return 0;
}
