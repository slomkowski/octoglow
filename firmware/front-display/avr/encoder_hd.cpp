#include "encoder.hpp"

#include "main.hpp"

#include <avr/interrupt.h>

#define ENC_PORT D
#define ENC_A_PIN 1
#define ENC_B_PIN 2
#define ENC_BTN_PIN 3

using namespace octoglow::front_display::encoder;

constexpr uint8_t DEBOUNCE_ITERATIONS = 20;

void ::octoglow::front_display::encoder::init() {
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
    } else if ((PIN(ENC_PORT) & _BV(ENC_BTN_PIN)) and prevButtonState) {
        ++currentDebounceIterations;

        if (currentDebounceIterations == DEBOUNCE_ITERATIONS) {
            currentDebounceIterations = 0;
            prevButtonState = false;
            _currentButtonState = ButtonState::JUST_RELEASED;
        }
    }
}

/*
 * Code borrowed from https://www.circuitsathome.com/mcu/rotary-encoder-interrupt-service-routine-for-avr-micros/
 */
ISR(PCINT2_vect) {
    static uint8_t old_AB = 3;
    static int8_t encval = 0;
    static const int8_t enc_states[] PROGMEM = {0, -1, 1, 0, 1, 0, 0, -1, -1, 0, 0, 1, 0, 1, -1, 0};
    /**/
    old_AB <<= 2;  //remember previous state
    old_AB |= (PIN(ENC_PORT) & (_BV(ENC_A_PIN) | _BV(ENC_B_PIN)))
            >> 1; // >> 1 shift is necessary because in original code the encoder was connected to 0 and 1 pins
    encval += pgm_read_byte(&(enc_states[(old_AB & 0x0f)]));
    /* post "Navigation forward/reverse" event */
    if (encval > 3) {  //four steps forward
        _currentEncoderSteps++;
        encval = 0;
    } else if (encval < -3) {  //four steps backwards
        _currentEncoderSteps--;
        encval = 0;
    }
}
