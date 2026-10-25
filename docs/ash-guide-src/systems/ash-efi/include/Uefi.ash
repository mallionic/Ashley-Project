/*
 * Uefi.ash - tipos, tablas y protocolos de UEFI para ASH          GPLv3+
 *
 * Equivale a MdePkg/Include/Uefi.h de EDK2 (UefiBaseType.h + UefiSpec.h +
 * los protocolos de consola y graficos). Mismos nombres y misma disposicion
 * en memoria que la especificacion UEFI 2.x (x64), asi que se usa igual que en C:
 *
 *   SystemTable->ConOut->OutputString(SystemTable->ConOut, L"Hola\r\n");
 *   gBS->Stall(1000000);
 *   gRT->ResetSystem(EfiResetShutdown, EFI_SUCCESS, 0, NULL);
 *
 * Los campos que en C son punteros a funcion (EFI_TEXT_STRING, EFI_STALL...)
 * son direcciones de 64 bits; ashc --win los llama con la convencion de
 * Microsoft x64 (EFIAPI), igual que un compilador de C para UEFI.
 */

// ------------------------------------------------------------------ tipos base (UefiBaseType.h)
typedef void VOID;

typedef uint8 UINT8;
typedef int8 INT8;
typedef uint16 UINT16;
typedef int16 INT16;
typedef uint32 UINT32;
typedef int32 INT32;
typedef uint64 UINT64;
typedef int64 INT64;
typedef uint64 UINTN;
typedef int64 INTN;
typedef uint8 BOOLEAN;
typedef uint8 CHAR8;
typedef uint16 CHAR16;

typedef uint64 EFI_STATUS;
typedef uint64 EFI_HANDLE;
typedef uint64 EFI_EVENT;
typedef uint64 EFI_TPL;
typedef uint64 EFI_LBA;
typedef uint64 EFI_PHYSICAL_ADDRESS;
typedef uint64 EFI_VIRTUAL_ADDRESS;

// punteros a funcion de los protocolos (EFIAPI)
typedef uint64 EFI_TEXT_RESET;
typedef uint64 EFI_TEXT_STRING;
typedef uint64 EFI_TEXT_TEST_STRING;
typedef uint64 EFI_TEXT_QUERY_MODE;
typedef uint64 EFI_TEXT_SET_MODE;
typedef uint64 EFI_TEXT_SET_ATTRIBUTE;
typedef uint64 EFI_TEXT_CLEAR_SCREEN;
typedef uint64 EFI_TEXT_SET_CURSOR_POSITION;
typedef uint64 EFI_TEXT_ENABLE_CURSOR;
typedef uint64 EFI_INPUT_RESET;
typedef uint64 EFI_INPUT_READ_KEY;
typedef uint64 EFI_SERVICE;

define TRUE 1;
define FALSE 0;
define NULL 0;

// ------------------------------------------------------------------ EFI_STATUS

define EFI_SUCCESS 0;
define EFI_LOAD_ERROR 0x8000000000000001;
define EFI_INVALID_PARAMETER 0x8000000000000002;
define EFI_UNSUPPORTED 0x8000000000000003;
define EFI_BAD_BUFFER_SIZE 0x8000000000000004;
define EFI_BUFFER_TOO_SMALL 0x8000000000000005;
define EFI_NOT_READY 0x8000000000000006;
define EFI_DEVICE_ERROR 0x8000000000000007;
define EFI_WRITE_PROTECTED 0x8000000000000008;
define EFI_OUT_OF_RESOURCES 0x8000000000000009;
define EFI_NOT_FOUND 0x800000000000000E;
define EFI_ACCESS_DENIED 0x800000000000000F;
define EFI_TIMEOUT 0x8000000000000012;
define EFI_ABORTED 0x8000000000000015;

// ------------------------------------------------------------------ enumeraciones

// EFI_MEMORY_TYPE
define EfiReservedMemoryType 0;
define EfiLoaderCode 1;
define EfiLoaderData 2;
define EfiBootServicesCode 3;
define EfiBootServicesData 4;
define EfiRuntimeServicesCode 5;
define EfiRuntimeServicesData 6;
define EfiConventionalMemory 7;

// EFI_ALLOCATE_TYPE
define AllocateAnyPages 0;
define AllocateMaxAddress 1;
define AllocateAddress 2;

// EFI_RESET_TYPE
define EfiResetCold 0;
define EfiResetWarm 1;
define EfiResetShutdown 2;

// EFI_LOCATE_SEARCH_TYPE
define AllHandles 0;
define ByRegisterNotify 1;
define ByProtocol 2;

// EFI_TIMER_DELAY
define TimerCancel 0;
define TimerPeriodic 1;
define TimerRelative 2;

// eventos
define EVT_TIMER 0x80000000;
define EVT_NOTIFY_WAIT 0x00000100;
define TPL_APPLICATION 4;
define TPL_CALLBACK 8;
define TPL_NOTIFY 16;

// OpenProtocol
define EFI_OPEN_PROTOCOL_BY_HANDLE_PROTOCOL 0x00000001;
define EFI_OPEN_PROTOCOL_GET_PROTOCOL 0x00000002;

// atributos de texto: EFI_TEXT_ATTR(fg, bg) = fg | (bg << 4)
define EFI_BLACK 0x00;
define EFI_BLUE 0x01;
define EFI_GREEN 0x02;
define EFI_CYAN 0x03;
define EFI_RED 0x04;
define EFI_MAGENTA 0x05;
define EFI_BROWN 0x06;
define EFI_LIGHTGRAY 0x07;
define EFI_DARKGRAY 0x08;
define EFI_LIGHTBLUE 0x09;
define EFI_LIGHTGREEN 0x0A;
define EFI_LIGHTCYAN 0x0B;
define EFI_LIGHTRED 0x0C;
define EFI_LIGHTMAGENTA 0x0D;
define EFI_YELLOW 0x0E;
define EFI_WHITE 0x0F;
define EFI_BACKGROUND_BLACK 0x00;
define EFI_BACKGROUND_BLUE 0x10;
define EFI_BACKGROUND_GREEN 0x20;
define EFI_BACKGROUND_CYAN 0x30;
define EFI_BACKGROUND_RED 0x40;
define EFI_BACKGROUND_MAGENTA 0x50;
define EFI_BACKGROUND_BROWN 0x60;
define EFI_BACKGROUND_LIGHTGRAY 0x70;

// ScanCode de EFI_INPUT_KEY
define SCAN_NULL 0x00;
define SCAN_UP 0x01;
define SCAN_DOWN 0x02;
define SCAN_RIGHT 0x03;
define SCAN_LEFT 0x04;
define SCAN_HOME 0x05;
define SCAN_END 0x06;
define SCAN_INSERT 0x07;
define SCAN_DELETE 0x08;
define SCAN_PAGE_UP 0x09;
define SCAN_PAGE_DOWN 0x0A;
define SCAN_F1 0x0B;
define SCAN_F10 0x14;
define SCAN_ESC 0x17;
define CHAR_NULL 0x0000;
define CHAR_BACKSPACE 0x0008;
define CHAR_TAB 0x0009;
define CHAR_LINEFEED 0x000A;
define CHAR_CARRIAGE_RETURN 0x000D;

// formatos de pixel del GOP
define PixelRedGreenBlueReserved8BitPerColor 0;
define PixelBlueGreenRedReserved8BitPerColor 1;
define PixelBitMask 2;
define PixelBltOnly 3;

// EFI_GRAPHICS_OUTPUT_BLT_OPERATION
define EfiBltVideoFill 0;
define EfiBltVideoToBltBuffer 1;
define EfiBltBufferToVideo 2;
define EfiBltVideoToVideo 3;

// ------------------------------------------------------------------ estructuras basicas

typedef struct {
    UINT32 Data1;
    UINT16 Data2;
    UINT16 Data3;
    UINT64 Data4;                 // UINT8 Data4[8]
} EFI_GUID;

typedef struct {
    UINT64 Signature;
    UINT32 Revision;
    UINT32 HeaderSize;
    UINT32 CRC32;
    UINT32 Reserved;
} EFI_TABLE_HEADER;

typedef struct {
    UINT16 ScanCode;
    CHAR16 UnicodeChar;
} EFI_INPUT_KEY;

typedef struct {
    UINT16 Year;
    UINT8 Month;
    UINT8 Day;
    UINT8 Hour;
    UINT8 Minute;
    UINT8 Second;
    UINT8 Pad1;
    UINT32 Nanosecond;
    INT16 TimeZone;
    UINT8 Daylight;
    UINT8 Pad2;
} EFI_TIME;

// ------------------------------------------------------------------ consola de texto

typedef struct {
    INT32 MaxMode;
    INT32 Mode;
    INT32 Attribute;
    INT32 CursorColumn;
    INT32 CursorRow;
    BOOLEAN CursorVisible;
} SIMPLE_TEXT_OUTPUT_MODE;

typedef struct {
    EFI_TEXT_RESET Reset;
    EFI_TEXT_STRING OutputString;
    EFI_TEXT_TEST_STRING TestString;
    EFI_TEXT_QUERY_MODE QueryMode;
    EFI_TEXT_SET_MODE SetMode;
    EFI_TEXT_SET_ATTRIBUTE SetAttribute;
    EFI_TEXT_CLEAR_SCREEN ClearScreen;
    EFI_TEXT_SET_CURSOR_POSITION SetCursorPosition;
    EFI_TEXT_ENABLE_CURSOR EnableCursor;
    SIMPLE_TEXT_OUTPUT_MODE* Mode;
} EFI_SIMPLE_TEXT_OUTPUT_PROTOCOL;

typedef struct {
    EFI_INPUT_RESET Reset;
    EFI_INPUT_READ_KEY ReadKeyStroke;
    EFI_EVENT WaitForKey;
} EFI_SIMPLE_TEXT_INPUT_PROTOCOL;

// ------------------------------------------------------------------ Boot Services

typedef struct {
    EFI_TABLE_HEADER Hdr;
    // prioridad
    EFI_SERVICE RaiseTPL;
    EFI_SERVICE RestoreTPL;
    // memoria
    EFI_SERVICE AllocatePages;
    EFI_SERVICE FreePages;
    EFI_SERVICE GetMemoryMap;
    EFI_SERVICE AllocatePool;
    EFI_SERVICE FreePool;
    // eventos y temporizadores
    EFI_SERVICE CreateEvent;
    EFI_SERVICE SetTimer;
    EFI_SERVICE WaitForEvent;
    EFI_SERVICE SignalEvent;
    EFI_SERVICE CloseEvent;
    EFI_SERVICE CheckEvent;
    // protocolos
    EFI_SERVICE InstallProtocolInterface;
    EFI_SERVICE ReinstallProtocolInterface;
    EFI_SERVICE UninstallProtocolInterface;
    EFI_SERVICE HandleProtocol;
    UINT64 Reserved;
    EFI_SERVICE RegisterProtocolNotify;
    EFI_SERVICE LocateHandle;
    EFI_SERVICE LocateDevicePath;
    EFI_SERVICE InstallConfigurationTable;
    // imagenes
    EFI_SERVICE LoadImage;
    EFI_SERVICE StartImage;
    EFI_SERVICE Exit;
    EFI_SERVICE UnloadImage;
    EFI_SERVICE ExitBootServices;
    // varios
    EFI_SERVICE GetNextMonotonicCount;
    EFI_SERVICE Stall;
    EFI_SERVICE SetWatchdogTimer;
    // drivers
    EFI_SERVICE ConnectController;
    EFI_SERVICE DisconnectController;
    // protocolos (1.1+)
    EFI_SERVICE OpenProtocol;
    EFI_SERVICE CloseProtocol;
    EFI_SERVICE OpenProtocolInformation;
    EFI_SERVICE ProtocolsPerHandle;
    EFI_SERVICE LocateHandleBuffer;
    EFI_SERVICE LocateProtocol;
    EFI_SERVICE InstallMultipleProtocolInterfaces;
    EFI_SERVICE UninstallMultipleProtocolInterfaces;
    // 32-bit CRC
    EFI_SERVICE CalculateCrc32;
    // memoria
    EFI_SERVICE CopyMem;
    EFI_SERVICE SetMem;
    EFI_SERVICE CreateEventEx;
} EFI_BOOT_SERVICES;

// ------------------------------------------------------------------ Runtime Services

typedef struct {
    EFI_TABLE_HEADER Hdr;
    EFI_SERVICE GetTime;
    EFI_SERVICE SetTime;
    EFI_SERVICE GetWakeupTime;
    EFI_SERVICE SetWakeupTime;
    EFI_SERVICE SetVirtualAddressMap;
    EFI_SERVICE ConvertPointer;
    EFI_SERVICE GetVariable;
    EFI_SERVICE GetNextVariableName;
    EFI_SERVICE SetVariable;
    EFI_SERVICE GetNextHighMonotonicCount;
    EFI_SERVICE ResetSystem;
    EFI_SERVICE UpdateCapsule;
    EFI_SERVICE QueryCapsuleCapabilities;
    EFI_SERVICE QueryVariableInfo;
} EFI_RUNTIME_SERVICES;

// ------------------------------------------------------------------ System Table

typedef struct {
    EFI_GUID VendorGuid;
    UINT64 VendorTable;
} EFI_CONFIGURATION_TABLE;

typedef struct {
    EFI_TABLE_HEADER Hdr;
    CHAR16* FirmwareVendor;
    UINT32 FirmwareRevision;
    EFI_HANDLE ConsoleInHandle;
    EFI_SIMPLE_TEXT_INPUT_PROTOCOL* ConIn;
    EFI_HANDLE ConsoleOutHandle;
    EFI_SIMPLE_TEXT_OUTPUT_PROTOCOL* ConOut;
    EFI_HANDLE StandardErrorHandle;
    EFI_SIMPLE_TEXT_OUTPUT_PROTOCOL* StdErr;
    EFI_RUNTIME_SERVICES* RuntimeServices;
    EFI_BOOT_SERVICES* BootServices;
    UINTN NumberOfTableEntries;
    EFI_CONFIGURATION_TABLE* ConfigurationTable;
} EFI_SYSTEM_TABLE;

// ------------------------------------------------------------------ Graphics Output Protocol

typedef struct {
    UINT32 RedMask;
    UINT32 GreenMask;
    UINT32 BlueMask;
    UINT32 ReservedMask;
} EFI_PIXEL_BITMASK;

typedef struct {
    UINT32 Version;
    UINT32 HorizontalResolution;
    UINT32 VerticalResolution;
    UINT32 PixelFormat;
    EFI_PIXEL_BITMASK PixelInformation;
    UINT32 PixelsPerScanLine;
} EFI_GRAPHICS_OUTPUT_MODE_INFORMATION;

typedef struct {
    UINT32 MaxMode;
    UINT32 Mode;
    EFI_GRAPHICS_OUTPUT_MODE_INFORMATION* Info;
    UINTN SizeOfInfo;
    EFI_PHYSICAL_ADDRESS FrameBufferBase;
    UINTN FrameBufferSize;
} EFI_GRAPHICS_OUTPUT_PROTOCOL_MODE;

typedef struct {
    EFI_SERVICE QueryMode;
    EFI_SERVICE SetMode;
    EFI_SERVICE Blt;
    EFI_GRAPHICS_OUTPUT_PROTOCOL_MODE* Mode;
} EFI_GRAPHICS_OUTPUT_PROTOCOL;

// pixel del Blt (B, G, R, reservado)
typedef struct {
    UINT8 Blue;
    UINT8 Green;
    UINT8 Red;
    UINT8 Reserved;
} EFI_GRAPHICS_OUTPUT_BLT_PIXEL;

// ------------------------------------------------------------------ Loaded Image Protocol

typedef struct {
    UINT32 Revision;
    EFI_HANDLE ParentHandle;
    EFI_SYSTEM_TABLE* SystemTable;
    EFI_HANDLE DeviceHandle;
    UINT64 FilePath;
    UINT64 Reserved;
    UINT32 LoadOptionsSize;
    UINT64 LoadOptions;
    UINT64 ImageBase;
    UINT64 ImageSize;
    UINT32 ImageCodeType;
    UINT32 ImageDataType;
    EFI_SERVICE Unload;
} EFI_LOADED_IMAGE_PROTOCOL;

// ------------------------------------------------------------------ GUIDs (como gEfi...Guid de EDK2)

// 9042a9de-23dc-4a38-96fb-7aded080516a
general uint8[] gEfiGraphicsOutputProtocolGuid = {
    0xde, 0xa9, 0x42, 0x90, 0xdc, 0x23, 0x38, 0x4a, 0x96, 0xfb, 0x7a, 0xde, 0xd0, 0x80, 0x51, 0x6a
};

// 5b1b31a1-9562-11d2-8e3f-00a0c969723b
general uint8[] gEfiLoadedImageProtocolGuid = {
    0xa1, 0x31, 0x1b, 0x5b, 0x62, 0x95, 0xd2, 0x11, 0x8e, 0x3f, 0x00, 0xa0, 0xc9, 0x69, 0x72, 0x3b
};

// 387477c1-69c7-11d2-8e39-00a0c969723b
general uint8[] gEfiSimpleTextInProtocolGuid = {
    0xc1, 0x77, 0x74, 0x38, 0xc7, 0x69, 0xd2, 0x11, 0x8e, 0x39, 0x00, 0xa0, 0xc9, 0x69, 0x72, 0x3b
};

// 387477c2-69c7-11d2-8e39-00a0c969723b
general uint8[] gEfiSimpleTextOutProtocolGuid = {
    0xc2, 0x77, 0x74, 0x38, 0xc7, 0x69, 0xd2, 0x11, 0x8e, 0x39, 0x00, 0xa0, 0xc9, 0x69, 0x72, 0x3b
};

// 964e5b22-6459-11d2-8e39-00a0c969723b
general uint8[] gEfiSimpleFileSystemProtocolGuid = {
    0x22, 0x5b, 0x4e, 0x96, 0x59, 0x64, 0xd2, 0x11, 0x8e, 0x39, 0x00, 0xa0, 0xc9, 0x69, 0x72, 0x3b
};
general uint8[] gEfiSimpleNetworkProtocolGuid = {
    0xB9, 0x32, 0x98, 0xA1,
    0x25, 0xAC,
    0xD3, 0x11,
    0x9A, 0x2D,
    0x00, 0x90, 0x27, 0x3F, 0xC1, 0x4D
};