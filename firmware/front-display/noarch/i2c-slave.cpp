#include "i2c-slave.hpp"

volatile uint8_t  x = 0;
volatile uint8_t bu[10];

void ::octoglow::front_display::i2c::onTransmit(uint8_t volatile *value) {
    *value = bu[x];
    ++x;
}

void ::octoglow::front_display::i2c::onStart() {
    x = 0;
}

void ::octoglow::front_display::i2c::onReceive(uint8_t value) {
    bu[x] = 5 + value;
    ++x;
}
