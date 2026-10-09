#pragma once

#include "protocol.hpp"

#include <inttypes.h>


namespace octoglow::geiger::i2c {
    constexpr uint8_t SLAVE_ADDRESS = 0x18;
    constexpr uint8_t BOOTLOADER_ADDRESS = 0x19; // also hardcoded in flash-over-i2c.sh

    static_assert(SLAVE_ADDRESS != BOOTLOADER_ADDRESS);

    /**
     * Set by ENTER_BOOTLOADER. The main loop resets the MCU after the reply is read, the bootloader
     * then waits ~1 s for the host.
     */
    extern volatile bool bootloaderRequested;

    void setClockToHigh();

    void setClockToLow();

    void onStart();

    void onStop();

    void onTransmit(uint8_t volatile *value);

    void onReceive(uint8_t value);

    void processDataIfAvailable();

    void init();

    namespace hd {
        volatile protocol::DeviceState &getDeviceState();
    }
}
