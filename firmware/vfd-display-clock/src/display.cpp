#include "display.hpp"

#include "global.hpp"

#include <avr/io.h>
#include <avr/pgmspace.h>
#include <util/delay.h>
#include <string.h>

#include "relay.hpp"

#define CK_PORT A
#define CK_PIN 2

#define CL_PORT B
#define CL_PIN 3

#define STB_PORT A
#define STB_PIN 1

#define S_IN_PORT A
#define S_IN_PIN 3

using namespace octoglow::vfd_clock::display;

constexpr uint8_t NUMBER_OF_POSITIONS = 4;
constexpr uint8_t PWM_STEPS = 255;

constexpr uint8_t RECEIVER_UPDATE_FLAG = 0b100;
constexpr uint8_t RECEIVER_UPDATE_CHARACTER_SHAPE = 0b1100010;

static_assert((RECEIVER_UPDATE_FLAG & LOWER_DOT) == 0);
static_assert((RECEIVER_UPDATE_FLAG & UPPER_DOT) == 0);


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

static_assert(sizeof(SEGMENT_ORDERING) == 4 * 7, "ordering doesn't sum to 4 chars x 7 segments");

/*
 *      aaaaaa
 *     f      b
 *     f      b
 *     f      b
 *      gggggg
 *     e      c
 *     e      c
 *     e      c
 *      dddddd
 */
static const uint8_t CHARACTER_SHAPES[] PROGMEM = {
        0b0000000,
        0b0111111,
        0b0000110,
        0b1011011,
        0b1001111,
        0b1100110,
        0b1101101,
        0b1111101,
        0b0000111,
        0b1111111,
        0b1100111,
        0b1000000,
        0b0001000
};

static const char CHARACTER_ORDER[] PROGMEM = {
        ' ', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-', '_'
};

static_assert(sizeof(CHARACTER_SHAPES) == sizeof(CHARACTER_ORDER), "every char shape has to have character defined");

static uint8_t characterBuffer[NUMBER_OF_POSITIONS];
static uint8_t dotBuffer = 0;
static uint8_t segmentBuffer[5];

static inline void setDot(const uint8_t outputNumber, const bool enabled) {

    if (enabled) {
        segmentBuffer[outputNumber / 8] |= _BV(outputNumber % 8);
    } else {
        segmentBuffer[outputNumber / 8] &= ~_BV(outputNumber % 8);
    }
}

static void reloadDisplay() {

    PORT(CK_PORT) &= ~_BV(CK_PIN);

    PORT(STB_PORT) &= ~_BV(STB_PIN);

    memset(segmentBuffer, 0, 5);

    for (uint8_t numberPos = 0; numberPos != NUMBER_OF_POSITIONS; ++numberPos) {
        const uint8_t characterShape =
                (numberPos == 0 and (dotBuffer & RECEIVER_UPDATE_FLAG))
                ? RECEIVER_UPDATE_CHARACTER_SHAPE
                : pgm_read_byte(CHARACTER_SHAPES + characterBuffer[numberPos]);

        for (uint8_t s = 0; s != 7; ++s) {
            if (characterShape & (1 << s)) {
                const uint8_t segment = pgm_read_byte(SEGMENT_ORDERING + 7 * numberPos + s) - 1;

                segmentBuffer[segment / 8] |= (1 << (segment % 8));
            }
        }
    }

    setDot(13, (dotBuffer & LOWER_DOT) != 0);
    setDot(14, (dotBuffer & UPPER_DOT) != 0);

    for (uint8_t a = 40; a != 0; --a) {

        const uint8_t idx = (a - 1) / 8;
        const uint8_t offset = (a - 1) % 8;

        PORT(CK_PORT) &= ~_BV(CK_PIN);

        if ((segmentBuffer[idx] & (1 << offset)) or a == 38) {
            PORT(S_IN_PORT) |= _BV(S_IN_PIN);
        } else {
            PORT(S_IN_PORT) &= ~_BV(S_IN_PIN);
        }

        PORT(CK_PORT) |= _BV(CK_PIN);
    }

    PORT(STB_PORT) |= _BV(STB_PIN);
}

void octoglow::vfd_clock::display::init() {
    // all connectors are outputs
    DDR(CK_PORT) |= _BV(CK_PIN);
    DDR(STB_PORT) |= _BV(STB_PIN);
    DDR(S_IN_PORT) |= _BV(S_IN_PIN);

    // setup timer
    DDR(CL_PORT) |= _BV(CL_PIN);
    TCCR1A = _BV(COM1B1) | _BV(PWM1B);
    TCCR1B = _BV(PSR1) | _BV(CS13) | _BV(CS11); // clk / 512

    OCR1C = PWM_STEPS;

    setBrightness(MAX_BRIGHTNESS);

    setDots(LOWER_DOT, false);
    setAllCharacters(const_cast<char *>("-_-_"));
}

void octoglow::vfd_clock::display::setBrightness(const uint8_t brightness) {
    OCR1B = (brightness > MAX_BRIGHTNESS ? MAX_BRIGHTNESS : brightness) * PWM_STEPS / MAX_BRIGHTNESS;
}

void octoglow::vfd_clock::display::setCharacter(const uint8_t position, const char character,
                                                const bool shouldReloadDisplay) {
    constexpr uint8_t numberOfCharacters = sizeof(CHARACTER_ORDER) / sizeof(CHARACTER_ORDER[0]);

    uint8_t c;
    for (c = 0; c < numberOfCharacters; ++c) {
        if (pgm_read_byte(CHARACTER_ORDER + c) == character) {
            break;
        }
    }

    if (c == numberOfCharacters) {
        c = 0;
    }

    characterBuffer[position % NUMBER_OF_POSITIONS] = c;

    if (shouldReloadDisplay) {
        reloadDisplay();
    }
}

void octoglow::vfd_clock::display::setAllCharacters(char *const characters) {
    uint8_t p = 0;
    for (; p != NUMBER_OF_POSITIONS - 1; ++p) {
        setCharacter(p, characters[p], false);
    }
    setCharacter(p, characters[p], true);
}

void ::octoglow::vfd_clock::display::setDots(const uint8_t newDotState, const bool shouldReloadDisplay) {

    dotBuffer = newDotState;

    if (shouldReloadDisplay) {
        reloadDisplay();
    }
}

void ::octoglow::vfd_clock::display::setReceiverUpdateFlag(const bool enabled) {
    if (enabled) {
        dotBuffer |= RECEIVER_UPDATE_FLAG;
    } else {
        dotBuffer &= ~RECEIVER_UPDATE_FLAG;
    }

    reloadDisplay();
}











