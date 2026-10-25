import "libA.ash";
import "libB.ash";
@Entry
pub func _start(): int64 { saludo(); printf("%d\n", Calc_sumar(1, 2)); return 0; }
