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

static const uint8_t GRAPHICS_HEART[] PROGMEM = {0x00, 0x06, 0x09, 0x11, 0x22, 0x22, 0x11, 0x09, 0x06, 0x00};
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

static void showEncoderValue() {
    static int8_t encoderVal = 0;

    const int8_t currentVal = encoder::getValueAndClear();

    const encoder::ButtonState bs = encoder::getButtonStateAndClear();

    if (currentVal != 0) {
        encoderVal += currentVal;

        char valueBuffer[5];

        itoa(encoderVal, valueBuffer, 10);

        display::writeStaticText(36, 4, valueBuffer);
    }

    if (bs == encoder::ButtonState::JUST_PRESSED) {
        display::drawGraphics_P(5 * 33, 10, false, GRAPHICS_HEART);
    } else if (bs == encoder::ButtonState::JUST_RELEASED) {
        display::writeStaticText_P(33, 2, PSTR("xD"));
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

        showEncoderValue();

        if (WATCHDOG_ENABLE) {
            wdt_reset();
        }
    }
}