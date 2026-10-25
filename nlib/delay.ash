/*
 * delay.ash - sleep helpers (The Ashley Project, GPLv3+)
 *
 *   delay(ms)    pause for ms milliseconds
 *   delayus(us)  pause for us microseconds
 */

extern void usleep(int64 usec);

pub func delay(int64 ms): void {
    usleep(ms * 1000);
}

pub func delayus(int64 us): void {
    usleep(us);
}
