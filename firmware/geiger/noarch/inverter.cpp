#include "inverter.hpp"
#include "FastPID.hpp"
#include "main.hpp"
#include "protocol.hpp"

using namespace octoglow::geiger::inverter::_private;

constexpr int16_t eyeAdcVal(const double voltage) {
    return desiredAdcReadout(EYE_DIVIDER_UPPER_RESISTOR,
                             EYE_DIVIDER_LOWER_RESISTOR,
                             voltage);
}

constexpr int16_t desiredAdcValues[] = {
    eyeAdcVal(0),
    eyeAdcVal(110),
    eyeAdcVal(140),
    eyeAdcVal(180),
    eyeAdcVal(210),
    eyeAdcVal(250),
};

constexpr int16_t EYE_MIN_PWM_DUTY_CYCLES = eyeCycles(EYE_PWM_MIN_DUTY);
constexpr int16_t EYE_MAX_PWM_DUTY_CYCLES = eyeCycles(EYE_PWM_MAX_DUTY);
constexpr int16_t GEIGER_MIN_PWM_DUTY_CYCLES = geigerCycles(GEIGER_PWM_MIN_DUTY);
constexpr int16_t GEIGER_MAX_PWM_DUTY_CYCLES = geigerCycles(GEIGER_PWM_MAX_DUTY);


static fastpid::FastPID eyePid(
    0.7,
    0.5,
    0.0,
    octoglow::geiger::TICK_TIMER_FREQ,
    EYE_MIN_PWM_DUTY_CYCLES,
    EYE_MAX_PWM_DUTY_CYCLES);

static fastpid::FastPID geigerPid(
    0.6,
    0.5,
    0.0,
    octoglow::geiger::TICK_TIMER_FREQ,
    GEIGER_MIN_PWM_DUTY_CYCLES,
    GEIGER_MAX_PWM_DUTY_CYCLES);

namespace octoglow::geiger::inverter {
    volatile int16_t desiredEyeAdcValue;

    volatile int16_t eyeAdcReadout;
    volatile int16_t geigerAdcReadout;

    namespace _private {
        volatile int16_t adcBuffer[ADC_TOTAL_SAMPLES_SIZE];
    }
}

void octoglow::geiger::inverter::_private::clearEyePid() {
    eyePid.clear();
}


int16_t octoglow::geiger::inverter::_private::readAdcValue(const uint8_t channel) {
    int16_t sum = 0;

    for (uint8_t i = channel; i < ADC_TOTAL_SAMPLES_SIZE; i += 2) {
        sum += adcBuffer[i];
    }

    return sum / ADC_SAMPLES_PER_CHANNEL;
}

void octoglow::geiger::inverter::_private::regulateEyeInverter(const int16_t adcReadout, uint16_t *const pwmValue) {
    const int16_t newPwmValue = eyePid.step(desiredEyeAdcValue, adcReadout);
    *pwmValue = newPwmValue;
}

void octoglow::geiger::inverter::setBrightness(const uint8_t brightness) {
    const auto limitedBrightness = (brightness > protocol::MAX_BRIGHTNESS ? protocol::MAX_BRIGHTNESS : brightness);
    desiredEyeAdcValue = desiredAdcValues[limitedBrightness];
    eyePid.clear();
}

void octoglow::geiger::inverter::_private::regulateGeigerInverter(const int16_t adcReadout, uint16_t *const pwmValue) {
    const int16_t newPwmValue = geigerPid.step(GEIGER_DESIRED_ADC_READOUT, adcReadout);
    *pwmValue = newPwmValue;
}
