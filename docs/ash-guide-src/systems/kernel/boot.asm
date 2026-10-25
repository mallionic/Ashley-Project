; boot.asm - multiboot header, stack and entry point of an ASH kernel
bits 32

MB_MAGIC equ 0x1BADB002
MB_FLAGS equ 0                    ; no special requests to the boot loader

section .multiboot
align 4
    dd MB_MAGIC
    dd MB_FLAGS
    dd -(MB_MAGIC + MB_FLAGS)     ; checksum: the three fields add up to 0

section .bss
align 16
stack_bottom:
    resb 16384                    ; 16 KiB of stack
stack_top:

section .text
global _start
extern kmain
_start:
    mov esp, stack_top            ; the loader does not give us a stack
    push ebx                      ; second argument: multiboot information
    push eax                      ; first argument: magic number 0x2BADB002
    call kmain                    ; kmain(magic, info), written in ASH
.hang:
    cli
    hlt
    jmp .hang
