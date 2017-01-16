#include "vfd.hpp"
#include "speaker.hpp"

#include <avr/io.h>
#include <avr/wdt.h>
#include <util/delay.h>
#include <avr/interrupt.h>

#include <i2c-slave.hpp>

static void processCommands(uint8_t *rxbuf, uint8_t *txbuf) {
}

int main() {
    vfd::init();
    speaker::init();

    // i2c
    TWI_Slave_Initialise((unsigned char) ((I2C_SLAVE_ADDRESS << TWI_ADR_BITS) | (1 << TWI_GEN_BIT)), processCommands);
    TWI_Start_Transceiver();

#if WATCHD0G_ENABLE
    wdt_enable(WDTO_120MS);
#endif

    sei();

    vfd::write("aącćzźżłl żółwia maść ZŻÓOŁWIA MĄKA");

    while (true) {

        vfd::pool();
        speaker::pool();


#if WATCHD0G_ENABLE
        wdt_reset();
#endif
    }
}