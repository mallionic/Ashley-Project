import "libB.ash";
pub func saludo(): void { printf("principal con 1000\n"); return; }
saludo.override = 1000;
@Entry
pub func _start(): int64 { saludo(); return 0; }
