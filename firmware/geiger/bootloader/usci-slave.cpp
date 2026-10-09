#include "usci-slave.hpp"
#include "twiboot.hpp"
#include "i2c-slave.hpp"

#include <msp430.h>

using namespace octoglow::geiger;
using namespace octoglow::geiger::bootloader;

#define SDA_PIN BIT7
#define SCL_PIN BIT6

static uint8_t byteCounter;

static uint8_t nextByteNumber() {
    const uint8_t result = byteCounter;
    if (byteCounter != 0xff) {
        ++byteCounter;
    }
    return result;
}

void usci_slave::init() {
    byteCounter = 0;

    P1SEL |= SDA_PIN | SCL_PIN;
    P1SEL2 |= SDA_PIN | SCL_PIN;
    UCB0CTL1 = UCSWRST;
    UCB0CTL0 = UCMODE_3 | UCSYNC; // I2C slave
    UCB0I2COA = i2c::BOOTLOADER_ADDRESS;
    UCB0CTL1 &= ~UCSWRST;
}

/*
 * The order matters. START (own address) is handled first: after the page is written in onStop(),
 * the next transmission may already have started and delivered its first byte. The last byte of the previous
 * transmission is always processed before, the loop polls much faster than one byte on the bus, except
 * when the flash is written, which happens only after STOP.
 */
void usci_slave::poll() {
    if (UCB0STAT & UCSTTIFG) {
        UCB0STAT &= ~UCSTTIFG;
        byteCounter = 0;
    }

    if (IFG2 & UCB0RXIFG) {
        const uint8_t data = UCB0RXBUF;
        twiboot::onDataWrite(nextByteNumber(), data);
    }

    if (UCB0STAT & UCSTPIFG) {
        UCB0STAT &= ~UCSTPIFG;
        twiboot::onStop();
    }

    if (IFG2 & UCB0TXIFG) {
        UCB0TXBUF = twiboot::onDataRead(nextByteNumber());
    }
}

void usci_slave::disable() {
    UCB0CTL1 = UCSWRST;
    P1SEL &= ~(SDA_PIN | SCL_PIN);
    P1SEL2 &= ~(SDA_PIN | SCL_PIN);
}
