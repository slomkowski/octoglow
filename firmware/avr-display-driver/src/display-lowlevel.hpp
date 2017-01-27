#pragma once

#include <stdint.h>

namespace octoglow {
    namespace vfd_front {
        namespace display_lowlevel {

            constexpr uint8_t NUM_OF_CHARACTERS = 40;
            constexpr uint8_t COLUMNS_IN_CHARACTER = 5;

            constexpr uint8_t MAX_BRIGHTNESS = 5;

            extern uint8_t frameBuffer[];

            extern uint32_t upperBarBuffer;

            void init();

            void displayPool();

            void setBrightness(const uint8_t brightness);
        }
    }
}

