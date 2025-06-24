#include "encoder.hpp"

#include "main.hpp"

#include <avr/interrupt.h>

#define ENC_PORT D
#define ENC_A_PIN 1
#define ENC_B_PIN 2
#define ENC_BTN_PIN 3

using namespace octoglow::front_display::encoder;

constexpr uint8_t DEBOUNCE_ITERATIONS = 20;

void octoglow::front_display::encoder::init() {
    // enable pull-up
    PORT(ENC_PORT) |= _BV(ENC_A_PIN) | _BV(ENC_B_PIN) | _BV(ENC_BTN_PIN);

    PCICR |= _BV(PCIE2);
    PCMSK2 |= _BV(PCINT18) | _BV(PCINT17);
}

void octoglow::front_display::encoder::pool() {
    static bool prevButtonState = false;
    static uint8_t currentDebounceIterations = 0;

    if (!(PIN(ENC_PORT) & _BV(ENC_BTN_PIN)) and !prevButtonState) {
        ++currentDebounceIterations;

        if (currentDebounceIterations == DEBOUNCE_ITERATIONS) {
            currentDebounceIterations = 0;
            prevButtonState = true;
            _currentButtonState = ButtonState::JUST_PRESSED;
        }
    } else if (PIN(ENC_PORT) & _BV(ENC_BTN_PIN) and prevButtonState) {
        ++currentDebounceIterations;

        if (currentDebounceIterations == DEBOUNCE_ITERATIONS) {
            currentDebounceIterations = 0;
            prevButtonState = false;
            _currentButtonState = ButtonState::JUST_RELEASED;
        }
    }
}

ISR(PCINT2_vect) {
    static uint8_t old = 0;
    const uint8_t current = 0b11 & (PIN(ENC_PORT) & (_BV(ENC_A_PIN) | _BV(ENC_B_PIN))) >> 1;

    if (old == 0b00) {
        if (current == 0b10) { _currentEncoderSteps++; }
        if (current == 0b01) { _currentEncoderSteps--; }
    }

    old = current;
}
