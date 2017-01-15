#include "vfd.hpp"

#include "global.hpp"

#include <avr/io.h>
#include <avr/pgmspace.h>
#include <util/delay.h>
#include <string.h>

#define CK_PORT A
#define CK_PIN 2

#define CL_PORT B
#define CL_PIN 3

#define STB_PORT A
#define STB_PIN 1

#define S_IN_PORT A
#define S_IN_PIN 3

/*
 * rows - display number
 * cols - segments a-g
 *
 */
static const uint8_t SEGMENT_ORDERING[] PROGMEM = {
        9, 7, 8, 9, 9, 11, 10,
        1, 2, 4, 3, 37, 6, 5,
        36, 35, 33, 36, 34, 39, 40,
        30, 29, 27, 26, 28, 32, 31
};

static const uint8_t CHARACTER_SHAPES[] PROGMEM = {
        0b00111111,
        0b00000110,
        0b01011011,
        0b01001111,
        0b01100110,
        0b01101101,
        0b01111101,
        0b00000111,
        0b01111111,
        0b01100111
};

static uint8_t characterBuffer[4] = {1, 2, 3, 4};

static inline void ckPulse() {
    PORT(CK_PORT) |= _BV(CK_PIN);
    PORT(CK_PORT) &= ~_BV(CK_PIN);
}

// todo remove
uint8_t pos = 0;

static inline void reloadDisplay() {

    PORT(STB_PORT) &= ~_BV(STB_PIN);

    static uint8_t segmentBuffer[5];

    memset(segmentBuffer, 0, 5);

    for (uint8_t numberPos = 0; numberPos != 4; ++numberPos) {
        const uint8_t characterShape = pgm_read_byte(CHARACTER_SHAPES + characterBuffer[numberPos]);

        for (uint8_t s = 0; s != 7; ++s) {
            if (characterShape & (1 << s)) {
                const uint8_t segment = pgm_read_byte(SEGMENT_ORDERING + 7 * numberPos + s) - 1;

                segmentBuffer[segment / 8] |= (1 << (segment % 8));
            }
        }
    }

    for (uint8_t a = 40; a != 0; --a) {

        const uint8_t idx = (a - 1) / 8;
        const uint8_t offset = (a - 1) % 8;

        if ((segmentBuffer[idx] & (1 << offset)) or a == 38) {
            PORT(S_IN_PORT) |= _BV(S_IN_PIN);
        } else {
            PORT(S_IN_PORT) &= ~_BV(S_IN_PIN);
        }

        ckPulse();
    }

    pos++;

    pos = pos % 10;

    PORT(STB_PORT) |= _BV(STB_PIN);
}

void vfd::init() {
    // all connectors are outputs
    DDR(CK_PORT) |= _BV(CK_PIN);
    DDR(CL_PORT) |= _BV(CL_PIN);
    DDR(STB_PORT) |= _BV(STB_PIN);
    DDR(S_IN_PORT) |= _BV(S_IN_PIN);


    PORT(CL_PORT) |= _BV(CL_PIN);

    while (true) {
        reloadDisplay();

        memset(characterBuffer, pos, 4);

        _delay_ms(200);
        _delay_ms(200);
        _delay_ms(200);

        _delay_ms(200);
        _delay_ms(200);
        _delay_ms(200);

        _delay_ms(200);
        _delay_ms(200);
        _delay_ms(200);
    }

    // font: 5 x 7

//     PORT(S_IN_PORT) |= _BV(S_IN_PIN);
//    PORT(CHG_PORT) |= _BV(CHG_PIN);
//    _delay_ms(1);
//    PORT(CL_PORT) |= _BV(CL_PIN);

}

