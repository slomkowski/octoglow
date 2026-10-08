/*
 * I2C bootloader for the front display, port of twiboot v3.2 by Olaf Rempel
 * (https://github.com/orempel/twiboot), GPLv2, see LICENSE in this directory.
 *
 * Lives in the boot section of ATmega88P, BOOTRST fuse makes every reset start here.
 * Linked with -nostartfiles: the code runs straight through the .init sections into main(),
 * there is no vector table, the application's one is never used, no interrupts are used.
 *
 * After reset, the bootloader waits TWIBOOT_TIMEOUT_MS for the host, then starts the application.
 * Any valid command from the host stops the countdown. The display shows BOOTLOADER meanwhile.
 */

#include "screen.hpp"
#include "twiboot.hpp"
#include "twi-slave.hpp"

#include <avr/io.h>

using namespace octoglow::front_display::bootloader;

constexpr uint16_t TIMER0_PRESCALER = 1024;
constexpr uint8_t TIMER0_TICKS_PER_PERIOD = static_cast<uint32_t>(twiboot::TIMER_TICK_MS) * F_CPU / TIMER0_PRESCALER / 1000;

static_assert(static_cast<uint32_t>(twiboot::TIMER_TICK_MS) * F_CPU / TIMER0_PRESCALER / 1000 < 256,
              "tick has to fit in the 8-bit timer");

extern "C" void init1() __attribute__((naked, used, section(".init1")));

void init1() {
    asm volatile ("clr __zero_reg__");
}

/**
 * Normally done by the startup files.
 */
extern "C" void initStack() __attribute__((naked, used, section(".init2")));

void initStack() {
    SP = RAMEND;
}

/**
 * The watchdog stays enabled after a watchdog reset (WDRF forces WDE). The application
 * uses the watchdog and resets through it to enter the bootloader, so disable it first.
 */
extern "C" void disableWatchdog() __attribute__((naked, used, section(".init3")));

void disableWatchdog() {
    MCUSR = 0;
    WDTCSR = _BV(WDCE) | _BV(WDE);
    WDTCSR = 0;
}

[[noreturn]] static void startApplication() {
    asm volatile ("ijmp" :: "z"(0x0000));
    __builtin_unreachable();
}

int main() __attribute__((OS_main, section(".init9")));

int main() {
    twiboot::init();
    screen::init();

    TCCR0B = _BV(CS02) | _BV(CS00); // prescaler 1024

    twislave::init();

    while (!twiboot::shouldStartApplication()) {
        twislave::poll();
        screen::poll();

        if (TIFR0 & _BV(TOV0)) {
            TCNT0 = 0xff - TIMER0_TICKS_PER_PERIOD;
            TIFR0 = _BV(TOV0);
            twiboot::onTimerTick();
            screen::onTimerTick(twislave::takeActivityFlag());
        }
    }

    twislave::disable();
    TCCR0B = 0;

    startApplication();
}
