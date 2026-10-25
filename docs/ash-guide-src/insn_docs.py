"""
Descriptions of the x86 instructions documented in Appendix A.
name: (title, description, flags)
The forms and encodings are generated from the shasm instruction database.
"""

GP = 'General-purpose'
INSN = {}


def add(group, name, title, desc, flags='None.'):
    INSN[name] = (group, title, desc, flags)


A = 'OF, SF, ZF, AF, CF and PF are set according to the result.'
LOGIC = 'OF and CF are cleared; SF, ZF and PF are set according to the result; AF is undefined.'

# ---------------------------------------------------------------- data transfer
add(GP, 'MOV', 'Move',
    'Copies the second operand (source) to the first operand (destination). The source may be an immediate, a '
    'register or memory; the destination a register or memory; both cannot be memory. Forms with segment, control '
    'and debug registers move values to and from those registers (the latter two are privileged). In 64-bit mode '
    '`mov r64, imm64` is the only instruction with a 64-bit immediate, and writing a 32-bit register clears the '
    'upper half of the 64-bit register. ASH uses `mov` for almost every transfer between variable homes.')
add(GP, 'MOVZX', 'Move with Zero-Extend',
    'Copies a byte or word source into a larger register and fills the remaining bits with zeros. ASH uses it to '
    'widen unsigned 8-bit and 16-bit values.')
add(GP, 'MOVSX', 'Move with Sign-Extend',
    'Copies a byte or word source into a larger register and fills the remaining bits with copies of the sign bit. '
    'ASH uses it to widen `int8` and `int16` values.')
add(GP, 'MOVSXD', 'Move Doubleword with Sign-Extend',
    'Sign-extends a 32-bit source to 64 bits. Used by ASH when an `int32` value is widened.')
add(GP, 'XCHG', 'Exchange',
    'Exchanges the contents of the two operands. With a memory operand the exchange is atomic (the processor '
    'asserts a lock implicitly), which makes `xchg` a building block of spin locks. `xchg eax, eax` with the '
    'one-byte encoding `90` is `nop`.')
add(GP, 'XADD', 'Exchange and Add',
    'Stores the sum of the operands in the destination and the original destination in the source. With a `lock` '
    'prefix it is an atomic fetch-and-add.', A)
add(GP, 'CMPXCHG', 'Compare and Exchange',
    'Compares the accumulator (`al`, `ax`, `eax`, `rax`) with the destination. If they are equal, the source is '
    'stored in the destination and ZF is set; otherwise the destination is loaded into the accumulator and ZF is '
    'cleared. With `lock`, the basis of lock-free algorithms.', A)
add(GP, 'CMPXCHG8B', 'Compare and Exchange 8 Bytes',
    'Compares `edx:eax` with a 64-bit memory operand; if equal stores `ecx:ebx` there, otherwise loads the operand '
    'into `edx:eax`. `cmpxchg16b` is the 128-bit form in 64-bit mode.', 'ZF is set if the values were equal; other flags unaffected.')
add(GP, 'BSWAP', 'Byte Swap',
    'Reverses the order of the bytes of a 32-bit or 64-bit register, converting between little-endian and '
    'big-endian representations.')
add(GP, 'MOVBE', 'Move Data After Swapping Bytes',
    'Loads from or stores to memory while reversing the byte order, combining `mov` and `bswap`.')
add(GP, 'PUSH', 'Push onto the Stack',
    'Decrements the stack pointer by the operand size and stores the operand at the new top of the stack. In 64-bit '
    'mode pushes are 8 bytes (immediates are sign-extended). ASH prologues start with `push rbp`.')
add(GP, 'POP', 'Pop from the Stack',
    'Loads the value at the top of the stack into the operand and increments the stack pointer.')
add(GP, 'PUSHF', 'Push Flags',
    'Pushes the flags register (`pushfq` in 64-bit mode).')
add(GP, 'POPF', 'Pop Flags',
    'Pops a value into the flags register; which flags may change depends on the privilege level.', 'All modifiable flags are loaded from the stack.')
add(GP, 'LEA', 'Load Effective Address',
    'Computes the address of the memory operand and stores the address (not the memory contents) in the '
    'register. Because it performs `base + index*scale + displacement` without touching the flags, it is also used '
    'for arithmetic. ASH uses `lea reg, [rip+disp]` to obtain the address of strings, globals and functions.')
add(GP, 'CBW', 'Convert Byte to Word', 'Sign-extends `al` into `ax`. `cwde` sign-extends `ax` into `eax` and `cdqe` `eax` into `rax`.')
add(GP, 'CWD', 'Convert Word to Doubleword', 'Sign-extends `ax` into `dx:ax`. `cdq` sign-extends `eax` into `edx:eax` and `cqo` `rax` into `rdx:rax`.')
add(GP, 'CQO', 'Convert Quadword to Octoword',
    'Sign-extends `rax` into `rdx:rax`. ASH emits `cqo` before every signed 64-bit division.')
add(GP, 'CDQ', 'Convert Doubleword to Quadword', 'Sign-extends `eax` into `edx:eax`, before a signed 32-bit division.')
add(GP, 'CDQE', 'Convert Doubleword to Quadword in RAX', 'Sign-extends `eax` into `rax`.')
add(GP, 'CWDE', 'Convert Word to Doubleword in EAX', 'Sign-extends `ax` into `eax`.')
add(GP, 'XLATB', 'Table Look-up Translation', 'Replaces `al` with the byte at `[rbx + al]` (`[ebx+al]`, `[bx+al]`).')

# ---------------------------------------------------------------- arithmetic
add(GP, 'ADD', 'Add', 'Adds the source to the destination and stores the result in the destination. The same '
    'instruction serves signed and unsigned integers: OF signals signed overflow and CF unsigned carry.', A)
add(GP, 'ADC', 'Add with Carry', 'Adds the source and the carry flag to the destination. Used to add numbers wider '
    'than a register: `add` the low parts, then `adc` the high parts.', A)
add(GP, 'SUB', 'Subtract', 'Subtracts the source from the destination.', A)
add(GP, 'SBB', 'Subtract with Borrow', 'Subtracts the source and the carry flag from the destination; the '
    'counterpart of `adc` for multi-word subtraction. `sbb reg, reg` produces 0 or -1 from CF.', A)
add(GP, 'INC', 'Increment by 1', 'Adds 1 to the operand without changing the carry flag. ASHIR `inc x` becomes this '
    'or an `add`.', 'CF is not affected; OF, SF, ZF, AF and PF are set according to the result.')
add(GP, 'DEC', 'Decrement by 1', 'Subtracts 1 from the operand without changing the carry flag.',
    'CF is not affected; OF, SF, ZF, AF and PF are set according to the result.')
add(GP, 'NEG', "Two's Complement Negation", 'Replaces the operand with 0 minus the operand.',
    'CF is cleared if the operand was 0 and set otherwise; OF, SF, ZF, AF and PF according to the result.')
add(GP, 'CMP', 'Compare Two Operands', 'Subtracts the second operand from the first, sets the flags like `sub` and '
    'discards the result. Followed by a conditional jump, `setcc` or `cmovcc`. Every ASH condition becomes a `cmp`.', A)
add(GP, 'MUL', 'Unsigned Multiply', 'Multiplies the accumulator by the operand as unsigned integers; the double-width '
    'product is stored in `ah:al`, `dx:ax`, `edx:eax` or `rdx:rax`.',
    'OF and CF are set if the upper half of the product is not zero; SF, ZF, AF and PF are undefined.')
add(GP, 'IMUL', 'Signed Multiply', 'Multiplies signed integers. The one-operand form works like `mul`; the two-operand '
    'form `imul r, r/m` and the three-operand form `imul r, r/m, imm` keep only the low half of the product, which is '
    'the same for signed and unsigned operands. ASH uses the two and three-operand forms for `*`.',
    'OF and CF are set when the result had to be truncated; SF, ZF, AF and PF are undefined.')
add(GP, 'DIV', 'Unsigned Divide', 'Divides `ax`, `dx:ax`, `edx:eax` or `rdx:rax` by the operand as unsigned integers; '
    'the quotient goes to `al`/`ax`/`eax`/`rax` and the remainder to `ah`/`dx`/`edx`/`rdx`. Division by zero or a '
    'quotient too large raises the divide error (#DE), reported by Linux as SIGFPE.', 'All arithmetic flags are undefined.')
add(GP, 'IDIV', 'Signed Divide', 'Signed counterpart of `div`; the quotient is truncated toward zero and the remainder '
    'has the sign of the dividend. ASH emits `cqo` followed by `idiv` for `/` and `%`.', 'All arithmetic flags are undefined.')

# ---------------------------------------------------------------- logic and shifts
add(GP, 'AND', 'Logical AND', 'Bitwise AND of the operands, stored in the destination.', LOGIC)
add(GP, 'OR', 'Logical Inclusive OR', 'Bitwise OR of the operands.', LOGIC)
add(GP, 'XOR', 'Logical Exclusive OR', 'Bitwise exclusive OR of the operands. `xor reg, reg` is the idiomatic way to '
    'set a register to zero; ASH uses `xor eax, eax` for `return 0`.', LOGIC)
add(GP, 'NOT', "One's Complement Negation", 'Inverts every bit of the operand.')
add(GP, 'TEST', 'Logical Compare', 'Computes the AND of the operands, sets the flags and discards the result. '
    '`test reg, reg` checks a register for zero or negative.', LOGIC)
add(GP, 'SHL', 'Shift Left', 'Shifts the destination left by 1, by `cl` or by an immediate; zeros enter from the '
    'right and the last bit shifted out goes to CF. The count is masked to 5 bits (6 for 64-bit operands). `sal` '
    'is the same instruction.', 'CF receives the last bit shifted out; OF is defined only for 1-bit shifts; SF, ZF and PF according to the result.')
add(GP, 'SAL', 'Shift Arithmetic Left', 'Synonym of `shl`.', 'As for SHL.')
add(GP, 'SHR', 'Shift Logical Right', 'Shifts right, filling with zeros: unsigned division by a power of two. ASH '
    'uses it for `>>` on unsigned operands.', 'As for SHL.')
add(GP, 'SAR', 'Shift Arithmetic Right', 'Shifts right, filling with copies of the sign bit: signed division by a '
    'power of two rounded toward minus infinity. ASH uses it for `>>` on signed operands.', 'As for SHL.')
add(GP, 'ROL', 'Rotate Left', 'Rotates the bits of the destination left; bits leaving on the left enter on the right.',
    'CF receives the last bit rotated; OF is defined only for 1-bit rotations; other flags unaffected.')
add(GP, 'ROR', 'Rotate Right', 'Rotates the bits of the destination right.', 'As for ROL.')
add(GP, 'RCL', 'Rotate Through Carry Left', 'Rotates left through the carry flag (a rotation of N+1 bits).', 'As for ROL.')
add(GP, 'RCR', 'Rotate Through Carry Right', 'Rotates right through the carry flag.', 'As for ROL.')
add(GP, 'SHLD', 'Double Precision Shift Left', 'Shifts the destination left, filling the vacated bits from the most '
    'significant bits of the source register.', 'CF receives the last bit shifted out; SF, ZF, PF according to the result.')
add(GP, 'SHRD', 'Double Precision Shift Right', 'Shifts the destination right, filling from the low bits of the source.',
    'As for SHLD.')

# ---------------------------------------------------------------- bits
add(GP, 'BT', 'Bit Test', 'Copies the selected bit of the first operand into CF.', 'CF receives the bit; other flags undefined or unaffected.')
add(GP, 'BTS', 'Bit Test and Set', 'Copies the selected bit into CF and sets it to 1.', 'As for BT.')
add(GP, 'BTR', 'Bit Test and Reset', 'Copies the selected bit into CF and clears it.', 'As for BT.')
add(GP, 'BTC', 'Bit Test and Complement', 'Copies the selected bit into CF and inverts it.', 'As for BT.')
add(GP, 'BSF', 'Bit Scan Forward', 'Stores the index of the lowest set bit of the source. If the source is zero, ZF '
    'is set and the destination is undefined.', 'ZF is set if the source is zero; other flags undefined.')
add(GP, 'BSR', 'Bit Scan Reverse', 'Stores the index of the highest set bit of the source (the integer base-2 '
    'logarithm, as computed by `ilog2` in `<math>`).', 'As for BSF.')
add(GP, 'POPCNT', 'Population Count', 'Counts the bits set to 1 in the source (the operation of `popcount` in '
    '`<math>`, in one instruction).', 'ZF is set if the source is zero; OF, SF, AF, CF, PF are cleared.')
add(GP, 'LZCNT', 'Count Leading Zero Bits', 'Counts the zero bits above the highest set bit. On processors without '
    'LZCNT the encoding executes as `bsr`.', 'CF is set if the source is zero, ZF if the result is zero.')
add(GP, 'TZCNT', 'Count Trailing Zero Bits', 'Counts the zero bits below the lowest set bit.', 'CF is set if the source is zero, ZF if the result is zero.')
add(GP, 'CRC32', 'Accumulate CRC32 Value', 'Updates a CRC-32C (Castagnoli) checksum in the destination with the '
    'source byte, word, doubleword or quadword.')

# ---------------------------------------------------------------- control transfer
add(GP, 'JMP', 'Jump', 'Transfers control to the target: relative (`jmp rel8`, `jmp rel32`), indirect through a '
    'register or memory, or far. ASH uses `jmp` for the end of loop bodies and for `else` branches.')
add(GP, 'JCC', 'Jump if Condition Is Met',
    'Jumps if the condition holds, otherwise continues with the next instruction. The conditions test the flags: '
    '`jo`/`jno` (OF), `jb`/`jc`/`jnae` and `jae`/`jnb`/`jnc` (CF), `je`/`jz` and `jne`/`jnz` (ZF), `jbe`/`jna` and '
    '`ja`/`jnbe` (CF or ZF), `js`/`jns` (SF), `jp`/`jpe` and `jnp`/`jpo` (PF), `jl`/`jnge` and `jge`/`jnl` (SF != OF), '
    '`jle`/`jng` and `jg`/`jnle` (ZF or SF != OF). The short form takes an 8-bit displacement, the near form a '
    '32-bit one. ASH uses the signed forms for signed operands and the unsigned forms (`jb`, `ja` ...) for unsigned ones.')
add(GP, 'JCXZ', 'Jump if CX Is Zero', 'Jumps if `cx` (`jecxz`: `ecx`, `jrcxz`: `rcx`) is zero, without testing flags.')
add(GP, 'LOOP', 'Loop According to ECX Counter', 'Decrements `rcx` (`ecx`, `cx`) and jumps if it is not zero. '
    '`loope`/`loopz` and `loopne`/`loopnz` also test ZF.')
add(GP, 'CALL', 'Call Procedure', 'Pushes the address of the next instruction and jumps to the target. ASH calls '
    'functions with `call rel32` (resolved by the object writer or by a relocation) and calls through pointers with '
    '`call rax`.')
add(GP, 'RET', 'Return from Procedure', 'Pops the return address and jumps to it. `ret imm16` also removes *imm16* '
    'bytes of arguments (the callee-cleanup conventions).')
add(GP, 'ENTER', 'Make Stack Frame', 'Creates a stack frame: `push rbp; mov rbp, rsp; sub rsp, n`, with optional '
    'nesting levels. Slower than the explicit sequence, which is what ASH emits.')
add(GP, 'LEAVE', 'High Level Procedure Exit', 'Releases a frame: `mov rsp, rbp; pop rbp`. Used by ASH epilogues.')
add(GP, 'INT', 'Call to Interrupt Procedure', 'Raises software interrupt *n*: in real mode it calls the BIOS or DOS '
    'service in the interrupt vector table (`int 0x10` video, `int 0x13` disk, `int 0x21` DOS); in 32-bit Linux '
    '`int 0x80` is the system call interface. `int3` (`CC`) is the breakpoint instruction.')
add(GP, 'INT3', 'Breakpoint', 'The one-byte breakpoint `CC`, used by debuggers.')
add(GP, 'INTO', 'Interrupt on Overflow', 'Raises interrupt 4 if OF is set. Not valid in 64-bit mode.')
add(GP, 'IRET', 'Interrupt Return', 'Returns from an interrupt or exception handler, restoring the instruction '
    'pointer, code segment and flags (and the stack in 64-bit mode, `iretq`). The last instruction of a `@naked` '
    'interrupt handler.')
add(GP, 'SETCC', 'Set Byte on Condition', 'Stores 1 in the byte operand if the condition holds and 0 otherwise. The '
    'conditions are those of `jcc`. A way to turn a comparison into a value without branching.')
add(GP, 'CMOVCC', 'Conditional Move', 'Moves the source into the destination register if the condition holds. The '
    'conditions are those of `jcc`. Used to compute minimum and maximum without branches.')

# ---------------------------------------------------------------- flags and misc
add(GP, 'CLC', 'Clear Carry Flag', 'Sets CF to 0.', 'CF = 0.')
add(GP, 'STC', 'Set Carry Flag', 'Sets CF to 1.', 'CF = 1.')
add(GP, 'CMC', 'Complement Carry Flag', 'Inverts CF.', 'CF is inverted.')
add(GP, 'CLD', 'Clear Direction Flag', 'Clears DF: string instructions advance (the state required by the System V '
    'ABI at function boundaries).', 'DF = 0.')
add(GP, 'STD', 'Set Direction Flag', 'Sets DF: string instructions go backwards.', 'DF = 1.')
add(GP, 'CLI', 'Clear Interrupt Flag', 'Disables maskable hardware interrupts (privileged in protected mode). Boot code '
    'typically starts with `cli` while it sets up segments and the stack.', 'IF = 0.')
add(GP, 'STI', 'Set Interrupt Flag', 'Enables maskable interrupts after the next instruction.', 'IF = 1.')
add(GP, 'LAHF', 'Load Status Flags into AH', 'Copies SF, ZF, AF, PF and CF into `ah`.')
add(GP, 'SAHF', 'Store AH into Flags', 'Loads SF, ZF, AF, PF and CF from `ah`.', 'SF, ZF, AF, PF and CF are loaded from AH.')
add(GP, 'NOP', 'No Operation', 'Does nothing. Besides the one-byte `90`, multi-byte forms (`0F 1F /0`) exist for '
    'alignment padding; `shasm` pads code with `nop` in `align`.')
add(GP, 'PAUSE', 'Spin Loop Hint', 'Tells the processor that the code is a spin-wait loop, saving power and avoiding '
    'memory-order penalties.')
add(GP, 'HLT', 'Halt', 'Stops the processor until the next interrupt. Privileged. A boot loader ends with a `hlt` loop.')
add(GP, 'UD2', 'Undefined Instruction', 'Raises the invalid-opcode exception on purpose; used to mark unreachable code.')
add(GP, 'CPUID', 'CPU Identification', 'Returns processor identification and feature information in `eax`, `ebx`, '
    '`ecx` and `edx` for the leaf selected by `eax` (and sub-leaf in `ecx`).')
add(GP, 'RDTSC', 'Read Time-Stamp Counter', 'Loads the 64-bit time-stamp counter into `edx:eax`. Used in [[ex-asm-string]] '
    'to measure elapsed cycles.')
add(GP, 'RDTSCP', 'Read Time-Stamp Counter and Processor ID', 'Like `rdtsc`, waiting for previous instructions, and '
    'also loads `IA32_TSC_AUX` into `ecx`.')
add(GP, 'RDRAND', 'Read Random Number', 'Loads a hardware random number into the register.', 'CF = 1 if a value was available; other arithmetic flags cleared.')
add(GP, 'RDSEED', 'Read Random Seed', 'Loads a random seed from the hardware entropy source.', 'As for RDRAND.')
add(GP, 'MFENCE', 'Memory Fence', 'Orders all loads and stores issued before it with respect to those after it.')
add(GP, 'LFENCE', 'Load Fence', 'Orders loads, and serializes instruction execution with respect to prior instructions.')
add(GP, 'SFENCE', 'Store Fence', 'Orders stores.')
add(GP, 'CLFLUSH', 'Flush Cache Line', 'Writes back and invalidates the cache line containing the address.')
add(GP, 'PREFETCHT0', 'Prefetch Data into Caches', 'Hints the processor to load the cache line into all levels of '
    'the cache (`prefetcht1`, `prefetcht2` and `prefetchnta` select other levels).')

# ---------------------------------------------------------------- strings
add(GP, 'MOVSB', 'Move String Byte', 'Copies the byte at `[rsi]` to `[rdi]` and advances both by 1 (backwards if DF '
    'is set). `movsw`, `movsd` and `movsq` move 2, 4 and 8 bytes. With `rep`, repeats `rcx` times: `rep movsb` is '
    'a complete `memcpy`.')
add(GP, 'STOSB', 'Store String Byte', 'Stores `al` at `[rdi]` and advances `rdi`. With `rep`, fills `rcx` bytes: a '
    'complete `memset`. `stosw`, `stosd`, `stosq` store `ax`, `eax`, `rax`.')
add(GP, 'LODSB', 'Load String Byte', 'Loads the byte at `[rsi]` into `al` and advances `rsi`. Used in the boot sector '
    'of [[ex-shasm-boot]] to walk a string.')
add(GP, 'SCASB', 'Scan String Byte', 'Compares `al` with the byte at `[rdi]` and advances `rdi`. With `repne`, '
    'searches for a byte: the core of `strlen` and `memchr`.', A)
add(GP, 'CMPSB', 'Compare String Bytes', 'Compares `[rsi]` with `[rdi]` and advances both. With `repe`, compares two '
    'memory blocks: the core of `memcmp`.', A)

# ---------------------------------------------------------------- I/O
add(GP, 'IN', 'Input from Port', 'Reads a byte, word or doubleword from an I/O port, given as an immediate (0-255) or '
    'in `dx`, into the accumulator. Privileged unless the I/O permission allows it.')
add(GP, 'OUT', 'Output to Port', 'Writes the accumulator to an I/O port. Used by kernels to program devices such as '
    'the PIC, the PIT and the serial port.')
add(GP, 'INSB', 'Input String from Port', 'Reads from port `dx` into `[rdi]` and advances `rdi`; with `rep`, reads a block.')
add(GP, 'OUTSB', 'Output String to Port', 'Writes `[rsi]` to port `dx` and advances `rsi`.')

# ---------------------------------------------------------------- system
SYS = 'System'
add(SYS, 'SYSCALL', 'Fast System Call', 'Enters the operating system kernel in 64-bit mode: the return address is '
    'saved in `rcx` and the flags in `r11`. Linux takes the system call number in `rax` and the arguments in `rdi`, '
    '`rsi`, `rdx`, `r10`, `r8`, `r9`, and returns the result in `rax`. Every function of `<syscalls>` ends in a `syscall`.')
add(SYS, 'SYSRET', 'Return from Fast System Call', 'Returns from the kernel to user mode, restoring `rip` from `rcx` '
    'and the flags from `r11`. Privileged.')
add(SYS, 'SYSENTER', 'Fast System Call (32-bit)', 'Fast entry to the kernel in 32-bit mode, using model-specific '
    'registers for the target.')
add(SYS, 'SYSEXIT', 'Fast Return from SYSENTER', 'Returns to user mode after `sysenter`. Privileged.')
add(SYS, 'LGDT', 'Load Global Descriptor Table Register', 'Loads the base and limit of the GDT from memory. Executed by '
    'boot loaders before entering protected or long mode. Privileged.')
add(SYS, 'LIDT', 'Load Interrupt Descriptor Table Register', 'Loads the base and limit of the IDT. Privileged.')
add(SYS, 'SGDT', 'Store Global Descriptor Table Register', 'Stores the GDT base and limit to memory.')
add(SYS, 'SIDT', 'Store Interrupt Descriptor Table Register', 'Stores the IDT base and limit to memory.')
add(SYS, 'LLDT', 'Load Local Descriptor Table Register', 'Loads the LDT selector. Privileged.')
add(SYS, 'LTR', 'Load Task Register', 'Loads the task register (TSS selector). Privileged.')
add(SYS, 'INVLPG', 'Invalidate TLB Entry', 'Invalidates the translation lookaside buffer entry of a page after the '
    'kernel changes its page table entry. Privileged.')
add(SYS, 'WBINVD', 'Write Back and Invalidate Cache', 'Writes back all modified cache lines and invalidates the caches. Privileged.')
add(SYS, 'RDMSR', 'Read from Model Specific Register', 'Reads the MSR selected by `ecx` into `edx:eax`. Privileged.')
add(SYS, 'WRMSR', 'Write to Model Specific Register', 'Writes `edx:eax` to the MSR selected by `ecx`. Privileged.')
add(SYS, 'RDPMC', 'Read Performance-Monitoring Counter', 'Reads the counter selected by `ecx` into `edx:eax`.')
add(SYS, 'CLTS', 'Clear Task-Switched Flag in CR0', 'Clears CR0.TS. Privileged.')
add(SYS, 'SWAPGS', 'Swap GS Base Register', 'Exchanges the GS base with the `IA32_KERNEL_GS_BASE` MSR, at kernel entry '
    'and exit in 64-bit mode. Privileged.')
add(SYS, 'LMSW', 'Load Machine Status Word', 'Loads the low four bits of CR0 (legacy way to enter protected mode).')
add(SYS, 'SMSW', 'Store Machine Status Word', 'Stores the low bits of CR0.')
add(SYS, 'ARPL', 'Adjust RPL Field of Segment Selector', 'Raises the requested privilege level of a selector. Not valid in 64-bit mode.')
add(SYS, 'LAR', 'Load Access Rights Byte', 'Loads the access rights of a segment descriptor.', 'ZF is set if the selector is valid.')
add(SYS, 'LSL', 'Load Segment Limit', 'Loads the limit of a segment descriptor.', 'ZF is set if the selector is valid.')
add(SYS, 'VERR', 'Verify a Segment for Reading', 'Sets ZF if the segment can be read at the current privilege level.', 'ZF as described.')
add(SYS, 'VERW', 'Verify a Segment for Writing', 'Sets ZF if the segment can be written.', 'ZF as described.')

# ---------------------------------------------------------------- x87
FPU = 'x87 floating point'
add(FPU, 'FLD', 'Load Floating-Point Value', 'Pushes a 32, 64 or 80-bit floating-point value (or `st(i)`) onto the x87 register stack.')
add(FPU, 'FST', 'Store Floating-Point Value', 'Stores `st(0)` to memory or to `st(i)`. `fstp` also pops the stack.')
add(FPU, 'FSTP', 'Store Floating-Point Value and Pop', 'Stores `st(0)` and pops the register stack.')
add(FPU, 'FILD', 'Load Integer', 'Converts a 16, 32 or 64-bit integer to floating point and pushes it.')
add(FPU, 'FISTP', 'Store Integer and Pop', 'Converts `st(0)` to an integer with the current rounding mode, stores it and pops.')
add(FPU, 'FADD', 'Add', 'Adds floating-point values: `st(0) += m`, `st(0) += st(i)` or `st(i) += st(0)`; `faddp` pops.')
add(FPU, 'FSUB', 'Subtract', 'Subtracts floating-point values; `fsubr` reverses the operands.')
add(FPU, 'FMUL', 'Multiply', 'Multiplies floating-point values.')
add(FPU, 'FDIV', 'Divide', 'Divides floating-point values; `fdivr` reverses the operands.')
add(FPU, 'FCHS', 'Change Sign', 'Negates `st(0)`.')
add(FPU, 'FABS', 'Absolute Value', 'Clears the sign of `st(0)`.')
add(FPU, 'FSQRT', 'Square Root', 'Replaces `st(0)` with its square root.')
add(FPU, 'FSIN', 'Sine', 'Replaces `st(0)` (radians) with its sine. Compare with the FPU-free `fx_sin` of `<math>`.')
add(FPU, 'FCOS', 'Cosine', 'Replaces `st(0)` with its cosine.')
add(FPU, 'FXCH', 'Exchange Register Contents', 'Exchanges `st(0)` and `st(i)`.')
add(FPU, 'FCOM', 'Compare Floating-Point Values', 'Compares `st(0)` with the operand and sets the x87 condition codes.')
add(FPU, 'FCOMI', 'Compare and Set EFLAGS', 'Compares `st(0)` with `st(i)` and sets ZF, PF and CF directly, so that '
    'ordinary conditional jumps can follow.', 'ZF, PF, CF as for an unsigned comparison.')
add(FPU, 'FINIT', 'Initialize FPU', 'Resets the x87 unit (`fninit` without waiting for pending exceptions).')
add(FPU, 'FLDZ', 'Load +0.0', 'Pushes 0.0. `fld1` pushes 1.0, `fldpi` pushes pi.')
add(FPU, 'FLD1', 'Load +1.0', 'Pushes 1.0.')
add(FPU, 'FLDPI', 'Load Pi', 'Pushes pi.')
add(FPU, 'FWAIT', 'Wait', 'Waits for pending x87 exceptions (`wait`).')

# ---------------------------------------------------------------- SSE / SSE2
SSE = 'SSE and SSE2'
add(SSE, 'MOVAPS', 'Move Aligned Packed Single', 'Moves 128 bits between XMM registers or aligned memory.')
add(SSE, 'MOVUPS', 'Move Unaligned Packed Single', 'Moves 128 bits between XMM registers or memory of any alignment.')
add(SSE, 'MOVDQA', 'Move Aligned Double Quadword', 'Moves 128 bits of integer data; memory must be 16-byte aligned.')
add(SSE, 'MOVDQU', 'Move Unaligned Double Quadword', 'Moves 128 bits of integer data at any alignment.')
add(SSE, 'MOVSS', 'Move Scalar Single', 'Moves one single-precision value.')
add(SSE, 'MOVSD', 'Move Scalar Double', 'Moves one double-precision value (the same mnemonic also names the string '
    'instruction `movsd` without operands).')
add(SSE, 'MOVD', 'Move Doubleword', 'Moves 32 bits between a general register or memory and an XMM or MMX register.')
add(SSE, 'MOVQ', 'Move Quadword', 'Moves 64 bits between a general register or memory and an XMM or MMX register. '
    '`movq rax, xmm0` is how the bit pattern of a `double` reaches an integer register.')
add(SSE, 'ADDSS', 'Add Scalar Single', 'Adds the low single-precision values.')
add(SSE, 'ADDSD', 'Add Scalar Double', 'Adds the low double-precision values.')
add(SSE, 'SUBSD', 'Subtract Scalar Double', 'Subtracts the low double-precision values.')
add(SSE, 'MULSD', 'Multiply Scalar Double', 'Multiplies the low double-precision values.')
add(SSE, 'DIVSD', 'Divide Scalar Double', 'Divides the low double-precision values.')
add(SSE, 'SQRTSD', 'Square Root Scalar Double', 'Computes the square root of the low double-precision value.')
add(SSE, 'ADDPS', 'Add Packed Single', 'Adds four single-precision values in parallel.')
add(SSE, 'MULPS', 'Multiply Packed Single', 'Multiplies four single-precision values in parallel.')
add(SSE, 'CVTSI2SD', 'Convert Integer to Scalar Double', 'Converts a 32 or 64-bit integer to double precision.')
add(SSE, 'CVTTSD2SI', 'Convert with Truncation Scalar Double to Integer', 'Converts a double to an integer, '
    'truncating toward zero (the C cast).')
add(SSE, 'CVTSD2SS', 'Convert Scalar Double to Scalar Single', 'Narrows a double to single precision.')
add(SSE, 'CVTSS2SD', 'Convert Scalar Single to Scalar Double', 'Widens a single-precision value to double.')
add(SSE, 'COMISD', 'Compare Scalar Ordered Double', 'Compares the low doubles and sets ZF, PF and CF; raises an '
    'exception for NaN operands. `ucomisd` does not.', 'ZF, PF, CF as for an unsigned comparison; OF, SF, AF cleared.')
add(SSE, 'UCOMISD', 'Unordered Compare Scalar Double', 'Compares the low doubles and sets ZF, PF and CF (PF = 1 for '
    'unordered results).', 'As for COMISD.')
add(SSE, 'ANDPS', 'Bitwise AND of Packed Single', 'Bitwise AND of 128 bits; used to clear sign bits (absolute value).')
add(SSE, 'ORPS', 'Bitwise OR of Packed Single', 'Bitwise OR of 128 bits.')
add(SSE, 'XORPS', 'Bitwise XOR of Packed Single', 'Bitwise XOR of 128 bits; `xorps xmm0, xmm0` zeroes a register.')
add(SSE, 'PXOR', 'Packed Logical XOR', 'Bitwise XOR of 128 integer bits.')
add(SSE, 'PAND', 'Packed Logical AND', 'Bitwise AND of 128 integer bits.')
add(SSE, 'POR', 'Packed Logical OR', 'Bitwise OR of 128 integer bits.')
add(SSE, 'PADDB', 'Packed Add Bytes', 'Adds sixteen bytes in parallel (wrap-around). `paddw`, `paddd`, `paddq` work on '
    'words, doublewords and quadwords.')
add(SSE, 'PADDD', 'Packed Add Doublewords', 'Adds four doublewords in parallel.')
add(SSE, 'PADDQ', 'Packed Add Quadwords', 'Adds two quadwords in parallel.')
add(SSE, 'PSUBD', 'Packed Subtract Doublewords', 'Subtracts four doublewords in parallel.')
add(SSE, 'PCMPEQB', 'Packed Compare for Equal Bytes', 'Compares sixteen bytes and sets each result byte to all ones '
    'where equal; combined with `pmovmskb`, finds a byte in 16 at a time (vectorized `strlen`).')
add(SSE, 'PMOVMSKB', 'Move Byte Mask', 'Collects the top bit of each of the sixteen bytes into a 16-bit integer mask.')
add(SSE, 'PSHUFD', 'Shuffle Packed Doublewords', 'Rearranges the four doublewords according to an immediate selector.')
add(SSE, 'SHUFPS', 'Shuffle Packed Single', 'Selects two single-precision values from each operand according to an immediate.')
add(SSE, 'UNPCKLPS', 'Unpack and Interleave Low Packed Single', 'Interleaves the low halves of two registers.')
add(SSE, 'LDMXCSR', 'Load MXCSR Register', 'Loads the SSE control and status register (rounding, exception masks).')
add(SSE, 'STMXCSR', 'Store MXCSR Register', 'Stores the SSE control and status register.')
add(SSE, 'EMMS', 'Empty MMX State', 'Marks the x87 registers as empty after MMX code.')
