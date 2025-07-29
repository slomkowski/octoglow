#include "encoder.hpp"

#include "main.hpp"

#include <avr/interrupt.h>

#define ENC_PORT D
#define ENC_A_PIN 1
#define ENC_B_PIN 2
#define ENC_BTN_PIN 3

using namespace octoglow::front_display::encoder;

constexpr uint8_t DEBOUNCE_ITERATIONS = 5;
constexpr uint16_t TICK_FREQUENCY = 250; // Hz

void octoglow::front_display::encoder::init() {
    // enable pull-up
    PORT(ENC_PORT) |= _BV(ENC_A_PIN) | _BV(ENC_B_PIN) | _BV(ENC_BTN_PIN);

    PCICR |= _BV(PCIE2);
    PCMSK2 |= _BV(PCINT18) | _BV(PCINT17);

    TCCR0A = _BV(WGM01);
    TCCR0B = _BV(CS02); // f_cpu / 256
    OCR0A = F_CPU / 256 / TICK_FREQUENCY - 1;
    TIMSK0 |= _BV(OCIE0A);
}

constexpr uint8_t DIR_NONE = 0x0;
constexpr uint8_t DIR_CW = 0x10;
constexpr uint8_t DIR_CCW = 0x20;

constexpr uint8_t R_START = 0x0;
constexpr uint8_t R_CW_FINAL = 0x1;
constexpr uint8_t R_CW_BEGIN = 0x2;
constexpr uint8_t R_CW_NEXT = 0x3;
constexpr uint8_t R_CCW_BEGIN = 0x4;
constexpr uint8_t R_CCW_FINAL = 0x5;
constexpr uint8_t R_CCW_NEXT = 0x6;

// encoder code taken from https://github.com/brianlow/Rotary/blob/master/Rotary.cpp
static const uint8_t ttable[7][4] PROGMEM = {
    // R_START
    {R_START, R_CW_BEGIN, R_CCW_BEGIN, R_START},
    // R_CW_FINAL
    {R_CW_NEXT, R_START, R_CW_FINAL, R_START | DIR_CW},
    // R_CW_BEGIN
    {R_CW_NEXT, R_CW_BEGIN, R_START, R_START},
    // R_CW_NEXT
    {R_CW_NEXT, R_CW_BEGIN, R_CW_FINAL, R_START},
    // R_CCW_BEGIN
    {R_CCW_NEXT, R_START, R_CCW_BEGIN, R_START},
    // R_CCW_FINAL
    {R_CCW_NEXT, R_CCW_FINAL, R_START, R_START | DIR_CCW},
    // R_CCW_NEXT
    {R_CCW_NEXT, R_CCW_FINAL, R_CCW_BEGIN, R_START},
};

static volatile bool prevButtonState = false;
static volatile uint8_t currentDebounceIterations = 0;

static volatile uint8_t state = R_START;

ISR(PCINT2_vect) {
    const uint8_t pinstate = 0b11 & (PIN(ENC_PORT) & (_BV(ENC_A_PIN) | _BV(ENC_B_PIN))) >> 1;
    state = pgm_read_byte(&ttable[state & 0xf][pinstate]);
    const uint8_t result = state & 0x30;

    if (result == DIR_CW) {
        _currentEncoderSteps++;
    } else if (result == DIR_CCW) {
        _currentEncoderSteps--;
    }
}

// triggered every 4 ms
ISR(TIMER0_COMPA_vect) {
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
