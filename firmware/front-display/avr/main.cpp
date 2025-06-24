#include "display.hpp"
#include "encoder.hpp"
#include "i2c-slave.hpp"
#include "main.hpp"

#include <avr/wdt.h>
#include <util/delay.h>
#include <avr/interrupt.h>

constexpr bool WATCHDOG_ENABLE = true;

using namespace octoglow::front_display;

static void showDemoOnDisplay() {
    display::clear();
    display::setBrightness(3);

    display::setUpperBarContent(0b11111111u);

    display::writeStaticText_P(0, 9, PSTR("OCTOGLOW"));

    display::writeScrollingText_P(1, 10, 10, PSTR("2016-2025 Michał Słomkowski slomkowski.eu"));

    display::writeStaticText_P(21, 19, PSTR("Controller boot..."));
}

[[noreturn]] int main() {
    //todo pull-up all unused pins

    encoder::init();
    display::init();

    i2c::init();

    if (WATCHDOG_ENABLE) {
        wdt_enable(WDTO_250MS);
    }

    sei();

    display::clear();

    showDemoOnDisplay();

    while (true) {
        display::pool();
        encoder::pool();

        if (WATCHDOG_ENABLE) {
            wdt_reset();
        }
    }
}
