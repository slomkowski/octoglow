#pragma once

#include <stdint.h>

#include <avr/pgmspace.h>

namespace octoglow {
    namespace vfd_front {
        constexpr uint8_t UNICODE_START_CODE = 126;
        constexpr uint8_t INVALID_CHARACTER_CODE = 146;

        extern const uint8_t Font5x7[] PROGMEM;
    }
}
