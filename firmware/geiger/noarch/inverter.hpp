#pragma once

#include <inttypes.h>

namespace octoglow::geiger::inverter {
    void init();

    void setPwmOutputsToSafeState();

    void tick();

    void setEyeEnabled(bool enabled);

    void setBrightness(uint8_t brightness);

    extern volatile int16_t desiredEyeAdcValue;

    extern volatile int16_t eyeAdcReadout;
    extern volatile int16_t geigerAdcReadout;

    /**
             * These methods are not part of the interface.
             */
    namespace _private {
        constexpr double REFERENCE_VOLTAGE = 2.5;

        constexpr double GEIGER_VOLTAGE = 395.0;
        constexpr double GEIGER_DIVIDER_UPPER_RESISTOR = 4 * 470;
        constexpr double GEIGER_DIVIDER_LOWER_RESISTOR = 4.7;
        constexpr double GEIGER_PWM_MIN_DUTY = 0.05;
        constexpr double GEIGER_PWM_MAX_DUTY = 0.55;
        constexpr uint32_t GEIGER_PWM_FREQUENCY = 33000; // 33 kHz // todo zwiększyć?
        static_assert(GEIGER_PWM_MAX_DUTY > GEIGER_PWM_MIN_DUTY, "invalid geiger PWM range");

        constexpr double EYE_DIVIDER_UPPER_RESISTOR = 180.0 * 3;
        constexpr double EYE_DIVIDER_LOWER_RESISTOR = 4.7;
        constexpr double EYE_PWM_MIN_DUTY = 0.02;
        constexpr double EYE_PWM_MAX_DUTY = 0.30;
        // todo pogodzić się z maksymalnym niższym napięciem, żeby uniknąć clików?
        constexpr uint32_t EYE_PWM_FREQUENCY = 60000; // 60 kHz
        static_assert(EYE_PWM_MAX_DUTY > EYE_PWM_MIN_DUTY, "invalid eye PWM range");

        constexpr uint32_t GEIGER_PWM_PERIOD = TIMER_CLOCK_SOURCE_FREQ / GEIGER_PWM_FREQUENCY;
        constexpr uint32_t EYE_PWM_PERIOD = TIMER_CLOCK_SOURCE_FREQ / EYE_PWM_FREQUENCY;

        constexpr int16_t eyeCycles(const double part) {
            return part * EYE_PWM_PERIOD;
        }

        constexpr int16_t geigerCycles(const double part) {
            return part * GEIGER_PWM_PERIOD;
        }

        constexpr int16_t desiredAdcReadout(double upperRes, double lowerRes, double inputVoltage) {
            const double desiredVoltage = lowerRes / (upperRes + lowerRes) * inputVoltage;
            return desiredVoltage / REFERENCE_VOLTAGE * 0x3ff;
        }

        constexpr int16_t EYE_MIDDLE_PWM_DUTY_CYCLES = eyeCycles((EYE_PWM_MAX_DUTY + EYE_PWM_MIN_DUTY) / 2.0);
        constexpr int16_t GEIGER_MIDDLE_PWM_DUTY_CYCLES = geigerCycles(
            (GEIGER_PWM_MAX_DUTY - GEIGER_PWM_MIN_DUTY) / 2.0);

        constexpr int16_t GEIGER_DESIRED_ADC_READOUT = desiredAdcReadout(
            GEIGER_DIVIDER_UPPER_RESISTOR, GEIGER_DIVIDER_LOWER_RESISTOR, GEIGER_VOLTAGE);

        constexpr uint8_t ADC_SAMPLES_PER_CHANNEL = 10;
        constexpr uint8_t ADC_TOTAL_SAMPLES_SIZE = ADC_SAMPLES_PER_CHANNEL * 2;

        int16_t readAdcValue(uint8_t channel);

        void regulateEyeInverter(int16_t adcReadout, uint16_t *pwmValue);

        void regulateGeigerInverter(int16_t adcReadout, uint16_t *pwmValue);

        extern volatile int16_t adcBuffer[ADC_TOTAL_SAMPLES_SIZE];

        constexpr uint8_t EYE_ADC_CHANNEL = 0; // channel 5
        constexpr uint8_t GEIGER_ADC_CHANNEL = 1; // channel 1
    }
}
