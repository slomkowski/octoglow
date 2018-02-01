#include "inverter.hpp"

#include <msp430.h>

#define PWM_BIT_GEIGER BIT2
#define PWM_BIT_EYE BIT4

static uint16_t readAdcValue(uint16_t inch) {
    ADC10CTL0 &= (~ENC);
    ADC10CTL1 = inch | SHS_0 | ADC10DIV_7 | ADC10SSEL_2 | CONSEQ_0;

    ADC10CTL0 |= ENC;
    ADC10CTL0 |= ADC10SC;

    while (ADC10CTL1 & ADC10BUSY);

    return ADC10MEM;
}

void ::octoglow::geiger::inverter::init() {
    // init ADC

    // pwm - p1.2
    // eye voltage - p1.4
    // geiger voltage - p1.5

    ADC10AE0 = BIT4 | BIT5;
    ADC10CTL0 &= (~ENC);
    ADC10CTL0 = SREF_1 | ADC10SHT_1 | REF2_5V | REFON | ADC10ON;


    // P2.4 - 12 - pwm oka
    // P2.2 - 10 - pwm geiger


    // init PWM

    // dla oka - 60 khz, 45 % wypełnienia

    P2DIR |= PWM_BIT_EYE | PWM_BIT_GEIGER;
    P2SEL |= PWM_BIT_EYE | PWM_BIT_GEIGER;
    P2OUT |= PWM_BIT_EYE |PWM_BIT_GEIGER;

    // timer counts to TA0CCR0, is in high state during TA0CCR1
    TA1CCR0 = _private::PWM_PERIOD;
    TA1CCTL1 = OUTMOD_7;
    TA1CCTL2 = OUTMOD_7;
    TA1CTL = TASSEL_2 | ID_0 | MC_1;

    setEyeEnabled(false);
}

void ::octoglow::geiger::inverter::tick() {

    const uint16_t eyeReadout = readAdcValue(INCH_4);
    uint16_t eyePwmValue = TA1CCR2;
    _private::regulateEyeInverter(eyeReadout, &eyePwmValue);
    TA1CCR2 = eyePwmValue;

    const uint16_t geigerReadout = readAdcValue(INCH_5);
    uint16_t geigerPwmValue = TA1CCR1;
    _private::regulateGeigerInverter(geigerReadout, &geigerPwmValue);
    TA1CCR1 = geigerPwmValue;
}

void ::octoglow::geiger::inverter::setEyeEnabled(bool enabled) {
    if (enabled) {
        P2SEL |= PWM_BIT_EYE;
    } else {
        P2SEL &= ~PWM_BIT_EYE;
        P2OUT |= PWM_BIT_EYE;
    }
}
