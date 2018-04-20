#include "magiceye.hpp"

#include <msp430.h>

#define DAC_IN BIT3
#define DAC_LATCH BIT4
#define DAC_CLK BIT5

#define HEATING_1 BIT3
#define HEATING_2 BIT4


void ::octoglow::geiger::magiceye::hd::enableHeater1(bool enabled) {
    if (enabled) {
        P1OUT &= ~HEATING_1;
    } else {
        P1OUT |= HEATING_1;
    }
}

void ::octoglow::geiger::magiceye::hd::enableHeater2(bool enabled) {
    if (enabled) {
        P1OUT |= HEATING_2;
    } else {
        P1OUT &= ~HEATING_2;
    }
}

void ::octoglow::geiger::magiceye::init() {

    // init heater
    P1DIR |= HEATING_1 | HEATING_2;
    hd::enableHeater2(false);
    hd::enableHeater1(false);

    // init DAC
    P2DIR |= DAC_LATCH | DAC_IN | DAC_CLK;

    setAdcValue(127); // set to half value
}

void ::octoglow::geiger::magiceye::setAdcValue(uint8_t v) {

    const uint8_t order[] = {0, 1, 2, 3, 4, 5, 7, 6};

    for (uint8_t i = 0; i != 8; ++i) {

        if (v & (1 << order[i])) {
            P2OUT |= DAC_IN;
        } else {
            P2OUT &= ~DAC_IN;
        }

        __nop();
        __nop();

        P2OUT |= DAC_CLK;
        __nop();
        __nop();
        P2OUT &= ~DAC_CLK;
        __nop();
    }

    P2OUT |= DAC_LATCH;
    __nop();
    __nop();
    P2OUT &= ~DAC_LATCH;
    __nop();
}
