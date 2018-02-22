#include "magiceye.hpp"

#include <msp430.h>

#define DAC_CE BIT1
#define DAC_IN BIT3
#define DAC_LATCH BIT2
#define DAC_CLK BIT5

#define RELAY_PREHEAT BIT1
#define RELAY_MAIN BIT3


void ::octoglow::geiger::magiceye::hd::enablePreheatRelay(bool enabled) {
    if (enabled) {
        P1OUT |= RELAY_PREHEAT;
    } else {
        P1OUT &= ~RELAY_PREHEAT;
    }
}

void ::octoglow::geiger::magiceye::hd::enableMainRelay(bool enabled) {
    if (enabled) {
        P1OUT |= RELAY_MAIN;
    } else {
        P1OUT &= ~RELAY_MAIN;
    }
}

void ::octoglow::geiger::magiceye::init() {

    // init relays

    P1DIR |= RELAY_MAIN | RELAY_PREHEAT;
    hd::enableMainRelay(false);
    hd::enablePreheatRelay(false);

    // init DAC

    P1SEL &= ~DAC_LATCH;
    P1SEL2 &= ~DAC_LATCH;
    P1DIR |= DAC_LATCH;

    P2SEL &= ~(DAC_CE | DAC_IN | DAC_CLK);
    P2SEL2 &= ~(DAC_CE | DAC_IN | DAC_CLK);
    P2DIR |= DAC_CE | DAC_IN | DAC_CLK;

    P2OUT |= DAC_CE;

    setAdcValue(127); // set to half value
}

void ::octoglow::geiger::magiceye::setAdcValue(uint8_t v) {

    const uint8_t order[] = {0, 1, 2, 3, 4, 5, 7, 6};

    P2OUT &= ~DAC_CE;

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
    }

    P1OUT |= DAC_LATCH;
    __nop();
    __nop();
    P1OUT &= ~DAC_LATCH;

    __nop();
    __nop();
    P2OUT |= DAC_CE;
}

void ::octoglow::geiger::magiceye::setControllerState(octoglow::geiger::protocol::EyeControllerState state) {

}
