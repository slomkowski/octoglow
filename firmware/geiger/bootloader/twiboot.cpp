#include "twiboot.hpp"
#include "flash.hpp"

using namespace octoglow::geiger::bootloader;
using namespace octoglow::geiger::bootloader::twiboot;

constexpr uint8_t TIMEOUT_TICKS = TWIBOOT_TIMEOUT_MS / TIMER_TICK_MS;

static_assert(TIMEOUT_TICKS > 1 and TWIBOOT_TIMEOUT_MS / TIMER_TICK_MS < 256);

enum class Command : uint8_t {
    WAIT = 0x00,
    READ_VERSION = 0x01,
    SWITCH_APPLICATION = READ_VERSION,
    ACCESS_MEMORY = 0x02,
    // internal
    ACCESS_CHIPINFO = 0x10 | ACCESS_MEMORY,
    ACCESS_FLASH = 0x20 | ACCESS_MEMORY,
    BOOT_APPLICATION = 0x20 | SWITCH_APPLICATION,
};

enum class MemoryType : uint8_t {
    CHIPINFO = 0x00,
    FLASH = 0x01,
};

constexpr uint8_t BOOTTYPE_APPLICATION = 0x80;

/**
 * The whole 16-bit address space above FLASH_START, reads beyond it would wrap to the peripherals.
 */
constexpr uint16_t FLASH_SIZE = 0x10000 - FLASH_START;

static constexpr char VERSION_INFO[16] = "TWIBOOT v3.2";

static constexpr uint8_t CHIP_INFO[8] = {
    0x00, 0x25, 0x53, // there's no AVR signature, MSP430G2553 device ID instead
    PAGE_SIZE,
    APPLICATION_SIZE >> 8, APPLICATION_SIZE & 0xff, // flash available for the application
    0, 0, // no EEPROM
};

static uint8_t pageBuffer[PAGE_SIZE];
static uint16_t address;
static Command command;
static uint8_t bootTimeoutTicks;
static bool pageReceived;

void twiboot::init() {
    address = 0;
    command = Command::WAIT;
    bootTimeoutTicks = TIMEOUT_TICKS;
    pageReceived = false;
}

void twiboot::onDataWrite(const uint8_t byteNumber, const uint8_t data) {
    switch (byteNumber) {
        case 0:
            pageReceived = false;
            if (data == static_cast<uint8_t>(Command::WAIT)
                or data == static_cast<uint8_t>(Command::SWITCH_APPLICATION)
                or data == static_cast<uint8_t>(Command::ACCESS_MEMORY)) {
                bootTimeoutTicks = 0;
                command = static_cast<Command>(data);
            } else {
                command = Command::BOOT_APPLICATION;
            }
            return;

        case 1:
            if (command == Command::SWITCH_APPLICATION) {
                if (data == BOOTTYPE_APPLICATION) {
                    command = Command::BOOT_APPLICATION;
                }
            } else if (command == Command::ACCESS_MEMORY) {
                if (data == static_cast<uint8_t>(MemoryType::CHIPINFO)) {
                    command = Command::ACCESS_CHIPINFO;
                } else if (data == static_cast<uint8_t>(MemoryType::FLASH)) {
                    command = Command::ACCESS_FLASH;
                }
            }
            return;

        case 2:
        case 3:
            address = (address << 8) | data;
            return;

        default:
            if (command != Command::ACCESS_FLASH) {
                return;
            }

            // the bytes after the page are ignored
            const uint8_t position = byteNumber - 4;
            if (position < PAGE_SIZE) {
                pageBuffer[position] = data;
                pageReceived = position == PAGE_SIZE - 1;
            }
    }
}

uint8_t twiboot::onDataRead(const uint8_t byteNumber) {
    switch (command) {
        case Command::READ_VERSION:
            return VERSION_INFO[byteNumber % sizeof(VERSION_INFO)];

        case Command::ACCESS_CHIPINFO:
            return CHIP_INFO[byteNumber % sizeof(CHIP_INFO)];

        case Command::ACCESS_FLASH:
            if (address < FLASH_SIZE) {
                return flash::readByte(FLASH_START + address++);
            }
            return 0xff;

        default:
            return 0xff;
    }
}

static bool isErased(const uint8_t *const data) {
    for (uint8_t i = 0; i != PAGE_SIZE; ++i) {
        if (data[i] != 0xff) {
            return false;
        }
    }
    return true;
}

void twiboot::onStop() {
    if (!pageReceived) {
        return;
    }
    pageReceived = false;

    // the bootloader itself is never overwritten
    if (address >= APPLICATION_SIZE or address % PAGE_SIZE != 0) {
        return;
    }

    const uint16_t flashAddress = FLASH_START + address;

    if (address == 0) {
        // the update begins, the application is invalid until its vector table is written again
        flash::eraseSegment(APPLICATION_VECTORS);
    }

    if (flashAddress % SEGMENT_SIZE == 0) {
        flash::eraseSegment(flashAddress);
    }

    // the gaps of the image don't have to be written, the segment is already erased
    if (!isErased(pageBuffer)) {
        flash::write(flashAddress, pageBuffer, PAGE_SIZE);
    }
}

void twiboot::onTimerTick() {
    if (bootTimeoutTicks > 1) {
        --bootTimeoutTicks;
    } else if (bootTimeoutTicks == 1) {
        command = Command::BOOT_APPLICATION;
    }
}

bool twiboot::isApplicationPresent() {
    return flash::readByte(APPLICATION_RESET_VECTOR) != 0xff or flash::readByte(APPLICATION_RESET_VECTOR + 1) != 0xff;
}

bool twiboot::shouldStartApplication() {
    return command == Command::BOOT_APPLICATION and isApplicationPresent();
}
