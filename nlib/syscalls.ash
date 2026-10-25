/*
 * syscalls.ash - llamadas al sistema de Linux x86-64 SIN libc (The Ashley Project, GPLv3+)
 *
 *   import <syscalls>;
 *   ashc prog.ash --elf --no-libc ; ld -e _start out/prog.o -o prog
 *
 * Cada funcion es un 'syscall' directo (rax = numero, rdi rsi rdx r10 r8 r9 =
 * argumentos). Devuelven lo que devuelve el kernel (negativo = -errno).
 *
 *   sys_read sys_write sys_open sys_close sys_lseek sys_mmap sys_munmap
 *   sys_brk sys_getpid sys_kill sys_exit sys_nanosleep sys_unlink sys_mkdir
 *   syscall6 (cualquier otra)
 *   print(texto) print_int(n) println(texto)   escriben en stdout (fd 1)
 */

define SYS_READ 0;
define SYS_WRITE 1;
define SYS_OPEN 2;
define SYS_CLOSE 3;
define SYS_LSEEK 8;
define SYS_MMAP 9;
define SYS_MUNMAP 11;
define SYS_BRK 12;
define SYS_NANOSLEEP 35;
define SYS_GETPID 39;
define SYS_EXIT 60;
define SYS_KILL 62;
define SYS_MKDIR 83;
define SYS_UNLINK 87;

define O_RDONLY 0;
define O_WRONLY 1;
define O_RDWR 2;
define O_CREAT 64;
define O_TRUNC 512;

define STDIN 0;
define STDOUT 1;
define STDERR 2;

/* syscall generica de hasta 6 argumentos */
pub func syscall6(int64 n, int64 a1, int64 a2, int64 a3, int64 a4, int64 a5, int64 a6): int64 {
    int64 r = 0;
    asm volatile(
        "mov rax, {n}\n"
        "mov rdi, {a1}\n"
        "mov rsi, {a2}\n"
        "mov rdx, {a3}\n"
        "mov r10, {a4}\n"
        "mov r8, {a5}\n"
        "mov r9, {a6}\n"
        "syscall\n"
        "mov {r}, rax"
    );
    return r;
}

pub func sys_read(int64 fd, uint8* buf, int64 n): int64 {
    return syscall6(0, fd, buf, n, 0, 0, 0);
}

pub func sys_write(int64 fd, uint8* buf, int64 n): int64 {
    return syscall6(1, fd, buf, n, 0, 0, 0);
}

pub func sys_open(uint8* path, int64 flags, int64 mode): int64 {
    return syscall6(2, path, flags, mode, 0, 0, 0);
}

pub func sys_close(int64 fd): int64 {
    return syscall6(3, fd, 0, 0, 0, 0, 0);
}

pub func sys_lseek(int64 fd, int64 off, int64 whence): int64 {
    return syscall6(8, fd, off, whence, 0, 0, 0);
}

/* memoria anonima lectura/escritura: sys_mmap(0, n) */
pub func sys_mmap(int64 addr, int64 n): int64 {
    return syscall6(9, addr, n, 3, 34, -1, 0);
}

pub func sys_munmap(int64 addr, int64 n): int64 {
    return syscall6(11, addr, n, 0, 0, 0, 0);
}

pub func sys_brk(int64 addr): int64 {
    return syscall6(12, addr, 0, 0, 0, 0, 0);
}

pub func sys_getpid(): int64 {
    return syscall6(39, 0, 0, 0, 0, 0, 0);
}

pub func sys_kill(int64 pid, int64 sig): int64 {
    return syscall6(62, pid, sig, 0, 0, 0, 0);
}

pub func sys_mkdir(uint8* path, int64 mode): int64 {
    return syscall6(83, path, mode, 0, 0, 0, 0);
}

pub func sys_unlink(uint8* path): int64 {
    return syscall6(87, path, 0, 0, 0, 0, 0);
}

/* duerme ms milisegundos */
pub func sys_nanosleep(int64 ms): int64 {
    int64[2] ts;
    ts[0] = ms / 1000;
    ts[1] = ms % 1000 * 1000000;
    return syscall6(35, ts, 0, 0, 0, 0, 0);
}

pub func sys_exit(int64 code): void {
    syscall6(60, code, 0, 0, 0, 0, 0);
}

// ------------------------------------------------------------------ salida sencilla

func __sys_len(uint8* s): int64 {
    int64 n = 0;
    while (s[n] != 0) {
        n++;
    }
    return n;
}

pub func print(uint8* s): int64 {
    return sys_write(1, s, __sys_len(s));
}

pub func println(uint8* s): int64 {
    int64 r = print(s);
    sys_write(1, "\n", 1);
    return r;
}

pub func print_int(int64 n): int64 {
    uint8[24] buf;
    int64 v = n;
    int64 k = 23;
    int64 neg = 0;
    buf[23] = 0;
    if (v == 0) {
        k--;
        buf[k] = 48;
    }
    if (v < 0) {
        neg = 1;
        v = 0 - v;
    }
    while (v > 0) {
        k--;
        buf[k] = 48 + v % 10;
        v = v / 10;
    }
    if (neg == 1) {
        k--;
        buf[k] = 45;
    }
    return sys_write(1, &buf + k, 23 - k);
}
