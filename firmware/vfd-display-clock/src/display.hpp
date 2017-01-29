#pragma once

#include <stdint.h>

namespace octoglow {
    namespace vfd_clock {
        namespace display {

            constexpr uint8_t UPPER_DOT = 0b01;
            constexpr uint8_t LOWER_DOT = 0b10;

            constexpr uint8_t MAX_BRIGHTNESS = 5;

            void init();

            void setCharacter(const uint8_t position, const char character, const bool shouldReloadDisplay = true);

            void setAllCharacters(char *const characters);

            void setBrightness(const uint8_t brightness);

            void setDots(const uint8_t newDotState, const bool shouldReloadDisplay = true);
        }
    }
}