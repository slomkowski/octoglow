#include "inverter.hpp"

using namespace octoglow::geiger::inverter::_private;


void octoglow::geiger::inverter::_private::regulateEyeInverter(const uint16_t adcReadout, uint16_t *const pwmValue) {
    if (adcReadout < EYE_DESIRED_ADC_READOUT) {
        *pwmValue -= EYE_PWM_STEP;
    } else if (adcReadout > EYE_DESIRED_ADC_READOUT) {
        *pwmValue += EYE_PWM_STEP;
    }

    if (*pwmValue > cycles(EYE_PWM_MAX_DUTY)) {
        *pwmValue = cycles(EYE_PWM_MAX_DUTY);
    } else if (*pwmValue < cycles(EYE_PWM_MIN_DUTY)) {
        *pwmValue = cycles(EYE_PWM_MIN_DUTY);
    }
}


void octoglow::geiger::inverter::_private::regulateGeigerInverter(const uint16_t adcReadout, uint16_t *const pwmValue) {

    const int16_t diff = adcReadout - GEIGER_DESIRED_ADC_READOUT;

    *pwmValue = cycles(GEIGER_PWM_MAX_DUTY- GEIGER_PWM_MIN_DUTY) + diff;
//
////    if (adcReadout < GEIGER_DESIRED_ADC_READOUT) {
////        *pwmValue -= GEIGER_PWM_STEP;
////    } else if (adcReadout > GEIGER_DESIRED_ADC_READOUT) {
////        *pwmValue += GEIGER_PWM_STEP;
////    }
//
    if (*pwmValue > cycles(GEIGER_PWM_MAX_DUTY)) {
        *pwmValue = cycles(GEIGER_PWM_MAX_DUTY);
    } else if (*pwmValue < 46) {
        *pwmValue = 46;;
    }

    //*pwmValue = 46;
}
