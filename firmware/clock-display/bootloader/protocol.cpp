#include "protocol.hpp"

#include <avr/io.h>
#include <avr/boot.h>
#include <avr/pgmspace.h>

using namespace octoglow::vfd_clock::bootloader;

constexpr uint16_t BOOTLOADER_START = TWIBOOT_START;
constexpr uint8_t TIMEOUT_TICKS = TWIBOOT_TIMEOUT_MS / protocol::TIMER_TICK_MS;

static_assert(BOOTLOADER_START % SPM_PAGESIZE == 0, "bootloader has to start at the page boundary");
static_assert(TIMEOUT_TICKS > 1 and TWIBOOT_TIMEOUT_MS / protocol::TIMER_TICK_MS < 256);

/*
 * ATtiny461A has no bootloader section, so the vector table is patched when page 0 is written:
 * the reset vector jumps to the bootloader, the vector below jumps to the original reset handler
 * of the application. This vector must not be used by the application.
 */
constexpr uint8_t APP_VECTOR_NUM = EE_RDY_vect_num;
constexpr uint16_t RESET_VECTOR_ADDRESS = 0x0000;
constexpr uint16_t APP_VECTOR_ADDRESS = APP_VECTOR_NUM * 2; // each vector is a 2-byte RJMP

static_assert(APP_VECTOR_ADDRESS + 1 < SPM_PAGESIZE, "vectors have to be in the first page");

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
static auto command = Command::WAIT;
static uint8_t bootTimeoutTicks = TIMEOUT_TICKS;

// original vectors sent by the host, returned on read to make the verification pass
static uint16_t savedResetVector;
static uint16_t savedAppVector;

/**
 * @param offset relative jump in words, counted from the next instruction
 */
static constexpr uint16_t rjmpOpcode(const uint16_t offset) {
    return 0xc000 | (offset & 0x0fff);
}

static void setPageBufferWord(const uint8_t position, const uint16_t value) {
    pageBuffer[position] = value & 0xff;
    pageBuffer[position + 1] = value >> 8;
}

static void writeFlashPage() {
    const uint16_t pageStart = address;

    if (pageStart == RESET_VECTOR_ADDRESS) {
        savedResetVector = pageBuffer[RESET_VECTOR_ADDRESS] | (pageBuffer[RESET_VECTOR_ADDRESS + 1] << 8);
        savedAppVector = pageBuffer[APP_VECTOR_ADDRESS] | (pageBuffer[APP_VECTOR_ADDRESS + 1] << 8);

        // RJMP offsets are in words: reset vector at word 0, app vector at word APP_VECTOR_NUM
        setPageBufferWord(RESET_VECTOR_ADDRESS, rjmpOpcode(BOOTLOADER_START / 2 - 1));
        setPageBufferWord(APP_VECTOR_ADDRESS, rjmpOpcode(savedResetVector - APP_VECTOR_NUM));
    }

    if (pageStart >= BOOTLOADER_START) {
        return;
    }

    boot_page_erase(pageStart);
    boot_spm_busy_wait();

    for (uint8_t i = 0; i != SPM_PAGESIZE; i += 2) {
        boot_page_fill(pageStart + i, pageBuffer[i] | (pageBuffer[i + 1] << 8));
    }

    boot_page_write(pageStart);
    boot_spm_busy_wait();
}

void protocol::init() {
    savedResetVector = pgm_read_word(RESET_VECTOR_ADDRESS);
    savedAppVector = pgm_read_word(APP_VECTOR_ADDRESS);
}

bool protocol::onDataWrite(const uint8_t byteNumber, const uint8_t data) {
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
                // SCL is held low by USI while the page is written
                writeFlashPage();
                return false;
            }
            return true;
    }
}

uint8_t protocol::onDataRead(const uint8_t byteNumber) {
    switch (command) {
        case Command::READ_VERSION:
            return VERSION_INFO[byteNumber % sizeof(VERSION_INFO)];

        case Command::ACCESS_CHIPINFO:
            return CHIP_INFO[byteNumber % sizeof(CHIP_INFO)];

        case Command::ACCESS_FLASH: {
            uint8_t data;
            switch (address) {
                case RESET_VECTOR_ADDRESS:
                    data = savedResetVector & 0xff;
                    break;
                case RESET_VECTOR_ADDRESS + 1:
                    data = savedResetVector >> 8;
                    break;
                case APP_VECTOR_ADDRESS:
                    data = savedAppVector & 0xff;
                    break;
                case APP_VECTOR_ADDRESS + 1:
                    data = savedAppVector >> 8;
                    break;
                default:
                    data = pgm_read_byte(address);
                    break;
            }
            ++address;
            return data;
        }

        default:
            return 0xff;
    }
}

void protocol::onTimerTick() {
    if (bootTimeoutTicks > 1) {
        --bootTimeoutTicks;
    } else if (bootTimeoutTicks == 1) {
        command = Command::BOOT_APPLICATION;
    }
}

bool protocol::shouldStartApplication() {
    return command == Command::BOOT_APPLICATION;
}

void protocol::startApplication() {
    // function pointers hold word addresses
    reinterpret_cast<void (*)()>(APP_VECTOR_ADDRESS / 2)();
    __builtin_unreachable();
}
