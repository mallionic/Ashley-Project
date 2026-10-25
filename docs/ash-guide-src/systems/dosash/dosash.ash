/*
 * dosash.ash - juegos para DOS en ASH, al estilo de Allegro         GPLv3+
 *
 * Programas .COM de 16 bits en modo real:
 *
 *   @bits(16)
 *   @originoffs(0x100)
 *   import "dosash.ash";
 *
 *   @Entry
 *   pub func _start(): int16 {
 *       allegro_init();
 *       install_keyboard();
 *       set_gfx_mode(GFX_VGA);              // 320x200, 256 colores
 *       clear_to_color(screen, 1);
 *       textout(screen, "HOLA DOS", 120, 96, 15);
 *       readkey();
 *       allegro_exit();
 *       return 0;
 *   }
 *
 * Graficos   set_gfx_mode  create_bitmap  destroy_bitmap  clear_to_color
 *            putpixel getpixel hline vline line rect rectfill circle circlefill
 *            blit_screen  vsync  draw_sprite  textout textout_centre textout_int
 *            set_color set_palette_gray
 * Teclado    install_keyboard remove_keyboard key  keypressed readkey clear_keybuf
 * Tiempo     ticks rest  retrace
 * Sonido     sound nosound beep
 * Raton      install_mouse mouse_x mouse_y mouse_b show_mouse
 * Varios     rand_int srand_seed  allegro_init allegro_exit
 *
 * Un "bitmap" es un segmento de 64000 bytes (320x200): 'screen' es la VGA
 * (A000h) y create_bitmap() pide otro a DOS para dibujar sin parpadeo y
 * copiarlo con blit_screen(). Todos los numeros son de 16 bits.
 */

// ------------------------------------------------------------------ constantes

define GFX_TEXT 3;
define GFX_VGA 0x13;
define SCREEN_W 320;
define SCREEN_H 200;
define screen 0xA000;

// codigos de tecla (scancodes del teclado del PC)
define KEY_ESC 1;
define KEY_1 2;
define KEY_2 3;
define KEY_3 4;
define KEY_Q 16;
define KEY_W 17;
define KEY_E 18;
define KEY_R 19;
define KEY_P 25;
define KEY_ENTER 28;
define KEY_A 30;
define KEY_S 31;
define KEY_D 32;
define KEY_SPACE 57;
define KEY_UP 72;
define KEY_LEFT 75;
define KEY_RIGHT 77;
define KEY_DOWN 80;

// colores de la paleta VGA por defecto
define BLACK 0;
define BLUE 1;
define GREEN 2;
define CYAN 3;
define RED 4;
define MAGENTA 5;
define BROWN 6;
define LIGHTGRAY 7;
define DARKGRAY 8;
define LIGHTBLUE 9;
define LIGHTGREEN 10;
define LIGHTCYAN 11;
define LIGHTRED 12;
define LIGHTMAGENTA 13;
define YELLOW 14;
define WHITE 15;

// ------------------------------------------------------------------ estado

ungeneral static uint16 __font_seg = 0
ungeneral static uint16 __font_off = 0
ungeneral static uint16 __kbd_on = 0
ungeneral static uint16 __rand = 12345
ungeneral static uint16 __mouse_on = 0

/*
 * Tabla de teclas: 128 bytes en DS:0080h (la linea de comandos del PSP, que ya
 * no hace falta) y la ultima tecla en DS:007Eh. La rutina de interrupcion las
 * escribe sin necesitar ninguna variable de ASH.
 */
define __KEYTAB 0x80;
define __LASTKEY 0x7E;
define __OLDVEC 0x5C;

// ------------------------------------------------------------------ sistema

pub func allegro_init(): int16 {
    srand_seed(ticks());
    return 0;
}

pub func allegro_exit(): void {
    nosound();
    remove_keyboard();
    set_gfx_mode(GFX_TEXT);
}

// ------------------------------------------------------------------ graficos

pub func set_gfx_mode(int16 mode): int16 {
    asm {
        mov ax, mode
        xor ah, ah
        int 0x10
    }
    if (mode == GFX_VGA) {
        // fuente 8x8 de la BIOS (INT 10h AX=1130h BH=03h -> ES:BP)
        uint16 fontseg = 0;
        uint16 fontoff = 0;
        asm {
            push es
            push bp
            mov ax, 0x1130
            mov bh, 3
            int 0x10
            mov ax, bp
            mov bx, es
            pop bp
            mov fontoff, ax
            mov fontseg, bx
            pop es
        }
        __font_seg = fontseg;
        __font_off = fontoff;
    }
    return 0;
}

/* bitmap de 320x200 en memoria (segmento de 64000 bytes). 0 si no hay memoria */
pub func create_bitmap(): uint16 {
    uint16 bseg = 0;
    asm {
        mov ah, 0x48
        mov bx, 4000
        int 0x21
        jnc cb_ok
        xor ax, ax
    cb_ok:
        mov bseg, ax
    }
    if (bseg != 0) {
        clear_to_color(bseg, 0);
    }
    return bseg;
}

pub func destroy_bitmap(uint16 bmp): void {
    asm {
        push es
        mov ax, bmp
        mov es, ax
        mov ah, 0x49
        int 0x21
        pop es
    }
}

pub func clear_to_color(uint16 bmp, int16 c): void {
    asm {
        push es
        push di
        mov ax, bmp
        mov es, ax
        xor di, di
        mov ax, c
        mov ah, al
        mov cx, 32000
        cld
        rep stosw
        pop di
        pop es
    }
}

pub func putpixel(uint16 bmp, int16 x, int16 y, int16 c): void {
    if (x < 0 || y < 0 || x >= SCREEN_W || y >= SCREEN_H) { return; }
    uint16 off = y * 320 + x;
    asm {
        push es
        push di
        mov ax, bmp
        mov es, ax
        mov di, off
        mov ax, c
        mov [es:di], al
        pop di
        pop es
    }
}

pub func getpixel(uint16 bmp, int16 x, int16 y): int16 {
    if (x < 0 || y < 0 || x >= SCREEN_W || y >= SCREEN_H) { return -1; }
    uint16 off = y * 320 + x;
    int16 c = 0;
    asm {
        push es
        push di
        mov ax, bmp
        mov es, ax
        mov di, off
        xor ax, ax
        mov al, [es:di]
        mov c, ax
        pop di
        pop es
    }
    return c;
}

/* linea horizontal de x1 a x2 (incluidos) */
pub func hline(uint16 bmp, int16 x1, int16 y, int16 x2, int16 c): void {
    if (y < 0 || y >= SCREEN_H) { return; }
    int16 a = x1;
    int16 b = x2;
    if (a > b) { a = x2; b = x1; }
    if (a < 0) { a = 0; }
    if (b >= SCREEN_W) { b = SCREEN_W - 1; }
    if (a > b) { return; }
    uint16 off = y * 320 + a;
    uint16 n = b - a + 1;
    asm {
        push es
        push di
        mov ax, bmp
        mov es, ax
        mov di, off
        mov cx, n
        mov ax, c
        cld
        rep stosb
        pop di
        pop es
    }
}

pub func vline(uint16 bmp, int16 x, int16 y1, int16 y2, int16 c): void {
    int16 a = y1;
    int16 b = y2;
    if (a > b) { a = y2; b = y1; }
    for (int16 y = a; y <= b; y++) {
        putpixel(bmp, x, y, c);
    }
}

pub func rect(uint16 bmp, int16 x1, int16 y1, int16 x2, int16 y2, int16 c): void {
    hline(bmp, x1, y1, x2, c);
    hline(bmp, x1, y2, x2, c);
    vline(bmp, x1, y1, y2, c);
    vline(bmp, x2, y1, y2, c);
}

pub func rectfill(uint16 bmp, int16 x1, int16 y1, int16 x2, int16 y2, int16 c): void {
    int16 a = y1;
    int16 b = y2;
    if (a > b) { a = y2; b = y1; }
    for (int16 y = a; y <= b; y++) {
        hline(bmp, x1, y, x2, c);
    }
}

/* linea de Bresenham */
pub func line(uint16 bmp, int16 x1, int16 y1, int16 x2, int16 y2, int16 c): void {
    int16 dx = x2 - x1;
    int16 dy = y2 - y1;
    int16 sx = 1;
    int16 sy = 1;
    if (dx < 0) { dx = 0 - dx; sx = -1; }
    if (dy < 0) { dy = 0 - dy; sy = -1; }
    int16 err = dx - dy;
    int16 x = x1;
    int16 y = y1;
    while (1 == 1) {
        putpixel(bmp, x, y, c);
        if (x == x2 && y == y2) { break; }
        int16 e2 = err + err;
        if (e2 > 0 - dy) { err = err - dy; x = x + sx; }
        if (e2 < dx) { err = err + dx; y = y + sy; }
    }
}

/* circulo (algoritmo del punto medio) */
pub func circle(uint16 bmp, int16 cx, int16 cy, int16 r, int16 c): void {
    int16 x = r;
    int16 y = 0;
    int16 err = 1 - r;
    while (x >= y) {
        putpixel(bmp, cx + x, cy + y, c);
        putpixel(bmp, cx + y, cy + x, c);
        putpixel(bmp, cx - y, cy + x, c);
        putpixel(bmp, cx - x, cy + y, c);
        putpixel(bmp, cx - x, cy - y, c);
        putpixel(bmp, cx - y, cy - x, c);
        putpixel(bmp, cx + y, cy - x, c);
        putpixel(bmp, cx + x, cy - y, c);
        y++;
        if (err < 0) {
            err = err + 2 * y + 1;
        } else {
            x--;
            err = err + 2 * (y - x) + 1;
        }
    }
}

pub func circlefill(uint16 bmp, int16 cx, int16 cy, int16 r, int16 c): void {
    int16 x = r;
    int16 y = 0;
    int16 err = 1 - r;
    while (x >= y) {
        hline(bmp, cx - x, cy + y, cx + x, c);
        hline(bmp, cx - x, cy - y, cx + x, c);
        hline(bmp, cx - y, cy + x, cx + y, c);
        hline(bmp, cx - y, cy - x, cx + y, c);
        y++;
        if (err < 0) {
            err = err + 2 * y + 1;
        } else {
            x--;
            err = err + 2 * (y - x) + 1;
        }
    }
}

/* espera al retrazado vertical (evita el parpadeo, marca 70 imagenes/s) */
pub func vsync(): void {
    asm {
        mov dx, 0x3DA
    vs_a:
        in al, dx
        test al, 8
        jnz vs_a
    vs_b:
        in al, dx
        test al, 8
        jz vs_b
    }
}

/* copia un bitmap entero a la pantalla (con vsync) */
pub func blit_screen(uint16 bmp): void {
    vsync();
    asm {
        push ds
        push es
        push si
        push di
        mov ax, bmp
        mov bx, 0xA000
        mov es, bx
        mov ds, ax
        xor si, si
        xor di, di
        mov cx, 32000
        cld
        rep movsw
        pop di
        pop si
        pop es
        pop ds
    }
}

/*
 * sprite de w x h bytes (uno por pixel) guardado en 'data'; el color 0 es
 * transparente. Se recorta en los bordes de la pantalla.
 */
pub func draw_sprite(uint16 bmp, uint8* data, int16 w, int16 h, int16 x, int16 y): void {
    for (int16 j = 0; j < h; j++) {
        for (int16 i = 0; i < w; i++) {
            int16 c = data[j * w + i];
            if (c != 0) {
                putpixel(bmp, x + i, y + j, c);
            }
        }
    }
}

// ------------------------------------------------------------------ paleta

/* cambia el color 'index' (r, g, b de 0 a 63) */
pub func set_color(int16 index, int16 r, int16 g, int16 b): void {
    asm {
        mov dx, 0x3C8
        mov ax, index
        out dx, al
        inc dx
        mov ax, r
        out dx, al
        mov ax, g
        out dx, al
        mov ax, b
        out dx, al
    }
}

/* colores first..first+63 en escala de grises / degradado de un color */
pub func set_palette_gray(int16 first): void {
    for (int16 i = 0; i < 64; i++) {
        set_color(first + i, i, i, i);
    }
}

pub func set_palette_ramp(int16 first, int16 r, int16 g, int16 b): void {
    for (int16 i = 0; i < 64; i++) {
        set_color(first + i, r * i / 63, g * i / 63, b * i / 63);
    }
}

// ------------------------------------------------------------------ texto

/* dibuja un caracter 8x8 de la fuente de la BIOS */
pub func draw_char(uint16 bmp, int16 ch, int16 x, int16 y, int16 c): void {
    uint16 fontseg = __font_seg;
    uint16 fontoff = __font_off + (ch & 127) * 8;
    for (int16 row = 0; row < 8; row++) {
        int16 glyph = 0;
        asm {
            push es
            push bx
            mov ax, fontseg
            mov es, ax
            mov bx, fontoff
            xor ax, ax
            mov al, [es:bx]
            mov glyph, ax
            pop bx
            pop es
        }
        int16 mask = 128;
        for (int16 col = 0; col < 8; col++) {
            if ((glyph & mask) != 0) {
                putpixel(bmp, x + col, y + row, c);
            }
            mask = mask >> 1;
        }
        fontoff++;
    }
}

pub func text_length(uint8* s): int16 {
    int16 n = 0;
    while (s[n] != 0) { n++; }
    return n * 8;
}

pub func textout(uint16 bmp, uint8* s, int16 x, int16 y, int16 c): void {
    int16 i = 0;
    while (s[i] != 0) {
        draw_char(bmp, s[i], x + i * 8, y, c);
        i++;
    }
}

pub func textout_centre(uint16 bmp, uint8* s, int16 x, int16 y, int16 c): void {
    textout(bmp, s, x - text_length(s) / 2, y, c);
}

/* numero entero en decimal; devuelve el ancho dibujado */
pub func textout_int(uint16 bmp, int16 v, int16 x, int16 y, int16 c): int16 {
    int16 px = x;
    uint16 n = v;
    if (v < 0) {
        draw_char(bmp, 45, px, y, c);
        px = px + 8;
        n = 0 - v;
    }
    uint16 dv = 10000;
    int16 started = 0;
    while (dv > 0) {
        uint16 d = n / dv;
        if (d != 0 || started == 1 || dv == 1) {
            draw_char(bmp, 48 + d, px, y, c);
            px = px + 8;
            started = 1;
        }
        n = n % dv;
        dv = dv / 10;
    }
    return px - x;
}

// ------------------------------------------------------------------ teclado

/* rutina de interrupcion del teclado (INT 9): mantiene la tabla de teclas */
@naked
func __keyboard_isr(): void {
    asm {
        push ax
        push bx
        push ds
        push cs
        pop ds
        in al, 0x60
        xor bx, bx
        mov bl, al
        and bl, 0x7F
        test al, 0x80
        jnz kbd_up
        mov byte [bx + 0x80], 1
        mov [0x7E], bl
        mov byte [0x7F], 1
        jmp kbd_ack
    kbd_up:
        mov byte [bx + 0x80], 0
    kbd_ack:
        in al, 0x61
        or al, 0x80
        out 0x61, al
        and al, 0x7F
        out 0x61, al
        mov al, 0x20
        out 0x20, al
        pop ds
        pop bx
        pop ax
        iret
    }
}

/* teclado en tiempo real: key(KEY_LEFT) dice si esta pulsada ahora mismo */
pub func install_keyboard(): int16 {
    if (__kbd_on == 1) { return 0; }
    uint16 isr = &__keyboard_isr;
    for (int16 i = 0; i < 128; i++) {
        *(uint8*)(__KEYTAB + i) = 0;
    }
    *(uint8*)(0x7F) = 0;
    asm {
        push es
        push bx
        mov ax, 0x3509
        int 0x21
        mov [0x5C], bx
        mov [0x5E], es
        pop bx
        pop es
        mov dx, isr
        mov ax, 0x2509
        int 0x21
    }
    __kbd_on = 1;
    return 0;
}

pub func remove_keyboard(): void {
    if (__kbd_on == 0) { return; }
    asm {
        push ds
        mov dx, [0x5C]
        mov ax, [0x5E]
        mov ds, ax
        mov ax, 0x2509
        int 0x21
        pop ds
    }
    __kbd_on = 0;
}

/* 1 si la tecla esta pulsada (con install_keyboard) */
pub func key(int16 scancode): int16 {
    return *(uint8*)(__KEYTAB + (scancode & 127));
}

/* 1 si hay una tecla esperando */
pub func keypressed(): int16 {
    if (__kbd_on == 1) {
        return *(uint8*)(0x7F);
    }
    int16 r = 0;
    asm {
        mov ah, 1
        int 0x16
        jz kp_none
        mov r, 1
    kp_none:
    }
    return r;
}

/* espera una tecla y devuelve su scancode (con install_keyboard) o ASCII | scancode<<8 */
pub func readkey(): int16 {
    if (__kbd_on == 1) {
        while (*(uint8*)(0x7F) == 0) { }
        *(uint8*)(0x7F) = 0;
        return *(uint8*)(0x7E);
    }
    int16 k = 0;
    asm {
        xor ah, ah
        int 0x16
        mov k, ax
    }
    return k;
}

pub func clear_keybuf(): void {
    *(uint8*)(0x7F) = 0;
}

// ------------------------------------------------------------------ tiempo

/* contador de la BIOS: 18.2 tics por segundo (0040:006C) */
pub func ticks(): uint16 {
    uint16 t = 0;
    asm {
        push es
        push bx
        mov ax, 0x40
        mov es, ax
        mov bx, 0x6C
        mov ax, [es:bx]
        mov t, ax
        pop bx
        pop es
    }
    return t;
}

/* espera 'ms' milisegundos (BIOS INT 15h AH=86h: espera en microsegundos) */
pub func rest(uint16 ms): void {
    asm {
        mov ax, ms
        mov bx, 1000
        mul bx
        mov cx, dx
        mov dx, ax
        mov ah, 0x86
        int 0x15
    }
}

/* espera 'n' retrazados verticales (70 por segundo) */
pub func retrace(int16 n): void {
    for (int16 i = 0; i < n; i++) {
        vsync();
    }
}

// ------------------------------------------------------------------ sonido (altavoz del PC)

pub func sound(uint16 freq): void {
    if (freq < 20) { return; }
    asm {
        mov al, 0xB6
        out 0x43, al
        mov dx, 0x12
        mov ax, 0x34DC
        mov bx, freq
        div bx
        out 0x42, al
        mov al, ah
        out 0x42, al
        in al, 0x61
        or al, 3
        out 0x61, al
    }
}

pub func nosound(): void {
    asm {
        in al, 0x61
        and al, 0xFC
        out 0x61, al
    }
}

/* pitido corto */
pub func beep(uint16 freq, int16 ms): void {
    sound(freq);
    rest(ms);
    nosound();
}

// ------------------------------------------------------------------ raton (INT 33h)

pub func install_mouse(): int16 {
    int16 ok = 0;
    asm {
        xor ax, ax
        int 0x33
        mov ok, ax
    }
    if (ok != 0) { __mouse_on = 1; return 1; }
    return 0;
}

pub func show_mouse(int16 visible): void {
    if (__mouse_on == 0) { return; }
    if (visible == 1) {
        asm {
            mov ax, 1
            int 0x33
        }
    } else {
        asm {
            mov ax, 2
            int 0x33
        }
    }
}

pub func mouse_x(): int16 {
    int16 v = 0;
    asm {
        mov ax, 3
        int 0x33
        shr cx, 1
        mov v, cx
    }
    return v;
}

pub func mouse_y(): int16 {
    int16 v = 0;
    asm {
        mov ax, 3
        int 0x33
        mov v, dx
    }
    return v;
}

pub func mouse_b(): int16 {
    int16 v = 0;
    asm {
        mov ax, 3
        int 0x33
        mov v, bx
    }
    return v;
}

// ------------------------------------------------------------------ numeros aleatorios

pub func srand_seed(uint16 s): void {
    __rand = s | 1;
}

/* numero aleatorio de 0 a max-1 */
pub func rand_int(int16 max): int16 {
    __rand = __rand * 25173 + 13849;
    uint16 r = __rand >> 1;
    if (max <= 0) { return 0; }
    return r % max;
}
