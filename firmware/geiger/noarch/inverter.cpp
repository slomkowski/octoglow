#include "inverter.hpp"

using namespace octoglow::geiger::inverter::_private;

namespace octoglow::geiger::inverter {
    volatile int16_t desiredEyeAdcValue;

    volatile uint16_t eyeAdcReadout;
    volatile uint16_t geigerAdcReadout;
}

void octoglow::geiger::inverter::_private::regulateEyeInverter(const uint16_t adcReadout, uint16_t *const pwmValue) {

    const int16_t diff = adcReadout - desiredEyeAdcValue;
    
    int16_t newPwmValue = eyeCycles((EYE_PWM_MAX_DUTY + EYE_PWM_MIN_DUTY) / 2) + diff / 3;

    if (newPwmValue > eyeCycles(EYE_PWM_MAX_DUTY)) {
        newPwmValue = eyeCycles(EYE_PWM_MAX_DUTY);
    } else if (newPwmValue < eyeCycles(EYE_PWM_MIN_DUTY)) {
        newPwmValue = eyeCycles(EYE_PWM_MIN_DUTY);
    }
    
    *pwmValue = newPwmValue;
}


void octoglow::geiger::inverter::_private::regulateGeigerInverter(const uint16_t adcReadout, uint16_t *const pwmValue) {

    const int16_t diff = adcReadout - GEIGER_DESIRED_ADC_READOUT;

    int16_t newPwmValue = geigerCycles(GEIGER_PWM_MAX_DUTY - GEIGER_PWM_MIN_DUTY) - diff / 2;

    if (newPwmValue > geigerCycles(GEIGER_PWM_MAX_DUTY)) {
        newPwmValue = geigerCycles(GEIGER_PWM_MAX_DUTY);
    } else if (newPwmValue < geigerCycles(GEIGER_PWM_MIN_DUTY)) {
        newPwmValue = geigerCycles(GEIGER_PWM_MIN_DUTY);
    }

    *pwmValue = newPwmValue;
}
