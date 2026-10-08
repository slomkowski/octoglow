#include "receiver433.hpp"
#include "display.hpp"

#include <avr/io.h>
#include <avr/interrupt.h>


namespace octoglow::vfd_clock::receiver433 {
    protocol::WeatherSensorState currentWeatherSensorState;
}


constexpr uint8_t NUM_OF_BITS_IN_PACKET = 40;

enum class ValueUpdateState : uint8_t {
    WAITING_FOR_FIRST_MEASUREMENT,
    FIRST_MEASUREMENT_PROVIDED,
    WAITING_FOR_SECOND_MEASUREMENT,
    SECOND_MEASUREMENT_PROVIDED,
    MEASUREMENT_ACCEPTED_BLINK_ENABLED
};

static volatile uint16_t timer1overflowCounter = 0;
static volatile uint8_t blinkOverflowsLeft = 0;
static volatile auto updateState = ValueUpdateState::WAITING_FOR_FIRST_MEASUREMENT;
static volatile uint8_t mainMeasurementBuffer[NUM_OF_BITS_IN_PACKET / 8 + 1];
static uint8_t comparingMeasurementBuffer[NUM_OF_BITS_IN_PACKET / 8 + 1];


constexpr uint8_t msToTimer0pulses(const double milliseconds) {
    // we assume prescaler is set to 1024
    return static_cast<uint8_t>(milliseconds / 1000.0 * static_cast<double>(F_CPU) / 1024.0);
}

constexpr uint8_t msToTimer1overflows(const double milliseconds) {
    // we assume prescaler is set to 128
    return static_cast<uint8_t>(milliseconds / 1000.0 * static_cast<double>(F_CPU) / 128.0 / 255.0);
}

static void timerRestart() {
    TCNT0L = 0;
    TCCR0B = _BV(CS02) | _BV(CS00); // prescaler to fclk/1024
}

static void timerStop() {
    TCCR0B = 0;
}

void octoglow::vfd_clock::receiver433::init() {
    MCUCR |= _BV(ISC01) | _BV(ISC00);
    GIMSK |= _BV(INT0);
    TIMSK |= _BV(TOIE1);

    TCCR0A = 0;

    currentWeatherSensorState.flags = 0;

    timerRestart();
}

void octoglow::vfd_clock::receiver433::pool() {
    // the blink is timed independently of the measurement timeout, so the flag is always cleared
    if (updateState == ValueUpdateState::MEASUREMENT_ACCEPTED_BLINK_ENABLED and blinkOverflowsLeft == 0) {
        display::setReceiverUpdateFlag(display::ReceiverUpdateFlag::DISABLED);
        updateState = ValueUpdateState::WAITING_FOR_FIRST_MEASUREMENT;
    }

    if (updateState == ValueUpdateState::MEASUREMENT_ACCEPTED_BLINK_ENABLED) {
        // wait for the blink to end
    } else if (timer1overflowCounter > msToTimer1overflows(700)) {
        timer1overflowCounter = 0;
        updateState = ValueUpdateState::WAITING_FOR_FIRST_MEASUREMENT;
    } else if (updateState == ValueUpdateState::FIRST_MEASUREMENT_PROVIDED) {
        for (uint8_t i = 0; i != sizeof(mainMeasurementBuffer); ++i) {
            comparingMeasurementBuffer[i] = mainMeasurementBuffer[i];
        }
        updateState = ValueUpdateState::WAITING_FOR_SECOND_MEASUREMENT;
    } else if (updateState == ValueUpdateState::SECOND_MEASUREMENT_PROVIDED) {
        bool buffersAreEqual = true;
        for (uint8_t i = 0; i != sizeof(mainMeasurementBuffer); ++i) {
            if (comparingMeasurementBuffer[i] != mainMeasurementBuffer[i]) {
                buffersAreEqual = false;
                break;
            }
        }

        if (buffersAreEqual) {
            for (uint8_t i = 0; i != sizeof(currentWeatherSensorState.rawData); ++i) {
                currentWeatherSensorState.rawData[i] = comparingMeasurementBuffer[i];
            }
            currentWeatherSensorState.flags = protocol::VALID_MEASUREMENT_FLAG;

            blinkOverflowsLeft = msToTimer1overflows(500);
            display::setReceiverUpdateFlag(display::ReceiverUpdateFlag::VALID);
            updateState = ValueUpdateState::MEASUREMENT_ACCEPTED_BLINK_ENABLED;
        } else {
            updateState = ValueUpdateState::WAITING_FOR_FIRST_MEASUREMENT;
        }
    }
}


ISR(INT0_vect) {
    static uint8_t position;
    static bool startBitWasFound = false;

    timerStop();

    constexpr double DELTA_MS = 0.2;

    if (position == 0
        and TCNT0L > msToTimer0pulses(8.8 - DELTA_MS)
        and TCNT0L < msToTimer0pulses(9.1 + DELTA_MS)) {
        startBitWasFound = true;
    } else if (startBitWasFound
               and position < NUM_OF_BITS_IN_PACKET
               and TCNT0L > msToTimer0pulses(4.7 - DELTA_MS)
               and TCNT0L < msToTimer0pulses(5.2 + DELTA_MS)) {
        // this is 1
        mainMeasurementBuffer[position / 8] |= _BV(7 - position % 8);
        ++position;
    } else if (startBitWasFound
               and position < NUM_OF_BITS_IN_PACKET
               and TCNT0L > msToTimer0pulses(2.4 - DELTA_MS)
               and TCNT0L < msToTimer0pulses(2.9 + DELTA_MS)) {
        // this is 0
        mainMeasurementBuffer[position / 8] &= ~_BV(7 - position % 8);
        ++position;
    } else {
        startBitWasFound = false;
        position = 0;
    }

    if (position == NUM_OF_BITS_IN_PACKET) {
        if (updateState == ValueUpdateState::WAITING_FOR_FIRST_MEASUREMENT) {
            updateState = ValueUpdateState::FIRST_MEASUREMENT_PROVIDED;
        } else if (updateState == ValueUpdateState::WAITING_FOR_SECOND_MEASUREMENT) {
            updateState = ValueUpdateState::SECOND_MEASUREMENT_PROVIDED;
        }
    }

    timerRestart();
}

// this is configured in init() in display.cpp. called every 4.096 ms
ISR(TIMER1_OVF_vect) {
    if (blinkOverflowsLeft != 0) {
        blinkOverflowsLeft -= 1;
    }

    if (timer1overflowCounter != 4 * 250) {
        timer1overflowCounter += 1;
    }
}
