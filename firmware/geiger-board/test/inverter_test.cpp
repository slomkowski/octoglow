#include "inverter.hpp"

#include <gtest/gtest.h>

#include <iostream>

bool eyeInverterEnabled;

using namespace std;
using namespace octoglow::geiger::inverter;


void ::octoglow::geiger::inverter::setEyeEnabled(bool enabled) {
    cout << "eye inverter " << enabled << endl;
    eyeInverterEnabled = enabled;

}

TEST(Inverter, AdcValues) {
    cout << "\nADC Geiger desired output: " << _private::GEIGER_DESIRED_ADC_READOUT << endl;
    cout << "ADC eye desired output: " << _private::EYE_DESIRED_ADC_READOUT << endl;
}

TEST(Inverter, EyeRegulation) {
    cout << endl;

    uint16_t adcValue = 134;
    uint16_t pwmValue = 100;
    _private::regulateEyeInverter(134, &pwmValue);

}
