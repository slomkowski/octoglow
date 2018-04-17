#pragma once

#include <cstdint>

namespace octoglow {
    namespace geiger {
        namespace inverter {
            void init();

            void tick();

            void setEyeEnabled(bool enabled);

            /**
             * These methods are not part of the interface.
             */
            namespace _private {
                constexpr double REFERENCE_VOLTAGE = 2.5;

                constexpr double GEIGER_VOLTAGE = 390;
                constexpr double GEIGER_DIVIDER_UPPER_RESISTOR = 4 * 470;
                constexpr double GEIGER_DIVIDER_LOWER_RESISTOR = 4.7;
                constexpr double GEIGER_PWM_MIN_DUTY = 0.05;
                constexpr double GEIGER_PWM_MAX_DUTY = 0.26;
                constexpr uint32_t GEIGER_PWM_FREQUENCY = 31000; // 60 kHz
                constexpr uint8_t GEIGER_PWM_STEP = 1;

                constexpr double EYE_VOLTAGE = 250 * 1.1;
                constexpr double EYE_DIVIDER_UPPER_RESISTOR = 540;
                constexpr double EYE_DIVIDER_LOWER_RESISTOR = 4.7;
                constexpr double EYE_PWM_MIN_DUTY = 0.35;
                constexpr double EYE_PWM_MAX_DUTY = 0.7;
                constexpr uint32_t EYE_PWM_FREQUENCY = 60000; // 60 kHz
                constexpr uint8_t EYE_PWM_STEP = 1;


                constexpr uint32_t GEIGER_PWM_PERIOD = F_CPU / GEIGER_PWM_FREQUENCY;
                constexpr uint32_t EYE_PWM_PERIOD = F_CPU / EYE_PWM_FREQUENCY;


                constexpr uint16_t eyeCycles(double part) {
                    return part * EYE_PWM_PERIOD;
                }

                constexpr uint16_t geigerCycles(double part) {
                    return part * GEIGER_PWM_PERIOD;
                }

                constexpr static uint16_t desiredAdcReadout(double upperRes, double lowerRes, double inputVoltage) {
                    const double desiredVoltage = lowerRes / (upperRes + lowerRes) * inputVoltage;
                    return desiredVoltage / REFERENCE_VOLTAGE * 0x3ff;
                }

                const uint16_t GEIGER_DESIRED_ADC_READOUT = desiredAdcReadout(GEIGER_DIVIDER_UPPER_RESISTOR, GEIGER_DIVIDER_LOWER_RESISTOR, GEIGER_VOLTAGE);
//todo dodać korekcje z rejestrów ADC offset itd
                const uint16_t EYE_DESIRED_ADC_READOUT = desiredAdcReadout(EYE_DIVIDER_UPPER_RESISTOR, EYE_DIVIDER_LOWER_RESISTOR, EYE_VOLTAGE);


                void regulateEyeInverter(uint16_t adcReadout, uint16_t *pwmValue);

                void regulateGeigerInverter(uint16_t adcReadout, uint16_t *pwmValue);
            }

            namespace hd {
            }
        }
    }
}
