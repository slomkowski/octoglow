#include <msp430g2452.h>

void delay(unsigned int d) {
    int i;
    for (i = 0; i < d; i++) {
        _NOP();
    }
}

int main(void) {
    WDTCTL = WDTPW | WDTHOLD;
    P1DIR = 0xFF;
    P1OUT = 0x01;

    for (;;) {
        P1OUT = ~P1OUT;
        delay(0x4fff);
    }
}