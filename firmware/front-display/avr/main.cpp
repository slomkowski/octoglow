#include "display.hpp"
#include "encoder.hpp"
#include "i2c-slave.hpp"
#include "main.hpp"
#include "eeprom.hpp"

#include <avr/wdt.h>
#include <avr/interrupt.h>
#include <avr/pgmspace.h>

#include <stdlib.h>

constexpr bool WATCHDOG_ENABLE = true;

using namespace octoglow::front_display;

constexpr char aboutStaticString[] PROGMEM = "2016-20xx Michał Słomkowski slomkowski.eu";

static inline void showDemoOnDisplay() {
    display::clear();
    display::setBrightness(3);

    display::setUpperBarContent(0b11111111u);

    display::writeStaticText_P(0, 9, PSTR("OCTOGLOW"));

    const uint8_t year = eeprom::readEndYearOfConstruction();

    static_assert(sizeof(aboutStaticString) > 12);
    char buffer[sizeof(aboutStaticString) + 2];
    memcpy_P(buffer, aboutStaticString, sizeof(aboutStaticString));

    buffer[7] = '0' + (year / 10);
    buffer[8] = '0' + (year % 10);

    display::writeScrollingText(1, 10, 10, buffer);

    display::writeStaticText_P(21, 19, PSTR("Controller boot..."));
}

[[noreturn]] int main() {
    // pull-up all unused pins
    PORTB |= _BV(PB3) | _BV(PB4) | _BV(PB5);
    PORTC |= _BV(PC0) | _BV(PC1) | _BV(PC2) | _BV(PC3) | _BV(PC6);
    PORTD |= _BV(PD0) | _BV(PD4);
    DDRD |= _BV(PD5);

    encoder::init();
    display::init();

    i2c::init();

    if (WATCHDOG_ENABLE) {
        wdt_enable(WDTO_250MS);
    }

    sei();

    showDemoOnDisplay();

    while (true) {
        display::pool();
        encoder::pool();

        if (WATCHDOG_ENABLE) {
            wdt_reset();
        }
    }
}
