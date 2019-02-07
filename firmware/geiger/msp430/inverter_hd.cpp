#include "inverter.hpp"
#include "main.hpp"

#include <msp430.h>

#define PWM_BIT_EYE BIT1


static uint16_t readAdcValue(uint16_t inch) {
    ADC10CTL0 &= (~ENC);
    ADC10CTL1 = inch | SHS_0 | ADC10DIV_7 | ADC10SSEL_3 | CONSEQ_0;

    ADC10CTL0 |= ENC;
    ADC10CTL0 |= ADC10SC;

    while (ADC10CTL1 & ADC10BUSY);

    return ADC10MEM;
}

static volatile uint16_t ta0cycles = 0;

__attribute__ ((interrupt(TIMER0_A0_VECTOR))) void TIMER0_A0_ISR() {

    ++ta0cycles;

    if (ta0cycles == octoglow::geiger::inverter::_private::GEIGER_PWM_FREQUENCY / octoglow::geiger::TICK_TIMER_FREQ) {
        octoglow::geiger::timerTicked = true;
        ta0cycles = 0;
    }
}

void ::octoglow::geiger::inverter::init() {

    ADC10AE0 = BIT1 | BIT5;
    ADC10CTL0 &= (~ENC);
    ADC10CTL0 = SREF_1 | ADC10SHT_3 | REF2_5V | REFON | ADC10ON;


    P1DIR |= BIT2;
    P1SEL |= BIT2;
    P2OUT &= ~BIT2;

    TA0CCR0 = _private::GEIGER_PWM_PERIOD;
    TA0CCTL0 = CCIE; // this interrupt is used for system tick
    TA0CCTL1 = OUTMOD_7;
    TA0CTL = TASSEL_2 | ID_0 | MC_1;
    TA0CCR1 = 10; // initial value


    P2DIR |= PWM_BIT_EYE;
    P2SEL |= PWM_BIT_EYE;
    P2OUT |= PWM_BIT_EYE;

    TA1CCR0 = _private::EYE_PWM_PERIOD;
    TA1CCTL1 = OUTMOD_7;
    TA1CTL = TASSEL_2 | ID_0 | MC_1;

    setEyeEnabled(false);
}

void ::octoglow::geiger::inverter::tick() {

    const uint16_t eyeReadout = readAdcValue(INCH_5);
    uint16_t eyePwmValue = TA1CCR2;
    _private::regulateEyeInverter(eyeReadout, &eyePwmValue);
    TA1CCR1 = eyePwmValue;

    const uint16_t geigerReadout = readAdcValue(INCH_1);
    uint16_t geigerPwmValue = TA1CCR1;
    _private::regulateGeigerInverter(geigerReadout, &geigerPwmValue);
    TA0CCR1 = geigerPwmValue;
}

void ::octoglow::geiger::inverter::setEyeEnabled(bool enabled) {
    if (enabled) {
        P2SEL |= PWM_BIT_EYE;
    } else {
        P2SEL &= ~PWM_BIT_EYE;
        P2OUT |= PWM_BIT_EYE;
    }
}
