#pragma once

#include <stdint.h>

namespace octoglow {
    namespace vfd_front {

        namespace text {
            constexpr uint8_t MODE = 't';
        }

        namespace scroll {

            constexpr uint8_t NUMBER_OF_SLOTS = 3;

            constexpr uint8_t MODE = 's';

            constexpr uint8_t SLOT0 = '0';
            constexpr uint8_t SLOT1 = '1';
            constexpr uint8_t SLOT2 = '2';

            constexpr uint8_t SLOT0_MAX_LENGTH = 150;
            constexpr uint8_t SLOT1_MAX_LENGTH = 70;
            constexpr uint8_t SLOT2_MAX_LENGTH = 30;
        }

        namespace pixel {
            constexpr uint8_t MODE_OVERRIDE = 'p';
            constexpr uint8_t MODE_SUM = 'P';
        }
    }
}