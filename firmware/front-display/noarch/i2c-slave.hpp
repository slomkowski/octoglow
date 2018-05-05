#pragma once

#include "main.hpp"

namespace octoglow {
    namespace front_display {
        namespace i2c {

            constexpr uint8_t SLAVE_ADDRESS = 0x14;

            void onStart();

            void onTransmit(uint8_t volatile *value);

            void onReceive(uint8_t value);

            void init();
        }
    }
}
