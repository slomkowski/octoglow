#pragma once

#include <cstdint>

// state of the hardware-dependent functions replaced by stubs in tests

namespace octoglow::front_display::test {
    extern int displayPoolCalls;

    extern uint8_t endYearOfConstruction;

    constexpr uint16_t FLASH_SIZE = 8192;

    /**
     * Flash of the MCU as seen by the bootloader, erased (0xff) by default.
     */
    extern uint8_t flash[FLASH_SIZE];

    extern int flashPageWrites;
}
