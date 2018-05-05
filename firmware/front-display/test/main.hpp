#pragma once

// macros to fake progmem support in tests

#define pgm_read_byte(addr) ((uint8_t)(*(addr)))

#define pgm_read_word(addr) ((uint16_t)(*(addr)))

#define PROGMEM

#define memcpy_P memcpy