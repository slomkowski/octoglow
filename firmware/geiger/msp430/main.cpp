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

/**
 * Time for the host to read the reply to ENTER_BOOTLOADER, in ticks.
 */
constexpr uint8_t BOOTLOADER_ENTRY_DELAY_TICKS = TICK_TIMER_FREQ / 10;

/**
 * Writing WDTCTL without the password resets the MCU immediately, the bootloader starts.
 */
[[noreturn]] static void resetIntoBootloader() {
    inverter::setPwmOutputsToSafeState();
    WDTCTL = 0;
    while (true) {
    }
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

    i2c::setClockToHigh();
}

__interrupt_vec(TRAPINT_VECTOR) [[noreturn]] void trapHandler() {
    inverter::setPwmOutputsToSafeState();

    while (true) {
        __no_operation();
    }
}

[[noreturn]] int main() {
    inverter::setPwmOutputsToSafeState();

    WDTCTL = WDTPW | WDTHOLD;

    P1DIR |= BIT0;
    P2DIR |= BIT0 + BIT7; // configure unused pins as output

    volatile uint16_t i = 0xfff0;
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

    uint8_t bootloaderEntryTicks = 0;

    while (true) {
        if (timerTicked) {
            P1OUT |= BIT0; // pin no 2

            timerTicked = false;

            // the code below is executed at the frequency TICK_TIMER_FREQ = 100 Hz

            inverter::tick();
            magiceye::tick();
            geiger_counter::tick();

            if (i2c::bootloaderRequested and ++bootloaderEntryTicks == BOOTLOADER_ENTRY_DELAY_TICKS) {
                resetIntoBootloader();
            }

            P1OUT &= ~BIT0;
        }
        i2c::processDataIfAvailable();

        WDTCTL = WDTPW + WDTCNTCL;
    }
}
