; boot.asm - a floppy boot sector that runs a .COM program without DOS
;   shasm -f bin -D SECTORS=n boot.asm -o boot.bin
;   disk image = boot.bin followed by the .COM file
; It loads the program at 1000:0100 and provides the few int 21h
; services that dosash uses: 02h 09h 25h 30h 35h 48h 49h 4Ah 4Ch.
bits 16
org 0x7C00

%ifndef SECTORS
%define SECTORS 32
%endif

PROG_SEG equ 0x1000
HEAP_SEG equ 0x2000

start:
    cli
    xor ax, ax
    mov ds, ax
    mov ss, ax
    mov sp, 0x7C00
    sti
    mov [drive], dl

    ; install int 20h and int 21h handlers in the interrupt vector table
    mov word [0x20*4], int20
    mov word [0x20*4+2], 0
    mov word [0x21*4], int21
    mov word [0x21*4+2], 0

    ; read SECTORS sectors, starting at LBA 1, to PROG_SEG:0100
    mov ax, PROG_SEG
    mov es, ax
    mov bx, 0x0100
    mov si, 1                   ; LBA of the next sector
    mov bp, SECTORS
.read:
    mov ax, si                  ; LBA -> cylinder, head, sector (18 sectors, 2 heads)
    xor dx, dx
    mov di, 18
    div di                      ; ax = track, dx = sector - 1
    inc dx
    mov cl, dl
    xor dx, dx
    shr ax, 1                   ; ax = cylinder, carry = head
    adc dh, 0
    mov ch, al
    mov dl, [drive]
    mov ax, 0x0201              ; read one sector
    int 0x13
    jc .fail
    add bx, 512
    inc si
    dec bp
    jnz .read

    ; a minimal PSP: offset 0 holds "int 20h", the return address of a .COM
    mov word [es:0], 0x20CD
    mov ax, PROG_SEG
    mov ds, ax
    mov es, ax
    cli
    mov ss, ax
    mov sp, 0xFFFE
    sti
    mov word [ss:0xFFFE], 0     ; "ret" from the program jumps to offset 0
    jmp PROG_SEG:0x0100

.fail:
    mov si, msg_fail
    call puts
    jmp int20

; ---------------------------------------------------------------- services
int20:                          ; program end: halt the machine
    cli
.halt:
    hlt
    jmp .halt

int21:
    cmp ah, 0x02
    je .putchar
    cmp ah, 0x09
    je .print
    cmp ah, 0x25
    je .setvec
    cmp ah, 0x35
    je .getvec
    cmp ah, 0x48
    je .alloc
    cmp ah, 0x49
    je .ok
    cmp ah, 0x4A
    je .ok
    cmp ah, 0x30
    je .version
    cmp ah, 0x4C
    je int20
.ok:
    clc
    retf 2                      ; return keeping the new flags
.putchar:
    push ax
    mov al, dl
    mov ah, 0x0E
    int 0x10
    pop ax
    jmp .ok
.print:                         ; ds:dx = text ended by '$'
    push ax
    push si
    mov si, dx
.p1:
    lodsb
    cmp al, '$'
    je .p2
    mov ah, 0x0E
    int 0x10
    jmp .p1
.p2:
    pop si
    pop ax
    jmp .ok
.setvec:                        ; al = vector, ds:dx = handler
    push es
    push bx
    xor bx, bx
    mov es, bx
    mov bl, al
    shl bx, 2
    mov [es:bx], dx
    mov [es:bx+2], ds
    pop bx
    pop es
    jmp .ok
.getvec:                        ; al = vector -> es:bx
    push ds
    xor bx, bx
    mov ds, bx
    mov bl, al
    shl bx, 2
    mov es, [bx+2]
    mov bx, [bx]
    pop ds
    jmp .ok
.alloc:                         ; bx = paragraphs -> ax = segment
    push ds
    xor ax, ax
    mov ds, ax
    mov ax, [heap]
    add [heap], bx
    pop ds
    jmp .ok
.version:
    mov ax, 0x0005              ; "DOS 5.0"
    jmp .ok

puts:
    lodsb
    test al, al
    jz .done
    mov ah, 0x0E
    int 0x10
    jmp puts
.done:
    ret

drive:    db 0
heap:     dw HEAP_SEG
msg_fail: db "disk read error", 0

times 510 - ($ - $$) db 0
dw 0xAA55
