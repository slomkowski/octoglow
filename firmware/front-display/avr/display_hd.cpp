#include "Font5x7.hpp"
#include "display.hpp"
#include "main.hpp"

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

using namespace octoglow::front_display::display;
using namespace octoglow::front_display::display::hd;

static uint8_t currentPosition = 0;

void octoglow::front_display::display::init() {
    // all connectors are outputs
    DDR(CK_PORT) |= _BV(CK_PIN);
    DDR(CHG_PORT) |= _BV(CHG_PIN);
    DDR(CL_PORT) |= _BV(CL_PIN);
    DDR(STB_PORT) |= _BV(STB_PIN);
    DDR(S_IN_PORT) |= _BV(S_IN_PIN);
}

static inline __attribute((always_inline)) void ckPulse() {
    PORT(CK_PORT) &= ~_BV(CK_PIN);
    PORT(CK_PORT) |= _BV(CK_PIN);
}

static void iterateOverPositionsAscending(const uint8_t startInclusive,
                                          const uint8_t stopInclusive,
                                          const uint8_t validPosition) {
    for (uint8_t p = startInclusive; p != stopInclusive + 1; ++p) {
        PORT(CK_PORT) &= ~_BV(CK_PIN);
        if (p == validPosition) {
            PORT(S_IN_PORT) |= _BV(S_IN_PIN);
        } else {
            PORT(S_IN_PORT) &= ~_BV(S_IN_PIN);
        }
        PORT(CK_PORT) |= _BV(CK_PIN);
    }
}

static void iterateOverPositionsDescending(const uint8_t startInclusive,
                                           const uint8_t stopInclusive,
                                           const uint8_t validPosition) {
    for (uint8_t p = startInclusive; p != stopInclusive - 1; --p) {
        PORT(CK_PORT) &= ~_BV(CK_PIN);
        if (p == validPosition) {
            PORT(S_IN_PORT) |= _BV(S_IN_PIN);
        } else {
            PORT(S_IN_PORT) &= ~_BV(S_IN_PIN);
        }
        PORT(CK_PORT) |= _BV(CK_PIN);
    }
}

static void setOutputPin(const uint8_t *characterBuffer, const int8_t column, const int8_t row) {
    if (const uint8_t go = characterBuffer[column]; go & 1 << row) {
        PORT(S_IN_PORT) |= _BV(S_IN_PIN);
    } else {
        PORT(S_IN_PORT) &= ~_BV(S_IN_PIN);
    }
}

//__attribute__((optimize("unroll-loops")))
static void holdCharacterOnDisplayInputs(uint8_t position) {
    PORT(STB_PORT) &= ~_BV(STB_PIN);

    if (_brightness == 0) {
        PORT(CL_PORT) &= ~_BV(CL_PIN);
    } else {
        PORT(CL_PORT) |= _BV(CL_PIN);
    }

    const uint8_t *characterPtr = &_frameBuffer[COLUMNS_IN_CHARACTER * position %
                                                (NUM_OF_CHARACTERS * COLUMNS_IN_CHARACTER)];

    if (position >= 10 and position <= 19) {
        position += 20;
    } else if ((position >= 20 and position <= 29) or position >= 30) {
        position -= 10;
    }

    if (_brightness == 1) {
        PORT(CL_PORT) &= ~_BV(CL_PIN);
    }

    if (position < 20) {
        // g7 - g1
        iterateOverPositionsDescending(6, 0, position);

        // g8 - g20
        iterateOverPositionsAscending(7, 19, position);
    } else {
        PORT(S_IN_PORT) &= ~_BV(S_IN_PIN);
        for (uint8_t i = 0; i != 20; ++i) {
            ckPulse();
        }
    }

    if (_brightness == 2) {
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

    if (_brightness == 3) {
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

    if (_brightness == 4) {
        PORT(CL_PORT) &= ~_BV(CL_PIN);
    }

    // a36 - upper bar
    if ((position < 10 and _upperBarBuffer & (1L << position))
        or (position >= 30 and position < 40 and _upperBarBuffer & (1L << (position - 20)))) {
        PORT(S_IN_PORT) |= _BV(S_IN_PIN);
    } else {
        PORT(S_IN_PORT) &= ~_BV(S_IN_PIN);
    }
    ckPulse();

    if (position > 19) {
        // g21 - g33
        iterateOverPositionsAscending(20, 32, position);

        // g40 - g34
        iterateOverPositionsDescending(39, 33, position);
    } else {
        PORT(S_IN_PORT) &= ~_BV(S_IN_PIN);
        for (uint8_t i = 0; i != 20; ++i) {
            ckPulse();
        }
    }

    PORT(STB_PORT) |= _BV(STB_PIN);
}

void hd::displayPool() {
    holdCharacterOnDisplayInputs(currentPosition);

    if (currentPosition == NUM_OF_CHARACTERS - 1) {
        currentPosition = 0;
    } else {
        ++currentPosition;
    }
}
