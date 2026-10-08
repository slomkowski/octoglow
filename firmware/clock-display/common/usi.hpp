/*
 * USI in two-wire (I2C) mode on ATtiny461A, shared by the application (src/) and the bootloader (bootloader/).
 * Only hardware configuration lives here, both I2C slave state machines are separate.
 */
#pragma once

#include <inttypes.h>
#include <avr/io.h>

namespace octoglow::vfd_clock::usi {
    constexpr uint8_t APP_I2C_ADDRESS = 0x10;
    constexpr uint8_t BOOTLOADER_I2C_ADDRESS = 0x11; // also hardcoded in flash-over-i2c.sh

    static_assert(APP_I2C_ADDRESS != BOOTLOADER_I2C_ADDRESS);

    constexpr uint8_t SDA_PIN = PB0;
    constexpr uint8_t SCL_PIN = PB2;

    /**
     * Pull-ups on both lines, SCL as output: USI drives it low only when holding the clock.
     */
    inline void initPins() {
        // separate statements compile to single SBI instructions
        PORTB |= _BV(SCL_PIN);
        PORTB |= _BV(SDA_PIN);
        DDRB |= _BV(SCL_PIN);
    }

    inline void setSdaInput() {
        DDRB &= ~_BV(SDA_PIN);
    }

    inline void setSdaOutput() {
        DDRB |= _BV(SDA_PIN);
    }

    inline bool isSdaHigh() {
        return PINB & _BV(SDA_PIN);
    }

    inline bool isSclHigh() {
        return PINB & _BV(SCL_PIN);
    }

    /**
     * USICR value for two-wire mode, counter clocked by both SCL edges.
     * SCL is always held low after the start condition, until USISIF is cleared.
     * @param holdSclOnOverflow also hold SCL low after counter overflow, until USIOIF is cleared
     */
    constexpr uint8_t controlRegister(const bool holdSclOnOverflow,
                                      const bool startInterrupt = false,
                                      const bool overflowInterrupt = false) {
        return (startInterrupt ? _BV(USISIE) : 0)
               | (overflowInterrupt ? _BV(USIOIE) : 0)
               | _BV(USIWM1) | (holdSclOnOverflow ? _BV(USIWM0) : 0)
               | _BV(USICS1);
    }

    /**
     * USISR counter value to overflow after the given number of bits; the counter counts both SCL edges.
     */
    constexpr uint8_t counterForBits(const uint8_t bits) {
        return ((16 - 2 * bits) & 0x0f) << USICNT0;
    }

    static_assert(counterForBits(8) == 0);
    static_assert(counterForBits(1) == 14 << USICNT0);
}
