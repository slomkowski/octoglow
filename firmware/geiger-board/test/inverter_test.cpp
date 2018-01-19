#include "inverter.hpp"

#include <iostream>

bool eyeInverterEnabled;

using namespace std;

void ::octoglow::geiger::inverter::setEyeEnabled(bool enabled) {
    cout << "eye inverter " << enabled << endl;
    eyeInverterEnabled = enabled;

}
