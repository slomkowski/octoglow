#pragma once

#include "main.hpp"

#include <inttypes.h>


namespace octoglow::front_display::i2c {
    constexpr uint8_t SLAVE_ADDRESS = 0x14;
    constexpr uint8_t BOOTLOADER_ADDRESS = 0x15; // also hardcoded in flash-over-i2c.sh

    static_assert(SLAVE_ADDRESS != BOOTLOADER_ADDRESS);

    /**
     * Set by ENTER_BOOTLOADER. The main loop stops feeding the watchdog, so the MCU resets
     * within WDTO_250MS. The I2C reply is still served in the meantime. BOOTRST fuse is programmed,
     * so every reset starts the bootloader, which waits ~1 s for the host.
     */
    extern volatile bool bootloaderRequested;

    void onStart();

    void onTransmit(uint8_t volatile *value);

    void onReceive(uint8_t value);

    void init();

    uint8_t crc8ccittUpdate(uint8_t inCrc, uint8_t inData);
}
