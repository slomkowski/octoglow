#include "i2c-slave.hpp"

#include <avr/io.h>
#include <util/twi.h>
#include <avr/interrupt.h>

using namespace octoglow::front_display::i2c;

/**
 * set the TWCR to enable address matching and enable TWI, clear TWINT, enable TWI interrupt
 */
inline static void prepareForNextByte() {
    TWCR |= (1 << TWIE) | (1 << TWINT) | (1 << TWEA) | (1 << TWEN);
}

void octoglow::front_display::i2c::init() {
    // load address into TWI address register
    TWAR = (SLAVE_ADDRESS << 1);

    prepareForNextByte();
}

ISR(TWI_vect) {
    uint8_t data;

    // own address has been acknowledged
    if ((TWSR & 0xF8) == TW_SR_SLA_ACK) {
        onStart();

        //onReceive(TWDR);

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
    } else {
        // if none of the above apply prepare TWI to be addressed again
        TWCR |= (1 << TWIE) | (1 << TWEA) | (1 << TWEN);
    }
}
