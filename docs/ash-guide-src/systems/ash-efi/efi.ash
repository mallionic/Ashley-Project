/*
 * efi.ash - todo UEFI para ASH en un solo import (como #include <Uefi.h> en EDK2)
 *
 *   import "efi.ash";            // o la ruta a esta carpeta: import "../efi.ash";
 *
 * Incluye:
 *   include/Uefi.ash        tipos (UINTN, CHAR16, EFI_STATUS...), EFI_SYSTEM_TABLE,
 *                           EFI_BOOT_SERVICES, EFI_RUNTIME_SERVICES, protocolos de
 *                           consola, GOP, Loaded Image, EFI_TIME y los GUIDs
 *   Library/UefiLib.ash     gST gBS gRT gImageHandle, InitializeLib, Print, AsciiPrint,
 *                           EFI_ERROR, AllocatePool, FreePool, CopyMem, StrLen...
 */
import "include/Uefi.ash";
import "Library/UefiLib.ash";
