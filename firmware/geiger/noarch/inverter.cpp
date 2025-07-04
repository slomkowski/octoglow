#include "inverter.hpp"
#include "FastPID.hpp"
#include "main.hpp"
#include "protocol.hpp"

using namespace octoglow::geiger::inverter::_private;

constexpr uint16_t eyeAdcVal(double voltage) {
    return desiredAdcReadout(EYE_DIVIDER_UPPER_RESISTOR,
                             EYE_DIVIDER_LOWER_RESISTOR,
                             voltage);
}

constexpr uint16_t desiredAdcValues[] = {
    eyeAdcVal(0),
    eyeAdcVal(110),
    eyeAdcVal(140),
    eyeAdcVal(180),
    eyeAdcVal(210),
    eyeAdcVal(250),
};

static fastpid::FastPID eyePid(
    0.7,
    0.5,
    0.0,
    octoglow::geiger::TICK_TIMER_FREQ,
    eyeCycles(EYE_PWM_MIN_DUTY),
    eyeCycles(EYE_PWM_MAX_DUTY));

namespace octoglow::geiger::inverter {
    volatile int16_t desiredEyeAdcValue;

    volatile int16_t eyeAdcReadout;
    volatile int16_t geigerAdcReadout;

    namespace _private {
        volatile int16_t adcBuffer[ADC_TOTAL_SAMPLES_SIZE];
    }
}

int16_t octoglow::geiger::inverter::_private::readAdcValue(const uint8_t channel) {
    int16_t sum = 0;

    for (uint8_t i = channel; i < ADC_TOTAL_SAMPLES_SIZE; i += 2) {
        sum += adcBuffer[i];
    }

    return sum / ADC_SAMPLES_PER_CHANNEL;
}

// todo dodać odczyt zmiennej error
void octoglow::geiger::inverter::_private::regulateEyeInverter(const int16_t adcReadout, uint16_t *const pwmValue) {
    const int16_t newPwmValue = eyePid.step(desiredEyeAdcValue, adcReadout);
    *pwmValue = newPwmValue;
}

void  octoglow::geiger::inverter::setBrightness(const uint8_t brightness) {
    const auto limitedBrightness = (brightness > protocol::MAX_BRIGHTNESS ? protocol::MAX_BRIGHTNESS : brightness);
    desiredEyeAdcValue = desiredAdcValues[limitedBrightness];
    eyePid.clear();
}

void octoglow::geiger::inverter::_private::regulateGeigerInverter(const int16_t adcReadout, uint16_t *const pwmValue) {
    const int16_t diff = adcReadout - GEIGER_DESIRED_ADC_READOUT;
}
