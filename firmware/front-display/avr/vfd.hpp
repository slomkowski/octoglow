/*
 * VFD driver hardware of the front display, shared by the application (avr/) and the bootloader (bootloader/).
 *
 * The display is multiplexed: each character gets a slot of SLOT_US, timed by Timer1. At the start of the slot,
 * the character shifted in during the previous slot is latched and CL is set high; Timer1 clears CL
 * (OC1B, PB2) after the on-time of the brightness level, independently of the code. The next character
 * is shifted in meanwhile. The brightness and the refresh rate don't depend on the generated code,
 * as long as shifting a character takes less than a slot.
 */
#pragma once

#include "display.hpp"
#include "main.hpp"

#include <util/atomic.h>
#include <util/delay.h>

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

namespace octoglow::front_display::vfd {
    using display::COLUMNS_IN_CHARACTER;
    using display::MAX_BRIGHTNESS;
    using display::NUM_OF_CHARACTERS;

    /**
     * The same refresh rate (192 Hz for the whole display) as the firmware before the slot timer.
     */
    constexpr uint8_t SLOT_US = 130;

    // Timer1 runs at F_CPU
    constexpr uint16_t SLOT_TICKS = F_CPU / 1000000 * SLOT_US;

    constexpr uint16_t onTicksForPermille(const uint16_t permille) {
        return static_cast<uint32_t>(SLOT_TICKS) * permille / 1000;
    }

    /**
     * CL on-time in Timer1 ticks for each brightness level. The duty cycles are the ones of the firmware
     * before the slot timer, which cleared CL at a point in the code (measured on the -O2 build).
     * The highest level clears CL at the end of the slot, so a character isn't lit longer if the next slot is late.
     */
    constexpr uint16_t BRIGHTNESS_ON_TICKS[MAX_BRIGHTNESS + 1] = {
        0,
        onTicksForPermille(119),
        onTicksForPermille(216),
        onTicksForPermille(523),
        onTicksForPermille(822),
        SLOT_TICKS - 1,
    };

    // CL is set high, then the compare output is switched to clear CL; it must not match in between
    static_assert(BRIGHTNESS_ON_TICKS[1] > 16, "on-time too short");

    inline void initPins() {
        // all connectors are outputs
        DDR(CK_PORT) |= _BV(CK_PIN);
        DDR(CHG_PORT) |= _BV(CHG_PIN);
        DDR(CL_PORT) |= _BV(CL_PIN);
        DDR(STB_PORT) |= _BV(STB_PIN);
        DDR(S_IN_PORT) |= _BV(S_IN_PIN);
    }

    /**
     * Timer1: CTC with TOP = OCR1A, one period is a slot; OC1B (CL) is cleared on compare match.
     */
    inline void initSlotTimer() {
        OCR1A = SLOT_TICKS - 1;
        TCCR1A = _BV(COM1B1);
        TCCR1B = _BV(WGM12) | _BV(CS10);
    }

    /**
     * Waits for the end of the current slot, then latches the character shifted in during it
     * and lights it for onTicks. Timer1 runs freely, so the slots are exactly SLOT_TICKS long.
     * If the previous slot is late, there is no waiting and the character is lit only until the end
     * of the current slot; CL has already been cleared by Timer1, so the late character isn't lit longer.
     */
    inline __attribute__((always_inline)) void startSlot(const uint16_t onTicks) {
        while (!(TIFR1 & _BV(OCF1A))) {
        }
        TIFR1 = _BV(OCF1A);

        // CL is low here, the latched character changes in the dark
        PORT(STB_PORT) |= _BV(STB_PIN);

        if (onTicks != 0) {
            // force OC1B high, then let the compare match clear it
            TCCR1A = _BV(COM1B1) | _BV(COM1B0);

            uint16_t start;
            // the on-time is counted from the forced match, an interrupt in between would shorten it
            ATOMIC_BLOCK(ATOMIC_RESTORESTATE) {
                TCCR1C = _BV(FOC1B);
                start = TCNT1;
            }

            const uint16_t end = start + onTicks;
            OCR1B = end < SLOT_TICKS - 1 ? end : SLOT_TICKS - 1;
            TCCR1A = _BV(COM1B1);
        }

        // the minimum strobe width of the driver isn't known, the firmware before the slot timer kept STB
        // high for several microseconds between the characters
        _delay_us(2);
    }

    inline __attribute((always_inline)) void ckPulse() {
        PORT(CK_PORT) &= ~_BV(CK_PIN);
        PORT(CK_PORT) |= _BV(CK_PIN);
    }

    inline void iterateOverPositionsAscending(const uint8_t startInclusive,
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

    inline void iterateOverPositionsDescending(const uint8_t startInclusive,
                                               const uint8_t stopInclusive,
                                               const uint8_t validPosition) {
        const uint8_t span = startInclusive - stopInclusive + 1;
        const uint8_t match = validPosition - stopInclusive + 1;

        for (uint8_t p = span; p != 0; --p) {
            PORT(CK_PORT) &= ~_BV(CK_PIN);
            if (p == match) {
                PORT(S_IN_PORT) |= _BV(S_IN_PIN);
            } else {
                PORT(S_IN_PORT) &= ~_BV(S_IN_PIN);
            }
            PORT(CK_PORT) |= _BV(CK_PIN);
        }
    }

    inline __attribute((always_inline)) void shiftBit(const bool value) {
        if (value) {
            PORT(S_IN_PORT) |= _BV(S_IN_PIN);
        } else {
            PORT(S_IN_PORT) &= ~_BV(S_IN_PIN);
        }
        ckPulse();
    }

    /**
     * Column and row are template parameters, so the mask is a constant: a variable shift is a loop on AVR.
     */
    template<uint8_t COLUMN, uint8_t ROW>
    inline __attribute((always_inline)) void shiftPixel(const uint8_t *const character) {
        static_assert(COLUMN < COLUMNS_IN_CHARACTER and ROW < 7);
        shiftBit(character[COLUMN] & _BV(ROW));
    }

    /**
     * Shifts the content of a single character into the display driver and selects its grid.
     * STB is left low, the character is shown after the next startSlot().
     *
     * @param position 0 to NUM_OF_CHARACTERS - 1
     * @param frameBuffer COLUMNS_IN_CHARACTER bytes per character, bit 0 is the top row
     * @param upperBar bits 0-19 are the segments of the bar above the upper line
     */
    inline __attribute__((always_inline)) void shiftCharacter(uint8_t position,
                                                              const uint8_t *const frameBuffer,
                                                              const uint32_t upperBar) {
        PORT(STB_PORT) &= ~_BV(STB_PIN);

        const uint8_t *const c = &frameBuffer[COLUMNS_IN_CHARACTER * position];

        if ((position >= 10) and (position <= 19)) {
            position += 20;
        } else if ((position >= 20) and (position <= 29)) {
            position -= 10;
        } else if (position >= 30) {
            position -= 10;
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

        // a1 - a11
        shiftPixel<2, 3>(c);
        shiftPixel<3, 3>(c);
        shiftPixel<4, 3>(c);
        shiftPixel<0, 4>(c);
        shiftPixel<1, 4>(c);
        shiftPixel<2, 4>(c);
        shiftPixel<3, 4>(c);
        shiftPixel<4, 4>(c);
        shiftPixel<0, 5>(c);
        shiftPixel<1, 5>(c);
        shiftPixel<2, 5>(c);

        // a18 - a14
        shiftPixel<4, 6>(c);
        shiftPixel<3, 6>(c);
        shiftPixel<2, 6>(c);
        shiftPixel<1, 6>(c);
        shiftPixel<0, 6>(c);

        // 2 dummy
        shiftBit(false);
        shiftBit(false);

        // a12 - a13
        shiftPixel<3, 5>(c);
        shiftPixel<4, 5>(c);

        // 2 dummy
        shiftBit(false);
        shiftBit(false);

        // a25 - a19
        shiftPixel<0, 2>(c);
        shiftPixel<1, 2>(c);
        shiftPixel<2, 2>(c);
        shiftPixel<3, 2>(c);
        shiftPixel<4, 2>(c);
        shiftPixel<0, 3>(c);
        shiftPixel<1, 3>(c);

        // a26 - a35
        shiftPixel<4, 1>(c);
        shiftPixel<3, 1>(c);
        shiftPixel<2, 1>(c);
        shiftPixel<1, 1>(c);
        shiftPixel<0, 1>(c);
        shiftPixel<4, 0>(c);
        shiftPixel<3, 0>(c);
        shiftPixel<2, 0>(c);
        shiftPixel<1, 0>(c);
        shiftPixel<0, 0>(c);

        // a36 - upper bar, segments 0-9 over grids 0-9, 10-19 over grids 30-39
        bool upperBarSegment = false;
        if (position < 10 or position >= 30) {
            const uint8_t segment = position < 10 ? position : position - 20;
            const uint8_t segmentByte = segment < 8
                                            ? static_cast<uint8_t>(upperBar)
                                            : segment < 16
                                                  ? static_cast<uint8_t>(upperBar >> 8)
                                                  : static_cast<uint8_t>(upperBar >> 16);
            upperBarSegment = segmentByte & _BV(segment & 7);
        }
        shiftBit(upperBarSegment);

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
    }
}
