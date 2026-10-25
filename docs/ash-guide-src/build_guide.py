#!/usr/bin/env python3
"""
build_guide.py - builds docs/ash-guide.pdf (ASH Programming Guide and Reference Manual).

    python3 build_guide.py            full build (every example is compiled and run)
    python3 build_guide.py --only 5   build only chapter files whose name starts with 05
    python3 build_guide.py --check    verify examples only, do not write the PDF

The text lives in chapters/*.txt (a small markup language, see README.txt).
Every ASH code block is compiled with the real compiler (config.ASHC); blocks
marked as examples are also linked and executed, and their real output,
ASHIR and disassembly are inserted into the book. Results are cached in
.cache/ keyed by the source text and the compiler binary.
"""
import glob
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile

from pygments.lexers import CLexer, NasmLexer, BashLexer, JavaLexer
from pygments.token import Token
from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER, TA_LEFT
from reportlab.lib.pagesizes import letter
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.units import inch
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.lib.fonts import addMapping
from reportlab.platypus import (BaseDocTemplate, PageTemplate, Frame, Paragraph,
                                Spacer, PageBreak, NextPageTemplate, Table,
                                TableStyle, Flowable, ListFlowable, ListItem,
                                KeepTogether, CondPageBreak)
from reportlab.platypus.tableofcontents import TableOfContents, SimpleIndex

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import config  # noqa: E402
import lexers  # noqa: E402

HERE = config.HERE
CACHE_DIR = os.path.join(HERE, '.cache')
ENGINE_VERSION = '8'
ENV = dict(os.environ, LC_ALL='C', LANG='C')

# ===================================================================== fonts
FONT_DIRS = ['/usr/share/fonts/truetype/liberation', '/usr/share/fonts/truetype/dejavu']


def _font(name, fname):
    for d in FONT_DIRS:
        p = os.path.join(d, fname)
        if os.path.exists(p):
            pdfmetrics.registerFont(TTFont(name, p))
            return
    raise SystemExit('font not found: ' + fname)


_font('Sans', 'LiberationSans-Regular.ttf')
_font('Sans-Bold', 'LiberationSans-Bold.ttf')
_font('Sans-Italic', 'LiberationSans-Italic.ttf')
_font('Sans-BoldItalic', 'LiberationSans-BoldItalic.ttf')
_font('Mono', 'DejaVuSansMono.ttf')
_font('Mono-Bold', 'DejaVuSansMono-Bold.ttf')
_font('Mono-Italic', 'DejaVuSansMono-Oblique.ttf')
_font('Mono-BoldItalic', 'DejaVuSansMono-BoldOblique.ttf')
addMapping('Sans', 0, 0, 'Sans')
addMapping('Sans', 1, 0, 'Sans-Bold')
addMapping('Sans', 0, 1, 'Sans-Italic')
addMapping('Sans', 1, 1, 'Sans-BoldItalic')
addMapping('Mono', 0, 0, 'Mono')
addMapping('Mono', 1, 0, 'Mono-Bold')
addMapping('Mono', 0, 1, 'Mono-Italic')
addMapping('Mono', 1, 1, 'Mono-BoldItalic')

ACCENT = colors.HexColor(config.ACCENT)
ACCENT_DARK = colors.HexColor(config.ACCENT_DARK)
INK = colors.HexColor(config.INK)
MUTED = colors.HexColor(config.MUTED)
RULE = colors.HexColor('#C9CDD3')

PAGE_W, PAGE_H = letter
LM, RM, TM, BM = 0.95 * inch, 0.95 * inch, 0.95 * inch, 0.85 * inch
TEXT_W = PAGE_W - LM - RM

# ===================================================================== styles
ST = {}
ST['body'] = ParagraphStyle('body', fontName='Sans', fontSize=10, leading=13.6,
                            textColor=INK, spaceBefore=0, spaceAfter=7)
ST['small'] = ParagraphStyle('small', parent=ST['body'], fontSize=8.6, leading=11, spaceAfter=0)
ST['cell'] = ParagraphStyle('cell', parent=ST['body'], fontSize=8.8, leading=11.2, spaceAfter=0)
ST['cellh'] = ParagraphStyle('cellh', parent=ST['cell'], fontName='Sans-Bold', textColor=colors.white)
ST['h0k'] = ParagraphStyle('h0k', fontName='Sans-Bold', fontSize=12, leading=16, textColor=ACCENT,
                           spaceBefore=0, spaceAfter=4)
ST['h0'] = ParagraphStyle('h0', fontName='Sans-Bold', fontSize=25, leading=30, textColor=ACCENT,
                          spaceBefore=0, spaceAfter=6, keepWithNext=1)
ST['h1'] = ParagraphStyle('h1', fontName='Sans-Bold', fontSize=13, leading=17, textColor=ACCENT,
                          spaceBefore=16, spaceAfter=7, keepWithNext=1)
ST['h2'] = ParagraphStyle('h2', fontName='Sans-Bold', fontSize=11.2, leading=15, textColor=ACCENT,
                          spaceBefore=12, spaceAfter=5, keepWithNext=1)
ST['h3'] = ParagraphStyle('h3', fontName='Sans-Bold', fontSize=10.2, leading=14, textColor=INK,
                          spaceBefore=10, spaceAfter=4, keepWithNext=1)
ST['caption'] = ParagraphStyle('caption', fontName='Sans-Bold', fontSize=9, leading=12, textColor=INK,
                               spaceBefore=6, spaceAfter=4, keepWithNext=1, alignment=TA_CENTER)
ST['label'] = ParagraphStyle('label', fontName='Sans-Bold', fontSize=8.6, leading=11, textColor=ACCENT_DARK,
                             spaceAfter=2)
ST['toctitle'] = ParagraphStyle('toctitle', fontName='Sans-Bold', fontSize=22, leading=28, textColor=ACCENT,
                                spaceAfter=14)
ST['centered'] = ParagraphStyle('centered', parent=ST['body'], alignment=TA_CENTER, textColor=MUTED)
ST['list'] = ParagraphStyle('list', parent=ST['body'], spaceAfter=2.5)

TOC_STYLES = [
    ParagraphStyle('toc0', fontName='Sans-Bold', fontSize=10, leading=13, leftIndent=0,
                   firstLineIndent=0, spaceBefore=7, textColor=ACCENT_DARK),
    ParagraphStyle('toc1', fontName='Sans', fontSize=9.2, leading=11.6, leftIndent=18,
                   firstLineIndent=0, textColor=INK),
    ParagraphStyle('toc2', fontName='Sans', fontSize=8.6, leading=10.6, leftIndent=40,
                   firstLineIndent=0, textColor=MUTED),
]


# ===================================================================== inline markup
def esc(s):
    return s.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')


LABELS = {}          # label -> (number string, kind)   filled by the first parse pass


def inline(text):
    """markup -> reportlab paragraph XML"""
    keep = []

    def stash(x):
        keep.append(x)
        return '\x00%d\x00' % (len(keep) - 1)

    # `code`
    def code(m):
        return stash('<font name="Mono" size="8.9" color="#6E1A12">%s</font>' % esc(m.group(1)))
    text = re.sub(r'``(.+?)``', code, text)
    text = re.sub(r'`([^`]+)`', code, text)

    # [[label]] / [[label|text]] / [[url:...|text]]
    def link(m):
        target, _, label = m.group(1).partition('|')
        if target.startswith('url:'):
            url = target[4:]
            return stash('<a href="%s" color="#1F5F9E"><u>%s</u></a>' % (esc(url), esc(label or url)))
        if target not in LABELS:
            BUILD.warn('unknown reference: ' + target)
            return stash(esc(label or target))
        num, kind = LABELS[target]
        shown = label or ('%s %s' % (kind, num))
        return stash('<a href="#%s" color="#1F5F9E">%s</a>' % (target, esc(shown)))
    text = re.sub(r'\[\[(.+?)\]\]', link, text)

    # {i:term}  invisible index entry
    text = re.sub(r'\{i:([^}]+)\}', lambda m: stash('<index item="%s"/>' % esc(m.group(1)).replace('"', '')), text)

    text = esc(text)
    text = re.sub(r'\*\*(.+?)\*\*', r'<b>\1</b>', text)
    text = re.sub(r'(?<![\w*])\*(?!\s)([^*]+?)(?<!\s)\*(?![\w*])', r'<i>\1</i>', text)
    text = text.replace('--', '–') if False else text
    text = re.sub(r'\x00(\d+)\x00', lambda m: keep[int(m.group(1))], text)
    return text


# ===================================================================== code boxes
LEXERS = {
    'ash': lexers.AshLexer, 'ashir': lexers.AshirLexer, 'disasm': lexers.ObjdumpLexer,
    'c': CLexer, 'asm': NasmLexer, 'session': lexers.SessionLexer, 'listing': lexers.ListingLexer, 'nasm': NasmLexer, 'sh': BashLexer, 'java': JavaLexer,
}
BOX = {
    # kind: (background, bar colour, label)
    'ash': ('#F6F7F9', config.ACCENT, 'ASH'),
    'c': ('#F6F7F9', '#1F5F9E', 'C'),
    'asm': ('#F6F7F9', '#7A3E9D', 'shasm'),
    'nasm': ('#F6F7F9', '#7A3E9D', 'shasm'),
    'sh': ('#F3F4F6', '#4A4F57', 'shell'),
    'session': ('#F3F4F6', '#4A4F57', 'shell session'),
    'listing': ('#F8F6F1', '#7A3E9D', 'shasm listing'),
    'java': ('#F6F7F9', '#8E3A00', 'Java'),
    'ashir': ('#F5F3FA', '#7A3E9D', 'ASHIR'),
    'disasm': ('#F8F6F1', '#8E3A00', 'x86 disassembly'),
    'output': ('#FFFFFF', '#2E7D32', 'output'),
    'text': ('#FFFFFF', '#8A8F98', ''),
    'diag': ('#FFF8F6', '#B3261E', 'compiler diagnostics'),
}
CODE_SIZE = 8.1
CODE_LEAD = 10.4
MONO_W = pdfmetrics.stringWidth('M', 'Mono', CODE_SIZE)


def tokenize(src, lang):
    """-> list of lines, each a list of (text, color, bold, italic)"""
    src = src.expandtabs(8 if lang == 'disasm' else 4)
    lines = [[]]
    if lang in LEXERS:
        toks = LEXERS[lang]().get_tokens(src)
    else:
        toks = [(Token.Text, src)]
    for tok, val in toks:
        color, bold, ital = lexers.style_for(tok) if lang in LEXERS else lexers.DEFAULT_COLOR
        parts = val.split('\n')
        for i, p in enumerate(parts):
            if i > 0:
                lines.append([])
            if p:
                lines[-1].append((p, color, bold, ital))
    while lines and not lines[-1]:
        lines.pop()
    # pygments adds a trailing newline; drop leading empty line duplicates only at end
    return lines


def wrap_line(runs, maxc):
    """split one tokenized line into visual lines of at most maxc chars"""
    out, cur, n = [], [], 0
    for text, c, b, i in runs:
        while text:
            room = maxc - n
            if len(text) <= room:
                cur.append((text, c, b, i))
                n += len(text)
                text = ''
            else:
                cur.append((text[:room], c, b, i))
                out.append(cur)
                cur, n = [('↪ ', '#9AA0A8', False, False)], 2
                text = text[room:]
    out.append(cur)
    return out


class CodeBox(Flowable):
    PADX, PADY = 9, 6

    def __init__(self, lines, kind, label=None, cont=False, first=True):
        Flowable.__init__(self)
        self.lines = lines
        self.kind = kind
        bg, bar, lab = BOX.get(kind, BOX['text'])
        self.bg = colors.HexColor(bg)
        self.bar = colors.HexColor(bar)
        self.label = lab if label is None else label
        self.cont = cont
        self.vis = None

    def _layout(self, aw):
        maxc = max(20, int((aw - 2 * self.PADX - 4) / MONO_W))
        vis = []
        for ln in self.lines:
            vis.extend(wrap_line(ln, maxc))
        return vis

    def wrap(self, aw, ah):
        self.aw = aw
        self.vis = self._layout(aw)
        self.top = 9 if self.label else 0
        self.height = len(self.vis) * CODE_LEAD + 2 * self.PADY + 2 + self.top
        return aw, self.height

    def split(self, aw, ah):
        vis = self._layout(aw)
        fit = int((ah - 2 * self.PADY - 2 - (9 if self.label else 0)) / CODE_LEAD)
        if fit < 3 or fit >= len(vis):
            return []
        a = CodeBox(vis[:fit], self.kind, self.label, self.cont)
        b = CodeBox(vis[fit:], self.kind, self.label, True)
        a.lines = vis[:fit]
        b.lines = vis[fit:]
        return [a, b]

    def draw(self):
        c = self.canv
        h = self.height
        c.saveState()
        c.setFillColor(self.bg)
        c.setStrokeColor(RULE)
        c.setLineWidth(0.5)
        c.rect(0, 0, self.aw, h, fill=1, stroke=1)
        c.setFillColor(self.bar)
        c.rect(0, 0, 2.6, h, fill=1, stroke=0)
        lab = self.label + (' (continued)' if self.cont and self.label else '')
        if lab:
            c.setFont('Sans-Bold', 6.4)
            c.setFillColor(colors.HexColor('#9AA0A8'))
            c.drawRightString(self.aw - 5, h - 9, lab.upper())
        y = h - self.PADY - CODE_SIZE - self.top
        for ln in self.vis:
            x = self.PADX + 2
            for text, col, b, i in ln:
                font = 'Mono' + ('-BoldItalic' if b and i else '-Bold' if b else '-Italic' if i else '')
                c.setFont(font, CODE_SIZE)
                c.setFillColor(colors.HexColor(col))
                c.drawString(x, y, text)
                x += MONO_W * len(text)
            y -= CODE_LEAD
        c.restoreState()


def code_flowables(src, kind, label=None):
    lines = tokenize(src.rstrip('\n'), kind)
    if not lines:
        lines = [[('', '#000000', False, False)]]
    return [CodeBox(lines, kind, label), Spacer(1, 7)]


# ===================================================================== example engine
class BuildState:
    def __init__(self):
        self.failures = []
        self.warnings = []
        self.cur = ''
        self.compiled = 0
        self.cached = 0

    def warn(self, msg):
        self.warnings.append('%s: %s' % (self.cur, msg))

    def fail(self, msg):
        self.failures.append('%s: %s' % (self.cur, msg))


BUILD = BuildState()
EXAMPLE_LOG = []
ANSI = re.compile(r'\x1b\[[0-9;]*m')


def ashc_stamp():
    try:
        st = os.stat(config.ASHC)
        return '%d-%d' % (st.st_size, int(st.st_mtime))
    except OSError:
        raise SystemExit('compiler not found: ' + config.ASHC)


STAMP = None


def sh(cmd, cwd, timeout=60, stdin=None):
    try:
        r = subprocess.run(cmd, cwd=cwd, env=ENV, capture_output=True, timeout=timeout,
                           input=stdin.encode() if stdin else None)
        return r.returncode, r.stdout.decode('utf-8', 'replace'), r.stderr.decode('utf-8', 'replace')
    except subprocess.TimeoutExpired:
        return -999, '', 'timeout'


def split_ir(ir):
    """ASHIR text -> list of (name or None, block)"""
    blocks, cur = [], []
    for line in ir.split('\n'):
        if line.strip() == '' and cur:
            blocks.append('\n'.join(cur))
            cur = []
        elif line.strip() != '':
            cur.append(line)
    if cur:
        blocks.append('\n'.join(cur))
    out = []
    for b in blocks:
        m = re.search(r'^func \S+ (\w+)\(', b, re.M)
        out.append((m.group(1) if m else None, b))
    return out


RUNTIME = re.compile(r'^(__ash_|__va_get|__stlib_|__str\d|__wstr|__g_|__ret_|__guide_entry)')


def filter_ir(ir, sel):
    blocks = split_ir(ir)
    if sel == 'full':
        keep = [b for _, b in blocks]
    elif sel in ('', True, 'user'):
        keep = [b for n, b in blocks if not (n and RUNTIME.match(n))]
    else:
        want = sel.split(',')
        keep = [b for n, b in blocks if n in want]
    return '\n\n'.join(keep)


def split_objdump(txt):
    funcs, cur, name = [], [], None
    for line in txt.split('\n'):
        m = re.match(r'^([0-9a-f]+) <([^>]+)>:$', line)
        if m:
            if name:
                funcs.append((name, '\n'.join(cur).rstrip()))
            name, cur = m.group(2), [line]
        elif name is not None:
            if line.startswith('Disassembly of section'):
                funcs.append((name, '\n'.join(cur).rstrip()))
                name, cur = None, []
                continue
            if line.strip() or cur:
                cur.append(line.rstrip())
    if name:
        funcs.append((name, '\n'.join(cur).rstrip()))
    return funcs


def filter_asm(txt, sel):
    funcs = split_objdump(txt)
    if sel in ('', True, 'user'):
        keep = [b for n, b in funcs if not RUNTIME.match(n)]
    elif sel == 'full':
        keep = [b for _, b in funcs]
    else:
        want = sel.split(',')
        keep = [b for n, b in funcs if n in want]
    return '\n\n'.join(k.strip('\n') for k in keep)


def trim(text, maxlines):
    lines = text.rstrip('\n').split('\n')
    if len(lines) > maxlines:
        extra = len(lines) - maxlines
        lines = lines[:maxlines] + ['... (%d more lines not shown)' % extra]
    return '\n'.join(lines)


SIGNALS = {134: 'SIGABRT', 139: 'SIGSEGV', 136: 'SIGFPE', 132: 'SIGILL'}


def run_ash(code, opts, files):
    """Compile (and maybe run) an ASH program. Returns a dict, cached."""
    global STAMP
    if STAMP is None:
        STAMP = ashc_stamp()
    key = hashlib.sha1(json.dumps([ENGINE_VERSION, STAMP, code, sorted(opts.items()),
                                   sorted(files.items())]).encode()).hexdigest()
    cpath = os.path.join(CACHE_DIR, key[:2], key + '.json')
    if os.path.exists(cpath):
        BUILD.cached += 1
        with open(cpath) as f:
            return json.load(f)
    BUILD.compiled += 1
    res = _run_ash(code, opts, files)
    if not res.get('error'):
        os.makedirs(os.path.dirname(cpath), exist_ok=True)
        with open(cpath, 'w') as f:
            json.dump(res, f)
    return res


def _run_ash(code, opts, files):
    res = {}
    tmp = tempfile.mkdtemp(prefix='ashguide')
    try:
        name = opts.get('name', 'example.ashir' if 'ashir' in opts else 'example.ash')
        for fn, body in files.items():
            with open(os.path.join(tmp, fn), 'w') as f:
                f.write(body)
        with open(os.path.join(tmp, name), 'w') as f:
            f.write(code)
        flags = opts.get('flags', '').split()
        if 'nolibc' in opts:
            flags.append('--no-libc')
        bits = opts.get('bits', '64')
        ashc = config.ASHC

        if 'fail' in opts:
            mode = '--compile' if bits == '16' else '--elf'
            rc, out, err = sh([ashc, name, mode, '-o', 'x.o'] + flags, tmp)
            diag = ANSI.sub('', (out + err)).replace(tmp + '/', '').strip()
            if rc == 0:
                res['error'] = 'expected a compile error but the program compiled'
            res['diag'] = diag
            return res

        flat = bits == '16' or 'flat' in opts
        if flat:
            rc, out, err = sh([ashc, name, '--compile', '-o', 'x.bin', '-s'] + flags, tmp)
        elif 'exe' in opts:
            rc, out, err = sh([ashc, name, '--exe', '-o', 'x', '-s'] + flags, tmp)
        elif 'win' in opts:
            rc, out, err = sh([ashc, name, '--win', '-o', 'x.obj', '-s'] + flags, tmp)
        else:
            rc, out, err = sh([ashc, name, '--elf', '-o', 'x.o', '-s'] + flags, tmp)
        if rc != 0:
            res['error'] = 'compile failed:\n' + ANSI.sub('', out + err)
            return res

        if 'ir' in opts:
            rc, out, err = sh([ashc, name, '--stdout', '-s'] + flags, tmp)
            if rc != 0:
                res['error'] = 'ashir failed: ' + ANSI.sub('', out + err)
                return res
            res['ir'] = filter_ir(out.strip(), opts['ir'])

        if 'asm' in opts:
            if flat or 'exe' in opts:
                mach = 'i8086' if bits == '16' else 'i386'
                target = 'x.bin' if flat else 'x'
                cmd = ['objdump', '-D', '-b', 'binary', '-m', mach, '-M', 'intel',
                       '--adjust-vma=' + opts.get('org', '0'), target]
                if 'exe' in opts:
                    cmd = ['objdump', '-D', '-b', 'binary', '-m', 'i386', '-M', 'intel',
                           '--start-address=' + opts.get('start', '0x54'), '--adjust-vma=0', 'x']
                rc, out, err = sh(cmd, tmp)
                body = out.split('<.data>:', 1)[-1]
                lines = [l for l in body.split('\n') if l.strip()]
                if 'asmlimit' in opts:
                    lines = lines[:int(opts['asmlimit'])]
                res['asm'] = '\n'.join(lines)
            elif 'win' in opts:
                rc, out, err = sh(['objdump', '-d', '-M', 'intel', 'x.obj'], tmp)
                res['asm'] = filter_asm(out, opts['asm'])
            else:
                rc, out, err = sh(['objdump', '-d', '-M', 'intel', 'x.o'], tmp)
                res['asm'] = filter_asm(out, opts['asm'])
                if bits == '32':
                    # 32-bit objects keep string data after the code without a symbol:
                    # cut every function after its last 'ret'
                    blocks = []
                    for blk in res['asm'].split('\n\n'):
                        ls = blk.split('\n')
                        last = max([i for i, l in enumerate(ls) if re.search(r'\tret\s*$', l)] or [len(ls) - 1])
                        blocks.append('\n'.join(ls[:last + 1]))
                    res['asm'] = '\n\n'.join(blocks)
            if not res['asm'].strip():
                res['error'] = 'empty disassembly (selection %r)' % opts['asm']

        if 'hexdump' in opts:
            target = 'x.bin' if flat else 'x' if 'exe' in opts else 'x.o'
            rc, out, err = sh(['xxd', '-g', '1', target], tmp)
            res['hex'] = out

        if 'run' in opts:
            obj = 'x.o'
            runcmd = ['./x']
            if flat:
                emu = os.path.join(config.PROJECT, 'tests', 'bits16' if bits == '16' else 'bits32',
                                   'emu16.py' if bits == '16' else ('emu_vga.py' if 'vga' in opts else 'emu32.py'))
                runcmd = [sys.executable, emu, 'x.bin'] + ([opts.get('org', '0x100')] if bits == '16' else [])
                rc = 0
            elif 'exe' in opts:
                rc = 0
            elif 'win' in opts:
                rc, out, err = sh(['x86_64-w64-mingw32-gcc', 'x.obj', '-o', 'x.exe', '-nostartfiles',
                                   '-Wl,-e,' + opts.get('entry', 'main')] + opts.get('libs', '-lkernel32').split(), tmp)
                runcmd = ['wine', 'x.exe']
            elif 'nolibc' in opts and bits != '32':
                rc, out, err = sh(['ld', '-e', opts.get('entry', '_start'), obj, '-o', 'x'], tmp)
            else:
                m32 = ['-m32'] if bits == '32' else []
                if 'ldraw' in opts:
                    cmd = ['ld'] + (['-m', 'elf_i386'] if bits == '32' else []) + ['-e', opts.get('entry', '_start')]
                    for cf in opts.get('cfile', '').split(','):
                        if cf:
                            sh(['gcc'] + m32 + ['-ffreestanding', '-fno-pic', '-fno-stack-protector', '-c', cf,
                                                 '-o', cf + '.o'], tmp)
                            cmd.append(cf + '.o')
                    cmd += [obj, '-o', 'x']
                else:
                    rc, out, err = sh(['nm', obj], tmp)
                    if re.search(r' T _start$', out, re.M) and 'cfile' not in opts:
                        sh(['objcopy', '--redefine-sym', '_start=main', obj, 'm.o'], tmp)
                        obj = 'm.o'
                    cmd = ['gcc'] + m32 + ['-no-pie', '-z', 'noexecstack', '-Wl,--no-warn-rwx-segments']
                    if 'cfile' in opts:
                        cmd += opts['cfile'].split(',')
                    cmd += [obj, '-o', 'x'] + opts.get('libs', '').split()
                rc, out, err = sh(cmd, tmp)
            if rc != 0:
                res['error'] = 'link failed: ' + out + err
                return res
            args = opts.get('args', '').split()
            stdin = opts.get('stdin')
            if stdin is not None:
                stdin = stdin.replace('\\n', '\n')
            rc, out, err = sh(runcmd + args, tmp, timeout=20, stdin=stdin)
            if rc == -999:
                res['error'] = 'program timed out'
                return res
            if 'win' in opts:
                err = '\n'.join(l for l in err.split('\n') if not re.match(r'^[0-9a-f]{4}:', l) and 'wine' not in l.lower())
            text = sanitize(out + (err if err.strip() else ''))
            res['out'] = text
            res['exit'] = rc
            if 'EMU ERROR' in text:
                res['error'] = 'emulator error: ' + text
            want = opts.get('expect')
            if want is not None and str(rc if rc >= 0 else 128 - rc) != want:
                res['error'] = 'exit code %s, expected %s; output:\n%s' % (rc, want, text)
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    return res


def sanitize(text):
    """never print the name or home directory of the person building the manual"""
    home = os.path.expanduser('~')
    user = os.path.basename(home)
    text = text.replace(home, '/home/user')
    if user and len(user) > 2:
        text = text.replace(user, 'user')
    return text


def run_session(script, files, opts):
    """run '$ cmd' lines in a scratch directory; returns the transcript"""
    global STAMP
    if STAMP is None:
        STAMP = ashc_stamp()
    key = hashlib.sha1(json.dumps(['sess', ENGINE_VERSION, STAMP, script, sorted(files.items()),
                                   sorted(opts.items())]).encode()).hexdigest()
    cpath = os.path.join(CACHE_DIR, key[:2], key + '.json')
    if os.path.exists(cpath):
        BUILD.cached += 1
        with open(cpath) as f:
            return json.load(f)
    BUILD.compiled += 1
    tmp = tempfile.mkdtemp(prefix='ashsess')
    bindir = os.path.join(tmp, '.bin')
    os.makedirs(bindir)
    os.symlink(config.ASHC, os.path.join(bindir, 'ashc'))
    with open(os.path.join(bindir, 'shasm'), 'w') as f:
        f.write('#!/bin/sh\nexec java -cp "%s" shasm.Main "$@"\n' % config.JAR)
    os.chmod(os.path.join(bindir, 'shasm'), 0o755)
    env = dict(ENV, PATH=bindir + ':' + ENV.get('PATH', ''))
    out_lines, err = [], None
    try:
        for fn_, body in files.items():
            os.makedirs(os.path.dirname(os.path.join(tmp, fn_)) or tmp, exist_ok=True)
            with open(os.path.join(tmp, fn_), 'w') as f:
                f.write(body)
        for line in script.split('\n'):
            if not line.startswith('$ '):
                out_lines.append(line)
                continue
            cmd = line[2:]
            out_lines.append(re.sub(r'\s+# fails\s*$', '', line))
            expect_fail = bool(re.search(r'#\s*fails\s*$', cmd))
            cmd = re.sub(r'\s*#\s*fails\s*$', '', cmd)
            r = subprocess.run(['bash', '-c', '{ ' + cmd + ' ; } 2>&1'], cwd=tmp, env=env, capture_output=True,
                               timeout=120)
            txt = ANSI.sub('', r.stdout.decode('utf-8', 'replace')).replace(tmp + '/', '').replace(tmp, '.')
            txt = re.sub(r'/tmp/tmp\w+', '/tmp/...', txt)
            if txt.strip():
                out_lines.append(txt.rstrip('\n'))
            if re.search(r'\b(Llamada|requiere|no declarada|Variable no|debe|esperaba|valor|cabe|archivo)\b', txt):
                err = 'Spanish text in session output: ' + txt
            if r.returncode != 0 and 'mayfail' not in opts and not expect_fail:
                err = 'command failed (%d): %s\n%s' % (r.returncode, cmd, txt)
    finally:
        if 'keep' not in opts:
            shutil.rmtree(tmp, ignore_errors=True)
    res = {'text': sanitize('\n'.join(out_lines)), 'error': err}
    if 'keep' in opts:
        res['dir'] = tmp
        return res
    if not err:
        os.makedirs(os.path.dirname(cpath), exist_ok=True)
        with open(cpath, 'w') as f:
            json.dump(res, f)
    return res


def run_shasm(src, opts, files):
    """assemble with shasm; returns listing, run output"""
    global STAMP
    if STAMP is None:
        STAMP = ashc_stamp()
    key = hashlib.sha1(json.dumps(['shasm', ENGINE_VERSION, STAMP, src, sorted(opts.items()),
                                   sorted(files.items())]).encode()).hexdigest()
    cpath = os.path.join(CACHE_DIR, key[:2], key + '.json')
    if os.path.exists(cpath):
        BUILD.cached += 1
        with open(cpath) as f:
            return json.load(f)
    BUILD.compiled += 1
    res = {}
    tmp = tempfile.mkdtemp(prefix='shasm')
    try:
        for fn_, body in files.items():
            with open(os.path.join(tmp, fn_), 'w') as f:
                f.write(body)
        name = opts.get('name', 'example.asm')
        with open(os.path.join(tmp, name), 'w') as f:
            f.write(src)
        fmt = opts.get('fmt', 'bin')
        out = 'x.o' if fmt.startswith(('elf', 'win')) else 'x.bin'
        cmd = ['java', '-cp', config.JAR, 'shasm.Main', '--lang', 'en', '-f', fmt, '-l', 'x.lst', '-o', out]
        cmd += opts.get('flags', '').split() + [name]
        rc, o, e = sh(cmd, tmp)
        diag = (o + e).replace(tmp + '/', '').strip()
        if 'fail' in opts:
            res['diag'] = diag
            if rc == 0:
                res['error'] = 'expected shasm to fail'
            return res
        if rc != 0:
            res['error'] = 'shasm failed: ' + diag
            return res
        if diag:
            res['diag'] = diag
        if os.path.exists(os.path.join(tmp, 'x.lst')):
            with open(os.path.join(tmp, 'x.lst')) as f:
                res['listing'] = f.read()
        if 'hexdump' in opts:
            rc, o, e = sh(['xxd', '-g', '1'] + (['-s', opts['hexskip']] if 'hexskip' in opts else []) + [out], tmp)
            res['hex'] = o
        if 'run' in opts:
            if fmt == 'elf64':
                rc, o, e = sh(['ld', '-e', opts.get('entry', '_start'), 'x.o', '-o', 'x'], tmp)
            elif fmt == 'elf32':
                rc, o, e = sh(['ld', '-m', 'elf_i386', '-e', opts.get('entry', '_start'), 'x.o', '-o', 'x'], tmp)
            else:
                rc, o, e = 1, '', 'run needs elf32/elf64'
            if rc != 0:
                res['error'] = 'link failed: ' + o + e
                return res
            rc, o, e = sh(['./x'], tmp, timeout=20)
            res['out'] = o + e
            res['exit'] = rc
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    if not res.get('error'):
        os.makedirs(os.path.dirname(cpath), exist_ok=True)
        with open(cpath, 'w') as f:
            json.dump(res, f)
    return res


def run_qemu(script, files, opts):
    """run '$ cmd' build lines, then boot opts['run'] headless and take a screenshot"""
    global STAMP
    if STAMP is None:
        STAMP = ashc_stamp()
    key = hashlib.sha1(json.dumps(['qemu', ENGINE_VERSION, STAMP, script, sorted(files.items()),
                                   sorted(opts.items())]).encode()).hexdigest()
    shot_dir = os.path.join(CACHE_DIR, 'shots')
    os.makedirs(shot_dir, exist_ok=True)
    png = os.path.join(shot_dir, key + '.png')
    cpath = os.path.join(CACHE_DIR, key[:2], key + '.json')
    if os.path.exists(cpath) and os.path.exists(png):
        BUILD.cached += 1
        with open(cpath) as f:
            return json.load(f)
    BUILD.compiled += 1
    sess = run_session(script, files, dict(opts, keep='1'))
    res = {'text': sess.get('text', ''), 'error': sess.get('error'), 'png': png}
    if res['error']:
        return res
    tmp = sess['dir']
    try:
        import socket
        import time
        sockdir = tempfile.mkdtemp(dir='/tmp')
        sock = os.path.join(sockdir, 'm.sock')
        cmd = opts['run'].split() + ['-display', 'none', '-monitor', 'unix:%s,server,nowait' % sock]
        p = subprocess.Popen(cmd, cwd=tmp, env=ENV, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        time.sleep(float(opts.get('wait', '3')))
        if p.poll() is not None:
            res['error'] = 'qemu exited early: ' + p.stdout.read().decode(errors='replace')
            return res
        s = socket.socket(socket.AF_UNIX)
        s.connect(sock)
        time.sleep(0.3)
        s.recv(4096)
        s.send(b'screendump shot.ppm\n')
        time.sleep(1.5)
        s.send(b'quit\n')
        time.sleep(0.5)
        try:
            p.wait(timeout=15)
        except subprocess.TimeoutExpired:
            p.kill()
        from PIL import Image as PILImage
        im = PILImage.open(os.path.join(tmp, 'shot.ppm'))
        if 'crop' in opts:
            w, h = [int(x) for x in opts['crop'].split('x')]
            im = im.crop((0, 0, min(w, im.width), min(h, im.height)))
        im.save(png)
        shutil.rmtree(sockdir, ignore_errors=True)
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    os.makedirs(os.path.dirname(cpath), exist_ok=True)
    with open(cpath, 'w') as f:
        json.dump(res, f)
    return res


def check_ash(code, mode, files):
    """compile-only verification of a code block that is not an example"""
    if mode == 'frag':
        body = '\n'.join('    ' + l for l in code.split('\n'))
        prog = '@Entry\npub func _start(): int64 {\n' + body + '\n    return 0;\n}\n'
    elif mode == 'top':
        prog = code + ('\n@Entry\npub func __guide_entry(): int64 { return 0; }\n'
                       if '@Entry' not in code else '')
    else:
        prog = code
    return run_ash(prog, {'check': mode}, files)


# ===================================================================== flowable helpers
class Heading(Paragraph):
    def __init__(self, text, style, level, key, plain):
        Paragraph.__init__(self, text, style)
        self.toc_level = level
        self.bm_key = key
        self.plain = plain


class ChapterOpen(Flowable):
    """remembers the running chapter title for the page header"""
    def __init__(self, title):
        Flowable.__init__(self)
        self.title = title

    def wrap(self, aw, ah):
        return 0, 0

    def draw(self):
        self.canv._ash_chapter = self.title


class Rule(Flowable):
    def __init__(self, width=None, color=ACCENT, thick=1.2, space=6):
        Flowable.__init__(self)
        self.w, self.color, self.thick, self.space = width, color, thick, space

    def wrap(self, aw, ah):
        self.aw = self.w or aw
        return self.aw, self.space * 2

    def draw(self):
        self.canv.setStrokeColor(self.color)
        self.canv.setLineWidth(self.thick)
        self.canv.line(0, self.space, self.aw, self.space)


class FlowDiagram(Flowable):
    """vertical chain of boxes: (title, detail) with arrows between them"""
    BOX_H = 25
    GAP = 9

    def __init__(self, steps):
        Flowable.__init__(self)
        self.steps = steps

    def wrap(self, aw, ah):
        self.aw = aw
        self.height = len(self.steps) * (self.BOX_H + self.GAP) - self.GAP + 4
        return aw, self.height

    def split(self, aw, ah):
        n = int((ah + self.GAP) / (self.BOX_H + self.GAP))
        if n < 2 or n >= len(self.steps):
            return []
        return [FlowDiagram(self.steps[:n]), FlowDiagram(self.steps[n:])]

    def draw(self):
        c = self.canv
        bw = 150
        x = 0
        y = self.height - self.BOX_H
        for i, (title, detail, kind) in enumerate(self.steps):
            fill = {'data': '#FBF3F1', 'stage': '#F3F5F8', 'out': '#EEF6EE'}.get(kind, '#F3F5F8')
            edge = {'data': config.ACCENT, 'stage': '#5B6B7F', 'out': '#2E7D32'}.get(kind, '#5B6B7F')
            c.setFillColor(colors.HexColor(fill))
            c.setStrokeColor(colors.HexColor(edge))
            c.setLineWidth(0.9)
            c.roundRect(x, y, bw, self.BOX_H, 4, fill=1, stroke=1)
            c.setFillColor(INK)
            c.setFont('Sans-Bold', 8.4)
            c.drawCentredString(x + bw / 2, y + self.BOX_H / 2 - 3, title)
            p = Paragraph(inline(detail), ST['small'])
            w, h = p.wrap(self.aw - bw - 16, self.BOX_H + 6)
            p.drawOn(c, x + bw + 12, y + (self.BOX_H - h) / 2)
            if i < len(self.steps) - 1:
                c.setStrokeColor(colors.HexColor('#8A8F98'))
                c.setFillColor(colors.HexColor('#8A8F98'))
                ax = x + bw / 2
                c.line(ax, y, ax, y - self.GAP + 3)
                pth = c.beginPath()
                pth.moveTo(ax - 3, y - self.GAP + 5)
                pth.lineTo(ax + 3, y - self.GAP + 5)
                pth.lineTo(ax, y - self.GAP + 1)
                pth.close()
                c.drawPath(pth, fill=1, stroke=0)
            y -= self.BOX_H + self.GAP


def boxed(flows, kind):
    colors_ = {
        'note': ('#F3F6FA', '#1F5F9E', 'NOTE'),
        'tip': ('#F2F8F2', '#2E7D32', 'TIP'),
        'warning': ('#FDF4F2', '#B3261E', 'WARNING'),
        'important': ('#FBF6EC', '#A0520A', 'IMPORTANT'),
        'machine': ('#F8F6F1', '#8E3A00', 'MACHINE CODE'),
    }
    bg, bar, title = colors_[kind]
    head = Paragraph('<font color="%s"><b>%s</b></font>' % (bar, title), ST['label'])
    t = Table([[[head] + flows]], colWidths=[TEXT_W])
    t.setStyle(TableStyle([
        ('BACKGROUND', (0, 0), (-1, -1), colors.HexColor(bg)),
        ('LINEBEFORE', (0, 0), (0, -1), 2.6, colors.HexColor(bar)),
        ('LEFTPADDING', (0, 0), (-1, -1), 10),
        ('RIGHTPADDING', (0, 0), (-1, -1), 10),
        ('TOPPADDING', (0, 0), (-1, -1), 6),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 4),
    ]))
    return [t, Spacer(1, 8)]


def make_table(rows, widths, header, title_par):
    data = []
    for r, row in enumerate(rows):
        st = ST['cellh'] if (header and r == 0) else ST['cell']
        data.append([Paragraph(inline(c.strip()), st) for c in row])
    ncol = max(len(r) for r in rows)
    for r in data:
        while len(r) < ncol:
            r.append('')
    if widths:
        ws = [float(w) for w in widths.split(',')]
        tot = sum(ws)
        cw = [TEXT_W * w / tot for w in ws]
    else:
        cw = [TEXT_W / ncol] * ncol
    t = Table(data, colWidths=cw, repeatRows=1 if header else 0)
    style = [
        ('GRID', (0, 0), (-1, -1), 0.5, RULE),
        ('VALIGN', (0, 0), (-1, -1), 'TOP'),
        ('LEFTPADDING', (0, 0), (-1, -1), 5),
        ('RIGHTPADDING', (0, 0), (-1, -1), 5),
        ('TOPPADDING', (0, 0), (-1, -1), 3),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 3.5),
        ('ROWBACKGROUNDS', (0, 1 if header else 0), (-1, -1), [colors.white, colors.HexColor('#F7F8FA')]),
    ]
    if header:
        style.append(('BACKGROUND', (0, 0), (-1, 0), ACCENT_DARK))
    t.setStyle(TableStyle(style))
    out = []
    if title_par:
        out.append(title_par)
    out += [t, Spacer(1, 9)]
    return out


# ===================================================================== markup parser
def parse_opts(s):
    """k=v k="v w" flag  ->  dict (flags map to '')"""
    opts = {}
    for m in re.finditer(r'(\w+)(?:=("([^"]*)"|\S+))?', s):
        k = m.group(1)
        if m.group(2) is None:
            opts[k] = ''
        else:
            opts[k] = m.group(3) if m.group(3) is not None else m.group(2)
    return opts


class Doc:
    """numbering state shared by both passes"""
    def __init__(self):
        self.chap = 0
        self.app = 0
        self.cur_chap = ''
        self.sec = self.sub = 0
        self.example_n = self.table_n = self.fig_n = 0
        self.keyseq = 0

    def new_key(self):
        self.keyseq += 1
        return 'h%d' % self.keyseq


HEAD_RE = re.compile(r'^@(chapter|appendix|section|subsection|subsubsection)(?:\{([\w:.-]+)\})?\s+(.*)$')


def number_pass(paths):
    """first pass: assign numbers to labels so [[refs]] can be resolved"""
    d = Doc()
    for p in paths:
        for line in read_source(p):
            m = HEAD_RE.match(line)
            if m:
                kind, label, title = m.groups()
                num = advance(d, kind)
                if label:
                    LABELS[label] = (num, {'chapter': 'Chapter', 'appendix': 'Appendix'}.get(kind, 'Section'))
                continue
            m = re.match(r'^@(example|table|code|flow|session|asmexample|qemu)\b(.*)$', line)
            if m:
                o = parse_opts(m.group(2))
                if 'title' in o and (m.group(1) != 'code' or o.get('title')):
                    if m.group(1) == 'table':
                        d.table_n += 1
                        n = '%s-%d' % (d.cur_chap, d.table_n)
                        kind = 'Table'
                    elif m.group(1) in ('flow', 'qemu'):
                        d.fig_n += 1
                        n = '%s-%d' % (d.cur_chap, d.fig_n)
                        kind = 'Figure'
                    else:
                        d.example_n += 1
                        n = '%s-%d' % (d.cur_chap, d.example_n)
                        kind = 'Example'
                    if 'label' in o:
                        LABELS[o['label']] = (n, kind)


def advance(d, kind):
    if kind == 'chapter':
        d.chap += 1
        d.cur_chap = str(d.chap)
        d.sec = d.sub = d.example_n = d.table_n = d.fig_n = 0
        return d.cur_chap
    if kind == 'appendix':
        d.app += 1
        d.cur_chap = chr(64 + d.app)
        d.sec = d.sub = d.example_n = d.table_n = d.fig_n = 0
        return d.cur_chap
    if kind == 'section':
        d.sec += 1
        d.sub = 0
        return '%s.%d' % (d.cur_chap, d.sec)
    if kind == 'subsection':
        d.sub += 1
        return '%s.%d.%d' % (d.cur_chap, d.sec, d.sub)
    return ''


GEN_CACHE = {}


def read_source(path):
    """file lines with @gen expanded"""
    with open(path, encoding='utf-8') as f:
        lines = f.read().split('\n')
    out = []
    for line in lines:
        m = re.match(r'^@gen\s+(\w+)\s*(.*)$', line)
        if m:
            key = (m.group(1), m.group(2))
            if key not in GEN_CACHE:
                import gen
                GEN_CACHE[key] = getattr(gen, m.group(1))(m.group(2).strip()).split('\n')
            out.extend(GEN_CACHE[key])
        else:
            out.append(line)
    return out


class Parser:
    def __init__(self, doc):
        self.d = doc
        self.story = []
        self.files = {}
        self.pending_index = []

    def para(self, text, style=None):
        idx = ''.join('<index item="%s"/>' % esc(t).replace('"', '') for t in self.pending_index)
        self.pending_index = []
        self.story.append(Paragraph(idx + inline(text), style or ST['body']))

    def heading(self, kind, label, title):
        d = self.d
        num = advance(d, kind)
        key = label or d.new_key()
        idx = ''.join('<index item="%s"/>' % esc(t).replace('"', '') for t in self.pending_index)
        self.pending_index = []
        if kind in ('chapter', 'appendix'):
            self.story.append(NextPageTemplate('normal'))
            self.story.append(PageBreak())
            word = 'CHAPTER' if kind == 'chapter' else 'APPENDIX'
            self.story.append(ChapterOpen(('%s %s ' % (word, num)) + title.upper()))
            self.story.append(Spacer(1, 30))
            self.story.append(Paragraph('%s %s' % (word, num), ST['h0k']))
            plain = ('%s ' % num) + title if kind == 'chapter' else 'Appendix %s %s' % (num, title)
            self.story.append(Heading('<a name="%s"/>%s%s' % (key, idx, inline(title).upper()), ST['h0'], 0, key, plain))
            self.story.append(Rule(space=4))
            self.story.append(Spacer(1, 12))
        elif kind == 'section':
            self.story.append(CondPageBreak(1.3 * inch))
            self.story.append(Heading('<a name="%s"/>%s%s&nbsp;&nbsp;&nbsp;%s' % (key, idx, num, inline(title).upper()),
                                      ST['h1'], 1, key, '%s  %s' % (num, title)))
        elif kind == 'subsection':
            self.story.append(CondPageBreak(1.0 * inch))
            self.story.append(Heading('<a name="%s"/>%s%s&nbsp;&nbsp;&nbsp;%s' % (key, idx, num, inline(title)),
                                      ST['h2'], 2, key, '%s  %s' % (num, title)))
        else:
            self.story.append(CondPageBreak(0.8 * inch))
            self.story.append(Heading('<a name="%s"/>%s%s' % (key, idx, inline(title)), ST['h3'], 3, key, title))

    def caption(self, kind, title, label):
        d = self.d
        if kind == 'Table':
            d.table_n += 1
            n = '%s-%d' % (d.cur_chap, d.table_n)
        elif kind == 'Figure':
            d.fig_n += 1
            n = '%s-%d' % (d.cur_chap, d.fig_n)
        else:
            d.example_n += 1
            n = '%s-%d' % (d.cur_chap, d.example_n)
        anchor = '<a name="%s"/>' % label if label else ''
        return Paragraph('%s%s %s.&nbsp;&nbsp;%s' % (anchor, kind, n, inline(title)), ST['caption'])

    # ------------------------------------------------------------ blocks
    def code_block(self, lang, opts, body):
        title = opts.get('title')
        cap = self.caption('Example', title, opts.get('label')) if title else None
        if 'file' in opts:
            self.files[opts['file']] = body
        if lang == 'ash' and 'nocheck' not in opts:
            mode = 'frag' if 'frag' in opts else 'top' if 'top' in opts else (
                'full' if '@Entry' in body or 'file' in opts else 'top')
            if 'file' in opts and '@Entry' not in body:
                mode = 'top'
            files = {k: v for k, v in self.files.items() if k != opts.get('file')}
            res = check_ash(body, mode, files)
            if res.get('error'):
                BUILD.fail('code block does not compile:\n' + body + '\n' + res['error'])
        if lang == 'ashir_check':
            lang = 'ashir'
        fl = code_flowables(body, lang, opts.get('label_text') or (opts['file'] if 'file' in opts else None))
        if cap:
            self.story.append(KeepTogether([cap, fl[0]]) if len(body.split('\n')) < 30 else cap)
            if len(body.split('\n')) >= 30:
                self.story.append(fl[0])
            self.story.append(fl[1])
        else:
            self.story.extend(fl)

    def example(self, opts, body):
        title = opts.get('title')
        if title:
            self.story.append(self.caption('Example', title, opts.get('label')))
        files_now = dict(self.files)
        if 'name' in opts:
            self.files[opts['name']] = body
        res = run_ash(body, {k: v for k, v in opts.items() if k not in ('title', 'label', 'show', 'translate')},
                      files_now)
        EXAMPLE_LOG.append({'file': BUILD.cur, 'title': title, 'src': body, 'out': res.get('out'),
                            'exit': res.get('exit'), 'diag': res.get('diag'), 'error': res.get('error')})
        if res.get('error'):
            BUILD.fail('example failed:\n' + body + '\n' + res['error'])
        show = opts.get('show', 'src,out,ir,asm,diag').split(',')
        if 'src' in show:
            self.story.extend(code_flowables(body, 'ashir' if 'ashir' in opts else 'ash',
                                             opts.get('name') or ('ASHIR source' if 'ashir' in opts else None)))
        if 'diag' in res and 'diag' in show:
            if 'translate' in opts:
                self.story.extend(code_flowables(opts['translate'].replace('\\n', '\n'), 'diag',
                                                 'compiler diagnostics (message translated to English)'))
            else:
                if re.search(r'\b(el|la|los|las|debe|valor|cabe|esperaba|tipo|linea|funcion|usa|declarada|Llamada|requiere|desconocid[oa]|Variable no)\b', res['diag']):
                    BUILD.fail('diagnostic text is not English; add translate="...":\n' + res['diag'])
                self.story.extend(code_flowables(res['diag'], 'diag'))
        if 'hex' in res:
            self.story.extend(code_flowables(trim(res['hex'], int(opts.get('hexlines', 40))), 'text', 'hex dump (xxd)'))
        if 'out' in res and 'out' in show:
            txt = trim(res['out'], int(opts.get('maxlines', 60)))
            rc = res['exit']
            if rc < 0:
                sig = -rc
                st = 'terminated by signal %d%s' % (sig, ' (SIGABRT)' if sig == 6 else ' (SIGSEGV)' if sig == 11 else '')
            else:
                st = 'exit status %d' % rc
            label = 'output — ' + st
            if not txt.strip():
                txt = '(no output)'
            self.story.extend(code_flowables(txt, 'output', label))
        if 'ir' in res and 'ir' in show:
            self.story.extend(code_flowables(trim(res['ir'], int(opts.get('irlines', 90))), 'ashir'))
        if 'asm' in res and 'asm' in show:
            lab = {'16': 'x86 disassembly (16-bit)', '32': 'x86 disassembly (32-bit)'}.get(
                opts.get('bits', '64'), 'x86-64 disassembly (objdump, Intel syntax)')
            self.story.extend(code_flowables(trim(res['asm'], int(opts.get('asmlines', 110))), 'disasm', lab))

    def asm_example(self, opts, body):
        title = opts.get('title')
        if title:
            self.story.append(self.caption('Example', title, opts.get('label')))
        res = run_shasm(body, {k: v for k, v in opts.items() if k not in ('title', 'label', 'show')},
                        dict(self.files))
        EXAMPLE_LOG.append({'file': BUILD.cur, 'title': title, 'src': body, 'out': res.get('out'),
                            'exit': res.get('exit'), 'diag': res.get('diag'), 'error': res.get('error')})
        if 'name' in opts:
            self.files[opts['name']] = body
        if res.get('error'):
            BUILD.fail('shasm example failed:\n' + body + '\n' + res['error'])
        show = opts.get('show', 'src,listing,out,diag,hex').split(',')
        if 'src' in show:
            self.story.extend(code_flowables(body, 'asm', opts.get('name')))
        if res.get('diag') and 'diag' in show:
            self.story.extend(code_flowables(res['diag'], 'diag', 'shasm diagnostics'))
        if res.get('listing') and 'listing' in show:
            self.story.extend(code_flowables(trim(res['listing'], int(opts.get('listlines', 80))), 'listing'))
        if 'hex' in res and 'hex' in show:
            self.story.extend(code_flowables(trim(res['hex'], int(opts.get('hexlines', 40))), 'text', 'hex dump (xxd)'))
        if 'out' in res and 'out' in show:
            txt = res['out'] if res['out'].strip() else '(no output)'
            self.story.extend(code_flowables(txt, 'output', 'output \u2014 exit status %d' % res['exit']))

    def list_block(self, items):
        """items: list of (indent, ordered, text)"""
        def build(i, base):
            flows, ordered = [], items[i][1]
            while i < len(items) and items[i][0] >= base:
                ind, ordd, text = items[i]
                if ind > base:
                    sub, i = build(i, ind)
                    flows[-1].append(sub)
                    continue
                flows.append([Paragraph(inline(text), ST['list'])])
                i += 1
            lf = ListFlowable([ListItem(f) for f in flows], bulletType='1' if ordered else 'bullet',
                              start=None if ordered else '•', leftIndent=14, bulletFontSize=8 if not ordered else 9.5,
                              bulletFontName='Sans', bulletColor=ACCENT if not ordered else INK,
                              bulletOffsetY=0 if ordered else -1)
            return lf, i
        lf, _ = build(0, items[0][0])
        self.story.append(lf)
        self.story.append(Spacer(1, 6))

    def run(self, lines):
        i, n = 0, len(lines)
        para = []

        def flush():
            if para:
                self.para(' '.join(para))
                para.clear()

        while i < n:
            line = lines[i]
            s = line.strip()
            if s.startswith('%%'):                      # source comment
                i += 1
                continue
            if not s:
                flush()
                i += 1
                continue
            m = HEAD_RE.match(s)
            if m and line.startswith('@'):
                flush()
                self.heading(*m.groups())
                i += 1
                continue
            if line.startswith('@'):
                flush()
                m = re.match(r'^@(\w+)\s*(.*)$', line)
                cmd, rest = m.group(1), m.group(2)
                if cmd in ('code', 'example', 'table', 'output', 'note', 'tip', 'warning', 'important',
                           'machine', 'diag', 'session', 'file', 'flow', 'asmexample', 'qemu'):
                    body, i = self.collect(lines, i + 1, cmd)
                    if cmd == 'code':
                        lang, _, o = rest.partition(' ')
                        self.code_block(lang.strip() or 'text', parse_opts(o), body)
                    elif cmd == 'example':
                        self.example(parse_opts(rest), body)
                    elif cmd == 'qemu':
                        o = parse_opts(rest)
                        r = run_qemu(body, dict(self.files), o)
                        if r.get('error'):
                            BUILD.fail('qemu block failed: ' + r['error'])
                        elif 'nosession' not in o:
                            self.story.extend(code_flowables(trim(r['text'], int(o.get('maxlines', 40))), 'session'))
                        if os.path.exists(r.get('png', '')):
                            from reportlab.platypus import Image as RLImage
                            from reportlab.lib.utils import ImageReader
                            iw, ih = ImageReader(r['png']).getSize()
                            w = TEXT_W * float(o.get('scale', '0.86'))
                            img = RLImage(r['png'], width=w, height=w * ih / iw)
                            t = Table([[img]], colWidths=[w + 8])
                            t.setStyle(TableStyle([('BOX', (0, 0), (-1, -1), 0.8, colors.HexColor('#5B616B')),
                                                   ('BACKGROUND', (0, 0), (-1, -1), colors.black),
                                                   ('LEFTPADDING', (0, 0), (-1, -1), 4), ('RIGHTPADDING', (0, 0), (-1, -1), 4),
                                                   ('TOPPADDING', (0, 0), (-1, -1), 4), ('BOTTOMPADDING', (0, 0), (-1, -1), 4)]))
                            cap = self.caption('Figure', o.get('title', 'Screenshot'), o.get('label'))
                            self.story.append(KeepTogether([cap, t]))
                            self.story.append(Spacer(1, 10))
                    elif cmd == 'asmexample':
                        self.asm_example(parse_opts(rest), body)
                    elif cmd == 'flow':
                        o = parse_opts(rest)
                        steps = []
                        for ln in body.split('\n'):
                            if ln.strip():
                                parts = [x.strip() for x in ln.split('|')]
                                steps.append((parts[0], parts[1] if len(parts) > 1 else '', parts[2] if len(parts) > 2 else 'stage'))
                        if 'title' in o:
                            self.story.append(self.caption('Figure', o['title'], o.get('label')))
                        self.story.append(FlowDiagram(steps))
                        self.story.append(Spacer(1, 10))
                    elif cmd == 'file':
                        self.files[rest.strip()] = body
                    elif cmd == 'session':
                        o = parse_opts(rest)
                        r = run_session(body, dict(self.files), o)
                        if r.get('error'):
                            BUILD.fail('session failed: ' + r['error'])
                        if 'title' in o:
                            self.story.append(self.caption('Example', o['title'], o.get('label')))
                        txt = trim(r['text'], int(o.get('maxlines', 70)))
                        self.story.extend(code_flowables(txt, 'session'))
                    elif cmd in ('output', 'diag'):
                        o = parse_opts(rest)
                        self.story.extend(code_flowables(body, 'output' if cmd == 'output' else 'diag', o.get('label')))
                    elif cmd == 'table':
                        o = parse_opts(rest)
                        rows = [r.split(' | ') if ' | ' in r else r.split('|') for r in body.split('\n') if r.strip()]
                        cap = self.caption('Table', o['title'], o.get('label')) if 'title' in o else None
                        self.story.extend(make_table(rows, o.get('widths'), 'noheader' not in o, cap))
                    else:
                        sub = Parser(self.d)
                        sub.files = self.files
                        sub.run(body.split('\n'))
                        flows = [f for f in sub.story]
                        while flows and isinstance(flows[-1], Spacer):
                            flows.pop()
                        self.story.extend(boxed(flows, cmd))
                    continue
                if cmd == 'pagebreak':
                    self.story.append(PageBreak())
                elif cmd == 'condbreak':
                    self.story.append(CondPageBreak(float(rest or 2) * inch))
                elif cmd == 'index':
                    self.pending_index += [t.strip() for t in rest.split(';') if t.strip()]
                elif cmd == 'spacer':
                    self.story.append(Spacer(1, float(rest)))
                elif cmd == 'resetfiles':
                    self.files = {}
                elif cmd == 'libdir':
                    src, _, dest = rest.partition(' as ')
                    base = os.path.join(HERE, src.strip())
                    for dp, dn, fns in os.walk(base):
                        for fn_ in fns:
                            full = os.path.join(dp, fn_)
                            rel = os.path.relpath(full, base)
                            with open(full, encoding='utf-8', errors='replace') as f:
                                self.files[os.path.join(dest.strip(), rel)] = f.read()
                elif cmd == 'center':
                    self.para(rest, ST['centered'])
                else:
                    BUILD.warn('unknown command @' + cmd)
                i += 1
                continue
            lm = re.match(r'^(\s*)(- |\d+\. )(.*)$', line)
            if lm:
                flush()
                items = []
                while i < n:
                    lm = re.match(r'^(\s*)(- |\d+\. )(.*)$', lines[i])
                    if lm:
                        items.append([len(lm.group(1)), lm.group(2) != '- ', lm.group(3)])
                        i += 1
                    elif lines[i].startswith('  ') and lines[i].strip() and items:
                        items[-1][2] += ' ' + lines[i].strip()
                        i += 1
                    else:
                        break
                self.list_block(items)
                continue
            para.append(s)
            i += 1
        flush()

    @staticmethod
    def collect(lines, i, cmd):
        body = []
        depth = 0
        while i < len(lines):
            l = lines[i]
            if l.strip() == '@end' and depth == 0:
                return '\n'.join(body), i + 1
            body.append(l)
            i += 1
        raise SystemExit('missing @end for @' + cmd)


# ===================================================================== page drawing
def draw_logo(c, x_right, y_top, height):
    """logo with its right edge at x_right; returns the drawn width"""
    if os.path.exists(config.LOGO):
        from reportlab.lib.utils import ImageReader
        img = ImageReader(config.LOGO)
        iw, ih = img.getSize()
        w = height * iw / ih
        c.drawImage(img, x_right - w, y_top - height, w, height, mask='auto')
        return w
    # text mark placeholder
    c.saveState()
    c.setFont('Sans-Bold', height * 0.8)
    c.setFillColor(ACCENT)
    w = c.stringWidth('ASH', 'Sans-Bold', height * 0.8)
    c.drawString(x_right - w, y_top - height * 0.78, 'ASH')
    c.restoreState()
    return w


def footer(c, doc):
    c.saveState()
    c.setFont('Sans', 7.4)
    c.setFillColor(MUTED)
    c.drawString(LM, 0.5 * inch, config.COPYRIGHT)
    c.setFont('Sans-Bold', 8.4)
    c.setFillColor(INK)
    num = str(c.getPageNumber())
    c.drawRightString(PAGE_W - RM, 0.5 * inch, num)
    c.setFont('Sans', 7.4)
    c.setFillColor(MUTED)
    c.drawRightString(PAGE_W - RM - c.stringWidth(num, 'Sans-Bold', 8.4) - 14, 0.5 * inch,
                      'Document Number: %s  Rev. %s' % (config.DOC_NUMBER, config.REVISION))
    c.restoreState()


def on_normal(c, doc):
    c.saveState()
    c.setFont('Sans', 7.6)
    c.setFillColor(ACCENT)
    chap = getattr(c, '_ash_chapter', '')
    c.drawString(LM, PAGE_H - 0.55 * inch, config.RUNNING_TITLE + (('  —  ' + chap) if chap else ''))
    draw_logo(c, PAGE_W - RM, PAGE_H - 0.38 * inch, 0.3 * inch)
    c.setStrokeColor(RULE)
    c.setLineWidth(0.5)
    c.line(LM, PAGE_H - 0.68 * inch, PAGE_W - RM, PAGE_H - 0.68 * inch)
    c.restoreState()
    footer(c, doc)


def on_plain(c, doc):
    footer(c, doc)


def on_cover(c, doc):
    c.saveState()
    draw_logo(c, PAGE_W - 0.9 * inch, PAGE_H - 0.75 * inch, 0.75 * inch)
    x = 0.9 * inch
    y = PAGE_H - 2.3 * inch
    c.setFillColor(ACCENT)
    c.setFont('Sans-Bold', 34)
    for t in config.TITLE_LINES:
        c.drawString(x, y, t)
        y -= 40
    y -= 4
    c.setFont('Sans-Bold', 12)
    c.drawString(x, y, config.COVER_LABEL)
    c.setStrokeColor(ACCENT)
    c.setLineWidth(1)
    c.line(x, y - 6, x + 4.1 * inch, y - 6)
    y -= 26
    c.setFillColor(INK)
    c.setFont('Sans', 10.5)
    c.drawString(x, y, config.SUBTITLE)
    y -= 26
    c.setFillColor(ACCENT)
    c.setFont('Sans-Bold', 10.5)
    c.drawString(x, y, config.DATE)
    y -= 16
    c.drawString(x, y, config.DOC_NUMBER)
    c.restoreState()
    c.saveState()
    c.setFont('Sans', 7.6)
    c.setFillColor(MUTED)
    c.drawString(x, 0.5 * inch, 'Document Number: %s' % config.DOC_NUMBER)
    c.drawString(x, 0.5 * inch + 11, config.COPYRIGHT)
    c.restoreState()


class GuideDoc(BaseDocTemplate):
    def __init__(self, fn):
        BaseDocTemplate.__init__(self, fn, pagesize=letter, leftMargin=LM, rightMargin=RM,
                                 topMargin=TM, bottomMargin=BM, title=' '.join(config.TITLE_LINES),
                                 author='Mallionic Inc.', subject='The Ashley Project (ASH)',
                                 creator='build_guide.py')
        frame = Frame(LM, BM, TEXT_W, PAGE_H - TM - BM, id='f', leftPadding=0, rightPadding=0,
                      topPadding=0, bottomPadding=0)
        self.addPageTemplates([
            PageTemplate('cover', [frame], onPage=on_cover),
            PageTemplate('plain', [frame], onPage=on_plain),
            PageTemplate('normal', [frame], onPageEnd=on_normal),
        ])

    def afterFlowable(self, f):
        if isinstance(f, Heading):
            lvl = f.toc_level
            if lvl <= 2:
                self.notify('TOCEntry', (lvl, f.plain, self.page, f.bm_key))
            self.canv.addOutlineEntry(f.plain, f.bm_key, min(lvl, 3), closed=lvl >= 1)


class GuideIndex(SimpleIndex):
    def _getlastEntries(self, dummy=None):
        lE = self._lastEntries or self._entries
        if not lE:
            return [(('(index is built on the next pass)',), [])]
        return list(sorted(lE.items()))


# ===================================================================== front matter
def front_matter(story):
    story.append(Spacer(1, 10))
    story.append(NextPageTemplate('plain'))
    story.append(PageBreak())
    story.append(Spacer(1, 3.4 * inch))
    story.append(Paragraph('This page intentionally left blank.', ST['centered']))
    story.append(NextPageTemplate('normal'))
    story.append(PageBreak())
    legal = os.path.join(HERE, 'chapters', '00_legal.txt')
    if os.path.exists(legal):
        p = Parser(Doc())
        p.run(read_source(legal))
        story.extend(p.story)
        story.append(PageBreak())
    story.append(ChapterOpen('CONTENTS'))
    story.append(Paragraph('<a name="contents"/>Contents', ST['toctitle']))
    toc = TableOfContents(levelStyles=TOC_STYLES, dotsMinLevel=0)
    story.append(toc)


def build(only=None, check=False):
    paths = sorted(glob.glob(os.path.join(HERE, 'chapters', '[0-9][0-9]_*.txt')))
    paths = [p for p in paths if not os.path.basename(p).startswith('00_')]
    if only:
        paths = [p for p in paths if os.path.basename(p).startswith(only)]
    number_pass(paths)
    story = []
    front_matter(story)
    doc = Doc()
    parser = Parser(doc)
    for p in paths:
        BUILD.cur = os.path.basename(p)
        print('  parsing %s' % BUILD.cur, flush=True)
        parser.files = {}
        parser.run(read_source(p))
    story.extend(parser.story)
    # index
    index = GuideIndex(dot=' . ', headers=True, style=[ParagraphStyle('ix0', fontName='Sans', fontSize=8.6, leading=10.4),
                                                       ParagraphStyle('ix1', fontName='Sans', fontSize=8.4, leading=10.2, leftIndent=12)])
    story.append(NextPageTemplate('normal'))
    story.append(PageBreak())
    story.append(ChapterOpen('INDEX'))
    story.append(Heading('<a name="index"/>INDEX', ST['h0'], 0, 'index', 'Index'))
    story.append(Rule(space=4))
    story.append(index)

    print('examples: %d compiled, %d from cache' % (BUILD.compiled, BUILD.cached))
    with open(os.path.join(HERE, 'examples_log.txt'), 'w') as f:
        for e in EXAMPLE_LOG:
            f.write('=' * 70 + '\n%s :: %s\n' % (e['file'], e['title']))
            if e['out'] is not None:
                f.write('--- output (exit %s)\n%s\n' % (e['exit'], e['out']))
            if e['diag']:
                f.write('--- diag\n%s\n' % e['diag'])
    for w in BUILD.warnings:
        print('warning: ' + w)
    if BUILD.failures:
        print('\n%d FAILURES:' % len(BUILD.failures))
        for f in BUILD.failures:
            print('-' * 70)
            print(f)
        if check or '--strict' in sys.argv:
            sys.exit(1)
    if check:
        return
    out = config.OUTPUT if not only else os.path.join(HERE, 'preview.pdf')
    os.makedirs(os.path.dirname(out), exist_ok=True)
    doc_t = GuideDoc(out)
    doc_t.multiBuild(story, canvasmaker=index.getCanvasMaker())
    print('written %s (%d pages)' % (out, doc_t.page))


if __name__ == '__main__':
    only = None
    if '--only' in sys.argv:
        only = sys.argv[sys.argv.index('--only') + 1]
    build(only, '--check' in sys.argv)
