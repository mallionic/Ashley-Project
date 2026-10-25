@bits(64)
@Entry

// Ejemplo 1: imprimir en consola y salir (kernel32)

extern uint64 GetStdHandle(int32 nStdHandle);
extern int32  WriteFile(uint64 h, uint64 buf, uint32 n, uint64 pWritten, uint64 overlapped);
extern void   ExitProcess(uint32 code);

func print_win(uint64 msg, uint32 len): void {
    uint64 h = GetStdHandle(-11);      // STD_OUTPUT_HANDLE
    uint32 written = 0;
    WriteFile(h, msg, len, &written, 0);
}

pub func main(): void {
    print_win("Hola Windows desde ASH\n", 23);
    print_win("Segunda linea\n", 14);
    ExitProcess(0);
}
