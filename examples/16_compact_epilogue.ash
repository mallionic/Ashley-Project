func add(int64 a, int64 b): int64 {
    int64 c = a + b;
    return c;
}
func nothing(): void {
    int64 x = 1;
}
func early(int64 a): int64 {
    if (a > 5) { return 1; }
    return 2;
}
@Entry
pub func _start(): int64 {
    int64 r = add(1, 2) + early(7);
    nothing();
    return r;
}
