#pragma once

#include <inttypes.h>

namespace octoglow::geiger::inverter {
    void init();

    void setPwmOutputsToSafeState();

    void tick();

    void setEyeEnabled(bool enabled);

    extern volatile int16_t desiredEyeAdcValue;

    extern volatile uint16_t eyeAdcReadout;
    extern volatile uint16_t geigerAdcReadout;

    /**
             * These methods are not part of the interface.
             */
    namespace _private {
        constexpr double REFERENCE_VOLTAGE = 2.5;

        constexpr double GEIGER_VOLTAGE = 400;
        constexpr double GEIGER_DIVIDER_UPPER_RESISTOR = 4 * 470;
        constexpr double GEIGER_DIVIDER_LOWER_RESISTOR = 4.7;
        constexpr double GEIGER_PWM_MIN_DUTY = 0.05;
        constexpr double GEIGER_PWM_MAX_DUTY = 0.31;
        constexpr uint32_t GEIGER_PWM_FREQUENCY = 33000;

        constexpr double EYE_DIVIDER_UPPER_RESISTOR = 180.0 * 3;
        constexpr double EYE_DIVIDER_LOWER_RESISTOR = 4.7;
        constexpr double EYE_PWM_MIN_DUTY = 0.40;
        constexpr double EYE_PWM_MAX_DUTY = 0.98;
        constexpr uint32_t EYE_PWM_FREQUENCY = 60000; // 60 kHz

        constexpr uint32_t GEIGER_PWM_PERIOD = F_CPU / GEIGER_PWM_FREQUENCY;
        constexpr uint32_t EYE_PWM_PERIOD = F_CPU / EYE_PWM_FREQUENCY;


        constexpr int16_t eyeCycles(const double part) {
            return part * EYE_PWM_PERIOD;
        }

        constexpr int16_t geigerCycles(const double part) {
            return part * GEIGER_PWM_PERIOD;
        }

        constexpr uint16_t desiredAdcReadout(double upperRes, double lowerRes, double inputVoltage) {
            const double desiredVoltage = lowerRes / (upperRes + lowerRes) * inputVoltage;
            return desiredVoltage / REFERENCE_VOLTAGE * 0x3ff;
        }

        constexpr uint16_t GEIGER_DESIRED_ADC_READOUT = desiredAdcReadout(GEIGER_DIVIDER_UPPER_RESISTOR, GEIGER_DIVIDER_LOWER_RESISTOR, GEIGER_VOLTAGE);

        void regulateEyeInverter(uint16_t adcReadout, uint16_t *pwmValue);

        void regulateGeigerInverter(uint16_t adcReadout, uint16_t *pwmValue);
    }

    constexpr uint16_t eyeAdcVal(double voltage) {
        return _private::desiredAdcReadout(_private::EYE_DIVIDER_UPPER_RESISTOR, _private::EYE_DIVIDER_LOWER_RESISTOR, voltage);
    }
}
