#include "main.hpp"
#include "magiceye.hpp"
#include "inverter.hpp"
#include "i2c-slave.hpp"
#include "geiger-counter.hpp"

#include <msp430.h>
#include <iomacros.h>

using namespace octoglow::geiger;


namespace octoglow::geiger {
    volatile bool timerTicked = false;
}

static inline void configureClockSystem() {
    BCSCTL3 = LFXT1S_3 | XCAP_0;

    do {
        IFG1 &= ~OFIFG;
        volatile uint16_t i = 250;
        while (--i) {
        }
    } while (IFG1 & OFIFG);

    BCSCTL2 = SELM_0 | DIVM_0 | SELS | DIVS_0;

    i2c::setClockToLow();
}

[[noreturn]] int main() {
    volatile uint16_t i;

    inverter::setPwmOutputsToSafeState();

    WDTCTL = WDTPW | WDTHOLD;

    P1DIR |= BIT0;
    P2DIR |= BIT0 + BIT7; // configure unused pins as output

    i = 0xfff0;
    do i--;
    while (i != 0);

    configureClockSystem();

    magiceye::init();
    inverter::init();
    i2c::init();
    geiger_counter::init();

    WDTCTL = WDTPW + WDTSSEL + WDTIS0;

    __nop();
    __enable_interrupt();
    __nop();

    while (true) {
        if (timerTicked) {
            P1OUT |= BIT0;

            timerTicked = false;

            // code below is executed at frequency TICK_TIMER_FREQ

            inverter::tick();
            magiceye::tick();
            geiger_counter::tick();

            P1OUT &= ~BIT0;
        }

        WDTCTL = WDTPW + WDTCNTCL;
    }
}
