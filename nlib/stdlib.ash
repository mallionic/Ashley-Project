/*
 * Copyright (C) 2026 Mallionic Inc.
 *
 * This file is part of The Ashley Project.
 *
 * The Ashley Project is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3, or (at your option)
 * any later version.
 *
 * The Ashley Project is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * Copyright holder: Mallionic Inc.
 */


define NULL (void*)0

define RAND_MAX 2147483647
define EXIT_FAILURE 1
define EXIT_SUCCESS 0

extern int64 __stlib_atof(string nptr);
extern int64 __stlib_atoi(string nptr);
extern int64 __stlib_atol(string nptr);
extern int64 __stlib_atoll(string nptr);

extern int64 __stlib_strtod(string nptr, void* endptr);
extern int64 __stlib_strtof(string nptr, void* endptr);
extern int64 __stlib_strtold(string nptr, void* endptr);

extern int64 __stlib_strtol(string nptr, void* endptr, int64 base);
extern uint64 __stlib_strtoul(string nptr, void* endptr, int64 base);
extern int64 __stlib_strtoll(string nptr, void* endptr, int64 base);
extern uint64 __stlib_strtoull(string nptr, void* endptr, int64 base);

/*
 * Convert a string to a floating-point value.
 */
pub func atof(string nptr): int64 {
    return __stlib_atof(nptr);
}

/*
 * Convert a string to an integer.
 */
pub func atoi(string nptr): int64 {
    return __stlib_atoi(nptr);
}

/*
 * Convert a string to a long integer.
 */
pub func atol(string nptr): int64 {
    return __stlib_atol(nptr);
}

/*
 * Convert a string to a long long integer.
 */
pub func atoll(string nptr): int64 {
    return __stlib_atoll(nptr);
}

/*
 * Convert a string to a floating-point value and return the first
 * unconverted character through endptr.
 */
pub func strtod(string nptr, void* endptr): int64 {
    return __stlib_strtod(nptr, endptr);
}

pub func strtof(string nptr, void* endptr): int64 {
    return __stlib_strtof(nptr, endptr);
}

pub func strtold(string nptr, void* endptr): int64 {
    return __stlib_strtold(nptr, endptr);
}

/*
 * Convert a string to a signed integer using the specified base.
 */
pub func strtol(string nptr, void* endptr, int64 base): int64 {
    return __stlib_strtol(nptr, endptr, base);
}

/*
 * Convert a string to an unsigned integer using the specified base.
 */
pub func strtoul(string nptr, void* endptr, int64 base): uint64 {
    return __stlib_strtoul(nptr, endptr, base);
}

pub func strtoll(string nptr, void* endptr, int64 base): int64 {
    return __stlib_strtoll(nptr, endptr, base);
}

pub func strtoull(string nptr, void* endptr, int64 base): uint64 {
    return __stlib_strtoull(nptr, endptr, base);
}


/*
 * Pseudo-random number generation.
 */
extern int64 __stlib_rand();
extern void __stlib_srand(uint64 seed);

pub func rand(): int64 {
    return __stlib_rand();
}

pub func srand(uint64 seed): void {
    __stlib_srand(seed);
}


/*
 * BSD-compatible random number generation.
 */
extern int64 __stlib_random();
extern void __stlib_srandom(uint64 seed);
extern int64 __stlib_initstate(uint64 seed, void* state, uint64 size);
extern int64 __stlib_setstate(void* state);

pub func random(): int64 {
    return __stlib_random();
}

pub func srandom(uint64 seed): void {
    __stlib_srandom(seed);
}

pub func initstate(uint64 seed, void* state, uint64 size): int64 {
    return __stlib_initstate(seed, state, size);
}

pub func setstate(void* state): int64 {
    return __stlib_setstate(state);
}


/*
 * Reentrant pseudo-random number generation.
 */
extern int64 __stlib_random_r(void* state, void* result);
extern int64 __stlib_srandom_r(uint64 seed, void* state);
extern int64 __stlib_initstate_r(uint64 seed, void* statebuf, uint64 size, void* result);
extern int64 __stlib_setstate_r(void* statebuf, void* result);

pub func random_r(void* state, void* result): int64 {
    return __stlib_random_r(state, result);
}

pub func srandom_r(uint64 seed, void* state): int64 {
    return __stlib_srandom_r(seed, state);
}

pub func initstate_r(uint64 seed, void* statebuf, uint64 size, void* result): int64 {
    return __stlib_initstate_r(seed, statebuf, size, result);
}

pub func setstate_r(void* statebuf, void* result): int64 {
    return __stlib_setstate_r(statebuf, result);
}


/*
 * BSD 48-bit random number generation.
 */
extern int64 __stlib_drand48();
extern int64 __stlib_erand48(void* xsubi);
extern int64 __stlib_lrand48();
extern uint64 __stlib_nrand48(void* xsubi);
extern int64 __stlib_mrand48();
extern int64 __stlib_jrand48(void* xsubi);

extern void __stlib_srand48(int64 seedval);
extern void* __stlib_seed48(void* seed16v);
extern void __stlib_lcong48(void* param);

pub func drand48(): int64 {
    return __stlib_drand48();
}

pub func erand48(void* xsubi): int64 {
    return __stlib_erand48(xsubi);
}

pub func lrand48(): int64 {
    return __stlib_lrand48();
}

pub func nrand48(void* xsubi): uint64 {
    return __stlib_nrand48(xsubi);
}

pub func mrand48(): int64 {
    return __stlib_mrand48();
}

pub func jrand48(void* xsubi): int64 {
    return __stlib_jrand48(xsubi);
}

pub func srand48(int64 seedval): void {
    __stlib_srand48(seedval);
}

pub func seed48(void* seed16v): void* {
    return __stlib_seed48(seed16v);
}

pub func lcong48(void* param): void {
    __stlib_lcong48(param);
}


/*
 * Reentrant 48-bit random number generation.
 */
extern int64 __stlib_drand48_r(void* buffer, void* result);
extern int64 __stlib_erand48_r(void* xsubi, void* buffer, void* result);
extern int64 __stlib_lrand48_r(void* buffer, void* result);
extern int64 __stlib_nrand48_r(void* xsubi, void* buffer, void* result);
extern int64 __stlib_mrand48_r(void* buffer, void* result);
extern int64 __stlib_jrand48_r(void* xsubi, void* buffer, void* result);
extern int64 __stlib_srand48_r(int64 seedval, void* buffer);
extern int64 __stlib_seed48_r(void* seed16v, void* buffer, void* result);
extern int64 __stlib_lcong48_r(void* param, void* buffer);

pub func drand48_r(void* buffer, void* result): int64 {
    return __stlib_drand48_r(buffer, result);
}

pub func erand48_r(void* xsubi, void* buffer, void* result): int64 {
    return __stlib_erand48_r(xsubi, buffer, result);
}

pub func lrand48_r(void* buffer, void* result): int64 {
    return __stlib_lrand48_r(buffer, result);
}

pub func nrand48_r(void* xsubi, void* buffer, void* result): int64 {
    return __stlib_nrand48_r(xsubi, buffer, result);
}

pub func mrand48_r(void* buffer, void* result): int64 {
    return __stlib_mrand48_r(buffer, result);
}

pub func jrand48_r(void* xsubi, void* buffer, void* result): int64 {
    return __stlib_jrand48_r(xsubi, buffer, result);
}

pub func srand48_r(int64 seedval, void* buffer): int64 {
    return __stlib_srand48_r(seedval, buffer);
}

pub func seed48_r(void* seed16v, void* buffer, void* result): int64 {
    return __stlib_seed48_r(seed16v, buffer, result);
}

pub func lcong48_r(void* param, void* buffer): int64 {
    return __stlib_lcong48_r(param, buffer);
}


/*
 * Memory allocation.
 */
extern void* __stlib_malloc(uint64 size);
extern void* __stlib_calloc(uint64 count, uint64 size);
extern void* __stlib_realloc(void* ptr, uint64 size);
extern void* __stlib_reallocarray(void* ptr, uint64 count, uint64 size);
extern void __stlib_free(void* ptr);

extern int64 __stlib_posix_memalign(void* memptr, uint64 alignment, uint64 size);
extern void* __stlib_aligned_alloc(uint64 alignment, uint64 size);
extern void* __stlib_valloc(uint64 size);

pub func malloc(uint64 size): void* {
    return __stlib_malloc(size);
}

pub func calloc(uint64 count, uint64 size): void* {
    return __stlib_calloc(count, size);
}

pub func realloc(void* ptr, uint64 size): void* {
    return __stlib_realloc(ptr, size);
}

pub func reallocarray(void* ptr, uint64 count, uint64 size): void* {
    return __stlib_reallocarray(ptr, count, size);
}

pub func free(void* ptr): void {
    __stlib_free(ptr);
}

pub func posix_memalign(void* memptr, uint64 alignment, uint64 size): int64 {
    return __stlib_posix_memalign(memptr, alignment, size);
}

pub func aligned_alloc(uint64 alignment, uint64 size): void* {
    return __stlib_aligned_alloc(alignment, size);
}

pub func valloc(uint64 size): void* {
    return __stlib_valloc(size);
}


/*
 * Process termination.
 */
extern void __stlib_abort();
extern void __stlib_exit(int64 status);
extern void __stlib_quick_exit(int64 status);
extern void __stlib__Exit(int64 status);

pub func abort(): void {
    __stlib_abort();
}

pub func exit(int64 status): void {
    __stlib_exit(status);
}

pub func quick_exit(int64 status): void {
    __stlib_quick_exit(status);
}

pub func _Exit(int64 status): void {
    __stlib__Exit(status);
}


/*
 * Functions executed when a process terminates normally.
 */
extern int64 __stlib_atexit(void* function);
extern int64 __stlib_at_quick_exit(void* function);
extern int64 __stlib_on_exit(void* function, void* arg);

pub func atexit(void* function): int64 {
    return __stlib_atexit(function);
}

pub func at_quick_exit(void* function): int64 {
    return __stlib_at_quick_exit(function);
}

pub func on_exit(void* function, void* arg): int64 {
    return __stlib_on_exit(function, arg);
}


/*
 * Environment access.
 */
extern string __stlib_getenv(string name);
extern string __stlib_secure_getenv(string name);

extern int64 __stlib_putenv(string string_value);
extern int64 __stlib_setenv(string name, string value, int64 overwrite);
extern int64 __stlib_unsetenv(string name);
extern int64 __stlib_clearenv();

pub func getenv(string name): string {
    return __stlib_getenv(name);
}

pub func secure_getenv(string name): string {
    return __stlib_secure_getenv(name);
}

pub func putenv(string string_value): int64 {
    return __stlib_putenv(string_value);
}

pub func setenv(string name, string value, int64 overwrite): int64 {
    return __stlib_setenv(name, value, overwrite);
}

pub func unsetenv(string name): int64 {
    return __stlib_unsetenv(name);
}

pub func clearenv(): int64 {
    return __stlib_clearenv();
}


/*
 * Temporary files and directories.
 */
extern string __stlib_mktemp(string template);
extern int64 __stlib_mkstemp(string template);
extern int64 __stlib_mkostemp(string template, int64 flags);
extern int64 __stlib_mkstemps(string template, int64 suffixlen);
extern int64 __stlib_mkostemps(string template, int64 suffixlen, int64 flags);
extern string __stlib_mkdtemp(string template);

pub func mktemp(string template): string {
    return __stlib_mktemp(template);
}

pub func mkstemp(string template): int64 {
    return __stlib_mkstemp(template);
}

pub func mkostemp(string template, int64 flags): int64 {
    return __stlib_mkostemp(template, flags);
}

pub func mkstemps(string template, int64 suffixlen): int64 {
    return __stlib_mkstemps(template, suffixlen);
}

pub func mkostemps(string template, int64 suffixlen, int64 flags): int64 {
    return __stlib_mkostemps(template, suffixlen, flags);
}

pub func mkdtemp(string template): string {
    return __stlib_mkdtemp(template);
}


/*
 * Execute a command through the system command processor.
 */
extern int64 __stlib_system(string command);

pub func system(string command): int64 {
    return __stlib_system(command);
}


/*
 * Resolve a path to its canonical absolute representation.
 */
extern string __stlib_realpath(string path, string resolved_path);
extern string __stlib_canonicalize_file_name(string path);

pub func realpath(string path, string resolved_path): string {
    return __stlib_realpath(path, resolved_path);
}

pub func canonicalize_file_name(string path): string {
    return __stlib_canonicalize_file_name(path);
}


/*
 * Binary search and quick sort.
 */
extern void* __stlib_bsearch(void* key, void* base, uint64 count, uint64 size, void* compare);
extern void __stlib_qsort(void* base, uint64 count, uint64 size, void* compare);

pub func bsearch(void* key, void* base, uint64 count, uint64 size, void* compare): void* {
    return __stlib_bsearch(key, base, count, size, compare);
}

pub func qsort(void* base, uint64 count, uint64 size, void* compare): void {
    __stlib_qsort(base, count, size, compare);
}


/*
 * Integer absolute-value functions.
 */
extern int64 __stlib_abs(int64 value);
extern int64 __stlib_labs(int64 value);
extern int64 __stlib_llabs(int64 value);

pub func abs(int64 value): int64 {
    return __stlib_abs(value);
}

pub func labs(int64 value): int64 {
    return __stlib_labs(value);
}

pub func llabs(int64 value): int64 {
    return __stlib_llabs(value);
}


/*
 * Integer division functions.
 *
 * The result structure contains both quotient and remainder.
 */
extern void __stlib_div(int64 numerator, int64 denominator, void* result);
extern void __stlib_ldiv(int64 numerator, int64 denominator, void* result);
extern void __stlib_lldiv(int64 numerator, int64 denominator, void* result);

pub func div(int64 numerator, int64 denominator, void* result): void {
    __stlib_div(numerator, denominator, result);
}

pub func ldiv(int64 numerator, int64 denominator, void* result): void {
    __stlib_ldiv(numerator, denominator, result);
}

pub func lldiv(int64 numerator, int64 denominator, void* result): void {
    __stlib_lldiv(numerator, denominator, result);
}


/*
 * Multibyte character conversion.
 */
extern int64 __stlib_mblen(string multibyte, uint64 length);
extern int64 __stlib_mbtowc(void* widechar, string multibyte, uint64 length);
extern int64 __stlib_wctomb(string multibyte, int64 widechar);
extern uint64 __stlib_mbstowcs(void* widechars, string multibyte, uint64 count);
extern uint64 __stlib_wcstombs(string multibyte, void* widechars, uint64 count);

pub func mblen(string multibyte, uint64 length): int64 {
    return __stlib_mblen(multibyte, length);
}

pub func mbtowc(void* widechar, string multibyte, uint64 length): int64 {
    return __stlib_mbtowc(widechar, multibyte, length);
}

pub func wctomb(string multibyte, int64 widechar): int64 {
    return __stlib_wctomb(multibyte, widechar);
}

pub func mbstowcs(void* widechars, string multibyte, uint64 count): uint64 {
    return __stlib_mbstowcs(widechars, multibyte, count);
}

pub func wcstombs(string multibyte, void* widechars, uint64 count): uint64 {
    return __stlib_wcstombs(multibyte, widechars, count);
}


/*
 * Parse a comma-separated option string.
 */
extern int64 __stlib_getsubopt(void* optionp, void* tokens, void* value);

pub func getsubopt(void* optionp, void* tokens, void* value): int64 {
    return __stlib_getsubopt(optionp, tokens, value);
}


/*
 * Return the system load average.
 */
extern int64 __stlib_getloadavg(void* loadavg, int64 count);

pub func getloadavg(void* loadavg, int64 count): int64 {
    return __stlib_getloadavg(loadavg, count);
}


/*
 * Test whether a string represents a positive or negative response.
 */
extern int64 __stlib_rpmatch(string response);

pub func rpmatch(string response): int64 {
    return __stlib_rpmatch(response);
}


/*
 * Obtain the controlling pseudo-terminal associated with a terminal.
 */
extern int64 __stlib_posix_openpt(int64 flags);
extern int64 __stlib_grantpt(int64 fd);
extern int64 __stlib_unlockpt(int64 fd);
extern string __stlib_ptsname(int64 fd);
extern int64 __stlib_ptsname_r(int64 fd, string buffer, uint64 buflen);
extern int64 __stlib_getpt();

pub func posix_openpt(int64 flags): int64 {
    return __stlib_posix_openpt(flags);
}

pub func grantpt(int64 fd): int64 {
    return __stlib_grantpt(fd);
}

pub func unlockpt(int64 fd): int64 {
    return __stlib_unlockpt(fd);
}

pub func ptsname(int64 fd): string {
    return __stlib_ptsname(fd);
}

pub func ptsname_r(int64 fd, string buffer, uint64 buflen): int64 {
    return __stlib_ptsname_r(fd, buffer, buflen);
}

pub func getpt(): int64 {
    return __stlib_getpt();
}


/*
 * Return the terminal slot number associated with a file descriptor.
 */
extern int64 __stlib_ttyslot();

pub func ttyslot(): int64 {
    return __stlib_ttyslot();
}


/*
 * Convert floating-point values to character strings.
 */
extern string __stlib_ecvt(int64 value, int64 ndigit, void* decpt, void* sign);
extern string __stlib_fcvt(int64 value, int64 ndigit, void* decpt, void* sign);
extern string __stlib_gcvt(int64 value, int64 ndigit, string buffer);

extern string __stlib_qecvt(int64 value, int64 ndigit, void* decpt, void* sign);
extern string __stlib_qfcvt(int64 value, int64 ndigit, void* decpt, void* sign);
extern string __stlib_qgcvt(int64 value, int64 ndigit, string buffer);

pub func ecvt(int64 value, int64 ndigit, void* decpt, void* sign): string {
    return __stlib_ecvt(value, ndigit, decpt, sign);
}

pub func fcvt(int64 value, int64 ndigit, void* decpt, void* sign): string {
    return __stlib_fcvt(value, ndigit, decpt, sign);
}

pub func gcvt(int64 value, int64 ndigit, string buffer): string {
    return __stlib_gcvt(value, ndigit, buffer);
}

pub func qecvt(int64 value, int64 ndigit, void* decpt, void* sign): string {
    return __stlib_qecvt(value, ndigit, decpt, sign);
}

pub func qfcvt(int64 value, int64 ndigit, void* decpt, void* sign): string {
    return __stlib_qfcvt(value, ndigit, decpt, sign);
}

pub func qgcvt(int64 value, int64 ndigit, string buffer): string {
    return __stlib_qgcvt(value, ndigit, buffer);
}


/*
 * Reentrant floating-point conversion functions.
 */
extern int64 __stlib_ecvt_r(int64 value, int64 ndigit, void* decpt, void* sign, string buffer, uint64 length);
extern int64 __stlib_fcvt_r(int64 value, int64 ndigit, void* decpt, void* sign, string buffer, uint64 length);
extern int64 __stlib_qecvt_r(int64 value, int64 ndigit, void* decpt, void* sign, string buffer, uint64 length);
extern int64 __stlib_qfcvt_r(int64 value, int64 ndigit, void* decpt, void* sign, string buffer, uint64 length);

pub func ecvt_r(int64 value, int64 ndigit, void* decpt, void* sign, string buffer, uint64 length): int64 {
    return __stlib_ecvt_r(value, ndigit, decpt, sign, buffer, length);
}

pub func fcvt_r(int64 value, int64 ndigit, void* decpt, void* sign, string buffer, uint64 length): int64 {
    return __stlib_fcvt_r(value, ndigit, decpt, sign, buffer, length);
}

pub func qecvt_r(int64 value, int64 ndigit, void* decpt, void* sign, string buffer, uint64 length): int64 {
    return __stlib_qecvt_r(value, ndigit, decpt, sign, buffer, length);
}

pub func qfcvt_r(int64 value, int64 ndigit, void* decpt, void* sign, string buffer, uint64 length): int64 {
    return __stlib_qfcvt_r(value, ndigit, decpt, sign, buffer, length);
}


/*
 * Convert a floating-point value directly into a character buffer.
 */
extern int64 __stlib_strfromd(string buffer, uint64 size, string format, int64 value);
extern int64 __stlib_strfromf(string buffer, uint64 size, string format, int64 value);
extern int64 __stlib_strfroml(string buffer, uint64 size, string format, int64 value);

pub func strfromd(string buffer, uint64 size, string format, int64 value): int64 {
    return __stlib_strfromd(buffer, size, format, value);
}

pub func strfromf(string buffer, uint64 size, string format, int64 value): int64 {
    return __stlib_strfromf(buffer, size, format, value);
}

pub func strfroml(string buffer, uint64 size, string format, int64 value): int64 {
    return __stlib_strfroml(buffer, size, format, value);
}

