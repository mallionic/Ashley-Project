ASH Programming Guide and Reference Manual - sources
====================================================

Output:   ../ash-guide.pdf

Build
  pip install reportlab pygments unicorn      (once; unicorn runs the 16/32-bit examples)
  python3 build_guide.py                      full build -> docs/ash-guide.pdf
  python3 build_guide.py --only 15            preview one chapter -> preview.pdf
  python3 build_guide.py --check              only verify the examples
  python3 set_logo.py my_logo.png             replace the logo (cover + headers) and rebuild
  python3 set_logo.py --remove                back to the "ASH" text mark

Every ASH code block is compiled with out/ashc; @example blocks are also linked and run,
and their real output / ASHIR / disassembly go into the PDF. Results are cached in .cache/.
examples_log.txt lists the output of every example of the last build.

Files
  config.py        titles, colours, logo path, compiler path
  build_guide.py   markup parser + PDF writer (reportlab) + example runner
  lexers.py        syntax highlighting for ASH, ASHIR, disassembly, sessions, listings
  gen.py           generated parts: nlib reference, instruction reference, registers, tables
  nlib_docs.py     English documentation of every nlib function
  insn_docs.py     descriptions of the x86 instructions of Appendix A
  dict_docs.py     entries of the Language Dictionary
  chapters/*.txt   the text of the book (sorted by file name)
  KNOWN_BUGS.md    compiler bugs found while writing the manual

Markup (chapters/*.txt)
  @chapter{label} Title / @section{label} / @subsection{label} / @subsubsection / @appendix{label}
  paragraphs, - lists, 1. lists, `code`, **bold**, *italic*, [[label]] cross references
  @code ash|c|asm|sh|text [nocheck|frag|top] [file=name] ... @end
  @example [run] [ir[=funcs]] [asm[=funcs]] [args=".."] [stdin=".."] [bits=16|32] [nolibc] [win] [exe]
           [fail translate=".."] [show=src,out,ir,asm] title=".." label=.. ... @end
  @asmexample [fmt=bin|elf64|elf32] [run] [bits=..] title=".." ... @end   (assembled with shasm)
  @session  lines "$ command"  (run in a scratch dir with ashc and shasm on PATH) ... @end
  @table widths=.. title=".." / rows "a | b" ... @end
  @note @tip @warning @important ... @end      @flow (diagram)  @file name ... @end
  @gen <function> <arg>   (gen.py)            @index term; term
