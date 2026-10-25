@Entry
pub func _start(): int64 {
    int64 total = 0;
    repeat (5) {
        total += 2;
    }
    int64 n = 0;
    loop {
        n++;
        if (n == 3) { continue; }
        if (n > 6) { break; }
        total += 10;
    }
    repeat (3) { repeat (2) { total += 1; } }
    return total;     // 10 + (n=1,2,4,5,6 -> 5*10=50) + 6 = 66
}
