#include "inverter.hpp"

using namespace octoglow::geiger::inverter::_private;

namespace octoglow::geiger::inverter {
    volatile int16_t desiredEyeAdcValue;

    volatile int16_t eyeAdcReadout;
    volatile int16_t geigerAdcReadout;
}

void octoglow::geiger::inverter::_private::regulateEyeInverter(const int16_t adcReadout, uint16_t *const pwmValue) {
    const int16_t diff = adcReadout - desiredEyeAdcValue;

    constexpr int16_t maxPwmDutyCycles = eyeCycles(EYE_PWM_MAX_DUTY);
    constexpr int16_t minPwmDutyCycles = eyeCycles(EYE_PWM_MIN_DUTY);

    int16_t newPwmValue = EYE_MIDDLE_PWM_DUTY_CYCLES - diff / 3;

    if (newPwmValue > maxPwmDutyCycles) {
        newPwmValue = maxPwmDutyCycles;
    } else if (newPwmValue < minPwmDutyCycles) {
        newPwmValue = minPwmDutyCycles;
    }

    *pwmValue = newPwmValue;
}


void octoglow::geiger::inverter::_private::regulateGeigerInverter(const int16_t adcReadout, uint16_t *const pwmValue) {
    const int16_t diff = adcReadout - GEIGER_DESIRED_ADC_READOUT;

    constexpr int16_t maxPwmDutyCycles = eyeCycles(GEIGER_PWM_MAX_DUTY);
    constexpr int16_t minPwmDutyCycles = eyeCycles(GEIGER_PWM_MIN_DUTY);

    int16_t newPwmValue = GEIGER_MIDDLE_PWM_DUTY_CYCLES - diff / 3;

    if (newPwmValue > maxPwmDutyCycles) {
        newPwmValue = maxPwmDutyCycles;
    } else if (newPwmValue < minPwmDutyCycles) {
        newPwmValue = minPwmDutyCycles;
    }

    *pwmValue = newPwmValue;
}
