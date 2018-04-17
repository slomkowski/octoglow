#include "main.hpp"
#include "magiceye.hpp"
#include "inverter.hpp"
#include "i2c-slave.hpp"
#include "geiger-counter.hpp"

#include <msp430.h>
#include <iomacros.h>

using namespace octoglow::geiger;

namespace octoglow {
    namespace geiger {
        volatile bool timerTicked = false;
    }
}

/**
 * Sets clock source for MCLK and SMCLK to external oscillator. Waits for oscillator fault flag to clear.
 */
static inline void configureClockSystem() {
    volatile uint16_t i;

    BCSCTL3 = LFXT1S_3 | XCAP_0;

    do {
        IFG1 &= ~OFIFG;
        i = 250;
        while (--i) {}
    } while (IFG1 & OFIFG);

    BCSCTL2 = SELM_3 | DIVM_0 | SELS | DIVS_0;
    BCSCTL1 = XT2OFF | XTS | DIVA_3;
}

int main() {
    volatile int i;

    WDTCTL = WDTPW | WDTHOLD;

    i = 10000;
    do i--;
    while (i != 0);

    configureClockSystem();

    P1DIR |= BIT0;

    //magiceye::init();
    inverter::init();
    i2c::init();
    geiger_counter::init();

    __nop();
    __enable_interrupt();

    //magiceye::setEnabled(false);

    uint8_t x = 0;

    while (true) {
        //
        if (timerTicked) {
            timerTicked = false;

            // code below is executed at frequency TICK_TIMER_FREQ
            P1OUT ^= BIT0;

            inverter::tick();
            //magiceye::tick();
            //geiger_counter::tick();

            //magiceye::setAdcValue(++x);
        }
    }
}
