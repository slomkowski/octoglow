#include "receiver433.hpp"

#include <avr/io.h>
#include <avr/interrupt.h>

namespace octoglow {
    namespace vfd_clock {
        namespace receiver433 {
            octoglow::vfd_clock::protocol::WeatherSensorState currentWeatherSensorState;
        }
    }
}

constexpr uint8_t NUM_OF_BITS_IN_PACKET = 37;

static volatile bool canFillCurrentWeatherSensorState = false;
static volatile uint8_t internalBuffer[NUM_OF_BITS_IN_PACKET / 8 + 1];

constexpr uint8_t pulses(double milliseconds) {
    return uint8_t(milliseconds / 1000.0 * ((double) F_CPU) / 1024.0); // we assume prescaler is set to 1024
}

static inline void timerStart() {
    TCNT0 = 0;
    TCCR0 = _BV(PSR0) | _BV(CS02) | _BV(CS00); // prescaler to fclk/1024
}

static inline void timerStop() {
    TCCR0 = 0;
}

void ::octoglow::vfd_clock::receiver433::init() {
    MCUCR |= _BV(ISC01) | _BV(ISC00);
    GIMSK |= _BV(INT0);

    timerStart();
}

void ::octoglow::vfd_clock::receiver433::pool() {
    if (canFillCurrentWeatherSensorState) {

        currentWeatherSensorState.temperature =
                (((uint16_t) internalBuffer[2]) << 4) + (0x0f & (internalBuffer[3] >> 4));
        currentWeatherSensorState.humidity = (0xf0 & (internalBuffer[3] << 4)) + (0x0f & (internalBuffer[4] >> 4));
        currentWeatherSensorState.weakBattery = (internalBuffer[1] & 0b1000) != 0;

        canFillCurrentWeatherSensorState = false;
    }
}


ISR(INT0_vect) {

    static uint8_t position;

    timerStop();

    if (TCNT0 > pulses(9) and TCNT0 < pulses(10.3)) { //
        // start bit
        position = 0;
    } else if (position < NUM_OF_BITS_IN_PACKET and TCNT0 > pulses(4) and TCNT0 < pulses(5.2)) {
        // this is 1
        internalBuffer[position / 8] |= _BV(7 - (position % 8));
        ++position;
    } else if (position < NUM_OF_BITS_IN_PACKET and TCNT0 > pulses(2) and TCNT0 < pulses(3)) {
        // this is 0
        internalBuffer[position / 8] &= ~_BV(7 - (position % 8));
        ++position;
    } else {
        position = 0;
    }

    if (position == NUM_OF_BITS_IN_PACKET) {
        canFillCurrentWeatherSensorState = true;
    }

    timerStart();
}