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
    cout << endl;

    cout << "Min PWM value: " << _private::eyeCycles(_private::EYE_PWM_MIN_DUTY) << endl;
    cout << "Max PWM value: " << _private::eyeCycles(_private::EYE_PWM_MAX_DUTY) << endl;

    constexpr uint16_t adcValue = 134;
    uint16_t pwmValue = 100;
    _private::regulateEyeInverter(adcValue, &pwmValue);

    cout << "PWM value: " << pwmValue << endl;
}

TEST(Inverter, GeigerRegulation) {
    cout << endl;

    cout << "Min PWM value: " << _private::geigerCycles(_private::GEIGER_PWM_MIN_DUTY) << endl;
    cout << "Max PWM value: " << _private::geigerCycles(_private::GEIGER_PWM_MAX_DUTY) << endl;

    constexpr uint16_t adcValue = 134;
    uint16_t pwmValue = 100;
    _private::regulateGeigerInverter(adcValue, &pwmValue);

    cout << "PWM value: " << pwmValue << endl;
}
