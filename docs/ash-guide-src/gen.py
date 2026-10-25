"""
Generators for the reference parts of the ASH guide.  Each public function
returns text in the markup of build_guide.py; it is expanded where a chapter
writes  @gen <function> <argument>.
"""
import os
import re

import config
import nlib_docs as D

NLIB = os.path.join(config.PROJECT, 'nlib')


# ------------------------------------------------------------------ nlib parsing
def _strip_comments(src):
    src = re.sub(r'/\*.*?\*/', '', src, flags=re.S)
    out = []
    for line in src.split('\n'):
        i = line.find('//')
        if i >= 0 and line[:i].count('"') % 2 == 0:
            line = line[:i].rstrip()
        out.append(line.rstrip())
    return '\n'.join(out)


def _functions(lib):
    """name -> (signature, full source of the function without comments)"""
    with open(os.path.join(NLIB, lib + '.ash'), encoding='utf-8') as f:
        src = _strip_comments(f.read())
    res = {}
    for m in re.finditer(r'^(pub )?func [^\n]*\{', src, re.M):
        start = m.start()
        depth, i = 0, src.index('{', start)
        while i < len(src):
            if src[i] == '{':
                depth += 1
            elif src[i] == '}':
                depth -= 1
                if depth == 0:
                    break
            i += 1
        body = src[start:i + 1]
        body = re.sub(r'\n\s*\n', '\n', body)
        name = re.match(r'(?:pub )?func (\w+)', body).group(1)
        sig = body.split('{', 1)[0].strip()
        res[name] = (sig, body)
    return res


def _defines(lib):
    with open(os.path.join(NLIB, lib + '.ash'), encoding='utf-8') as f:
        src = _strip_comments(f.read())
    return re.findall(r'^\s*define\s+(\w+)\s+([^;\n]+);?', src, re.M)


def _indent(body, n=4):
    return '\n'.join((' ' * n + l) if l.strip() else '' for l in body.split('\n'))


# ------------------------------------------------------------------ per function
def _entry(lib, name, sig, impl, doc, label_prefix, run_opts='', show_impl=True):
    desc, params, returns, example, notes = doc
    out = ['@subsection{nlib-%s-%s} %s' % (label_prefix, name, name),
           '@index %s; %s (<%s>)' % (name, name, lib),
           '@code ash nocheck', sig, '@end',
           desc, '']
    if params:
        out += ['@table widths=22,78 noheader_off', 'Parameter | Description']
        for p, d in params:
            out.append('`%s` | %s' % (p, d))
        out += ['@end']
    out += ['**Returns.** ' + returns, '']
    if notes:
        out += ['**Notes.** ' + notes, '']
    if show_impl:
        out += ['**Implementation.** The source of the function in `nlib/%s.ash`:' % lib, '',
                '@code ash nocheck label_text="nlib/%s.ash"' % lib, impl, '@end']
    return out


def _example(lib, name, example, run_opts, entry_sig='pub func main(): int64', ret='0', imports=None):
    imps = imports or ['import <%s>;' % lib]
    prog = '\n'.join(imps) + '\n\n@Entry\n' + entry_sig + ' {\n' + _indent(example) + '\n    return ' + ret + ';\n}'
    return ['@example %s asm=%s show=src,out,asm asmlines=140 title="Using %s"' % (run_opts, name, name),
            prog, '@end']


def nlib(arg):
    """@gen nlib <lib>"""
    lib = arg.strip()
    table = {'string': D.STRING, 'math': D.MATH, 'syscalls': D.SYSCALLS, 'args': D.ARGS,
             'delay': D.DELAY, 'types': D.TYPES}[lib]
    funcs = _functions(lib)
    out = []
    missing = [n for n in funcs if n not in table and not n.startswith('__')]
    if missing:
        raise SystemExit('undocumented nlib functions in %s: %s' % (lib, missing))
    for name, doc in table.items():
        if name not in funcs:
            raise SystemExit('documented function not in nlib/%s.ash: %s' % (lib, name))
        sig, impl = funcs[name]
        out += _entry(lib, name, sig, impl, doc, lib)
        example = doc[3]
        if lib == 'args':
            continue
        if lib == 'types':
            out += ['**Machine code.** The function as compiled in a program that calls it:', '',
                    '@example asm=%s show=asm' % name,
                    'import <types>;\n\n@Entry\npub func main(): int64 {\n    double x = %s();\n    return 0;\n}' % name,
                    '@end']
            continue
        if example:
            out += ['**Example.**', '']
            out += _example(lib, name, example, 'run')
    return '\n'.join(out)


def nlib_constants(arg):
    lib = arg.strip()
    out = ['@table widths=34,26,40 title="Constants defined by <%s>"' % lib, 'Name | Value | Meaning']
    meaning = {
        'FX_ONE': '1.0 in Q16.16', 'FX_HALF': '0.5 in Q16.16', 'FX_PI': 'pi in Q16.16',
        'FX_TWO_PI': '2 pi in Q16.16', 'FX_HALF_PI': 'pi / 2 in Q16.16',
        'O_RDONLY': 'open for reading', 'O_WRONLY': 'open for writing', 'O_RDWR': 'open for reading and writing',
        'O_CREAT': 'create the file if needed', 'O_TRUNC': 'truncate to length 0',
        'STDIN': 'standard input descriptor', 'STDOUT': 'standard output descriptor', 'STDERR': 'standard error descriptor',
        'true': 'boolean true', 'false': 'boolean false', 'TRUE': 'boolean true', 'FALSE': 'boolean false',
        'NULL': 'null pointer', 'null': 'null pointer', 'nil': 'null pointer', 'NIL': 'null pointer',
        'unsafePtr': 'the null pointer constant used by the others',
        'RAND_MAX': 'largest value of rand()', 'EXIT_SUCCESS': 'successful exit status', 'EXIT_FAILURE': 'failure exit status',
    }
    for n, v in _defines(lib):
        m = meaning.get(n, 'Linux system call number' if n.startswith('SYS_') else '')
        out.append('`%s` | `%s` | %s' % (n, v.strip(), m))
    out.append('@end')
    return '\n'.join(out)


# ------------------------------------------------------------------ stdio / stdlib
STDIO = {
    'remove': 'Deletes a file.', 'rename': 'Renames or moves a file.',
    'tmpfile': 'Creates a temporary file that is deleted when closed.',
    'tmpnam': 'Generates a unique temporary file name (unsafe; prefer mkstemp).',
    'tmpnam_r': 'Reentrant tmpnam.', 'tempnam': 'Generates a temporary name in a directory with a prefix.',
    'fflush': 'Writes the buffered output of a stream (all streams if the argument is 0).',
    'fclose': 'Flushes and closes a stream.', 'fcloseall': 'Closes all open streams.',
    'fopen': 'Opens a file and returns a stream (`FILE*`), or 0 on failure. Modes: "r", "w", "a", "r+", "w+", "a+", with "b" accepted.',
    'freopen': 'Reopens a stream on another file.', 'fopen64': 'fopen with 64-bit file offsets.',
    'freopen64': 'freopen with 64-bit file offsets.', 'setbuf': 'Sets the buffer of a stream.',
    'setvbuf': 'Sets the buffer and buffering mode of a stream (0 full, 1 line, 2 none).',
    'setbuffer': 'Sets the buffer of a stream with an explicit size.', 'setlinebuf': 'Makes a stream line-buffered.',
    'printf': 'Formatted output to standard output.', 'fprintf': 'Formatted output to a stream.',
    'sprintf': 'Formatted output into a buffer (no size check; prefer snprintf).',
    'vprintf': 'printf with a va_list.', 'vfprintf': 'fprintf with a va_list.', 'vsprintf': 'sprintf with a va_list.',
    'snprintf': 'Formatted output into a buffer of a given size, always NUL-terminated.',
    'vsnprintf': 'snprintf with a va_list.', 'asprintf': 'Formatted output into a newly allocated string.',
    'vasprintf': 'asprintf with a va_list.', 'dprintf': 'Formatted output to a file descriptor.',
    'vdprintf': 'dprintf with a va_list.', 'scanf': 'Formatted input from standard input.',
    'fscanf': 'Formatted input from a stream.', 'sscanf': 'Formatted input from a string.',
    'vscanf': 'scanf with a va_list.', 'vfscanf': 'fscanf with a va_list.', 'vsscanf': 'sscanf with a va_list.',
    'fgetc': 'Reads one byte from a stream; -1 (EOF) at end of file.', 'getc': 'Same as fgetc.',
    'getchar': 'Reads one byte from standard input.', 'getc_unlocked': 'getc without locking.',
    'getchar_unlocked': 'getchar without locking.', 'fgetc_unlocked': 'fgetc without locking.',
    'fputc': 'Writes one byte to a stream.', 'putc': 'Same as fputc.', 'putchar': 'Writes one byte to standard output.',
    'fputc_unlocked': 'fputc without locking.', 'putc_unlocked': 'putc without locking.',
    'putchar_unlocked': 'putchar without locking.', 'getw': 'Reads an int from a stream (obsolete).',
    'putw': 'Writes an int to a stream (obsolete).',
    'fgets': 'Reads a line (at most size-1 bytes) into a buffer, keeping the newline; returns 0 at end of file.',
    'fgets_unlocked': 'fgets without locking.', 'fputs': 'Writes a string to a stream (no newline added).',
    'fputs_unlocked': 'fputs without locking.', 'puts': 'Writes a string and a newline to standard output.',
    'ungetc': 'Pushes a byte back onto a stream.', 'fread': 'Reads count items of size bytes from a stream.',
    'fwrite': 'Writes count items of size bytes to a stream.', 'fread_unlocked': 'fread without locking.',
    'fwrite_unlocked': 'fwrite without locking.', 'fseek': 'Moves the position of a stream (0 SEEK_SET, 1 SEEK_CUR, 2 SEEK_END).',
    'ftell': 'Returns the position of a stream.', 'rewind': 'Moves to the start of a stream and clears its error flag.',
    'fseeko': 'fseek with an off_t offset.', 'ftello': 'ftell returning off_t.',
    'fgetpos': 'Stores the position of a stream in an fpos_t.', 'fsetpos': 'Restores a position saved by fgetpos.',
    'fseeko64': '64-bit fseeko.', 'ftello64': '64-bit ftello.', 'fgetpos64': '64-bit fgetpos.', 'fsetpos64': '64-bit fsetpos.',
    'clearerr': 'Clears the end-of-file and error flags of a stream.', 'feof': 'Non-zero if the end of file was reached.',
    'ferror': 'Non-zero if an error occurred on the stream.', 'clearerr_unlocked': 'clearerr without locking.',
    'feof_unlocked': 'feof without locking.', 'ferror_unlocked': 'ferror without locking.',
    'perror': 'Prints a message followed by the description of errno on standard error.',
    'fileno': 'Returns the file descriptor of a stream.', 'fileno_unlocked': 'fileno without locking.',
    'pclose': 'Closes a stream opened by popen and waits for the command.',
    'popen': 'Runs a shell command and returns a stream connected to its input or output ("r" or "w").',
    'ctermid': 'Returns the name of the controlling terminal.', 'cuserid': 'Returns the name of the user (obsolete).',
    'flockfile': 'Locks a stream for the calling thread.', 'ftrylockfile': 'Tries to lock a stream.',
    'funlockfile': 'Unlocks a stream.', '__uflow': 'Internal glibc helper (refill a read buffer).',
    '__overflow': 'Internal glibc helper (flush a write buffer).',
}


def stdio_table(arg):
    with open(os.path.join(NLIB, 'stdio.ash'), encoding='utf-8') as f:
        lines = [l.strip() for l in f if l.startswith('extern')]
    out = ['@table widths=48,52 title="Declarations of <stdio>" label=tab-stdio', 'Declaration | Description']
    for l in lines:
        decl = l[len('extern '):].rstrip(';')
        name = re.match(r'[\w*]+\s*\*?\s*(\w+)\(', decl.replace('void* ', 'void *')).group(1)
        if name not in STDIO:
            raise SystemExit('stdio: no description for ' + name)
        out.append('`%s` | %s' % (decl.replace('|', '\\|'), STDIO[name]))
    out.append('@end')
    return '\n'.join(out)


STDLIB_GROUPS = [
    ('Number conversion', 'atof atoi atol atoll strtod strtof strtold strtol strtoul strtoll strtoull',
     'Convert text to numbers; the `strto*` functions report where conversion stopped and accept a base.'),
    ('Random numbers', 'rand srand random srandom initstate setstate random_r srandom_r initstate_r setstate_r '
     'drand48 erand48 lrand48 nrand48 mrand48 jrand48 srand48 seed48 lcong48 drand48_r erand48_r lrand48_r '
     'nrand48_r mrand48_r jrand48_r srand48_r seed48_r lcong48_r',
     'Pseudo-random generators of the C library and of the System V `*rand48` family.'),
    ('Memory', 'malloc calloc realloc reallocarray free posix_memalign aligned_alloc valloc',
     'Heap allocation.'),
    ('Process control', 'abort exit quick_exit _Exit atexit at_quick_exit on_exit system',
     'Ending the process, registering exit handlers, running shell commands.'),
    ('Environment', 'getenv secure_getenv putenv setenv unsetenv clearenv', 'Environment variables.'),
    ('Temporary files and paths', 'mktemp mkstemp mkostemp mkstemps mkostemps mkdtemp realpath canonicalize_file_name',
     'Safe temporary names and canonical paths.'),
    ('Searching and sorting', 'bsearch qsort', 'Binary search and quicksort with a comparison callback.'),
    ('Integer arithmetic', 'abs labs llabs div ldiv lldiv', 'Absolute values and quotient/remainder pairs.'),
    ('Multibyte characters', 'mblen mbtowc wctomb mbstowcs wcstombs', 'Conversion between multibyte and wide characters.'),
    ('Terminals and miscellany', 'getsubopt getloadavg rpmatch posix_openpt grantpt unlockpt ptsname ptsname_r getpt ttyslot',
     'Pseudo-terminals, option parsing, load average.'),
    ('Floating-point formatting', 'ecvt fcvt gcvt qecvt qfcvt qgcvt ecvt_r fcvt_r qecvt_r qfcvt_r strfromd strfromf strfroml',
     'Conversion of floating-point values to text.'),
]


def stdlib_table(arg):
    names = set(_functions('stdlib'))
    out = ['@table widths=24,46,30 title="Functions wrapped by <stdlib>" label=tab-stdlib', 'Group | Functions | Purpose']
    seen = set()
    for g, fs, purpose in STDLIB_GROUPS:
        lst = fs.split()
        for n in lst:
            if n not in names:
                raise SystemExit('stdlib group lists unknown function ' + n)
            seen.add(n)
        out.append('%s | %s | %s' % (g, ', '.join('`%s`' % n for n in lst), purpose))
    rest = sorted(n for n in names if n not in seen and not n.startswith('__'))
    if rest:
        raise SystemExit('stdlib functions not grouped: %s' % rest)
    out.append('@end')
    return '\n'.join(out)


# ------------------------------------------------------------------ registers
def registers(arg):
    path = os.path.join(os.path.dirname(config.HERE), '..', 'out')
    import subprocess
    dump = os.path.join(config.HERE, '.cache', 'regs.txt')
    if not os.path.exists(dump):
        os.makedirs(os.path.dirname(dump), exist_ok=True)
        _dump_embedded(dump, 'regs')
    groups = {}
    for line in open(dump):
        p = line.rstrip('\n').split('\t')
        if len(p) < 4:
            continue
        name, cls, kinds, num = p
        groups.setdefault(kinds.split(',')[0], []).append((name, num))
    titles = [
        ('reg8', '8-bit legacy registers'), ('reg8_rex', '8-bit registers that need a REX prefix'),
        ('reg16', '16-bit general-purpose registers'), ('reg32', '32-bit general-purpose registers'),
        ('reg64', '64-bit general-purpose registers'), ('sreg', 'Segment registers'),
        ('creg', 'Control registers'), ('dreg', 'Debug registers'), ('treg', 'Test registers'),
        ('fpureg', 'x87 floating-point stack'), ('mmxreg', 'MMX registers'), ('xmmreg', 'XMM registers (SSE)'),
        ('ymmreg', 'YMM registers (AVX)'), ('zmmreg', 'ZMM registers (AVX-512)'), ('opmaskreg', 'Opmask registers (AVX-512)'),
        ('bndreg', 'Bound registers (MPX)'), ('tmmreg', 'Tile registers (AMX)'),
    ]
    out = []
    for key, title in titles:
        if key not in groups:
            continue
        regs = groups[key]
        out += ['@table widths=25,25,25,25 title="%s"' % title, 'Register | Number | Register | Number']
        for i in range(0, len(regs), 2):
            a = regs[i]
            b = regs[i + 1] if i + 1 < len(regs) else ('', '')
            out.append('`%s` | %s | %s | %s' % (a[0], a[1], ('`%s`' % b[0]) if b[0] else '', b[1]))
        out.append('@end')
    return '\n'.join(out)


def _dump_embedded(path, what):
    import subprocess
    import tempfile
    d = tempfile.mkdtemp()
    src = os.path.join(d, 'Dump.java')
    with open(src, 'w') as f:
        f.write('public class Dump { public static void main(String[] a) throws Exception {\n'
                '  java.nio.file.Files.writeString(java.nio.file.Path.of(a[0]), shasm.data.Embedded.insns());\n'
                '  java.nio.file.Files.writeString(java.nio.file.Path.of(a[1]), shasm.data.Embedded.regs()); } }\n')
    subprocess.check_call(['javac', '-cp', config.JAR, '-d', d, src])
    cache = os.path.dirname(path)
    subprocess.check_call(['java', '-cp', config.JAR + ':' + d, 'Dump',
                           os.path.join(cache, 'insns.txt'), os.path.join(cache, 'regs.txt')])


# ------------------------------------------------------------------ instruction reference
CPU_NAMES = [('8086', '8086'), ('186', '80186'), ('286', '80286'), ('386', '80386'), ('486', '80486'),
             ('PENT', 'Pentium'), ('P6', 'P6'), ('KATMAI', 'SSE'), ('WILLAMETTE', 'SSE2'), ('PRESCOTT', 'SSE3'),
             ('SSSE3', 'SSSE3'), ('SSE41', 'SSE4.1'), ('SSE42', 'SSE4.2'), ('X86_64', 'x86-64'), ('FPU', 'x87'),
             ('MMX', 'MMX'), ('SSE', 'SSE'), ('SSE2', 'SSE2')]
SKIP_FLAGS = {'ND', 'APX', 'KILL', 'FUTURE', 'NEVER', 'OBSOLETE', 'PSEUDO', 'CYRIX', '3DNOW', 'LATEVEX'}
SKIP_OPS = ('mask', 'kreg', 'zmm', 'ymm', 'tmm', 'bnd', '?', 'sae', '|er', 'b32', 'b64', 'b16', 'far', 'zmem', 'xmem', 'ymem')

OPS_SHOW = {
    'reg8': 'r8', 'reg16': 'r16', 'reg32': 'r32', 'reg64': 'r64', 'rm8': 'r/m8', 'rm16': 'r/m16',
    'rm32': 'r/m32', 'rm64': 'r/m64', 'mem': 'm', 'mem8': 'm8', 'mem16': 'm16', 'mem32': 'm32', 'mem64': 'm64',
    'mem80': 'm80', 'mem128': 'm128', 'imm8': 'imm8', 'imm16': 'imm16', 'imm32': 'imm32', 'imm64': 'imm64',
    'sbyteword16': 'imm8', 'sbytedword32': 'imm8', 'sbytedword64': 'imm8', 'sbytedword': 'imm8', 'sbyteword': 'imm8',
    'sdword64': 'imm32', 'udword64': 'imm32', 'unity': '1', 'reg_cl': 'CL', 'reg_al': 'AL', 'reg_ax': 'AX',
    'reg_eax': 'EAX', 'reg_rax': 'RAX', 'reg_dx': 'DX', 'reg_ecx': 'ECX', 'reg_rcx': 'RCX', 'reg_sreg': 'Sreg',
    'reg_creg': 'CRn', 'reg_dreg': 'DRn', 'reg_es': 'ES', 'reg_cs': 'CS', 'reg_ss': 'SS', 'reg_ds': 'DS',
    'reg_fs': 'FS', 'reg_gs': 'GS', 'xmmreg': 'xmm1', 'xmmrm': 'xmm2/m128', 'xmmrm128': 'xmm2/m128',
    'xmmrm64': 'xmm2/m64', 'xmmrm32': 'xmm2/m32', 'xmmrm16': 'xmm2/m16', 'mmxreg': 'mm1', 'mmxrm': 'mm2/m64',
    'mmxrm64': 'mm2/m64', 'fpureg': 'ST(i)', 'fpu0': 'ST(0)', 'imm|near': 'rel32', 'imm|short': 'rel8',
    'imm': 'imm', 'reg_ecx': 'ECX', 'rm64|near': 'r/m64', 'rm32|near': 'r/m32', 'rm16|near': 'r/m16',
    'imm|near|short': 'rel', 'reg_rcx': 'RCX', 'reg_cx': 'CX', 'fpureg|to': 'TO ST(i)',
}


def _sample(op, others, bits):
    m64 = bits == 64
    base = '[rbx]' if m64 else '[ebx]'
    other_regs = ' '.join(others)
    simple = {
        'reg8': 'cl', 'reg16': 'cx', 'reg32': 'ecx', 'reg64': 'rcx', 'rm8': 'byte ' + base,
        'rm16': 'word ' + base, 'rm32': 'dword ' + base, 'rm64': 'qword ' + base, 'mem': base,
        'mem8': 'byte ' + base, 'mem16': 'word ' + base, 'mem32': 'dword ' + base, 'mem64': 'qword ' + base,
        'mem80': 'tword ' + base, 'mem128': 'oword ' + base, 'imm8': '0x12', 'imm16': '0x1234',
        'imm32': '0x12345678', 'imm64': '0x1122334455667788', 'sbyteword16': '0x12', 'sbytedword32': '0x12',
        'sbytedword64': '0x12', 'sbytedword': '0x12', 'sbyteword': '0x12', 'sdword64': '0x12345678',
        'udword64': '0x12345678', 'unity': '1', 'reg_cl': 'cl', 'reg_al': 'al', 'reg_ax': 'ax', 'reg_eax': 'eax',
        'reg_rax': 'rax', 'reg_dx': 'dx', 'reg_ecx': 'ecx', 'reg_rcx': 'rcx', 'reg_cx': 'cx', 'reg_sreg': 'ds',
        'reg_creg': 'cr0', 'reg_dreg': 'dr1', 'reg_es': 'es', 'reg_cs': 'cs', 'reg_ss': 'ss', 'reg_ds': 'ds',
        'reg_fs': 'fs', 'reg_gs': 'gs', 'xmmreg': 'xmm1', 'xmmrm': 'xmm2', 'xmmrm128': 'xmm2', 'xmmrm64': 'xmm2',
        'xmmrm32': 'xmm2', 'xmmrm16': 'xmm2', 'mmxreg': 'mm1', 'mmxrm': 'mm2', 'mmxrm64': 'mm2', 'fpureg': 'st1',
        'fpu0': 'st0', 'imm|near': 'near $+0x100', 'imm|short': 'short $+0x10', 'rm64|near': 'rcx',
        'rm32|near': 'ecx', 'rm16|near': 'cx', 'fpureg|to': 'to st1',
    }
    if op == 'imm':
        if 'reg64' in other_regs or 'rax' in other_regs:
            return '0x1122334455667788'
        if 'reg16' in other_regs or 'rm16' in other_regs or 'reg_ax' in other_regs:
            return '0x1234'
        if 'reg8' in other_regs or 'rm8' in other_regs or 'reg_al' in other_regs:
            return '0x12'
        return '0x12345678'
    return simple.get(op)


ENC_MAP = {'o16': '66', 'o32': '', 'o64': 'REX.W', 'o64nw': '', 'odf': '', 'osz': '', 'nw': '', 'rex.l': '',
           'np': '', 'jcc8': '', 'norexw': '', 'norexb': '', 'norexx': '', 'norexr': '', 'a16': '67', 'a32': '67',
           'ib': 'ib', 'ib,s': 'ib', 'ib,u': 'ib', 'iw': 'iw', 'id': 'id', 'id,s': 'id', 'iq': 'iq', 'iwd': 'iw/id',
           'iwdq': 'iw/id/iq', 'rel8': 'cb', 'rel': 'cd', 'rel16': 'cw', 'rel32': 'cd', 'f2i': 'F2', 'f3i': 'F3',
           'wait': '9B', 'hle': '', 'hlexr': '', 'hlenl': '', 'repe': '', 'resb': '', 'adf': '', 'mustrep': 'F3',
           'nof3': '', 'mustrepne': 'F2', 'nohi': '', 'hle': '', 'iwdq,s': 'iw/id'}


def _opcode(enc):
    out = []
    for t in enc.split():
        if re.fullmatch(r'[0-9a-f]{2}', t):
            out.append(t.upper())
        elif re.fullmatch(r'[0-9a-f]{4}', t):
            out += [t[:2].upper(), t[2:].upper()]
        elif re.fullmatch(r'[0-9a-f]{2}\+[rc]', t):
            out.append(t[:2].upper() + ('+r' if t.endswith('r') else '+cc'))
        elif re.fullmatch(r'/[0-7r]', t):
            out.append(t)
        elif t in ENC_MAP:
            if ENC_MAP[t]:
                out.append(ENC_MAP[t])
        else:
            return None
    return ' '.join(out)


def _forms(mnem, db):
    res, seen = [], set()
    for f in db.get(mnem.upper(), []):
        name, ops, letters, enc, flags = f
        fl = set(flags.split(','))
        if fl & SKIP_FLAGS:
            continue
        if re.search(r'\b(e?vex|xop|rex2)\b|evex\.|vex\.|xop\.|jlen|jmp8|jcc8', enc):
            continue
        if any(s in ops for s in SKIP_OPS):
            continue
        opl = [o.rstrip('*') for o in re.split(r'[,:]', ops)] if ops else []
        if any(o not in OPS_SHOW for o in opl):
            continue
        shown = ', '.join(OPS_SHOW[o] for o in opl)
        if mnem.upper() in ('JCXZ', 'LOOP', 'LOOPE', 'LOOPNE', 'CALL', 'JMP', 'JCC') and opl == ['imm']:
            shown = 'rel (assembler chooses)'
        oc = _opcode(enc)
        if oc is None:
            continue
        key = (shown, 'NOLONG' in fl)
        if key in seen:
            continue
        seen.add(key)
        bits = 32 if 'NOLONG' in fl else 64
        sample_ops = [_sample(o, opl, bits) for o in opl]
        if mnem.upper() in ('JCXZ', 'LOOP', 'LOOPE', 'LOOPNE', 'CALL', 'JMP', 'JCC') and opl == ['imm']:
            sample_ops = ['$+0x10']
        inst = None
        if all(s is not None for s in sample_ops):
            mn = mnem.lower()
            if mnem.upper() == 'JCC':
                mn = 'je'
            elif mnem.upper() == 'SETCC':
                mn = 'sete'
            elif mnem.upper() == 'CMOVCC':
                mn = 'cmove'
            inst = (mn + ' ' + ', '.join(sample_ops)).strip()
        cpu = ''
        for k, v in CPU_NAMES:
            if k in fl:
                cpu = v
                break
        mode = 'Invalid' if 'NOLONG' in fl else 'Valid'
        priv = 'PRIV' in fl
        mn_show = {'JCC': 'Jcc', 'SETCC': 'SETcc', 'CMOVCC': 'CMOVcc'}.get(mnem.upper(), mnem.upper())
        res.append({'op': oc, 'insn': (mn_show + ' ' + shown).strip(), 'mode': mode, 'cpu': cpu,
                    'inst': inst, 'bits': bits, 'priv': priv})
    return res


def _load_db():
    path = os.path.join(config.HERE, '.cache', 'insns.txt')
    if not os.path.exists(path):
        _dump_embedded(os.path.join(config.HERE, '.cache', 'regs.txt'), 'all')
    db = {}
    for line in open(path, encoding='utf-8'):
        p = line.rstrip('\n').split('\t')
        if len(p) >= 5:
            db.setdefault(p[0].upper(), []).append(p)
    return db


def _encode_all(items):
    import json
    import subprocess
    cache_p = os.path.join(config.HERE, '.cache', 'encodings.json')
    cache = json.load(open(cache_p)) if os.path.exists(cache_p) else {}
    todo = sorted({'%d\t%s' % (b, i) for b, i in items if '%d\t%s' % (b, i) not in cache})
    if todo:
        encdir = os.path.join(config.HERE, '.cache', 'enc')
        if not os.path.exists(os.path.join(encdir, 'EncodeLines.class')):
            os.makedirs(encdir, exist_ok=True)
            subprocess.check_call(['javac', '-cp', config.JAR, '-d', encdir,
                                   os.path.join(config.HERE, 'tools', 'EncodeLines.java')])
        r = subprocess.run(['java', '-cp', config.JAR + ':' + encdir, 'EncodeLines'],
                           input='\n'.join(todo) + '\n', capture_output=True, text=True, timeout=1200)
        outs = r.stdout.split('\n')
        for k, v in zip(todo, outs):
            cache[k] = v.strip()
        json.dump(cache, open(cache_p, 'w'))
    return cache


def _cell(t):
    return t.replace('|', '/')


def instructions(arg):
    import insn_docs
    db = _load_db()
    entries = []
    for name, (group, title, desc, flags) in insn_docs.INSN.items():
        forms = _forms(name, db)
        if not forms and name in ('CBW', 'CWD', 'CQO', 'CDQ', 'CDQE', 'CWDE'):
            forms = []
        entries.append((group, name, title, desc, flags, forms))
    enc = _encode_all([(f['bits'], f['inst']) for e in entries for f in e[5] if f['inst']])
    out = []
    cur_group = None
    intro = {
        'General-purpose': 'Instructions that operate on the general-purpose registers and memory: data transfer, '
                           'arithmetic, logic, shifts, bit manipulation, control transfer, strings and I/O.',
        'System': 'Instructions used by operating systems and boot code. Most are privileged: they fault at '
                  'privilege level 3.',
        'x87 floating point': 'Instructions of the x87 floating-point unit, which works on a stack of eight 80-bit '
                              'registers `st(0)`-`st(7)`. In memory forms, `m32`, `m64` and `m80` are single, double '
                              'and extended precision values.',
        'SSE and SSE2': 'Scalar and packed instructions on the XMM registers. On x86-64 the floating-point calling '
                        'convention uses `xmm0`-`xmm7`.',
    }
    for group, name, title, desc, flags, forms in entries:
        if group != cur_group:
            cur_group = group
            lab = re.sub(r'[^a-z0-9]+', '-', group.lower()).strip('-')
            out += ['@section{insn-%s} %s Instructions' % (lab, group), intro.get(group, ''), '']
        disp = {'JCC': 'Jcc', 'SETCC': 'SETcc', 'CMOVCC': 'CMOVcc'}.get(name, name)
        out += ['@subsection{insn-%s} %s — %s' % (name.lower(), disp, title), '@index %s' % disp.lower()]
        if forms:
            out += ['@table widths=21,25,10,11,33 small',
                    'Opcode | Instruction | 64-bit | Since | Example and encoding']
            for f in forms:
                ex = ''
                if f['inst']:
                    e = enc.get('%d\t%s' % (f['bits'], f['inst']), '')
                    if e and not e.startswith('ERR'):
                        ex = '`%s` = `%s`' % (_cell(f['inst']), e)
                        if f['bits'] == 32:
                            ex += ' (32-bit)'
                out.append('`%s` | `%s` | %s | %s | %s' % (_cell(f['op']), _cell(f['insn']), f['mode'],
                                                          f['cpu'] + (' priv' if f['priv'] else ''), ex))
            out.append('@end')
        if not forms:
            out += ['**Availability.** The instruction database of `shasm` 1.2.0 contains no encodable form of '
                    '`%s`: it is rejected in `shasm` sources and (silently) in `asm` blocks. Encode it with `emit` '
                    'if you need it.' % disp.lower(), '']
        out += [desc, '', '**Flags affected.** ' + flags, '']
    return '\n'.join(out)


def ascii_table(arg):
    names = {0: 'NUL', 7: 'BEL (\\a)', 8: 'BS (\\b)', 9: 'TAB (\\t)', 10: 'LF (\\n)', 11: 'VT (\\v)', 12: 'FF (\\f)',
             13: 'CR (\\r)', 27: 'ESC', 32: 'space', 127: 'DEL'}
    out = ['@table widths=11,11,11,17,11,11,11,17 title="ASCII character codes" label=tab-ascii',
           'Dec | Hex | Bin | Char | Dec | Hex | Bin | Char']
    rows = []
    for c in range(64):
        a, b = c, c + 64
        def cell(v):
            if v in names:
                ch = names[v]
            elif 32 < v < 127:
                ch = '`%s`' % chr(v) if chr(v) not in '`|' else ('backquote' if chr(v) == '`' else 'vertical bar')
            else:
                ch = 'control'
            return '%d | %02X | %s | %s' % (v, v, format(v, '07b'), ch)
        rows.append(cell(a) + ' | ' + cell(b))
    out += rows + ['@end']
    return '\n'.join(out)


def powers_table(arg):
    out = ['@table widths=12,30,30,28 title="Powers of two" label=tab-powers', 'n | 2^n (decimal) | 2^n (hexadecimal) | Where it appears']
    notes = {8: 'values of one byte (0..255)', 10: 'one kibibyte (1 KiB = 1024 bytes)', 16: 'values of a 16-bit integer',
             20: 'one mebibyte (1 MiB)', 30: 'one gibibyte (1 GiB)', 31: 'limit of int32', 32: 'values of a 32-bit integer',
             63: 'limit of int64', 64: 'values of a 64-bit integer', 4: 'one hexadecimal digit', 12: 'one memory page (4096 bytes)'}
    for n in list(range(0, 17)) + [20, 24, 30, 31, 32, 40, 48, 63, 64]:
        out.append('%d | %d | 0x%X | %s' % (n, 2 ** n, 2 ** n, notes.get(n, '')))
    out.append('@end')
    return '\n'.join(out)


def dictionary(arg):
    import dict_docs
    out = []
    for i, (word, cat, syntax, meaning, prog, see) in enumerate(dict_docs.DICT):
        if i == 0:
            out += ['@section{dict-symbols} Symbols and Operators', '']
        if word == 'asm':
            out += ['@section{dict-words} Words, from A to Z', '']
        out += ['@subsection{dict-%d} %s' % (i + 1, word.replace('{', '(').replace('}', ')')),
                '@index %s' % word.replace(';', ','),
                '**Kind:** %s.' % cat, '',
                '@code text nocheck', syntax, '@end',
                meaning, '']
        if prog:
            out += ['@example run', prog, '@end']
        if see:
            out += ['**See also:** ' + ', '.join('[[%s]]' % s for s in see) + '.', '']
    return '\n'.join(out)
