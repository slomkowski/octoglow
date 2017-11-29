#include <msp430.h>

#include "magiceye.hpp"


void delay(unsigned int d) {
    for (uint16_t i = 0; i < d; i++) {
        _NOP();
    }
}

using namespace octoglow::geiger;

int main(void) {
    WDTCTL = WDTPW | WDTHOLD;


    P1DIR = 0xFF;
    P1OUT = 0x01;

    P2SEL |= (BIT6 | BIT7); // Set P2.6 and P2.6 SEL for XIN, XOUT
    P2SEL2 &= ~(BIT6 | BIT7); // Set P2.6 and P2.7 SEL2 for XIN, XOUT

    magiceye::init();

    //DCOCTL = CAL_DCO_8MHZ;
    P1DIR |= BIT2; //Set pin 1.2 to the output direction.
    P1SEL |= BIT2; //Select pin 1.2 as our PWM output.
    TA0CCR0 = 11; //Set the period in the Timer A0 Capture/Compare 0 register to 1000 us.
    TA0CCTL1 = OUTMOD_7;
    TA0CCR1 = 8; //The period in microseconds that the power is ON. It's half the time, which translates to a 50% duty cycle.
    TA0CTL = TASSEL_2 + MC_1; //TASSEL_2 selects SMCLK as the clock source, and MC_1 tells it to count up to the value in TA0CCR0.

//    BCSCTL1 = XT2OFF;
//    BCSCTL2 = SELM_3 | SELS;
//    BCSCTL3 = LFXT1S_3;
//
//    _BIC_SR(OSCOFF);
//
//    while (IFG1 & OFIFG) {
//        IFG1 &= ~OFIFG;
//        delay(0x1000);
//    }
//
//    P1SEL |= BIT0;
//    P1SEL2 &= ~(BIT0);
//    P1DIR |= BIT0;

//    for (;;) {
//        P1OUT = ~P1OUT;
//        delay(0x4fff);
//    }

    magiceye::setAdcValue(48);

    while (true) {}
}