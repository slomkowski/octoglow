#include "receiver433.hpp"
#include "display.hpp"

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

enum class ValueUpdateState : uint8_t {
    WAITING_FOR_FIRST_MEASUREMENT,
    FIRST_MEASUREMENT_PROVIDED,
    WAITING_FOR_SECOND_MEASUREMENT,
    SECOND_MEASUREMENT_PROVIDED,
    MEASUREMENT_ACCEPTED_BLINK_ENABLED
};

static volatile uint8_t timer1overflowCounter = 0;
static volatile ValueUpdateState updateState = ValueUpdateState::WAITING_FOR_FIRST_MEASUREMENT;
static volatile uint8_t mainMeasurementBuffer[NUM_OF_BITS_IN_PACKET / 8 + 1];
static uint8_t comparingMeasurementBuffer[NUM_OF_BITS_IN_PACKET / 8 + 1];


constexpr uint8_t msToTimer0pulses(double milliseconds) {
    return uint8_t(milliseconds / 1000.0 * ((double) F_CPU) / 1024.0); // we assume prescaler is set to 1024
}

constexpr uint8_t msToTimer1overflows(double milliseconds) {
    return uint8_t(milliseconds / 1000.0 * ((double) F_CPU) / 512.0 / 255.0);// we assume prescaler is set to 512
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
    TIMSK |= _BV(TOIE1);

    timerStart();
}

void ::octoglow::vfd_clock::receiver433::pool() {

    if (timer1overflowCounter > msToTimer1overflows(700)) {
        timer1overflowCounter = 0;
        updateState = ValueUpdateState::WAITING_FOR_FIRST_MEASUREMENT;
    } else if (updateState == ValueUpdateState::FIRST_MEASUREMENT_PROVIDED) {
        for (uint8_t i = sizeof(mainMeasurementBuffer) - 1; i != 0; --i) {
            comparingMeasurementBuffer[i] = mainMeasurementBuffer[i];
        }
        updateState = ValueUpdateState::WAITING_FOR_SECOND_MEASUREMENT;
    } else if (updateState == ValueUpdateState::SECOND_MEASUREMENT_PROVIDED) {
        bool buffersAreEqual = true;
        for (uint8_t i = sizeof(mainMeasurementBuffer) - 1; i != 0; --i) {
            if (comparingMeasurementBuffer[i] != mainMeasurementBuffer[i]) {
                buffersAreEqual = false;
                break;
            }
        }

        if (buffersAreEqual) {
            currentWeatherSensorState.temperature =
                    (((uint16_t) mainMeasurementBuffer[2]) << 4) + (0x0f & (mainMeasurementBuffer[3] >> 4));
            currentWeatherSensorState.humidity =
                    (0xf0 & (mainMeasurementBuffer[3] << 4)) + (0x0f & (mainMeasurementBuffer[4] >> 4));
            currentWeatherSensorState.weakBattery = (mainMeasurementBuffer[1] & 0b1000) != 0;

            updateState = ValueUpdateState::MEASUREMENT_ACCEPTED_BLINK_ENABLED;
            display::setReceiverUpdateFlag(true);
        } else {
            updateState = ValueUpdateState::WAITING_FOR_FIRST_MEASUREMENT;
        }
    } else if (updateState == ValueUpdateState::MEASUREMENT_ACCEPTED_BLINK_ENABLED
               and timer1overflowCounter > msToTimer1overflows(500)) {
        display::setReceiverUpdateFlag(false);
        updateState = ValueUpdateState::WAITING_FOR_FIRST_MEASUREMENT;
    }
}


ISR(INT0_vect) {

    static uint8_t position;

    timerStop();

    if (TCNT0 > msToTimer0pulses(9) and TCNT0 < msToTimer0pulses(10.3)) { //
        // start bit
        position = 0;
    } else if (position < NUM_OF_BITS_IN_PACKET and TCNT0 > msToTimer0pulses(4) and TCNT0 < msToTimer0pulses(5.2)) {
        // this is 1
        mainMeasurementBuffer[position / 8] |= _BV(7 - (position % 8));
        ++position;
    } else if (position < NUM_OF_BITS_IN_PACKET and TCNT0 > msToTimer0pulses(2) and TCNT0 < msToTimer0pulses(3)) {
        // this is 0
        mainMeasurementBuffer[position / 8] &= ~_BV(7 - (position % 8));
        ++position;
    } else {
        position = 0;
    }

    if (position == NUM_OF_BITS_IN_PACKET) {
        if (updateState == ValueUpdateState::WAITING_FOR_FIRST_MEASUREMENT) {
            updateState = ValueUpdateState::FIRST_MEASUREMENT_PROVIDED;
        } else if (updateState == ValueUpdateState::WAITING_FOR_SECOND_MEASUREMENT) {
            updateState = ValueUpdateState::SECOND_MEASUREMENT_PROVIDED;
        }
    }

    timerStart();
}

// this is configured in init() in display.cpp. called every 16.384 ms
ISR(TIMER1_OVF1_vect) {
    if (timer1overflowCounter != 250) {
        ++timer1overflowCounter;
    }
}