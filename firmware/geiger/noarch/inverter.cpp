#include "inverter.hpp"

using namespace octoglow::geiger::inverter::_private;


void octoglow::geiger::inverter::_private::regulateEyeInverter(const uint16_t adcReadout, uint16_t *const pwmValue) {

    const int16_t diff = adcReadout - EYE_DESIRED_ADC_READOUT;

    *pwmValue = eyeCycles((EYE_PWM_MAX_DUTY + EYE_PWM_MIN_DUTY) / 2) + diff / 30;

    if (*pwmValue > eyeCycles(EYE_PWM_MAX_DUTY)) {
        *pwmValue = eyeCycles(EYE_PWM_MAX_DUTY);
    } else if (*pwmValue < eyeCycles(EYE_PWM_MIN_DUTY)) {
        *pwmValue = eyeCycles(EYE_PWM_MIN_DUTY);
    }
}


void octoglow::geiger::inverter::_private::regulateGeigerInverter(const uint16_t adcReadout, uint16_t *const pwmValue) {

    const int16_t diff = adcReadout - GEIGER_DESIRED_ADC_READOUT;

    *pwmValue = geigerCycles(GEIGER_PWM_MAX_DUTY - GEIGER_PWM_MIN_DUTY) - diff / 2;

    if (*pwmValue > geigerCycles(GEIGER_PWM_MAX_DUTY)) {
        *pwmValue = geigerCycles(GEIGER_PWM_MAX_DUTY);
    } else if (*pwmValue < geigerCycles(GEIGER_PWM_MIN_DUTY)) {
        *pwmValue = geigerCycles(GEIGER_PWM_MIN_DUTY);
    }
}
