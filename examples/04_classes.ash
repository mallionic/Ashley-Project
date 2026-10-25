// clases con pub / priv; se llaman como Clase.metodo()
pub class Math() {
    pub func twice(int64 x): int64 { return x << 1; }
    priv func secret(): int64 { return 7; }
    func three(): int64 { return 3; }
    pub func reveal(): int64 { return Math.secret(); }   // priv: solo desde la clase
}

@Entry
pub func _start(): int64 {
    // Math.secret() desde aqui da error: es priv. Se usa a traves de reveal().
    return Math.twice(Math.three()) + Math.reveal();   // 13
}
