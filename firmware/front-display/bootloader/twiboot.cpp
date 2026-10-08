#include "twiboot.hpp"
#include "flash.hpp"
#include "main.hpp"

using namespace octoglow::front_display::bootloader;

constexpr uint16_t BOOTLOADER_START = TWIBOOT_START;
constexpr uint8_t TIMEOUT_TICKS = TWIBOOT_TIMEOUT_MS / twiboot::TIMER_TICK_MS;

static_assert(BOOTLOADER_START % SPM_PAGESIZE == 0, "bootloader has to start at the page boundary");
static_assert(TIMEOUT_TICKS > 1 and TWIBOOT_TIMEOUT_MS / twiboot::TIMER_TICK_MS < 256);

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

// not in PROGMEM: .progmem is linked before the code, but the bootloader has to start with code
static constexpr char VERSION_INFO[16] = "TWIBOOT v3.2";

static constexpr uint8_t CHIP_INFO[8] = {
    SIGNATURE_0, SIGNATURE_1, SIGNATURE_2,
    SPM_PAGESIZE,
    BOOTLOADER_START >> 8, BOOTLOADER_START & 0xff, // flash available for the application
    0, 0, // no EEPROM support
};

static uint8_t pageBuffer[SPM_PAGESIZE];
static uint16_t address;
static Command command;
static uint8_t bootTimeoutTicks;

void twiboot::init() {
    command = Command::WAIT;
    bootTimeoutTicks = TIMEOUT_TICKS;
}

bool twiboot::onDataWrite(const uint8_t byteNumber, const uint8_t data) {
    switch (byteNumber) {
        case 0:
            if (data == static_cast<uint8_t>(Command::WAIT)
                or data == static_cast<uint8_t>(Command::SWITCH_APPLICATION)
                or data == static_cast<uint8_t>(Command::ACCESS_MEMORY)) {
                bootTimeoutTicks = 0;
                command = static_cast<Command>(data);
                return true;
            }
            command = Command::BOOT_APPLICATION;
            return false;

        case 1:
            if (command == Command::SWITCH_APPLICATION) {
                if (data == BOOTTYPE_APPLICATION) {
                    command = Command::BOOT_APPLICATION;
                }
            } else if (command == Command::ACCESS_MEMORY) {
                if (data == static_cast<uint8_t>(MemoryType::CHIPINFO)) {
                    command = Command::ACCESS_CHIPINFO;
                    return true;
                }
                if (data == static_cast<uint8_t>(MemoryType::FLASH)) {
                    command = Command::ACCESS_FLASH;
                    return true;
                }
            }
            return false;

        case 2:
        case 3:
            address = (address << 8) | data;
            return true;

        default:
            if (command != Command::ACCESS_FLASH) {
                return false;
            }

            const uint8_t position = byteNumber - 4;
            pageBuffer[position] = data;

            if (position == SPM_PAGESIZE - 1) {
                // the bootloader itself is never overwritten; SCL is held low while the page is written
                if (address < BOOTLOADER_START and address % SPM_PAGESIZE == 0) {
                    flash::writePage(address, pageBuffer);
                }
                return false;
            }
            return true;
    }
}

uint8_t twiboot::onDataRead(const uint8_t byteNumber) {
    switch (command) {
        case Command::READ_VERSION:
            return VERSION_INFO[byteNumber % sizeof(VERSION_INFO)];

        case Command::ACCESS_CHIPINFO:
            return CHIP_INFO[byteNumber % sizeof(CHIP_INFO)];

        case Command::ACCESS_FLASH:
            return flash::readByte(address++);

        default:
            return 0xff;
    }
}

void twiboot::onTimerTick() {
    if (bootTimeoutTicks > 1) {
        --bootTimeoutTicks;
    } else if (bootTimeoutTicks == 1) {
        command = Command::BOOT_APPLICATION;
    }
}

bool twiboot::shouldStartApplication() {
    return command == Command::BOOT_APPLICATION;
}
