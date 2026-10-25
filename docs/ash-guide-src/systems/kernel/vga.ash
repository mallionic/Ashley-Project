// vga.ash - a small driver for the VGA text screen (80 x 25 cells at 0xB8000)
define VGA_TEXT 0xB8000;
define VGA_COLS 80;
define VGA_ROWS 25;

general static int32 vga_row = 0
general static int32 vga_col = 0
general static int32 vga_color = 0x07

// attribute byte: background color in bits 4-6, foreground color in bits 0-3
func vga_attr(int32 fg, int32 bg): int32 {
    return (bg << 4) | fg;
}

func vga_put(int32 col, int32 row, int32 ch, int32 attr): void {
    uint8* cell = VGA_TEXT + (row * VGA_COLS + col) * 2;
    cell[0] = ch;
    cell[1] = attr;
}

func vga_clear(int32 attr): void {
    for (int32 i = 0; i < VGA_COLS * VGA_ROWS; i++) {
        vga_put(i % VGA_COLS, i / VGA_COLS, ' ', attr);
    }
    vga_row = 0;
    vga_col = 0;
}

func vga_goto(int32 col, int32 row): void {
    vga_col = col;
    vga_row = row;
}

// move the blinking hardware cursor: CRT controller registers 14 and 15
func vga_cursor(int32 col, int32 row): void {
    int32 pos = row * VGA_COLS + col;
    int32 high = (pos >> 8) & 255;
    int32 low = pos & 255;
    asm {
        mov dx, 0x3D4
        mov al, 14
        out dx, al
        mov dx, 0x3D5
        mov eax, high
        out dx, al
        mov dx, 0x3D4
        mov al, 15
        out dx, al
        mov dx, 0x3D5
        mov eax, low
        out dx, al
    }
}

// move every line up by one and clear the last line
func vga_scroll(): void {
    uint8* screen = VGA_TEXT;
    for (int32 i = 0; i < (VGA_ROWS - 1) * VGA_COLS * 2; i++) {
        screen[i] = screen[i + VGA_COLS * 2];
    }
    for (int32 c = 0; c < VGA_COLS; c++) {
        vga_put(c, VGA_ROWS - 1, ' ', vga_color);
    }
    vga_row = VGA_ROWS - 1;
}

func vga_putc(int32 ch): void {
    if (ch == '\n') {
        vga_col = 0;
        vga_row = vga_row + 1;
    } else {
        vga_put(vga_col, vga_row, ch, vga_color);
        vga_col = vga_col + 1;
        if (vga_col == VGA_COLS) {
            vga_col = 0;
            vga_row = vga_row + 1;
        }
    }
    if (vga_row == VGA_ROWS) {
        vga_scroll();
    }
}

func vga_puts(uint8* s): void {
    int32 i = 0;
    while (s[i] != 0) {
        vga_putc(s[i]);
        i++;
    }
}

func vga_dec(uint32 v): void {
    uint8[12] digits;
    int32 n = 0;
    if (v == 0) {
        vga_putc('0');
        return;
    }
    while (v > 0) {
        digits[n] = '0' + v % 10;
        v = v / 10;
        n++;
    }
    while (n > 0) {
        n--;
        vga_putc(digits[n]);
    }
}

func vga_hex(uint32 v): void {
    vga_puts("0x");
    for (int32 shift = 28; shift >= 0; shift = shift - 4) {
        int32 d = (v >> shift) & 15;
        if (d < 10) { vga_putc('0' + d); } else { vga_putc('A' + d - 10); }
    }
}

// a frame drawn with the double-line characters of code page 437
func vga_frame(int32 col, int32 row, int32 w, int32 h, int32 attr): void {
    vga_put(col, row, 0xC9, attr);
    vga_put(col + w - 1, row, 0xBB, attr);
    vga_put(col, row + h - 1, 0xC8, attr);
    vga_put(col + w - 1, row + h - 1, 0xBC, attr);
    for (int32 x = 1; x < w - 1; x++) {
        vga_put(col + x, row, 0xCD, attr);
        vga_put(col + x, row + h - 1, 0xCD, attr);
    }
    for (int32 y = 1; y < h - 1; y++) {
        vga_put(col, row + y, 0xBA, attr);
        vga_put(col + w - 1, row + y, 0xBA, attr);
    }
}

// a bar of 'full' cells out of 'size', in the given color
func vga_bar(int32 col, int32 row, int32 size, int32 full, int32 attr): void {
    for (int32 i = 0; i < size; i++) {
        if (i < full) {
            vga_put(col + i, row, 0xDB, attr);
        } else {
            vga_put(col + i, row, 0xB0, 0x08);
        }
    }
}
