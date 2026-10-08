/*
 * Self-programming of the application section, implemented in flash_hd.cpp, replaced by a stub in tests.
 */
#pragma once

#include <inttypes.h>

namespace octoglow::front_display::bootloader::flash {
    /**
     * Erases and writes one page, the application section can be read again afterwards.
     * @param address start of the page
     * @param data SPM_PAGESIZE bytes
     */
    void writePage(uint16_t address, const uint8_t *data);

    uint8_t readByte(uint16_t address);
}
