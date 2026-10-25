// Banderas de linea de comandos con la libreria <args>:
//   ./out/24_args_flags -v 3        (-v = verboso, 2do arg = cuantas veces repetir)
import <args>;

@Entry
pub func _start(int64 argc, int64 argv): int64 {
    int64 verbose = arg_has(argc, argv, "-v");
    int64 veces = 1;

    if (argc > 2) {
        veces = arg_int(argv, 2);
    }

    for (int64 i = 0; i < veces; i++) {
        if (verbose == 1) {
            printf("[verbose] vuelta %d de %d\n", i + 1, veces);
        } else {
            printf("hola %d\n", i + 1);
        }
    }
    return 0;
}
