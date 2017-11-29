#include "magiceye.hpp"

#include <msp430.h>

#define DAC_CE BIT1
#define DAC_CS BIT2
#define DAC_IN BIT3
#define DAC_LATCH BIT4
#define DAC_CLK BIT5

void ::octoglow::geiger::magiceye::init() {

    P2SEL &= ~(DAC_CE | DAC_CS | DAC_IN | DAC_LATCH | DAC_CLK);
    P2SEL2 &= ~(DAC_CE | DAC_CS | DAC_IN | DAC_LATCH | DAC_CLK);
    P2DIR |= DAC_CE | DAC_CS | DAC_IN | DAC_LATCH | DAC_CLK;

    P2OUT |= DAC_CS;
    P2OUT &= ~DAC_CE;

    setAdcValue(127); // set to half value
}

void ::octoglow::geiger::magiceye::setAdcValue(uint8_t v) {

    const uint8_t order[] = {0, 1, 2, 3, 4, 5, 7, 6};

    P2OUT &= ~DAC_CS;

    for (uint8_t i = 0; i != 8; ++i) {

        if (v & (1 << order[i])) {
            P2OUT |= DAC_IN;
        } else {
            P2OUT &= ~DAC_IN;
        }

        P2OUT |= DAC_CLK;
        P2OUT &= ~DAC_CLK;
    }

    P2OUT |= DAC_LATCH;
    P2OUT &= ~DAC_LATCH;

    P2OUT |= DAC_CS;
}
