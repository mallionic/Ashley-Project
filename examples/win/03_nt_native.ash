@bits(64)
@Entry

// Ejemplo 3: Native API (ntdll): escribir, esperar 1 s y terminar proceso

extern uint64 GetStdHandle(int32 nStdHandle);
extern int32  NtWriteFile(uint64 h, uint64 event, uint64 apcRoutine, uint64 apcCtx,
                          uint64 pIoStatusBlock, uint64 buf, uint32 len,
                          uint64 pByteOffset, uint64 pKey);
extern int32  NtDelayExecution(int32 alertable, uint64 pInterval);
extern int32  NtTerminateProcess(uint64 hProcess, int32 status);

func print_nt(uint64 msg, uint32 len): void {
    uint64 h = GetStdHandle(-11);
    uint64 iosb[2];                    // IO_STATUS_BLOCK (16 bytes)
    iosb[0] = 0;
    iosb[1] = 0;
    uint64 piosb = iosb;
    NtWriteFile(h, 0, 0, 0, piosb, msg, len, 0, 0);
}

pub func main(): void {
    print_nt("NT: hola\n", 9);

    // Intervalo en unidades de 100 ns; negativo = relativo. -10.000.000 = 1 s
    int64 interval = -10000000;
    NtDelayExecution(0, &interval);

    print_nt("NT: despues de 1 s\n", 19);

    int64 cur = -1;                    // pseudo-handle del proceso actual
    NtTerminateProcess(cur, 0);
}
