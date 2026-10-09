#include "flash.hpp"
#include "twiboot.hpp"

#include <msp430.h>

using namespace octoglow::geiger::bootloader;
using twiboot::BOOTLOADER_START;
using twiboot::FLASH_START;

static bool isInApplication(const uint16_t address) {
    return address >= FLASH_START and address < BOOTLOADER_START;
}

/*
 * The code runs from the flash, so the CPU is held until the erase or write is done. No interrupts are enabled.
 * Writing FWKEY alone to FCTL3 doesn't toggle LOCKA, the information memory segment A stays locked.
 */

void flash::eraseSegment(const uint16_t address) {
    if (!isInApplication(address)) {
        return;
    }

    FCTL3 = FWKEY;
    FCTL1 = FWKEY | ERASE;
    *reinterpret_cast<volatile uint16_t *>(address) = 0; // dummy write starts the erase
    FCTL1 = FWKEY;
    FCTL3 = FWKEY | LOCK;
}

void flash::write(const uint16_t address, const uint8_t *const data, const uint8_t length) {
    if (!isInApplication(address) or !isInApplication(address + length - 1)) {
        return;
    }

    FCTL3 = FWKEY;
    FCTL1 = FWKEY | WRT;

    for (uint8_t i = 0; i != length; i += 2) {
        *reinterpret_cast<volatile uint16_t *>(address + i) = data[i] | (data[i + 1] << 8);
    }

    FCTL1 = FWKEY;
    FCTL3 = FWKEY | LOCK;
}

uint8_t flash::readByte(const uint16_t address) {
    return *reinterpret_cast<const volatile uint8_t *>(address);
}
