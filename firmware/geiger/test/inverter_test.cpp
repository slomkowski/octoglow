#include "inverter.hpp"

#include <gtest/gtest.h>

#include <iostream>

bool eyeInverterEnabled;

using namespace std;
using namespace octoglow::geiger::inverter;


void octoglow::geiger::inverter::setEyeEnabled(const bool enabled) {
    cout << "eye inverter " << enabled << endl;
    eyeInverterEnabled = enabled;
}

TEST(Inverter, AdcValues) {
    cout << "\nADC Geiger desired output: " << _private::GEIGER_DESIRED_ADC_READOUT << endl;
    cout << "ADC eye desired output: " << desiredEyeAdcValue << endl;
}

TEST(Inverter, EyeRegulation) {
    using namespace _private;

    cout << "Eye regulation" << endl;

    setBrightness(3);

    cout << "Min PWM value: " << eyeCycles(EYE_PWM_MIN_DUTY) << endl;
    cout << "Max PWM value: " << eyeCycles(EYE_PWM_MAX_DUTY) << endl;

    for (int i = 0; i < 200; ++i) {
        const uint16_t adcValue = eyeAdcVal(150.0 + 0.3 * i);

        const uint16_t pwmValue = regulateEyeInverter(adcValue);
        cout << "PWM value: " << pwmValue << endl;
    }
}

TEST(Inverter, GeigerRegulation) {
    using namespace _private;
    cout << "Geiger regulation" << endl;

    cout << "Min PWM value: " << geigerCycles(GEIGER_PWM_MIN_DUTY) << endl;
    cout << "Max PWM value: " << geigerCycles(GEIGER_PWM_MAX_DUTY) << endl;

    for (int i = 0; i < 200; ++i) {
        constexpr uint16_t adcValue = desiredAdcReadout(
            GEIGER_DIVIDER_UPPER_RESISTOR, GEIGER_DIVIDER_LOWER_RESISTOR, 390.0);

        const uint16_t pwmValue = regulateGeigerInverter(adcValue);
        cout << "PWM value: " << pwmValue << endl;
    }
}
