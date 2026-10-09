/*
 * Self-programming of the main flash, implemented in flash_hd.cpp, replaced by a stub in tests.
 * The addresses are absolute.
 */
#pragma once

#include <inttypes.h>

namespace octoglow::geiger::bootloader::flash {
    /**
     * Erases the 512-byte segment which contains the address. Addresses outside the application are ignored.
     */
    void eraseSegment(uint16_t address);

    /**
     * Writes the bytes to the erased flash, word by word. Addresses outside the application are ignored.
     * @param address even
     * @param length even
     */
    void write(uint16_t address, const uint8_t *data, uint8_t length);

    uint8_t readByte(uint16_t address);
}
