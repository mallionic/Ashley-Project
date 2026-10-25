@bits(64)
@Entry

// Ejemplo 2: MessageBox (user32) con dos cuadros y salida segun boton

extern int32 MessageBoxA(uint64 hwnd, uint64 text, uint64 caption, uint32 type);
extern void  ExitProcess(uint32 code);

pub func main(): void {
    // MB_OK (0) | MB_ICONINFORMATION (0x40) = 64
    MessageBoxA(0, "Hola desde ASH", "Informacion", 64);

    // MB_YESNO (4) | MB_ICONQUESTION (0x20) = 36
    int32 r = MessageBoxA(0, "Quieres salir con codigo 0?", "Pregunta", 36);

    if (r == 6) {          // IDYES
        ExitProcess(0);
    }

    ExitProcess(1);        // IDNO
}
