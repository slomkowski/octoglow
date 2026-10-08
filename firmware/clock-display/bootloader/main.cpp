/*
 * I2C bootloader for the clock display, port of twiboot v3.2 by Olaf Rempel
 * (https://github.com/orempel/twiboot), GPLv2, see LICENSE in this directory.
 *
 * Linked with -nostartfiles: the code runs straight through the .init sections into main(),
 * there is no vector table, the application's one is used. No interrupts are used.
 *
 * After reset, the bootloader waits TWIBOOT_TIMEOUT_MS for the host, then starts the application.
 * Any valid command from the host stops the countdown.
 */

#include "protocol.hpp"
#include "usi-slave.hpp"

#include <avr/io.h>

using namespace octoglow::vfd_clock::bootloader;

constexpr uint16_t TIMER0_PRESCALER = 1024;
constexpr uint8_t TIMER0_TICKS_PER_PERIOD = static_cast<uint32_t>(protocol::TIMER_TICK_MS) * F_CPU / TIMER0_PRESCALER / 1000;

extern "C" void init1() __attribute__((naked, used, section(".init1")));

void init1() {
    asm volatile ("clr __zero_reg__");
}

/**
 * The watchdog stays enabled after a watchdog reset (WDRF forces WDE). The application
 * uses the watchdog and resets through it to enter the bootloader, so disable it first.
 */
extern "C" void disableWatchdog() __attribute__((naked, used, section(".init3")));

void disableWatchdog() {
    MCUSR = 0;
    WDTCR = _BV(WDCE) | _BV(WDE);
    WDTCR = 0;
}

int main() __attribute__((OS_main, section(".init9")));

int main() {
    protocol::init();

    TCCR0B = _BV(CS02) | _BV(CS00); // prescaler 1024

    usislave::init();

    while (!protocol::shouldStartApplication()) {
        usislave::poll();

        if (TIFR & _BV(TOV0)) {
            TCNT0L = 0xff - TIMER0_TICKS_PER_PERIOD;
            TIFR = _BV(TOV0);
            protocol::onTimerTick();
        }
    }

    usislave::disable();
    TCCR0B = 0;

    protocol::startApplication();
}
