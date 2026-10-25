repeat:
    inc r15
    cmp r15, 10
    je ret
    ;action
    jmp repeat
