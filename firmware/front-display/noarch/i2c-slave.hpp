#pragma once

#include "main.hpp"

#include <inttypes.h>


namespace octoglow::front_display::i2c {
    constexpr uint8_t SLAVE_ADDRESS = 0x14;

    void onStart();

    void onTransmit(uint8_t volatile *value);

    void onReceive(uint8_t value);

    void init();
}
