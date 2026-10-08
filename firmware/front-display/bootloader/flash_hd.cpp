#include "flash.hpp"

#include <avr/boot.h>
#include <avr/pgmspace.h>

using namespace octoglow::front_display::bootloader;

void flash::writePage(const uint16_t address, const uint8_t *const data) {
    boot_page_erase(address);
    boot_spm_busy_wait();

    for (uint8_t i = 0; i != SPM_PAGESIZE; i += 2) {
        boot_page_fill(address + i, data[i] | (data[i + 1] << 8));
    }

    boot_page_write(address);
    boot_spm_busy_wait();

    // the application (RWW) section can't be read until it's enabled again
    boot_rww_enable();
}

uint8_t flash::readByte(const uint16_t address) {
    return pgm_read_byte(address);
}
