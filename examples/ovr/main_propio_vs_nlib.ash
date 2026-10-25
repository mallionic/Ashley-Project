import <delay>;
pub func delay(int64 ms): void { printf("mi delay propio %d\n", ms); return; }
@Entry
pub func _start(): int64 { delay(7); return 0; }
