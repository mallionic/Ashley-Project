ENTRY(_start)
SECTIONS
{
    . = 1M;                                  /* the kernel is loaded at 1 MiB */
    .text   : { *(.multiboot) *(.text) *(.ashtext) }
    .rodata : { *(.rodata) }
    .data   : { *(.data) }
    .bss    : { *(.bss) }
}
