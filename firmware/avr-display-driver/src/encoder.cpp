#include "global.hpp"

#include "encoder.hpp"

#include <avr/io.h>

#define ENC_PORT D
#define ENC_A_PIN 1
#define ENC_B_PIN 2
#define ENC_BTN_PIN 3

void vfd::encoder::init() {
    // enable pull-up
    PORT(ENC_PORT) |= _BV(ENC_A_PIN) | _BV(ENC_B_PIN) | _BV(ENC_BTN_PIN);
}

static int8_t currentValue = 0;
static const int8_t encStates[] = {0, -1, 1, 0, 1, 0, 0, -1, -1, 0, 0, 1, 0, 1, -1, 0};

/**
 * returns change in encoder state (-1,0,1)
 * this code is based on https://www.circuitsathome.com/mcu/reading-rotary-encoder-on-arduino/
 */
static int8_t readEncoder() {
    static uint8_t oldAB = 0;
    oldAB <<= 2;
    oldAB |= (PIN(ENC_PORT) & (_BV(ENC_A_PIN) | _BV(ENC_B_PIN)));
    return (encStates[(oldAB & 0x0f)]);
}


void vfd::encoder::pool() {
    static int8_t buffer = 0;
    const int8_t v = readEncoder();
    if (v != buffer) {
        buffer = v;
        currentValue = v;
    }
}

int8_t vfd::encoder::getValueAndClear() {
    auto v = currentValue;
    currentValue = 0;
    return v;
}





