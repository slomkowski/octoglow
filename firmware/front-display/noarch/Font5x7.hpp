#pragma once

#include "main.hpp"

#include <stdint.h>

namespace octoglow::front_display::display {
    constexpr uint8_t UNICODE_START_CODE = 126;
    constexpr uint8_t INVALID_CHARACTER_CODE = 147;

    extern const uint8_t Font5x7[] PROGMEM;
}
