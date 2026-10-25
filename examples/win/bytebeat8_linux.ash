@bits(64)
@Entry

// ============================================================
// Bytebeat 8: ALSA sin ventana X11
// Reproduccion continua hasta Ctrl+C
// Version SIN desplazamientos de cantidad variable
// (evita: 'b' already exists as DWORD but QWORD is needed)
// ============================================================

// ---- ALSA ----
extern int32 snd_pcm_open(uint64 pcm, uint64 name, int32 stream, int32 mode);
extern int32 snd_pcm_set_params(uint64 pcm, int32 format, int32 access, uint32 channels, uint32 rate, int32 soft_resample, uint32 latency);
extern int64 snd_pcm_writei(uint64 pcm, uint64 buffer, uint64 frames);
extern int64 snd_pcm_avail_update(uint64 pcm);
extern int32 snd_pcm_recover(uint64 pcm, int32 err, int32 silent);
extern int32 snd_pcm_start(uint64 pcm);
extern int32 snd_pcm_drop(uint64 pcm);
extern int32 snd_pcm_prepare(uint64 pcm);
extern int32 snd_pcm_drain(uint64 pcm);
extern int32 snd_pcm_close(uint64 pcm);

// ---- libc ----
extern int32 usleep(uint32 us);
extern int32 printf(uint64 fmt, ...);
extern void exit(int32 code);

// ---------------------------------------------------------------
// Bytebeat 8:
// (t * ((t & 4096 ? 6 : 16) + (1 & (t >> 14))) >> (3 & (t >> 8)))
//   | (t >> (t & 4096 ? 3 : 4))
// Solo desplazamientos con constante: el compilador los maneja bien.
// ---------------------------------------------------------------
func song8(int32 t): int32 {
    int32 factor = 16;

    if ((t & 4096) != 0) {
        factor = 6;
    }

    int32 a = t * (factor + (1 & (t >> 14)));

    // a >> (3 & (t >> 8))  ->  s en 0..3, cada caso con constante
    int32 s = (t >> 8) & 3;
    int32 b = a;

    if (s == 1) {
        b = a >> 1;
    } else if (s == 2) {
        b = a >> 2;
    } else if (s == 3) {
        b = a >> 3;
    }

    // t >> (t & 4096 ? 3 : 4)
    int32 c = t >> 4;

    if ((t & 4096) != 0) {
        c = t >> 3;
    }

    return b | c;
}

pub func main(): void {

    uint64 pcm = 0;

    if (snd_pcm_open(&pcm, "default", 0, 1) < 0) {
        printf("Error al abrir ALSA\n");
        exit(1);
    }

    // S16_LE, interleaved, mono, 44100 Hz, 100 ms de latencia
    if (snd_pcm_set_params(pcm, 2, 3, 1, 44100, 1, 100000) < 0) {
        printf("Error al configurar ALSA\n");
        snd_pcm_close(pcm);
        exit(1);
    }

    int16 buf[1024];
    uint64 base = buf;

    int64 pos = 0;
    int32 running = 1;

    printf("Bytebeat 8 - ALSA\n");
    printf("Pulsa Ctrl+C para detener.\n");

    while (running != 0) {

        int64 avail = snd_pcm_avail_update(pcm);

        if (avail < 0) {
            int32 recovered = snd_pcm_recover(pcm, avail, 1);

            if (recovered < 0) {
                printf("Error de recuperacion ALSA\n");
                snd_pcm_drop(pcm);
                snd_pcm_close(pcm);
                exit(1);
            }

        } else if (avail >= 1024) {

            int64 i = 0;

            while (i < 1024) {
                int64 tt = ((pos + i) * 8000) / 44100;
                int32 t = tt;
                int32 v = song8(t);

                buf[i] = ((v & 255) - 128) * 128;
                i++;
            }

            int64 w = snd_pcm_writei(pcm, base, 1024);

            if (w < 0) {
                int32 recovered = snd_pcm_recover(pcm, w, 1);

                if (recovered < 0) {
                    printf("Error al escribir audio ALSA\n");
                    snd_pcm_drop(pcm);
                    snd_pcm_close(pcm);
                    exit(1);
                }

            } else {
                pos = pos + w;

                if (pos == 2048) {
                    snd_pcm_start(pcm);
                }
            }

        } else {
            usleep(3000);
        }
    }

    snd_pcm_drop(pcm);
    snd_pcm_close(pcm);

    printf("Bytebeat detenido\n");
    exit(0);
}
