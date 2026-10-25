#include <stdio.h>
extern unsigned long calcular_clase(unsigned long, unsigned long);
extern unsigned long contador;
extern unsigned int VERSION;
int main(void) {
    printf("desde C: %lu\n", calcular_clase(30, 12));
    printf("contador=%lu VERSION=%u\n", contador, VERSION);
    contador = 500;
    calcular_clase(1, 1);
    printf("tras escribir desde C: contador=%lu\n", contador);
    return 0;
}
