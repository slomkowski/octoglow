#include "inverter.hpp"

#include <msp430.h>

#define PWM_BIT_GEIGER BIT2
#define PWM_BIT_EYE BIT4

constexpr uint32_t PWM_FREQUENCY = 60000; // 60 kHz
constexpr uint32_t PWM_PERIOD = F_CPU / PWM_FREQUENCY;

constexpr double REFERENCE_VOLTAGE = 2.5;

constexpr double GEIGER_VOLTAGE = 395;
constexpr double GEIGER_DIVIDER_UPPER_RESISTOR = 720;
constexpr double GEIGER_DIVIDER_LOWER_RESISTOR = 3.9;


constexpr double EYE_VOLTAGE = 250;
constexpr double EYE_DIVIDER_UPPER_RESISTOR = 540;
constexpr double EYE_DIVIDER_LOWER_RESISTOR = 4.7;

constexpr double EYE_PWM_MIN_DUTY = 0.35;
constexpr double EYE_PWM_MAX_DUTY = 0.7;
constexpr uint8_t EYE_PWM_STEP = 1;


constexpr uint16_t EYE_PWM_MAX_DUTY_CYCLES = EYE_PWM_MAX_DUTY * PWM_PERIOD;
constexpr uint16_t EYE_PWM_MIN_DUTY_CYCLES = EYE_PWM_MIN_DUTY * PWM_PERIOD;

constexpr static uint16_t desiredAdcReadout(double upperRes, double lowerRes, double inputVoltage) {
    const double desiredVoltage = lowerRes / (upperRes + lowerRes) * inputVoltage;
    return desiredVoltage / REFERENCE_VOLTAGE * 0x3ff;
}

const uint16_t GEIGER_DESIRED_ADC_READOUT = desiredAdcReadout(GEIGER_DIVIDER_UPPER_RESISTOR, GEIGER_DIVIDER_LOWER_RESISTOR, GEIGER_VOLTAGE);
//todo dodać korekcje z rejestrów ADC offset itd
const uint16_t EYE_DESIRED_ADC_READOUT = desiredAdcReadout(EYE_DIVIDER_UPPER_RESISTOR, EYE_DIVIDER_LOWER_RESISTOR, EYE_VOLTAGE);

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
    P2OUT |= PWM_BIT_EYE;
    P2OUT &= ~PWM_BIT_GEIGER;

    // timer counts to TA0CCR0, is in high state during TA0CCR1
    TA1CCR0 = PWM_PERIOD;

    TA1CCTL1 = OUTMOD_7;
    TA1CCR1 = ((uint16_t) (0.2 * PWM_PERIOD));

    TA1CCTL2 = OUTMOD_7;
    TA1CCR2 = ((uint16_t) (0.45 * PWM_PERIOD));

    TA1CTL = TASSEL_2 | ID_0 | MC_1;

    setEyeEnabled(false);
}

void ::octoglow::geiger::inverter::tick() {

    const uint16_t geigerReadout = readAdcValue(INCH_5);

    const uint16_t eyeReadout = readAdcValue(INCH_4);

    uint16_t eyePwmValue = TA1CCR2;

    if (eyeReadout < EYE_DESIRED_ADC_READOUT) {
        eyePwmValue -= EYE_PWM_STEP;
    } else if (eyeReadout > EYE_DESIRED_ADC_READOUT) {
        eyePwmValue += EYE_PWM_STEP;
    }

    if (eyePwmValue > EYE_PWM_MAX_DUTY_CYCLES) {
        eyePwmValue = EYE_PWM_MAX_DUTY_CYCLES;
    } else if (eyePwmValue < EYE_PWM_MIN_DUTY_CYCLES) {
        eyePwmValue = EYE_PWM_MIN_DUTY_CYCLES;
    }

    TA1CCR2 = eyePwmValue;
}

void ::octoglow::geiger::inverter::setEyeEnabled(bool enabled) {
    if (enabled) {
        P2SEL |= PWM_BIT_EYE;
    } else {
        P2SEL &= ~PWM_BIT_EYE;
        P2OUT |= PWM_BIT_EYE;
    }
}
