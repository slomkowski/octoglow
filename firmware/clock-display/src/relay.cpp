#include "relay.hpp"

#include "global.hpp"

#include <avr/io.h>

#define RELAY_PORT A
#define RELAY1_PIN 6
#define RELAY2_PIN 7

void octoglow::vfd_clock::relay::init() {
    DDR(RELAY_PORT) |= _BV(RELAY1_PIN) | _BV(RELAY2_PIN);
}

void octoglow::vfd_clock::relay::setState(const Relay relay, const bool enabled) {
    uint8_t shift;

    if (relay == Relay::RELAY_1) {
        shift = _BV(RELAY1_PIN);
    } else {
        shift = _BV(RELAY2_PIN);
    }

    if (enabled) {
        PORT(RELAY_PORT) |= shift;
    } else {
        PORT(RELAY_PORT) &= ~shift;
    }
}
