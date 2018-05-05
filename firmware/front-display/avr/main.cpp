#include "display.hpp"
#include "encoder.hpp"
#include "i2c-slave.hpp"
#include "main.hpp"

#include <avr/wdt.h>
#include <util/delay.h>
#include <avr/interrupt.h>

#include <stdlib.h>

constexpr bool WATCHDOG_ENABLE = false;

using namespace octoglow::front_display;

static const uint8_t GRAPHICS_STRIKETHROUGH[] PROGMEM = {0x01};

static void showDemoOnDisplay() {
    display::clear();
    display::setBrightness(3);
    display::writeStaticText_P(0, 6, PSTR("Lorem:"));
    display::writeStaticText_P(7, 12, PSTR(__DATE__));

    display::setUpperBarContent(0b111101101110000000u);

    display::writeScrollingText_P(0, 21, 10, PSTR("Zażółć gęślą jaźń! ū \"no i ja się pytam człowieku dumny "
                                                  "ty jesteś z siebie zdajesz sobie sprawę z tego "
                                                  "co robisz?masz ty wogóle rozum i godnośc człowieka?\""));

    for (uint8_t c = 5; c < 25; ++c) {
        display::drawGraphics_P(c, 1, true, GRAPHICS_STRIKETHROUGH);
    }
}

int main() {
    encoder::init();
    display::init();

    i2c::init();

    if (WATCHDOG_ENABLE) {
        wdt_enable(WDTO_120MS);
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