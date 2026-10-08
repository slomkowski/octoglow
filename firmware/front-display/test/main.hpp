#pragma once

// macros to fake progmem support in tests

#define pgm_read_byte(addr) ((uint8_t)(*(addr)))

#define pgm_read_word(addr) ((uint16_t)(*(addr)))

#define PROGMEM

#define memcpy_P memcpy

// ATmega88P values from avr/io.h, used by the bootloader

#define SPM_PAGESIZE 64

#define SIGNATURE_0 0x1E
#define SIGNATURE_1 0x93
#define SIGNATURE_2 0x0F
