"""
Pygments lexers for ASH, ASHIR and objdump listings, plus the colour
scheme used by the code boxes of the ASH Programming Guide.
"""
import re
from pygments.lexer import RegexLexer, bygroups, include, words
from pygments.token import (Token, Comment, Keyword, Name, Number, String,
                            Operator, Punctuation, Text, Whitespace, Generic)

ASH_KEYWORDS = (
    'func', 'class', 'struct', 'return', 'terminate', 'unreturnable', 'unreturn',
    'if', 'elif', 'else', 'switch', 'case', 'default', 'while', 'for', 'repeat',
    'loop', 'break', 'continue', 'import', 'extern', 'define', 'typedef',
    'redefine', 'pub', 'priv', 'general', 'ungeneral', 'static', 'const',
    'volatile', 'runnable', 'matrix', 'global', 'macro',
)
ASH_TYPES = (
    'int8', 'int16', 'int32', 'int64', 'uint8', 'uint16', 'uint32', 'uint64',
    'int8_t', 'int16_t', 'int32_t', 'int64_t', 'uint8_t', 'uint16_t',
    'uint32_t', 'uint64_t', 'byte', 'word', 'dword', 'qword', 'char', 'uchar',
    'schar', 'bool', 'short', 'ushort', 'int', 'uint', 'long', 'ulong', 'llong',
    'ullong', 'size_t', 'ssize_t', 'uintptr_t', 'intptr_t', 'ptrdiff_t',
    'float', 'double', 'float32', 'float64', 'string', 'cstring', 'ptr', 'void',
    'unsigned', 'signed',
)
ASH_BUILTINS = (
    'va_arg', 'va_count', 'rows', 'cols', 'count', 'len', 'length',
    'emit', 'emit8', 'emit16', 'emit32', 'emit64',
)
REGS = (r'\b(r(?:[abcd]x|[sd]i|[sb]p|ip)|e(?:[abcd]x|[sd]i|[sb]p|ip)|[abcd]x|[sd]i|[sb]p|'
        r'[abcd][lh]|[sd]il|[sb]pl|r(?:8|9|1[0-5])[dwb]?|[cdefgs]s|[xyz]mm\d+|st\d?|mm\d|'
        r'cr[0-8]|dr[0-7])\b')


class AshLexer(RegexLexer):
    name = 'ASH'
    aliases = ['ash']
    flags = re.MULTILINE

    tokens = {
        'root': [
            (r'^\s*%[a-z_]+\b.*?$', Comment.Preproc),
            (r'\n', Whitespace),
            (r'\s+', Whitespace),
            (r'//.*?$', Comment.Single),
            (r'/\*', Comment.Multiline, 'comment'),
            (r'^\s*"use strict"\s*;?', Keyword.Pseudo),
            (r'"""', String, 'tstring'),
            (r'L?"', String, 'string'),
            (r"'(\\.|[^'\\])'", String.Char),
            (r'@[A-Za-z_]\w*', Name.Decorator),
            (r'\b(asm|__asm__)(\s*)(\{)', bygroups(Keyword, Whitespace, Punctuation), 'asmblock'),
            (r'\b(asm|__asm__)\b', Keyword),
            (words(ASH_KEYWORDS, suffix=r'\b'), Keyword),
            (words(ASH_TYPES, suffix=r'\b'), Keyword.Type),
            (r'\b(true|false|TRUE|FALSE|NULL|null|nil|NIL)\b', Keyword.Constant),
            (r'\b(ashley)(\.)(\w+)', bygroups(Name.Builtin, Punctuation, Name.Builtin)),
            (words(ASH_BUILTINS, suffix=r'\b(?=\s*\()'), Name.Builtin),
            (r'\b0[xX][0-9A-Fa-f_]+(ULL|UL|LL|U|L)?\b', Number.Hex),
            (r'\b0[bB][01_]+\b', Number.Bin),
            (r'\b0[oO][0-7_]+\b', Number.Oct),
            (r'\b\d[\d_]*\.\d+([eE][-+]?\d+)?[fF]?\b', Number.Float),
            (r'\b\d[\d_]*(ULL|UL|LL|U|L)?\b', Number.Integer),
            (r'\b[A-Za-z_]\w*(?=\s*\()', Name.Function),
            (r'\b[A-Z][A-Z0-9_]{2,}\b', Name.Constant),
            (r'\b[A-Za-z_]\w*\b', Name),
            (r'\*\*\*|\*\*|<<=|>>=|\+=|-=|\*=|/=|%=|&=|\|=|\^=|->|\+\+|--|==|!=|<=|>=|&&|\|\||[-+*/%&|^~!<>=#?:]', Operator),
            (r'\.\.\.', Operator),
            (r'[{}()\[\];,.]', Punctuation),
        ],
        'comment': [
            (r'[^*]+', Comment.Multiline),
            (r'\*/', Comment.Multiline, '#pop'),
            (r'\*', Comment.Multiline),
        ],
        'string': [
            (r'\\(x[0-9A-Fa-f]{2}|u[0-9A-Fa-f]{4}|[0-7]{1,3}|.)', String.Escape),
            (r'%[-+ #0]*(\d+|\*)?(\.(\d+|\*))?(hh|h|ll|l|z|j|t|L)?[diouxXeEfFgGaAcspn%]', String.Interpol),
            (r'"', String, '#pop'),
            (r'[^"\\%\n]+', String),
            (r'%', String),
            (r'\n', String),
        ],
        'tstring': [
            (r'"""', String, '#pop'),
            (r'\\.', String.Escape),
            (r'[^"\\]+', String),
            (r'"', String),
        ],
        'asmblock': [
            (r'\}', Punctuation, '#pop'),
            (r'\n', Whitespace),
            (r'\s+', Whitespace),
            (r';.*?$', Comment.Single),
            (r'//.*?$', Comment.Single),
            (r'\b(byte|word|dword|qword|tword|oword|ptr|rel|abs)\b', Keyword.Type),
            (REGS, Name.Builtin),
            (r'\b0[xX][0-9A-Fa-f]+\b|\b\d+\b', Number),
            (r'^(\s*)([A-Za-z_.][\w.]*:)', bygroups(Whitespace, Name.Label)),
            (r'(^|(?<=\n))(\s*)([a-z][a-z0-9]*)\b', bygroups(Whitespace, Whitespace, Keyword.Reserved)),
            (r'\b[A-Za-z_]\w*\b', Name.Variable),
            (r'[\[\]+\-*,:]', Operator),
            (r'.', Text),
        ],
    }


class AshirLexer(RegexLexer):
    name = 'ASHIR'
    aliases = ['ashir']
    flags = re.MULTILINE
    tokens = {
        'root': [
            (r'\n', Whitespace),
            (r'\s+', Whitespace),
            (r'//.*?$', Comment.Single),
            (r';.*?$', Comment.Single),
            (r'\[(entry|naked)\]', Name.Decorator),
            (r'\b(func|extern|ret)\b', Keyword),
            (r'\b(byte|word|dword|qword|int8|int16|int32|int64|uint8|uint16|uint32|uint64|ptr|float32|float64|void)\b', Keyword.Type),
            (r'\.[a-z][a-z0-9]*', Comment.Preproc),
            (r'(%[A-Za-z_]\w*)(\s+)([a-z_]+)(?=\[)', bygroups(Name.Variable, Whitespace, Keyword.Reserved)),
            (r'^(\s*)(store|call|inc|dec|load)\b', bygroups(Whitespace, Keyword.Reserved)),
            (r'\b(eq|ne|lt|le|gt|ge|ltu|leu|gtu|geu|b|be|a|ae)\b(?=\s*,)', Operator.Word),
            (r'x[0-9a-f]{8,}', Number.Hex),
            (r'"(\\.|[^"\\])*"', String),
            (r'\b0[xX][0-9A-Fa-f]+\b|-?\b\d+\b', Number),
            (r'\b__L\d+\b', Name.Label),
            (r'\b[A-Za-z_]\w*(?=\()', Name.Function),
            (r'\b[A-Za-z_]\w*\b', Name),
            (r'[{}()\[\],:]', Punctuation),
            (r'.', Text),
        ]
    }


class ObjdumpLexer(RegexLexer):
    """Intel-syntax `objdump -d` / shasm listing lines."""
    name = 'Disasm'
    aliases = ['disasm']
    flags = re.MULTILINE
    tokens = {
        'root': [
            (r'^([0-9a-f]+)( <)([^>]+)(>:)$', bygroups(Number.Hex, Punctuation, Name.Function, Punctuation)),
            (r'^(\s*[0-9a-f]+:)(\t)((?:[0-9a-f]{2} )+\s*)(\t?)', bygroups(Comment, Whitespace, Comment.Special, Whitespace), 'insn'),
            (r'^(\s*[0-9a-f]+:)(\t)((?:[0-9a-f]{2} ?)+)$', bygroups(Comment, Whitespace, Comment.Special)),
            (r'\n', Whitespace),
            (r'.', Text),
        ],
        'insn': [
            (r'\n', Whitespace, '#pop'),
            (r'(\s*)([a-z][a-z0-9.]*)', bygroups(Whitespace, Keyword.Reserved), 'operands'),
        ],
        'operands': [
            (r'(?=\n)', Text, '#pop:2'),
            (r'#.*?(?=\n)', Comment),
            (r'<[^>]*>', Name.Function),
            (r'\b(BYTE|WORD|DWORD|QWORD|TBYTE|XMMWORD|PTR)\b', Keyword.Type),
            (REGS, Name.Builtin),
            (r'\b0x[0-9a-f]+\b|\b\d+\b', Number),
            (r'[a-z_][\w.@]*', Name),
            (r'[ \t]+', Whitespace),
            (r'.', Punctuation),
        ],
    }


# ------------------------------------------------------------------ colours
PALETTE = {
    Comment: ('#6E7781', False, True),
    Comment.Preproc: ('#7A3E9D', True, False),
    Keyword: ('#B3261E', True, False),
    Keyword.Type: ('#1F5F9E', False, False),
    Keyword.Constant: ('#A0520A', True, False),
    Keyword.Pseudo: ('#2E7D32', True, False),
    Keyword.Reserved: ('#8E3A00', True, False),
    Operator.Word: ('#B3261E', False, False),
    Name.Decorator: ('#2E7D32', True, False),
    Name.Function: ('#6B4A00', False, False),
    Name.Builtin: ('#0B6E7A', False, False),
    Name.Constant: ('#A0520A', False, False),
    Name.Label: ('#7A3E9D', False, False),
    Name.Variable: ('#24425E', False, False),
    Number: ('#A0520A', False, False),
    String: ('#2E7D32', False, False),
    String.Escape: ('#A0520A', True, False),
    String.Interpol: ('#B07800', True, False),
    String.Char: ('#2E7D32', False, False),
    Generic.Output: ('#3A3F47', False, False),
    Operator: ('#4A4F57', False, False),
    Punctuation: ('#4A4F57', False, False),
}
DEFAULT_COLOR = ('#1E2228', False, False)


def style_for(tok):
    while tok is not None:
        if tok in PALETTE:
            return PALETTE[tok]
        tok = tok.parent
    return DEFAULT_COLOR


class SessionLexer(RegexLexer):
    """shell transcript: '$ command' lines highlighted, the rest is output"""
    name = 'Session'
    aliases = ['session']
    flags = re.MULTILINE
    tokens = {
        'root': [
            (r'^(\$ )(\S+)([^\n]*)', bygroups(Generic.Prompt, Keyword.Reserved, Name.Variable)),
            (r'^\[BUILD SUCCESS\][^\n]*', String),
            (r'^[^\n]*\berror\b[^\n]*', Generic.Error),
            (r'^[^\n]*\bwarning\b[^\n]*', Generic.Strong),
            (r'[^\n]+', Generic.Output),
            (r'\n', Whitespace),
        ]
    }


PALETTE[Generic.Prompt] = ('#B3261E', True, False)
PALETTE[Generic.Error] = ('#8E1F7A', True, False)
PALETTE[Generic.Strong] = ('#A0520A', False, False)
PALETTE[Comment.Special] = ('#8A8F98', False, False)


class ListingLexer(RegexLexer):
    """shasm listing: line, address, bytes, source"""
    name = 'Listing'
    aliases = ['listing']
    flags = re.MULTILINE
    tokens = {
        'root': [
            (r'^(\s*\d+ )([0-9A-F]{8} )([0-9A-F]*)(\s*)([^\n]*)',
             bygroups(Comment, Number.Hex, Comment.Special, Whitespace, Text)),
            (r'[^\n]+', Text),
            (r'\n', Whitespace),
        ]
    }
