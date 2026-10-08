#include "stubs.hpp"

#include "display.hpp"
#include "eeprom.hpp"
#include "encoder.hpp"
#include "i2c-slave.hpp"

using namespace octoglow::front_display;

int test::displayPoolCalls = 0;

uint8_t test::endYearOfConstruction = 77;

void display::hd::displayPool() {
    ++test::displayPoolCalls;
}

int8_t encoder::getValueAndClear() {
    const auto v = _currentEncoderSteps;
    _currentEncoderSteps = 0;
    return v;
}

encoder::ButtonState encoder::getButtonStateAndClear() {
    const auto v = _currentButtonState;
    _currentButtonState = ButtonState::NO_CHANGE;
    return v;
}

uint8_t eeprom::readEndYearOfConstruction() {
    return test::endYearOfConstruction;
}

void eeprom::saveEndYearOfConstruction(const uint8_t year) {
    test::endYearOfConstruction = year;
}

// the same algorithm as _crc8_ccitt_update() from avr-libc
uint8_t i2c::crc8ccittUpdate(const uint8_t inCrc, const uint8_t inData) {
    uint8_t data = inCrc ^ inData;
    for (int i = 0; i < 8; i++) {
        if ((data & 0x80) != 0) {
            data <<= 1;
            data ^= 0x07;
        } else {
            data <<= 1;
        }
    }
    return data;
}
