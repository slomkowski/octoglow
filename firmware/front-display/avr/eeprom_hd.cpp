#include "eeprom.hpp"
#include "main.hpp"

#include <avr/eeprom.h>

constexpr uint8_t EEPROM_END_YEAR_OF_CONSTRUCTION_ADDRESS = 10;

uint8_t octoglow::front_display::eeprom::readEndYearOfConstruction() {
    return eeprom_read_byte(reinterpret_cast<uint8_t *>(EEPROM_END_YEAR_OF_CONSTRUCTION_ADDRESS));
}

void octoglow::front_display::eeprom::saveEndYearOfConstruction(const uint8_t year) {
    if (const uint8_t oldYear = readEndYearOfConstruction(); year != oldYear) {
        eeprom_write_byte(reinterpret_cast<uint8_t *>(EEPROM_END_YEAR_OF_CONSTRUCTION_ADDRESS), year);
    }
}
