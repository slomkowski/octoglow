#include "i2c-slave.hpp"

#include <avr/io.h>
#include <util/twi.h>
#include <avr/interrupt.h>
#include <util/crc16.h>

using namespace octoglow::front_display::i2c;

/**
 * set the TWCR to enable address matching and enable TWI, clear TWINT, enable TWI interrupt
 */
static void prepareForNextByte() {
    TWCR = _BV(TWIE) | _BV(TWINT) | _BV(TWEA) | _BV(TWEN);
}

void octoglow::front_display::i2c::init() {
    // load address into TWI address register
    TWAR = (SLAVE_ADDRESS << 1);

    prepareForNextByte();
}

uint8_t octoglow::front_display::i2c::crc8ccittUpdate(const uint8_t inCrc, const uint8_t inData) {
    return _crc8_ccitt_update(inCrc, inData);
}


ISR(TWI_vect) {
    uint8_t data;

    // own address has been acknowledged
    if ((TWSR & 0xF8) == TW_SR_SLA_ACK) {
        onStart();

        prepareForNextByte();
    } else if ((TWSR & 0xF8) == TW_ST_SLA_ACK) {
        onStart();

        onTransmit(&data);
        TWDR = data;

        prepareForNextByte();
    } else if ((TWSR & 0xF8) == TW_SR_DATA_ACK) {
        onReceive(TWDR);

        prepareForNextByte();
    } else if ((TWSR & 0xF8) == TW_ST_DATA_ACK) {
        onTransmit(&data);
        TWDR = data;

        prepareForNextByte();
    } else if ((TWSR & 0xF8) == TW_SR_STOP) {
        onStop();
        prepareForNextByte();
    } else {
        // if none of the above, apply the 'prepare TWI' to be addressed again
        TWCR = _BV(TWIE) | _BV(TWEA) | _BV(TWEN);
    }
}
