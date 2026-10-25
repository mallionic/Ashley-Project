// terminate [valor]: termina la funcion EN ESE MOMENTO y devuelve el valor, desde cualquier
// bucle (for, while, repeat, loop), switch o bloque. Igual que return, pero:
//   terminate;  sin valor vale en cualquier funcion: devuelve lo pendiente de unreturnable, o 0.
// ashc 49_terminate.ash --elf   -> buscar=307 clasifica=10 20 99 pendiente=7 sinpendiente=0 contador=1 var=5
func buscar(int64 objetivo): int64 {
    for (int64 i = 0; i < 10; i++) {
        for (int64 j = 0; j < 10; j++) {
            if (i * 10 + j == objetivo) {
                terminate i * 100 + j;      // sale de los dos bucles y de la funcion
            }
        }
    }
    return -1;
}

func clasifica(int64 n): int64 {
    switch (n) {
        case 1:
            terminate 10;
        case 2:
            terminate 20;
        default:
            break;
    }
    repeat (5) {
        loop {
            terminate 99;
        }
    }
    return 0;
}

func pendiente(): int64 {
    unreturnable 7;
    int64 k = 0;
    while (k < 100) {
        k++;
        if (k == 3) {
            terminate;                     // devuelve lo pendiente: 7
        }
    }
    return 1;
}

func sinpendiente(): int64 {
    terminate;                             // nada pendiente: 0
}

general static int64 contador = 0

func avisa(int64 x): void {
    if (x > 2) {
        terminate;                         // void: solo sale
    }
    contador = contador + 1;
}

@Entry
pub func _start(): int64 {
    int64 terminate = 4;                   // una variable puede llamarse terminate
    terminate = terminate + 1;
    avisa(1);
    avisa(5);
    printf("buscar=%d clasifica=%d %d %d pendiente=%d sinpendiente=%d contador=%d var=%d\n",
        buscar(37), clasifica(1), clasifica(2), clasifica(3), pendiente(), sinpendiente(), contador, terminate);
    return 0;
}
