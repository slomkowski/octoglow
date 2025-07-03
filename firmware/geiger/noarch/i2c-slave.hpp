#pragma once

#include "protocol.hpp"

#include <inttypes.h>


namespace octoglow::geiger::i2c {
    constexpr uint8_t SLAVE_ADDRESS = 0x12;

    void setClockToHigh();

    void setClockToLow();

    void onStart();

    void onTransmit(uint8_t volatile *value);

    void onReceive(uint8_t value);

    void init();

    namespace hd {
        volatile protocol::DeviceState &getDeviceState();
    }
}
