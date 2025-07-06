#include "inverter.hpp"
#include "main.hpp"

#include <msp430.h>

#define PWM_BIT_EYE BIT1


static volatile uint16_t ta0cycles = 0;
__interrupt_vec(TIMER0_A0_VECTOR) void TIMER0_A0_ISR() {
    ++ta0cycles;

    if (ta0cycles == octoglow::geiger::inverter::_private::GEIGER_PWM_FREQUENCY / octoglow::geiger::TICK_TIMER_FREQ) {
        octoglow::geiger::timerTicked = true;
        ta0cycles = 0;
    }
}

void octoglow::geiger::inverter::setPwmOutputsToSafeState() {
    // set eye to level high
    P2OUT |= PWM_BIT_EYE;
    P2SEL &= ~PWM_BIT_EYE;
    P2SEL2 &= ~PWM_BIT_EYE;
    P2DIR |= PWM_BIT_EYE;

    // set geiger to level low
    P1OUT &= ~BIT2;
    P1DIR |= BIT2;
    P1SEL &= ~BIT2;
    P1SEL2 &= ~BIT2;
}


__interrupt_vec(ADC10_VECTOR) void ADC10_ISR() {
    static volatile uint8_t bufferIndex = 0;

    ADC10CTL0 &= (~ENC);

    octoglow::geiger::inverter::_private::adcBuffer[bufferIndex] = ADC10MEM;
    bufferIndex = (bufferIndex + 1) % octoglow::geiger::inverter::_private::ADC_TOTAL_SAMPLES_SIZE;

    uint16_t inch;
    if (bufferIndex % 2) {
        inch = INCH_1; // geiger
    } else {
        inch = INCH_5; // eye
    }

    ADC10CTL1 = inch | SHS_0 | ADC10DIV_7 | ADC10SSEL_0 | CONSEQ_0;
    ADC10CTL0 |= ENC | ADC10SC;
}

void ::octoglow::geiger::inverter::init() {
    using namespace _private;

    ADC10CTL0 &= (~ENC);
    ADC10AE0 = BIT1 | BIT5;
    ADC10CTL0 = SREF_1 | ADC10SHT_3 | REF2_5V | REFON | ADC10ON | ADC10IE; // 2.5 V ref, sample-and-hold 64 cycles
    ADC10CTL1 = INCH_5 | SHS_0 | ADC10DIV_7 | ADC10SSEL_0 | CONSEQ_0;
    ADC10CTL0 |= ENC | ADC10SC;

    TA0CCR0 = GEIGER_PWM_PERIOD;
    TA0CCR1 = GEIGER_MIDDLE_PWM_DUTY_CYCLES;
    TA0CCTL0 = CCIE; // this interrupt is used for system tick
    TA0CCTL1 = OUTMOD_7;
    TA0CTL = TASSEL_2 | ID_0 | MC_1; // SMCLK clock source, /1 divider, Up mode.

    TA1CCR0 = EYE_PWM_PERIOD;
    TA1CCR1 = EYE_MIDDLE_PWM_DUTY_CYCLES;
    TA1CCTL1 = OUTMOD_3;
    TA1CTL = TASSEL_2 | ID_0 | MC_1; // SMCLK clock source, /1 divider, Up mode.

    // eye PWM output
    P2DIR |= PWM_BIT_EYE;
    P2SEL |= PWM_BIT_EYE;
    P2OUT |= PWM_BIT_EYE;

    // geiger PWM output
    P1DIR |= BIT2;
    P1SEL |= BIT2;

    setEyeEnabled(false);
}

void octoglow::geiger::inverter::tick() {
    using namespace _private;
    eyeAdcReadout = readAdcValue(EYE_ADC_CHANNEL);
    TA1CCR1 = regulateEyeInverter(eyeAdcReadout);

    geigerAdcReadout = readAdcValue(GEIGER_ADC_CHANNEL);
    TA0CCR1 = regulateGeigerInverter(geigerAdcReadout);
}

void octoglow::geiger::inverter::setEyeEnabled(const bool enabled) {
    if (enabled) {
        P2SEL |= PWM_BIT_EYE;
        _private::clearEyePid();
    } else {
        P2SEL &= ~PWM_BIT_EYE;
        P2OUT |= PWM_BIT_EYE;
    }
}
