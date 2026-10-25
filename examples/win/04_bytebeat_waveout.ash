@bits(64)
@Entry

// Ejemplo 4: Bytebeat 8 en Windows con winmm waveOut. Salir con ESC.
// Doble buffer de 4096 muestras S16 mono a 44100 Hz.

extern uint32 waveOutOpen(uint64 phwo, uint32 deviceId, uint64 pWfx, uint64 cb, uint64 inst, uint32 flags);
extern uint32 waveOutPrepareHeader(uint64 hwo, uint64 pHdr, uint32 size);
extern uint32 waveOutWrite(uint64 hwo, uint64 pHdr, uint32 size);
extern uint32 waveOutUnprepareHeader(uint64 hwo, uint64 pHdr, uint32 size);
extern uint32 waveOutReset(uint64 hwo);
extern uint32 waveOutClose(uint64 hwo);
extern int16  GetAsyncKeyState(int32 vk);
extern uint64 GetStdHandle(int32 nStdHandle);
extern int32  WriteFile(uint64 h, uint64 buf, uint32 n, uint64 pWritten, uint64 overlapped);
extern void   Sleep(uint32 ms);
extern void   ExitProcess(uint32 code);

func print_win(uint64 msg, uint32 len): void {
    uint64 h = GetStdHandle(-11);
    uint32 written = 0;
    WriteFile(h, msg, len, &written, 0);
}

// Solo desplazamientos con constante (ver bytebeat8_linux.ash)
func song8(int32 t): int32 {
    int32 factor = 16;

    if ((t & 4096) != 0) {
        factor = 6;
    }

    int32 a = t * (factor + (1 & (t >> 14)));
    int32 s = (t >> 8) & 3;
    int32 b = a;

    if (s == 1) {
        b = a >> 1;
    } else if (s == 2) {
        b = a >> 2;
    } else if (s == 3) {
        b = a >> 3;
    }

    int32 c = t >> 4;

    if ((t & 4096) != 0) {
        c = t >> 3;
    }

    return b | c;
}

pub func main(): void {

    // WAVEFORMATEX (18 bytes) como 9 palabras de 16 bits
    uint16 wfx[9];
    wfx[0] = 1;        // WAVE_FORMAT_PCM
    wfx[1] = 1;        // canales
    wfx[2] = 44100;    // muestras/s (parte baja)
    wfx[3] = 0;        //            (parte alta)
    wfx[4] = 22664;    // bytes/s = 88200 = 0x15888 (parte baja)
    wfx[5] = 1;        //                           (parte alta)
    wfx[6] = 2;        // block align
    wfx[7] = 16;       // bits por muestra
    wfx[8] = 0;        // cbSize

    int16 buf[8192];                   // 2 buffers x 4096 muestras
    uint64 hdr[12];                    // 2 WAVEHDR x 48 bytes (x64)
    uint64 pbuf = buf;
    uint64 phdr = hdr;
    uint64 pwfx = wfx;

    uint64 hwo = 0;

    // WAVE_MAPPER = 0xFFFFFFFF, CALLBACK_NULL = 0
    if (waveOutOpen(&hwo, 4294967295, pwfx, 0, 0, 0) != 0) {
        print_win("Error en waveOutOpen\n", 21);
        ExitProcess(1);
    }

    int64 k = 0;
    while (k < 2) {
        hdr[k * 6 + 0] = pbuf + k * 8192;  // lpData
        hdr[k * 6 + 1] = 8192;             // dwBufferLength (bytes)
        hdr[k * 6 + 2] = 0;                // dwUser
        hdr[k * 6 + 3] = 0;                // dwFlags | dwLoops
        hdr[k * 6 + 4] = 0;                // lpNext
        hdr[k * 6 + 5] = 0;                // reserved
        waveOutPrepareHeader(hwo, phdr + k * 48, 48);
        hdr[k * 6 + 3] = 1;                // marcar "done" para rellenar ya
        k++;
    }

    print_win("Bytebeat 8 - waveOut. ESC para salir.\n", 38);

    int64 pos = 0;
    int32 running = 1;

    while (running != 0) {

        k = 0;
        while (k < 2) {

            if ((hdr[k * 6 + 3] & 1) != 0) {      // WHDR_DONE
                int64 i = 0;

                while (i < 4096) {
                    int64 tt = ((pos + i) * 8000) / 44100;
                    int32 t = tt;
                    int32 v = song8(t);

                    buf[k * 4096 + i] = ((v & 255) - 128) * 128;
                    i++;
                }

                pos = pos + 4096;
                waveOutWrite(hwo, phdr + k * 48, 48);
            }

            k++;
        }

        if (GetAsyncKeyState(27) < 0) {           // VK_ESCAPE, bit alto = pulsada
            running = 0;
        }

        Sleep(5);
    }

    waveOutReset(hwo);
    waveOutUnprepareHeader(hwo, phdr, 48);
    waveOutUnprepareHeader(hwo, phdr + 48, 48);
    waveOutClose(hwo);

    print_win("Bytebeat detenido\n", 18);
    ExitProcess(0);
}
