/*
 * I2C bootloader for the front display, port of twiboot v3.2 by Olaf Rempel
 * (https://github.com/orempel/twiboot), GPLv2, see LICENSE in this directory.
 *
 * The protocol is compatible with the twiboot tool from https://github.com/orempel/multiboot_tool:
 *
 * - abort boot timeout:                SLA+W, 0x00, STO
 * - show bootloader version:           SLA+W, 0x01, SLA+R, {16 bytes}, STO
 * - start application:                 SLA+W, 0x01, 0x80, STO
 * - read chip info:                    SLA+W, 0x02, 0x00, 0x00, 0x00, SLA+R, {8 bytes}, STO
 *                                      3 byte signature, 1 byte page size, 2 byte flash size, 2 byte eeprom size
 * - read one (or more) flash bytes:    SLA+W, 0x02, 0x01, addrh, addrl, SLA+R, {* bytes}, STO
 * - write one flash page:              SLA+W, 0x02, 0x01, addrh, addrl, {page size bytes}, STO
 *
 * EEPROM access is not supported, the end year of construction stored there is kept.
 *
 * This file doesn't depend on the hardware, it's tested on the host.
 */
#pragma once

#include <inttypes.h>

namespace octoglow::front_display::bootloader::twiboot {
    constexpr uint8_t TIMER_TICK_MS = 10;

    void init();

    /**
     * @param byteNumber number of the byte in the current master write transmission, starting from 0
     * @return false if the following bytes of the transmission should be NAKed
     */
    bool onDataWrite(uint8_t byteNumber, uint8_t data);

    /**
     * @param byteNumber number of the byte in the current master read transmission, starting from 0
     */
    uint8_t onDataRead(uint8_t byteNumber);

    /**
     * Called every TIMER_TICK_MS, counts down the time the bootloader waits for the host after reset.
     */
    void onTimerTick();

    bool shouldStartApplication();
}
