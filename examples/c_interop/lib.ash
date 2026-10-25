class Miclaseash {
    pub func sumar(uint64 a, uint64 b): uint64 { return a + b; }
    priv func secreto(): uint64 { return 7; }
}

general static uint64 contador = 100
general uint32 VERSION = 3

redefine global uint64 calcular_clase(uint64 a, uint64 b) {
    contador = contador + 1;
    return Miclaseash.sumar(a, b);
}
