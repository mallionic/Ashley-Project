/*
 * Copyright (C) 2026 Mallionic Inc.
 *
 * This file is part of The Ashley Project.
 *
 * The Ashley Project is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3, or (at your option)
 * any later version.
 *
 * The Ashley Project is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * Copyright holder: Mallionic Inc.
 */

extern int64 remove(string filename);
extern int64 rename(string old, string new);

extern void* tmpfile();
extern string tmpnam(void* buffer);
extern string tmpnam_r(void* buffer);
extern string tempnam(string dir, string prefix);

extern int64 fflush(void* stream);
extern int64 fclose(void* stream);
extern int64 fcloseall();

extern void* fopen(string filename, string modes);
extern void* freopen(string filename, string modes, void* stream);
extern void* fopen64(string filename, string modes);
extern void* freopen64(string filename, string modes, void* stream);

extern void setbuf(void* stream, string buffer);
extern int64 setvbuf(void* stream, string buffer, int64 mode, uint64 size);
extern void setbuffer(void* stream, string buffer, uint64 size);
extern void setlinebuf(void* stream);

extern int64 printf(string format, ...);
extern int64 fprintf(void* stream, string format, ...);
extern int64 sprintf(string buffer, string format, ...);
extern int64 vprintf(string format, void* args);
extern int64 vfprintf(void* stream, string format, void* args);
extern int64 vsprintf(string buffer, string format, void* args);
extern int64 snprintf(string buffer, uint64 size, string format, ...);
extern int64 vsnprintf(string buffer, uint64 size, string format, void* args);
extern int64 asprintf(void* buffer, string format, ...);
extern int64 vasprintf(void* buffer, string format, void* args);
extern int64 dprintf(int64 fd, string format, ...);
extern int64 vdprintf(int64 fd, string format, void* args);

extern int64 scanf(string format, ...);
extern int64 fscanf(void* stream, string format, ...);
extern int64 sscanf(string buffer, string format, ...);
extern int64 vscanf(string format, void* args);
extern int64 vfscanf(void* stream, string format, void* args);
extern int64 vsscanf(string buffer, string format, void* args);

extern int64 fgetc(void* stream);
extern int64 getc(void* stream);
extern int64 getchar();
extern int64 getc_unlocked(void* stream);
extern int64 getchar_unlocked();
extern int64 fgetc_unlocked(void* stream);

extern int64 fputc(int64 c, void* stream);
extern int64 putc(int64 c, void* stream);
extern int64 putchar(int64 c);
extern int64 fputc_unlocked(int64 c, void* stream);
extern int64 putc_unlocked(int64 c, void* stream);
extern int64 putchar_unlocked(int64 c);

extern int64 getw(void* stream);
extern int64 putw(int64 value, void* stream);

extern string fgets(string buffer, int64 size, void* stream);
extern string fgets_unlocked(string buffer, int64 size, void* stream);

extern int64 fputs(string string, void* stream);
extern int64 fputs_unlocked(string string, void* stream);
extern int64 puts(string string);

extern int64 ungetc(int64 c, void* stream);

extern uint64 fread(void* ptr, uint64 size, uint64 count, void* stream);
extern uint64 fwrite(void* ptr, uint64 size, uint64 count, void* stream);
extern uint64 fread_unlocked(void* ptr, uint64 size, uint64 count, void* stream);
extern uint64 fwrite_unlocked(void* ptr, uint64 size, uint64 count, void* stream);

extern int64 fseek(void* stream, int64 offset, int64 whence);
extern int64 ftell(void* stream);
extern void rewind(void* stream);

extern int64 fseeko(void* stream, int64 offset, int64 whence);
extern int64 ftello(void* stream);
extern int64 fgetpos(void* stream, void* position);
extern int64 fsetpos(void* stream, void* position);

extern int64 fseeko64(void* stream, int64 offset, int64 whence);
extern int64 ftello64(void* stream);
extern int64 fgetpos64(void* stream, void* position);
extern int64 fsetpos64(void* stream, void* position);

extern void clearerr(void* stream);
extern int64 feof(void* stream);
extern int64 ferror(void* stream);
extern void clearerr_unlocked(void* stream);
extern int64 feof_unlocked(void* stream);
extern int64 ferror_unlocked(void* stream);

extern void perror(string string);

extern int64 fileno(void* stream);
extern int64 fileno_unlocked(void* stream);

extern int64 pclose(void* stream);
extern void* popen(string command, string modes);

extern string ctermid(string buffer);
extern string cuserid(string buffer);

extern int64 flockfile(void* stream);
extern int64 ftrylockfile(void* stream);
extern void funlockfile(void* stream);

extern int64 __uflow(void* stream);
extern int64 __overflow(void* stream, int64 c);