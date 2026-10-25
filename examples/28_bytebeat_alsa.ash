@bits(64)
@Entry

extern int32 snd_pcm_open(uint64 pcm, uint64 name, int32 stream, int32 mode);
extern int32 snd_pcm_set_params(uint64 pcm, int32 format, int32 access, uint32 channels, uint32 rate, int32 soft_resample, uint32 latency);
extern int64 snd_pcm_writei(uint64 pcm, uint64 buffer, uint64 frames);
extern int32 snd_pcm_recover(uint64 pcm, int32 err, int32 silent);
extern int32 snd_pcm_drain(uint64 pcm);
extern int32 snd_pcm_close(uint64 pcm);

// ---------------------------------------------------------------
// Cancion 1: 10*(t>>7|t|t>>6)+4*(t&t>>13|t>>6)
// ---------------------------------------------------------------
func song1(int32 t): int32 {
    int32 a = ((t >> 7) | t) | (t >> 6);
    int32 b = (t & (t >> 13)) | (t >> 6);
    return 10 * a + 4 * b;
}

// ---------------------------------------------------------------
// Cancion 2: t*(t^t+(t>>15|1)^(t-1280^t)>>10)
// ---------------------------------------------------------------
func song2(int32 t): int32 {
    int32 x = t + ((t >> 15) | 1);
    int32 y = ((t - 1280) ^ t) >> 10;
    return t * ((t ^ x) ^ y);
}

// ---------------------------------------------------------------
// Cancion 3: cadena de "?:" de 262144 muestras
// ---------------------------------------------------------------
func song3(int32 t): int32 {
    int32 m = t % 262144;
    int32 r = 0;

    int32 c2 = 0;
    int32 c3 = 0;
    int32 c4 = 0;
    int32 c5 = 0;

    if (m > 131072) { if (m < 163840) { c2 = 1; } }
    if (m > 163840) { if (m < 196608) { c3 = 1; } }
    if (m > 196608) { if (m < 229376) { c4 = 1; } }
    if (m > 229376) { if (m < 245760) { c5 = 1; } }

    if (131072 > m) {
        r = (((t / 64) >> 3) & (2 * t) & (10 * t)) | ((t >> 5) & (6 * t) & ((t >> 4) | (t >> 5)));
    } else if (c2 != 0) {
        r = ((t >> 4) & (8 * t) & ((t >> 5) | (t >> 4))) | ((3 * t) & (10 * t));
    } else if (c3 != 0) {
        r = ((t >> 4) & (8 * t) & ((t >> 5) | (t >> 4))) | ((3 * t) & (6 * t));
    } else if (c4 != 0) {
        r = ((t >> 4) & (8 * t) & ((t >> 5) | (t >> 4))) | ((4 * t) & (6 * t));
    } else if (c5 != 0) {
        r = ((t >> 4) & (8 * t) & ((t >> 5) | (t >> 4))) | ((4 * t) & (2 * t));
    } else {
        r = ((t >> 4) & (8 * t) & (t >> 4)) | (((4 * t) & (2 * t)) >> 20);
    }

    return r;
}

// ---------------------------------------------------------------
// Cancion 4: (-t&t>>4&t>>4&t>>11)-1&-t>>4
// ---------------------------------------------------------------
func song4(int32 t): int32 {
    int32 nt = 0 - t;
    int32 a = (((nt & (t >> 4)) & (t >> 4)) & (t >> 11)) - 1;
    return a & (nt >> 4);
}

func sample(int32 song, int32 t): int32 {
    int32 v = 0;

    if (song == 0) {
        v = song1(t);
    } else if (song == 1) {
        v = song2(t);
    } else if (song == 2) {
        v = song3(t);
    } else {
        v = song4(t);
    }

    return v;
}

pub func main(): void {
    uint64 pcm = 0;

    if (snd_pcm_open(&pcm, "default", 0, 0) < 0) {
        printf("Error al abrir ALSA\n");
        exit(1);
    }

    // S16_LE, interleaved, mono, 44100 Hz, remuestreo suave, 200 ms de latencia
    if (snd_pcm_set_params(pcm, 2, 3, 1, 44100, 1, 200000) < 0) {
        printf("Error al configurar ALSA\n");
        snd_pcm_close(pcm);
        exit(1);
    }

    // Buffer pequeno que se reutiliza (en vez de 5 millones de muestras)
    int16 buf[4096];
    uint64 base = &buf;

    int64 total = 1323000;      // 30 s * 44100 Hz por cancion
    int32 song = 0;

    while (song < 4) {

        int64 done = 0;

        while (done < total) {

            int64 n = total - done;
            if (n > 4096) {
                n = 4096;
            }

            int64 i = 0;
            while (i < n) {
                // reloj bytebeat clasico a 8000 Hz sobre salida de 44100 Hz
                int64 tt = ((done + i) * 8000) / 44100;
                int32 t = tt;
                int32 v = sample(song, t);

                buf[i] = ((v & 255) - 128) * 128;
                i++;
            }

            int64 off = 0;
            while (off < n) {
                int64 w = snd_pcm_writei(pcm, base + off * 2, n - off);

                if (w < 0) {
                    if (snd_pcm_recover(pcm, w, 1) < 0) {
                        printf("Error al escribir PCM\n");
                        snd_pcm_close(pcm);
                        exit(1);
                    }
                } else {
                    off = off + w;
                }
            }

            done = done + n;
        }

        song++;
    }

    snd_pcm_drain(pcm);
    snd_pcm_close(pcm);

    exit(0);
}
