#include "i2c-slave.hpp"
#include "magiceye.hpp"

#include <msp430.h>

#define SDA_PIN BIT7
#define SCL_PIN BIT6

using namespace octoglow::geiger;

void octoglow::geiger::i2c::init() {
    P1SEL |= SDA_PIN + SCL_PIN;               // Assign I2C pins to USCI_B0
    P1SEL2 |= SDA_PIN + SCL_PIN;              // Assign I2C pins to USCI_B0
    UCB0CTL1 |= UCSWRST;                      // Enable SW reset
    UCB0CTL0 = UCMODE_3 + UCSYNC;             // I2C Slave, synchronous mode
    UCB0I2COA = SLAVE_ADDRESS;                // set own (slave) address
    UCB0CTL1 &= ~UCSWRST;                     // Clear SW reset, resume operation
    IE2 |= UCB0TXIE + UCB0RXIE;               // Enable TX interrupt
    UCB0I2CIE |= UCSTTIE;                     // Enable STT interrupt
}

__attribute__ ((interrupt(USCIAB0TX_VECTOR))) void USCIAB0TX_ISR() {
    if (IFG2 & UCB0TXIFG) {
        i2c::onTransmit(&UCB0TXBUF);
    } else {
        i2c::onReceive(UCB0RXBUF);
    }
}

// USCI_B0 State ISR
__attribute__ ((interrupt(USCIAB0RX_VECTOR))) void USCIAB0RX_ISR() {
    UCB0STAT &= ~UCSTTIFG;                    // Clear start condition int flag
    i2c::onStart();
}

protocol::DeviceState& octoglow::geiger::i2c::hd::getDeviceState() {
    static protocol::DeviceState state;
    state.eyeInverterState = magiceye::getState();
    state.eyeControllerState = magiceye::getControllerState();
    state.eyePwmValue = TA1CCR2;
    state.geigerPwmValue = TA1CCR1;
    state.geigerVoltage;
    state.eyeVoltage;

    return state;
}