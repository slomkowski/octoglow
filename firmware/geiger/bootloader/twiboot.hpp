/*
 * I2C bootloader for the Geiger counter board, port of twiboot v3.2 by Olaf Rempel
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
 * Differences from the AVR variants, caused by MSP430:
 *
 * - Addresses of the protocol are relative to the start of the flash (FLASH_START), so the application
 *   starts at 0, as on AVR. The host subtracts FLASH_START from the addresses of the hex file.
 * - The flash is erased in 512-byte segments, but the page of the protocol has 64 bytes (the page size
 *   is one byte in the chip info). The segment is erased when its first page is written, so the pages
 *   have to be written in ascending order, starting from a segment boundary, like the host tools do.
 * - The page is written after STOP, not during the transmission: the whole page is ACKed and the bus
 *   isn't held while the flash is written. The host has to wait PAGE_WRITE_TIME_MS before the next transmission,
 *   otherwise SCL is held low until the page is written.
 * - The bytes the bootloader doesn't expect are ACKed and ignored.
 * - The vector table of the application lies at the end of its flash (just below the bootloader), the real
 *   vector table jumps through it. Writing page 0 erases the segment with the application's vector table,
 *   so an interrupted update leaves no reset vector and the bootloader doesn't start the broken application.
 *   The reset vector is written as the last page of the image.
 *
 * EEPROM access is not supported, MSP430G2553 has no EEPROM. The information memory isn't accessible,
 * its segment A holds the DCO calibration.
 *
 * This file doesn't depend on the hardware, it's tested on the host.
 */
#pragma once

#include <inttypes.h>

namespace octoglow::geiger::bootloader::twiboot {
    constexpr uint8_t TIMER_TICK_MS = 10;

    constexpr uint16_t FLASH_START = 0xc000;
    constexpr uint16_t SEGMENT_SIZE = 512;
    constexpr uint8_t PAGE_SIZE = 64;

    constexpr uint16_t BOOTLOADER_START = TWIBOOT_START;

    /**
     * Size of the application in the addresses of the protocol, includes its vector table.
     */
    constexpr uint16_t APPLICATION_SIZE = BOOTLOADER_START - FLASH_START;

    constexpr uint8_t NUMBER_OF_VECTORS = 16;

    /**
     * The application is linked with its vector table here, the real one at 0xffe0 belongs to the bootloader.
     */
    constexpr uint16_t APPLICATION_VECTORS = BOOTLOADER_START - 2 * NUMBER_OF_VECTORS;
    constexpr uint16_t APPLICATION_RESET_VECTOR = BOOTLOADER_START - 2;

    /**
     * Erase of the segment and write of the page, rounded up. Writing page 0 erases two segments.
     */
    constexpr uint8_t PAGE_WRITE_TIME_MS = 30;

    static_assert(BOOTLOADER_START % SEGMENT_SIZE == 0, "bootloader has to start at the segment boundary");
    static_assert(SEGMENT_SIZE % PAGE_SIZE == 0);
    static_assert(BOOTLOADER_START > FLASH_START);

    void init();

    /**
     * @param byteNumber number of the byte in the current master write transmission, starting from 0
     */
    void onDataWrite(uint8_t byteNumber, uint8_t data);

    /**
     * @param byteNumber number of the byte in the current master read transmission, starting from 0
     */
    uint8_t onDataRead(uint8_t byteNumber);

    /**
     * Called on STOP, writes the page received in the transmission, if any.
     */
    void onStop();

    /**
     * Called every TIMER_TICK_MS, counts down the time the bootloader waits for the host after reset.
     */
    void onTimerTick();

    /**
     * @return true if the application has the reset vector
     */
    bool isApplicationPresent();

    /**
     * The application is started on the timeout or on the host's command, but only if it's present.
     */
    bool shouldStartApplication();
}
