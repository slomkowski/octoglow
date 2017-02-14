#pragma once

#include "protocol.hpp"

#include <stdint.h>

namespace octoglow {
    namespace vfd_clock {
        namespace display {

            void init();

            void setCharacter(const uint8_t position, const char character, const bool shouldReloadDisplay = true);

            void setAllCharacters(char *const characters);

            void setBrightness(const uint8_t brightness);

            void setDots(const uint8_t newDotState, const bool shouldReloadDisplay = true);

            void setReceiverUpdateFlag(const bool enabled);
        }
    }
}