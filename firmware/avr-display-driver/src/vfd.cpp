#include "vfd.hpp"

#include "global.hpp"

#include <avr/io.h>
#include <avr/pgmspace.h>
#include <util/delay.h>
#include <stdlib.h>
#include <string.h>

#define CK_PORT D
#define CK_PIN 7

#define CHG_PORT D
#define CHG_PIN 6

#define CL_PORT B
#define CL_PIN 2

#define STB_PORT B
#define STB_PIN 1

#define S_IN_PORT B
#define S_IN_PIN 0

static const uint8_t Font5x7[] PROGMEM = {
        0x00, 0x00, 0x00, 0x00, 0x00,            // Code for char
        0x00, 0x00, 0x5F, 0x00, 0x00,            // Code for char !
        0x00, 0x07, 0x00, 0x07, 0x00,            // Code for char "
        0x14, 0x7F, 0x14, 0x7F, 0x14,            // Code for char #
        0x24, 0x2A, 0x7F, 0x2A, 0x12,            // Code for char $
        0x23, 0x13, 0x08, 0x64, 0x62,            // Code for char %
        0x36, 0x49, 0x55, 0x22, 0x50,            // Code for char &
        0x00, 0x05, 0x03, 0x00, 0x00,            // Code for char '
        0x00, 0x1C, 0x22, 0x41, 0x00,            // Code for char (
        0x00, 0x41, 0x22, 0x1C, 0x00,            // Code for char )
        0x08, 0x2A, 0x1C, 0x2A, 0x08,            // Code for char *
        0x08, 0x08, 0x3E, 0x08, 0x08,            // Code for char +
        0x00, 0x50, 0x30, 0x00, 0x00,            // Code for char ,
        0x08, 0x08, 0x08, 0x08, 0x08,            // Code for char -
        0x00, 0x60, 0x60, 0x00, 0x00,            // Code for char .
        0x20, 0x10, 0x08, 0x04, 0x02,            // Code for char /
        0x3E, 0x51, 0x49, 0x45, 0x3E,            // Code for char 0
        0x00, 0x42, 0x7F, 0x40, 0x00,            // Code for char 1
        0x42, 0x61, 0x51, 0x49, 0x46,            // Code for char 2
        0x21, 0x41, 0x45, 0x4B, 0x31,            // Code for char 3
        0x18, 0x14, 0x12, 0x7F, 0x10,            // Code for char 4
        0x27, 0x45, 0x45, 0x45, 0x39,            // Code for char 5
        0x3C, 0x4A, 0x49, 0x49, 0x30,            // Code for char 6
        0x01, 0x71, 0x09, 0x05, 0x03,            // Code for char 7
        0x36, 0x49, 0x49, 0x49, 0x36,            // Code for char 8
        0x06, 0x49, 0x49, 0x29, 0x1E,            // Code for char 9
        0x00, 0x36, 0x36, 0x00, 0x00,            // Code for char :
        0x00, 0x56, 0x36, 0x00, 0x00,            // Code for char ;
        0x00, 0x08, 0x14, 0x22, 0x41,            // Code for char <
        0x14, 0x14, 0x14, 0x14, 0x14,            // Code for char =
        0x41, 0x22, 0x14, 0x08, 0x00,            // Code for char >
        0x02, 0x01, 0x51, 0x09, 0x06,            // Code for char ?
        0x32, 0x49, 0x79, 0x41, 0x3E,            // Code for char @
        0x7E, 0x09, 0x09, 0x09, 0x7E,            // Code for char A
        0x7F, 0x49, 0x49, 0x49, 0x36,            // Code for char B
        0x3E, 0x41, 0x41, 0x41, 0x22,            // Code for char C
        0x7F, 0x41, 0x41, 0x22, 0x1C,            // Code for char D
        0x7F, 0x49, 0x49, 0x49, 0x41,            // Code for char E
        0x7F, 0x09, 0x09, 0x01, 0x01,            // Code for char F
        0x3E, 0x41, 0x41, 0x51, 0x32,            // Code for char G
        0x7F, 0x08, 0x08, 0x08, 0x7F,            // Code for char H
        0x00, 0x41, 0x7F, 0x41, 0x00,            // Code for char I
        0x20, 0x40, 0x41, 0x3F, 0x01,            // Code for char J
        0x7F, 0x08, 0x14, 0x22, 0x41,            // Code for char K
        0x7F, 0x40, 0x40, 0x40, 0x40,            // Code for char L
        0x7F, 0x02, 0x04, 0x02, 0x7F,            // Code for char M
        0x7F, 0x04, 0x08, 0x10, 0x7F,            // Code for char N
        0x3E, 0x41, 0x41, 0x41, 0x3E,            // Code for char O
        0x7F, 0x09, 0x09, 0x09, 0x06,            // Code for char P
        0x3E, 0x41, 0x51, 0x21, 0x5E,            // Code for char Q
        0x7F, 0x09, 0x19, 0x29, 0x46,            // Code for char R
        0x46, 0x49, 0x49, 0x49, 0x31,            // Code for char S
        0x01, 0x01, 0x7F, 0x01, 0x01,            // Code for char T
        0x3F, 0x40, 0x40, 0x40, 0x3F,            // Code for char U
        0x1F, 0x20, 0x40, 0x20, 0x1F,            // Code for char V
        0x7F, 0x20, 0x18, 0x20, 0x7F,            // Code for char W
        0x63, 0x14, 0x08, 0x14, 0x63,            // Code for char X
        0x03, 0x04, 0x78, 0x04, 0x03,            // Code for char Y
        0x61, 0x51, 0x49, 0x45, 0x43,            // Code for char Z
        0x00, 0x00, 0x7F, 0x41, 0x41,            // Code for char [
        0x02, 0x04, 0x08, 0x10, 0x20,            // Code for char BackSlash
        0x41, 0x41, 0x7F, 0x00, 0x00,            // Code for char ]
        0x04, 0x02, 0x01, 0x02, 0x04,            // Code for char ^
        0x40, 0x40, 0x40, 0x40, 0x40,            // Code for char _
        0x00, 0x01, 0x02, 0x04, 0x00,            // Code for char `
        0x20, 0x54, 0x54, 0x54, 0x78,            // Code for char a
        0x7F, 0x48, 0x44, 0x44, 0x38,            // Code for char b
        0x38, 0x44, 0x44, 0x44, 0x20,            // Code for char c
        0x38, 0x44, 0x44, 0x48, 0x7F,            // Code for char d
        0x38, 0x54, 0x54, 0x54, 0x18,            // Code for char e
        0x08, 0x7E, 0x09, 0x01, 0x02,            // Code for char f
        0x08, 0x14, 0x54, 0x54, 0x3C,            // Code for char g
        0x7F, 0x08, 0x04, 0x04, 0x78,            // Code for char h
        0x00, 0x44, 0x7D, 0x40, 0x00,            // Code for char i
        0x20, 0x40, 0x44, 0x3D, 0x00,            // Code for char j
        0x00, 0x7F, 0x10, 0x28, 0x44,            // Code for char k
        0x00, 0x41, 0x7F, 0x40, 0x00,            // Code for char l
        0x7C, 0x04, 0x18, 0x04, 0x78,            // Code for char m
        0x7C, 0x08, 0x04, 0x04, 0x78,            // Code for char n
        0x38, 0x44, 0x44, 0x44, 0x38,            // Code for char o
        0x7C, 0x14, 0x14, 0x14, 0x08,            // Code for char p
        0x08, 0x14, 0x14, 0x18, 0x7C,            // Code for char q
        0x7C, 0x08, 0x04, 0x04, 0x08,            // Code for char r
        0x48, 0x54, 0x54, 0x54, 0x20,            // Code for char s
        0x04, 0x3F, 0x44, 0x40, 0x20,            // Code for char t
        0x3C, 0x40, 0x40, 0x20, 0x7C,            // Code for char u
        0x1C, 0x20, 0x40, 0x20, 0x1C,            // Code for char v
        0x3C, 0x40, 0x30, 0x40, 0x3C,            // Code for char w
        0x44, 0x28, 0x10, 0x28, 0x44,            // Code for char x
        0x0C, 0x50, 0x50, 0x50, 0x3C,            // Code for char y
        0x44, 0x64, 0x54, 0x4C, 0x44,            // Code for char z
        0x00, 0x08, 0x36, 0x41, 0x00,            // Code for char {
        0x00, 0x00, 0x7F, 0x00, 0x00,            // Code for char |
        0x00, 0x41, 0x36, 0x08, 0x00,            // Code for char }
        0x7E, 0x09, 0x09, 0x49, 0x3E,            // Code for char ~
        0x10, 0x2A, 0x6A, 0x2A, 0x3C,            // Code for char 
        0x3C, 0x42, 0x46, 0x43, 0x24,            // Code for char €
        0x38, 0x44, 0x46, 0x45, 0x20,            // Code for char 
        0x3F, 0x29, 0x29, 0x69, 0x21,            // Code for char ‚
        0x1C, 0x2A, 0x6A, 0x2A, 0x0C,            // Code for char ƒ
        0x7F, 0x50, 0x48, 0x44, 0x40,            // Code for char „
        0x00, 0x51, 0x7F, 0x44, 0x00,            // Code for char …
        0x7E, 0x08, 0x12, 0x21, 0x7E,            // Code for char †
        0x7C, 0x04, 0x06, 0x05, 0x78,            // Code for char ‡
        0x3C, 0x42, 0x46, 0x43, 0x3C,            // Code for char ˆ
        0x38, 0x44, 0x46, 0x45, 0x38,            // Code for char ‰
        0x44, 0x4A, 0x4A, 0x4B, 0x32,            // Code for char Š
        0x48, 0x54, 0x56, 0x55, 0x20,            // Code for char ‹
        0x42, 0x66, 0x53, 0x4A, 0x46,            // Code for char Œ
        0x44, 0x64, 0x56, 0x4D, 0x44,            // Code for char 
        0x69, 0x59, 0x49, 0x4D, 0x4B,            // Code for char Ž
        0x44, 0x64, 0x55, 0x4C, 0x44,            // Code for char 
        0x44, 0x4A, 0x4A, 0x4B, 0x32,            // Code for char 
        0x44, 0x4A, 0x4A, 0x4B, 0x32             // Code for char ‘
};

static uint8_t brightness = 5;

static uint8_t currentPosition = 0;
static uint8_t displayCharBuffer[40];

static uint8_t currentCharBuffer[5];

static inline __attribute((always_inline)) void ckPulse() {
    PORT(CK_PORT) |= _BV(CK_PIN);
    PORT(CK_PORT) &= ~_BV(CK_PIN);
}

static inline void posUp(const int8_t startInclusive, const int8_t stopInclusive, const int8_t validPosition) {

    for (int8_t p = startInclusive; p != stopInclusive + 1; ++p) {
        if (p == validPosition) {
            PORT(S_IN_PORT) |= _BV(S_IN_PIN);
        } else {
            PORT(S_IN_PORT) &= ~_BV(S_IN_PIN);
        }
        ckPulse();
    }
}

static inline void posDown(const int8_t startInclusive, const int8_t stopInclusive, const int8_t validPosition) {

    for (int8_t p = startInclusive; p != stopInclusive - 1; --p) {
        if (p == validPosition) {
            PORT(S_IN_PORT) |= _BV(S_IN_PIN);
        } else {
            PORT(S_IN_PORT) &= ~_BV(S_IN_PIN);
        }
        ckPulse();
    }
}

static inline void setOutputPin(const int8_t column, const int8_t row) {

    const uint8_t go = currentCharBuffer[column];

    if (go & (1 << row)) {
        PORT(S_IN_PORT) |= _BV(S_IN_PIN);
    } else {
        PORT(S_IN_PORT) &= ~_BV(S_IN_PIN);
    }
}

//__attribute__((optimize("unroll-loops")))
static inline void loadLetter(uint8_t position, const uint8_t character) {

    const uint16_t letterOffset = (character - 32) * 5;

    PORT(STB_PORT) &= ~_BV(STB_PIN);

    PORT(CL_PORT) |= _BV(CL_PIN);

    memcpy_P(currentCharBuffer, Font5x7 + letterOffset, 5);

    if ((position >= 10) and (position <= 19)) {
        position += 20;
    } else if ((position >= 20) and (position <= 29)) {
        position -= 10;
    } else if (position >= 30) {
        position -= 10;
    }

    if (position < 20) {
        // g7 - g1
        posDown(6, 0, position);

        // g8 - g20
        posUp(7, 19, position);
    } else {
        PORT(S_IN_PORT) &= ~_BV(S_IN_PIN);
        for (uint8_t i = 0; i != 20; ++i) {
            ckPulse();
        }
    }

    if (brightness == 1) {
        PORT(CL_PORT) &= ~_BV(CL_PIN);
    }

    // a1 - a11
    int8_t column = 2;
    int8_t row = 3;
    for (uint8_t a = 0; a != 11; ++a) {

        setOutputPin(column, row);

        ++column;

        if (column == 5) {
            column = 0;
            ++row;
        }

        ckPulse();
    }

    column = 4;
    row = 6;
    // a18 - a14
    for (uint8_t a = 17; a != 12; --a) {
        setOutputPin(column, row);
        --column;
        ckPulse();
    }

    // 2 dummy
    PORT(S_IN_PORT) &= ~_BV(S_IN_PIN);
    ckPulse();
    ckPulse();

    if (brightness == 2) {
        PORT(CL_PORT) &= ~_BV(CL_PIN);
    }

    // a12 - a13
    setOutputPin(3, 5);
    ckPulse();
    setOutputPin(4, 5);
    ckPulse();


    // 2 dummy
    PORT(S_IN_PORT) &= ~_BV(S_IN_PIN);
    ckPulse();
    ckPulse();

    // a25 - a19
    column = 0;
    row = 2;
    for (uint8_t a = 24; a != 17; --a) {
        setOutputPin(column, row);

        if (column == 4) {
            column = 0;
            ++row;
        } else {
            ++column;
        }

        ckPulse();
    }

    // a26 - a35
    column = 4;
    row = 1;
    for (uint8_t a = 25; a != 35; ++a) {
        setOutputPin(column, row);

        if (column == 0) {
            column = 4;
            --row;
        } else {
            --column;
        }

        ckPulse();
    }

    if (brightness == 3) {
        PORT(CL_PORT) &= ~_BV(CL_PIN);
    }

    // a36
    PORT(S_IN_PORT) &= ~_BV(S_IN_PIN);
    ckPulse();

    if (position > 19) {
        // g21 - g33
        posUp(20, 32, position);

        // g40 - g34
        posDown(39, 33, position);
    } else {
        for (uint8_t i = 0; i != 20; ++i) {
            ckPulse();
        }
    }

    PORT(STB_PORT) |= _BV(STB_PIN);
}

void vfd::init() {
    // all connectors are outputs
    DDR(CK_PORT) |= _BV(CK_PIN);
    DDR(CHG_PORT) |= _BV(CHG_PIN);
    DDR(CL_PORT) |= _BV(CL_PIN);
    DDR(STB_PORT) |= _BV(STB_PIN);
    DDR(S_IN_PORT) |= _BV(S_IN_PIN);

    //PORT(CL_PORT) |= _BV(CL_PIN);
    //PORT(STB_PORT) |= _BV(STB_PIN);

    // font: 5 x 7

//     PORT(S_IN_PORT) |= _BV(S_IN_PIN);
//    PORT(CHG_PORT) |= _BV(CHG_PIN);
//    _delay_ms(1);
//    PORT(CL_PORT) |= _BV(CL_PIN);

}

void vfd::pool() {
    loadLetter(currentPosition, displayCharBuffer[currentPosition]);

    if (currentPosition == 39) {
        currentPosition = 0;
    } else {
        ++currentPosition;
    }
}

void vfd::setBrightness(const uint8_t b) {
    brightness = b;
}

static const uint16_t utfMappings[] PROGMEM = {
        0x104, 0x105,
        0x106, 0x107,
        0x118, 0x119,
        0x141, 0x142,
        0x143, 0x144,
        0x0d3, 0x0f3,
        0x15a, 0x15b,
        0x179, 0x17a,
        0x17b, 0x17c
};

void vfd::write(const char *str) {

    uint8_t strIdx = 0;

    uint8_t currPos = 0;

    while (str[strIdx] != 0) {
        uint8_t byte = str[strIdx];

        if (byte < 0x80) {
            strIdx++;

            displayCharBuffer[currPos] = byte;
        }
        else {
            uint16_t unicode = str[strIdx + 1] & 0x3f;
            unicode += (byte & 0x1f) << 6;
            strIdx += 2;

            for (uint8_t offset = 0; offset < 18; ++offset) {
                if (pgm_read_word(&utfMappings[offset]) == unicode) {
                    displayCharBuffer[currPos] = 126 + offset;
                    break;
                }
            }

            //todo displaybuffer with conversion map
        }

        ++currPos;
    }


}

