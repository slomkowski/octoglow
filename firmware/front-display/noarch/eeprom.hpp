#pragma once

#include "main.hpp"


namespace octoglow::front_display::eeprom {

    void saveEndYearOfConstruction(uint8_t year);

    uint8_t readEndYearOfConstruction();
}
