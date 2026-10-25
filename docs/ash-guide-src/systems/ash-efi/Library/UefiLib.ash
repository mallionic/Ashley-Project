/*
 * UefiLib.ash - libreria UEFI estilo EDK2 para ASH                GPLv3+
 *
 * Equivale a (EDK2 MdePkg):
 *   UefiBootServicesTableLib  gST gBS gImageHandle
 *   UefiRuntimeServicesTableLib  gRT
 *   UefiLib                   Print  AsciiPrint  EFI_ERROR
 *   MemoryAllocationLib       AllocatePool  AllocateZeroPool  FreePool
 *   BaseMemoryLib             CopyMem  SetMem  ZeroMem  CompareMem
 *   BaseLib                   StrLen  StrCmp  StrCpy  AsciiStrLen
 *
 * Se importa con todo lo demas desde efi.ash:
 *
 *   import "efi.ash";
 *
 *   @Entry
 *   @msabi
 *   pub func efi_main(EFI_HANDLE ImageHandle, EFI_SYSTEM_TABLE* SystemTable): EFI_STATUS {
 *       InitializeLib(ImageHandle, SystemTable);
 *       Print(L"Hola %s\n", L"UEFI");
 *       return EFI_SUCCESS;
 *   }
 */

import "../include/Uefi.ash";

// ------------------------------------------------------------------ globales (como EDK2)

general static EFI_SYSTEM_TABLE* gST = 0
general static EFI_BOOT_SERVICES* gBS = 0
general static EFI_RUNTIME_SERVICES* gRT = 0
general static EFI_HANDLE gImageHandle = 0

// buffer interno de Print (CHAR16)
ungeneral static UINT64 __PrintBuffer = 0
ungeneral static UINTN __PrintLength = 0

define __PRINT_MAX 1000;

// ------------------------------------------------------------------ inicializacion

/*
 * Rellena gST, gBS, gRT, gImageHandle (en EDK2 lo hacen los constructores de
 * UefiBootServicesTableLib / UefiRuntimeServicesTableLib) y desactiva el
 * watchdog de 5 minutos. Mismo nombre que en gnu-efi.
 */
pub func InitializeLib(EFI_HANDLE ImageHandle, EFI_SYSTEM_TABLE* SystemTable): EFI_STATUS {
    gImageHandle = ImageHandle;
    gST = SystemTable;
    gBS = SystemTable->BootServices;
    gRT = SystemTable->RuntimeServices;
    gBS->SetWatchdogTimer(0, 0, 0, NULL);
    __PrintBuffer = AllocatePool((__PRINT_MAX + 8) * 2);
    if (__PrintBuffer == 0) {
        return EFI_OUT_OF_RESOURCES;
    }
    return EFI_SUCCESS;
}

/* TRUE si el EFI_STATUS es un error (bit mas alto activo) */
pub func EFI_ERROR(EFI_STATUS Status): BOOLEAN {
    INT64 s = Status;
    if (s < 0) {
        return TRUE;
    }
    return FALSE;
}

// ------------------------------------------------------------------ MemoryAllocationLib

pub func AllocatePool(UINTN AllocationSize): UINT64 {
    UINT64 Buffer = 0;
    EFI_STATUS Status = gBS->AllocatePool(EfiBootServicesData, AllocationSize, &Buffer);
    if (EFI_ERROR(Status) == TRUE) {
        return 0;
    }
    return Buffer;
}

pub func AllocateZeroPool(UINTN AllocationSize): UINT64 {
    UINT64 Buffer = AllocatePool(AllocationSize);
    if (Buffer != 0) {
        gBS->SetMem(Buffer, AllocationSize, 0);
    }
    return Buffer;
}

pub func FreePool(UINT64 Buffer): void {
    gBS->FreePool(Buffer);
}

// ------------------------------------------------------------------ BaseMemoryLib

pub func CopyMem(UINT64 Destination, UINT64 Source, UINTN Length): UINT64 {
    gBS->CopyMem(Destination, Source, Length);
    return Destination;
}

pub func SetMem(UINT64 Buffer, UINTN Length, UINT8 Value): UINT64 {
    gBS->SetMem(Buffer, Length, Value);
    return Buffer;
}

pub func ZeroMem(UINT64 Buffer, UINTN Length): UINT64 {
    gBS->SetMem(Buffer, Length, 0);
    return Buffer;
}

pub func CompareMem(UINT8* A, UINT8* B, UINTN Length): INTN {
    UINTN i = 0;
    while (i < Length) {
        INTN x = A[i];
        INTN y = B[i];
        if (x != y) {
            return x - y;
        }
        i++;
    }
    return 0;
}

pub func CompareGuid(UINT8* A, UINT8* B): BOOLEAN {
    if (CompareMem(A, B, 16) == 0) {
        return TRUE;
    }
    return FALSE;
}

// ------------------------------------------------------------------ BaseLib (cadenas CHAR16 / CHAR8)

pub func StrLen(CHAR16* String): UINTN {
    UINTN n = 0;
    while (String[n] != 0) {
        n++;
    }
    return n;
}

pub func StrCmp(CHAR16* A, CHAR16* B): INTN {
    UINTN i = 0;
    while (TRUE == 1) {
        INTN x = A[i];
        INTN y = B[i];
        if (x != y) {
            return x - y;
        }
        if (x == 0) {
            return 0;
        }
        i++;
    }
    return 0;
}

pub func StrCpy(CHAR16* Destination, CHAR16* Source): UINT64 {
    UINTN i = 0;
    while (Source[i] != 0) {
        Destination[i] = Source[i];
        i++;
    }
    Destination[i] = 0;
    return Destination;
}

pub func AsciiStrLen(CHAR8* String): UINTN {
    UINTN n = 0;
    while (String[n] != 0) {
        n++;
    }
    return n;
}

// ------------------------------------------------------------------ Print (UefiLib)

func __PrintFlush(): void {
    CHAR16* b = __PrintBuffer;
    b[__PrintLength] = 0;
    gST->ConOut->OutputString(gST->ConOut, __PrintBuffer);
    __PrintLength = 0;
}

func __PrintChar(UINTN c): void {
    CHAR16* b = __PrintBuffer;
    // como en EDK2, '\n' sale como "\r\n"
    if (c == 10) {
        b[__PrintLength] = 13;
        __PrintLength++;
    }
    b[__PrintLength] = c;
    __PrintLength++;
    if (__PrintLength >= __PRINT_MAX) {
        __PrintFlush();
    }
}

func __PrintStr(CHAR16* s): void {
    if (s == 0) {
        __PrintStr(L"(null)");
        return;
    }
    UINTN i = 0;
    while (s[i] != 0) {
        __PrintChar(s[i]);
        i++;
    }
}

func __PrintAscii(CHAR8* s): void {
    UINTN i = 0;
    while (s[i] != 0) {
        __PrintChar(s[i]);
        i++;
    }
}

/* numero sin signo en 'base' (10 o 16), al menos 'width' cifras con 'pad' delante */
func __PrintNum(UINT64 v, UINTN base, UINTN width, UINTN pad, UINTN upper): void {
    CHAR16* b = __PrintBuffer;
    UINTN start = __PRINT_MAX + 7;     // los digitos se escriben al reves al final del buffer
    UINTN n = 0;
    UINT64 x = v;
    UINTN letter = 87;                 // 'a' - 10
    if (upper == 1) {
        letter = 55;                   // 'A' - 10
    }
    if (x == 0) {
        b[start] = 48;
        n = 1;
    }
    while (x != 0) {
        UINTN d = x % base;
        if (d < 10) {
            b[start - n] = 48 + d;
        } else {
            b[start - n] = letter + d;
        }
        x = x / base;
        n++;
    }
    while (n < width) {
        __PrintChar(pad);
        width--;
    }
    UINTN k = n;
    while (k > 0) {
        k--;
        __PrintChar(b[start - k]);
    }
}

func __PrintStatus(EFI_STATUS s): void {
    if (s == EFI_SUCCESS) { __PrintStr(L"Success"); return; }
    if (s == EFI_LOAD_ERROR) { __PrintStr(L"Load Error"); return; }
    if (s == EFI_INVALID_PARAMETER) { __PrintStr(L"Invalid Parameter"); return; }
    if (s == EFI_UNSUPPORTED) { __PrintStr(L"Unsupported"); return; }
    if (s == EFI_BUFFER_TOO_SMALL) { __PrintStr(L"Buffer Too Small"); return; }
    if (s == EFI_NOT_READY) { __PrintStr(L"Not Ready"); return; }
    if (s == EFI_DEVICE_ERROR) { __PrintStr(L"Device Error"); return; }
    if (s == EFI_OUT_OF_RESOURCES) { __PrintStr(L"Out of Resources"); return; }
    if (s == EFI_NOT_FOUND) { __PrintStr(L"Not Found"); return; }
    if (s == EFI_ACCESS_DENIED) { __PrintStr(L"Access Denied"); return; }
    if (s == EFI_TIMEOUT) { __PrintStr(L"Time out"); return; }
    __PrintStr(L"Status ");
    __PrintNum(s, 16, 16, 48, 1);
}

func __PrintGuid(UINT8* g): void {
    UINT32* d1 = g;
    UINT16* d23 = g;
    __PrintNum(d1[0], 16, 8, 48, 0);
    __PrintChar(45);
    __PrintNum(d23[2], 16, 4, 48, 0);
    __PrintChar(45);
    __PrintNum(d23[3], 16, 4, 48, 0);
    __PrintChar(45);
    __PrintNum(g[8], 16, 2, 48, 0);
    __PrintNum(g[9], 16, 2, 48, 0);
    __PrintChar(45);
    for (UINTN i = 10; i < 16; i++) {
        __PrintNum(g[i], 16, 2, 48, 0);
    }
}

/*
 * Nucleo de Print / AsciiPrint: recorre el formato (CHAR16 si wide=1, CHAR8 si 0).
 *   %d %i  entero con signo     %u  sin signo        %x %X  hexadecimal
 *   %ld %lx %lu  (l se acepta)  %08x  ancho y relleno  %p  puntero
 *   %s  CHAR16*   %a  CHAR8*    %c  caracter         %r  EFI_STATUS en texto
 *   %g  EFI_GUID*               %%  '%'
 */
func __PrintFormat(UINT64 fmt, UINTN wide, UINT64 a0, UINT64 a1, UINT64 a2, UINT64 a3,
                   UINT64 a4, UINT64 a5, UINT64 a6, UINT64 a7): void {
    CHAR16* f16 = fmt;
    CHAR8* f8 = fmt;
    UINTN i = 0;
    UINTN argi = 0;
    while (TRUE == 1) {
        UINTN c = 0;
        if (wide == 1) { c = f16[i]; } else { c = f8[i]; }
        if (c == 0) { break; }
        i++;
        if (c != 37) {
            __PrintChar(c);
            continue;
        }
        // %[0][ancho][l]tipo
        UINTN pad = 32;
        UINTN width = 0;
        if (wide == 1) { c = f16[i]; } else { c = f8[i]; }
        if (c == 48) { pad = 48; i++; }
        while (TRUE == 1) {
            if (wide == 1) { c = f16[i]; } else { c = f8[i]; }
            if (c < 48 || c > 57) { break; }
            width = width * 10 + (c - 48);
            i++;
        }
        while (c == 108 || c == 76) {          // l, L
            i++;
            if (wide == 1) { c = f16[i]; } else { c = f8[i]; }
        }
        i++;
        if (c == 37) { __PrintChar(37); continue; }
        if (c == 0) { break; }

        UINT64 v = 0;
        if (argi == 0) { v = a0; }
        if (argi == 1) { v = a1; }
        if (argi == 2) { v = a2; }
        if (argi == 3) { v = a3; }
        if (argi == 4) { v = a4; }
        if (argi == 5) { v = a5; }
        if (argi == 6) { v = a6; }
        if (argi == 7) { v = a7; }
        argi++;

        if (c == 100 || c == 105) {            // d i
            INT64 sv = v;
            if (sv < 0) {
                __PrintChar(45);
                if (width > 0) { width--; }
                __PrintNum(0 - sv, 10, width, pad, 0);
            } else {
                __PrintNum(v, 10, width, pad, 0);
            }
        } elif (c == 117) {                    // u
            __PrintNum(v, 10, width, pad, 0);
        } elif (c == 120) {                    // x
            __PrintNum(v, 16, width, pad, 0);
        } elif (c == 88) {                     // X
            __PrintNum(v, 16, width, pad, 1);
        } elif (c == 112) {                    // p
            __PrintStr(L"0x");
            __PrintNum(v, 16, 16, 48, 0);
        } elif (c == 115) {                    // s
            __PrintStr(v);
        } elif (c == 97) {                     // a
            __PrintAscii(v);
        } elif (c == 99) {                     // c
            __PrintChar(v);
        } elif (c == 114) {                    // r
            __PrintStatus(v);
        } elif (c == 103) {                    // g
            __PrintGuid(v);
        } else {
            __PrintChar(37);
            __PrintChar(c);
        }
    }
    __PrintFlush();
}

/* Print(L"formato", ...) como UefiLib de EDK2: hasta 8 valores */
pub func Print(CHAR16* Format, ...): UINTN {
    __PrintFormat(Format, 1, va_arg(0), va_arg(1), va_arg(2), va_arg(3),
                  va_arg(4), va_arg(5), va_arg(6), va_arg(7));
    return 0;
}
pub func conout(CHAR16* Format, ...): UINTN {
    __PrintFormat(Format, 1, va_arg(0), va_arg(1), va_arg(2), va_arg(3),
                  va_arg(4), va_arg(5), va_arg(6), va_arg(7));
    return 0;
}
/* AsciiPrint("formato", ...): igual con formato CHAR8 */
pub func AsciiPrint(CHAR8* Format, ...): UINTN {
    __PrintFormat(Format, 0, va_arg(0), va_arg(1), va_arg(2), va_arg(3),
                  va_arg(4), va_arg(5), va_arg(6), va_arg(7));
    return 0;
}

// ------------------------------------------------------------------ memoria del runtime de ASH

/*
 * Las variables struct locales (EFI_TIME Time;) y los arrays de ASH piden memoria
 * con calloc/free y abortan con exit. En UEFI no hay libc: estas tres las da el
 * firmware (el compilador usa estas en lugar de las de libc).
 */
pub func calloc(UINT64 Count, UINT64 Size): UINT64 {
    return AllocateZeroPool(Count * Size);
}

pub func free(UINT64 Buffer): UINT64 {
    if (Buffer != 0) {
        FreePool(Buffer);
    }
    return 0;
}

pub func exit(UINT64 Code): void {
    gBS->Exit(gImageHandle, Code, 0, NULL);
}
