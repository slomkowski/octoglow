#include "vfd.hpp"

#include "global.hpp"
#include "protocol.hpp"
#include "encoder.hpp"

#include "Font5x7.hpp"

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

using namespace vfd::main;

static uint8_t brightness = 5;

static uint8_t frameBuffer[40 * 5];

static uint8_t scrolTextBuffer0[scroll::SLOT0_MAX_LENGTH];
static uint8_t scrolTextBuffer1[scroll::SLOT1_MAX_LENGTH];
static uint8_t scrolTextBuffer2[scroll::SLOT2_MAX_LENGTH];

struct ScrollingSlot {
    uint8_t startPosition;
    uint8_t length;

    uint16_t currentShift;

    uint8_t textLength;
    const uint8_t maxTextLength;
    uint8_t *const convertedText;
};

static ScrollingSlot scrollingSlots[3] = {
        {0, 0, 0, 0, scroll::SLOT0_MAX_LENGTH, scrolTextBuffer0},
        {0, 0, 0, 0, scroll::SLOT1_MAX_LENGTH, scrolTextBuffer1},
        {0, 0, 0, 0, scroll::SLOT2_MAX_LENGTH, scrolTextBuffer2}
};

static_assert(sizeof(scrollingSlots) / sizeof(scrollingSlots[0]) == scroll::NUMBER_OF_SLOTS);

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

static void loadUtf8text(ScrollingSlot *slot, const char *str) {

    uint8_t strIdx = 0;

    uint8_t currPos = 0;

    while (str[strIdx] != 0 and currPos < slot->maxTextLength - 1) {
        const uint8_t singleAsciiValue = str[strIdx];

        if (singleAsciiValue < 0x80) {
            strIdx++;
            slot->convertedText[currPos] = singleAsciiValue;
        }
        else {
            const uint16_t twoByteUnicodeValue = (str[strIdx + 1] & 0x3f) + ((singleAsciiValue & 0x1f) << 6);
            strIdx += 2;

            for (uint8_t offset = 0; offset < 18; ++offset) {
                if (pgm_read_word(&utfMappings[offset]) == twoByteUnicodeValue) {

                    slot->convertedText[currPos] = 126 + offset;
                    break;
                }
            }
        }

        ++currPos;
    }

    slot->currentShift = 0;
    slot->textLength = currPos;
}

constexpr uint8_t LOOP_NUMBER_OF_SPACES = 2;

// if text shorter than window, fall to static text
static void fillPixelsColumnMode(ScrollingSlot *slot) {

    uint8_t characterOffset = slot->currentShift / 5;
    uint8_t columnOffset = slot->currentShift % 5;
    uint8_t charactersSkpLines = 0;

    for (uint16_t p = 0; p < slot->length * 5; ++p) {

        const uint8_t op = characterOffset;
        uint8_t character;

        if (op < slot->textLength) {
            character = slot->convertedText[op];
        } else if (op - slot->textLength < LOOP_NUMBER_OF_SPACES or slot->textLength <= slot->length) {
            character = ' ';
        } else {
            character = slot->convertedText[(op - LOOP_NUMBER_OF_SPACES) % slot->textLength];
        }


        //const uint8_t character = slot->convertedText[characterOffset];

        const uint8_t frameBufferColumn = 5 * slot->startPosition + p;

        frameBuffer[frameBufferColumn + charactersSkpLines]
                = pgm_read_byte(Font5x7 + 5 * (character - ' ') + columnOffset);

        ++columnOffset;

        if (columnOffset == 5) {
            frameBuffer[frameBufferColumn + charactersSkpLines + 1] = 0;
            columnOffset = 0;
            ++characterOffset;
            ++charactersSkpLines;
        }
    }

    if (slot->textLength <= slot->length) {
        slot->currentShift = 0;
        return;
    }

    slot->currentShift++;

    if (slot->currentShift == 5 * slot->textLength + 10) {
        slot->currentShift = 0;
    }
}



static void fillPixelsCellMode(ScrollingSlot *slot) {

    // p is position within window
    for (uint8_t p = 0; p < slot->length; ++p) {

        const uint8_t op = slot->currentShift + p;
        uint8_t character;

        if (op < slot->textLength) {
            character = slot->convertedText[op];
        } else if (op - slot->textLength < LOOP_NUMBER_OF_SPACES or slot->textLength <= slot->length) {
            character = ' ';
        } else {
            character = slot->convertedText[(op - LOOP_NUMBER_OF_SPACES) % slot->textLength];
        }

        memcpy_P(frameBuffer + 5 * (slot->startPosition + p), Font5x7 + 5 * (character - ' '), 5);
    }

    if (slot->textLength <= slot->length) {
        slot->currentShift = 0;
        return;
    }

    slot->currentShift++;

    if (slot->currentShift == slot->textLength) {
        slot->currentShift = 0;
    }
}

static void scrollTextToNextPosition(ScrollingSlot *slot) {

    if (slot->textLength <= slot->length) {
        // fall back to static text
    } else {
        fillPixelsColumnMode(slot);
    }
}

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

static inline void setOutputPin(const uint8_t *characterBuffer, const int8_t column, const int8_t row) {

    const uint8_t go = characterBuffer[column];

    if (go & (1 << row)) {
        PORT(S_IN_PORT) |= _BV(S_IN_PIN);
    } else {
        PORT(S_IN_PORT) &= ~_BV(S_IN_PIN);
    }
}

//__attribute__((optimize("unroll-loops")))
static inline void loadLetter(uint8_t position) {

    PORT(STB_PORT) &= ~_BV(STB_PIN);

    PORT(CL_PORT) |= _BV(CL_PIN);

    const uint8_t *characterPtr = &frameBuffer[(5 * position) % 200];

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

        setOutputPin(characterPtr, column, row);

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
        setOutputPin(characterPtr, column, row);
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
    setOutputPin(characterPtr, 3, 5);
    ckPulse();
    setOutputPin(characterPtr, 4, 5);
    ckPulse();


    // 2 dummy
    PORT(S_IN_PORT) &= ~_BV(S_IN_PIN);
    ckPulse();
    ckPulse();

    // a25 - a19
    column = 0;
    row = 2;
    for (uint8_t a = 24; a != 17; --a) {
        setOutputPin(characterPtr, column, row);

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
        setOutputPin(characterPtr, column, row);

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
}

static uint8_t currentPosition = 0;

static uint16_t x = 0;

void vfd::pool() {

    loadLetter(currentPosition);

    const auto encVal = vfd::encoder::getValueAndClear();

//    if (encVal != 0) {
//        scrollingSlots[0].currentShift += encVal;
//        fillPixelsColumnMode(&scrollingSlots[0]);
//    }

    if (currentPosition == 39) {
        currentPosition = 0;

        x++;
        if (x == 6) {
            scrollTextToNextPosition(&scrollingSlots[0]);
            x = 0;
        }

    } else {
        ++currentPosition;
    }
}

void vfd::setBrightness(const uint8_t b) {
    brightness = b;
}


void vfd::write(const char *str) {
    scrollingSlots[0].startPosition = 3;
    scrollingSlots[0].length = 10;

    loadUtf8text(&scrollingSlots[0], str);
}

