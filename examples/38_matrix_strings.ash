// matrix, rows/cols/len/count, strings largos """ """, concatenacion y arrays con coma final.
@Entry
pub func _start(): int64 {
    matrix m[] []{
        [uint64 64324], [uint64 64231], [uint64 6445],
        [uint64 64324], [uint64 64234], [uint64 6454],
    }
    printf("filas=%d columnas=%d total=%d m[1][2]=%llu\n", rows(m), cols(m), count(m), m[1][2]);

    string titulo = "Hola " "mundo " "concatenado";
    string largo = """Primera linea
segunda "con comillas"
tercera""";
    string[] bloques = {"""
array 0
con lineas
""",
        """array 1
2da linea
""",
    };

    printf("%s\n%s\n", titulo, largo);
    printf("%s%s", bloques[0], bloques[1]);
    return 0;
}
