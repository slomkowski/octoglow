#pragma once

#include "protocol.hpp"

#include <stdint.h>

namespace octoglow {
    namespace vfd_clock {
        namespace display {

            void init();

            void setCharacter(uint8_t position, char character, bool shouldReloadDisplay = true);

            void setAllCharacters(char *characters);

            void setBrightness(uint8_t brightness);

            void setDots(uint8_t newDotState, bool shouldReloadDisplay = true);

            void setReceiverUpdateFlag(bool enabled);
        }
    }
}