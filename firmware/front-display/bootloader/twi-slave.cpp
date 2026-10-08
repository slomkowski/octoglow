#include "twi-slave.hpp"
#include "twiboot.hpp"
#include "i2c-slave.hpp"

#include <avr/io.h>
#include <util/twi.h>

using namespace octoglow::front_display;
using namespace octoglow::front_display::bootloader;

static uint8_t byteCounter;
static bool addressed;

void twislave::init() {
    TWAR = i2c::BOOTLOADER_ADDRESS << 1;
    TWCR = _BV(TWEA) | _BV(TWEN);
}

void twislave::poll() {
    if (!(TWCR & _BV(TWINT))) {
        return;
    }

    // TWEA: acknowledge the next byte or own address
    uint8_t control = _BV(TWINT) | _BV(TWEA) | _BV(TWEN);

    switch (TW_STATUS) {
        case TW_SR_SLA_ACK:
            byteCounter = 0;
            addressed = true;
            break;

        case TW_SR_DATA_ACK:
            if (!twiboot::onDataWrite(byteCounter++, TWDR)) {
                control &= ~_BV(TWEA);
            }
            break;

        case TW_ST_SLA_ACK:
            byteCounter = 0;
            addressed = true;
            [[fallthrough]];

        case TW_ST_DATA_ACK:
            TWDR = twiboot::onDataRead(byteCounter++);
            break;

        case TW_BUS_ERROR:
            // releases the lines, no STOP is sent in slave mode
            control |= _BV(TWSTO);
            break;

        default:
            // STOP, repeated START or NAK, wait to be addressed again
            break;
    }

    TWCR = control;
}

void twislave::disable() {
    TWCR = 0;
}

bool twislave::takeActivityFlag() {
    const bool result = addressed;
    addressed = false;
    return result;
}
