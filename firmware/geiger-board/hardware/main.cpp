#include "main.hpp"
#include "magiceye.hpp"
#include "inverter.hpp"

#include <msp430.h>
#include <iomacros.h>

using namespace octoglow::geiger;

static volatile bool timerTicked = false;

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

/**
 * Configures tick timer. Tick ensures that cyclic event have reliable time source.
 */
static inline void configureTickTimer() {
    TA0CCR0 = F_CPU / 8 / octoglow::geiger::TICK_TIMER_FREQ; // we set clock divider to 8
    TA0CCTL0 = CCIE;
    TA0CTL = TASSEL_2 | ID_3 | MC_1;
}

__attribute__ ((interrupt(TIMER0_A0_VECTOR))) void TIMER0_A0_ISR() {
    timerTicked = true;
}

int main() {
    volatile int i;

    WDTCTL = WDTPW | WDTHOLD;

    i = 10000;
    do i--;
    while (i != 0);

    configureClockSystem();

    P1DIR |= BIT0;

    configureTickTimer();

    magiceye::init();
    inverter::init();

    __nop();
    __enable_interrupt();

    //magiceye::setEnabled(true);

    uint8_t  x = 0;

    while (true) {
        //
        if (timerTicked) {
            timerTicked = false;

            // code below is executed at frequency TICK_TIMER_FREQ
            P1OUT ^= BIT0;

            inverter::tick();
            magiceye::tick();

            magiceye::setAdcValue(++x);
        }
    }
}
