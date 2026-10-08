/*
 * VFD driver hardware shared by the application (src/) and the bootloader (bootloader/).
 * The VFD is statically driven by a 40-bit shift register: CK - clock, S_IN - data, STB - latch.
 * CL is the blanking input, PWM on OC1B sets the brightness.
 */
#pragma once

#include <inttypes.h>
#include <avr/io.h>
#include <avr/pgmspace.h>

namespace octoglow::vfd_clock::vfd {
    constexpr uint8_t CK_PIN = PA2;
    constexpr uint8_t STB_PIN = PA1;
    constexpr uint8_t S_IN_PIN = PA3;
    constexpr uint8_t CL_PIN = PB3;

    constexpr uint8_t NUMBER_OF_POSITIONS = 4;
    constexpr uint8_t NUMBER_OF_SEGMENTS = 40;
    constexpr uint8_t SEGMENT_BUFFER_SIZE = NUMBER_OF_SEGMENTS / 8;

    constexpr uint8_t ALWAYS_ON_SEGMENT = 37; // 0-based bit in the segment buffer
    constexpr uint8_t UPPER_DOT_SEGMENT = 14;
    constexpr uint8_t LOWER_DOT_SEGMENT = 13;

    // the dots are in the second byte of the segment buffer
    constexpr uint8_t UPPER_DOT = 1 << (UPPER_DOT_SEGMENT % 8);
    constexpr uint8_t LOWER_DOT = 1 << (LOWER_DOT_SEGMENT % 8);
    static_assert(UPPER_DOT_SEGMENT / 8 == 1 and LOWER_DOT_SEGMENT / 8 == 1);

    static constexpr uint8_t BRIGHTNESS_PWM_VALUES[] PROGMEM = {0, 1, 4, 10, 20, 60};
    constexpr uint8_t DEFAULT_BRIGHTNESS = 3;

    /*
     * rows - display number
     * cols - segments a-g, 1-based segment number
     */
    static constexpr uint8_t SEGMENT_ORDERING[] PROGMEM = {
        9, 7, 8, 9, 9, 11, 10,
        1, 2, 4, 3, 37, 6, 5,
        36, 35, 33, 36, 34, 39, 40,
        30, 29, 27, 26, 28, 32, 31
    };

    static_assert(sizeof(SEGMENT_ORDERING) == NUMBER_OF_POSITIONS * 7, "ordering doesn't sum to 4 chars x 7 segments");

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
     *
     * bit 0 - a, ..., bit 6 - g
     */
    static constexpr uint8_t CHARACTER_SHAPES[] PROGMEM = {
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
        0b0001000,
        0b0110001,
    };

    static constexpr char CHARACTER_ORDER[] PROGMEM = {
        ' ', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-', '_', 'T'
    };

    static_assert(sizeof(CHARACTER_SHAPES) == sizeof(CHARACTER_ORDER),
                  "every char shape has to have character defined");

    struct SegmentBuffer {
        uint8_t bytes[SEGMENT_BUFFER_SIZE];
    };

    /**
     * Compile-time only (reads PROGMEM tables directly): the segment buffer for the given character shapes.
     */
    consteval SegmentBuffer segmentsForShapes(const uint8_t (&shapes)[NUMBER_OF_POSITIONS]) {
        SegmentBuffer buffer{};
        buffer.bytes[ALWAYS_ON_SEGMENT / 8] |= 1 << (ALWAYS_ON_SEGMENT % 8);

        for (uint8_t position = 0; position != NUMBER_OF_POSITIONS; ++position) {
            for (uint8_t s = 0; s != 7; ++s) {
                if (shapes[position] & 1 << s) {
                    const uint8_t segment = SEGMENT_ORDERING[7 * position + s] - 1;
                    buffer.bytes[segment / 8] |= 1 << (segment % 8);
                }
            }
        }

        return buffer;
    }

    consteval uint8_t shapeOf(const char character) {
        for (uint8_t c = 0; c != sizeof(CHARACTER_ORDER); ++c) {
            if (CHARACTER_ORDER[c] == character) {
                return CHARACTER_SHAPES[c];
            }
        }
        __builtin_unreachable(); // compile error: character not supported
    }

    inline void initPins() {
        DDRA |= _BV(CK_PIN);
        DDRA |= _BV(STB_PIN);
        DDRA |= _BV(S_IN_PIN);
    }

    inline void setBrightnessPwm(const uint8_t pwmValue) {
        OCR1B = pwmValue;
    }

    /**
     * Timer1 runs at clk / 128, PWM on OC1B (CL pin). The application also uses its overflow as a time base.
     */
    inline void initBrightnessPwm(const uint8_t pwmValue) {
        DDRB |= _BV(CL_PIN);
        TCCR1A = _BV(COM1B1) | _BV(PWM1B);
        TCCR1B = _BV(PSR1) | _BV(CS13);
        setBrightnessPwm(pwmValue);
    }

    /**
     * Shifts the segment buffer out, the last segment first, and latches it.
     */
    inline void loadSegments(const uint8_t (&segments)[SEGMENT_BUFFER_SIZE]) {
        PORTA &= ~_BV(STB_PIN);

        for (uint8_t idx = SEGMENT_BUFFER_SIZE; idx != 0; --idx) {
            const uint8_t byte = segments[idx - 1];

            for (uint8_t mask = 0x80; mask != 0; mask >>= 1) {
                PORTA &= ~_BV(CK_PIN);

                if (byte & mask) {
                    PORTA |= _BV(S_IN_PIN);
                } else {
                    PORTA &= ~_BV(S_IN_PIN);
                }

                PORTA |= _BV(CK_PIN);
            }
        }

        PORTA |= _BV(STB_PIN);
    }
}
