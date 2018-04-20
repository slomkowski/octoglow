#include "common.hpp"
#include "magiceye.hpp"
#include "protocol.hpp"

#include <gtest/gtest.h>

#include <iostream>

using namespace std;
using namespace octoglow::geiger::magiceye;
using namespace octoglow::geiger::protocol;

uint8_t currentAdcValue;

void octoglow::geiger::magiceye::hd::enableHeater1(bool enabled) {
    cout << "heater1 " << enabled << endl;
}

void octoglow::geiger::magiceye::hd::enableHeater2(bool enabled) {
    cout << "heater2 " << enabled << endl;
}

void octoglow::geiger::magiceye::setAdcValue(uint8_t v) {
    cout << "ADC set to " << (int) v << endl;
    currentAdcValue = v;
}

TEST(MagicEye, HeatingProcedure) {
    cout << endl;
    setControllerState(EyeControllerState::FIXED_VALUE);

    setEnabled(false);
    ASSERT_EQ(EyeInverterState::DISABLED, getState());
    ASSERT_FALSE(eyeInverterEnabled);

    setEnabled(true);
    ASSERT_EQ(EyeInverterState::HEATING_LIMITED, getState());
    tick();
    ASSERT_EQ(EyeInverterState::HEATING_LIMITED, getState());
    tick();
    ASSERT_EQ(EyeInverterState::HEATING_LIMITED, getState());
    ASSERT_FALSE(eyeInverterEnabled);

    for (int i = 0; i < 50 * 8; ++i) {
        tick();
    }

    ASSERT_EQ(EyeInverterState::HEATING_FULL, getState());
    ASSERT_FALSE(eyeInverterEnabled);
    tick();
    ASSERT_EQ(EyeInverterState::HEATING_FULL, getState());
    ASSERT_FALSE(eyeInverterEnabled);

    for (int i = 0; i < 50 * 5; ++i) {
        tick();
    }

    ASSERT_EQ(EyeInverterState::RUNNING, getState());
    ASSERT_TRUE(eyeInverterEnabled);
    for (int i = 0; i < UINT16_MAX * 3; ++i) {
        tick();
    }
    ASSERT_TRUE(eyeInverterEnabled);
    ASSERT_EQ(EyeInverterState::RUNNING, getState());

    setEnabled(false);
    ASSERT_FALSE(eyeInverterEnabled);
    ASSERT_EQ(EyeInverterState::DISABLED, getState());
    tick();
    ASSERT_EQ(EyeInverterState::DISABLED, getState());

    // two seconds - tubes still hot
    for (int i = 0; i < 50 * 2; ++i) {
        tick();
    }
    setEnabled(true);
    ASSERT_FALSE(eyeInverterEnabled);
    ASSERT_EQ(EyeInverterState::HEATING_FULL, getState());

    for (int i = 0; i < 50 * 6; ++i) {
        tick();
    }
    ASSERT_TRUE(eyeInverterEnabled);
    ASSERT_EQ(EyeInverterState::RUNNING, getState());
    for (int i = 0; i < UINT16_MAX * 3; ++i) {
        tick();
    }
    ASSERT_TRUE(eyeInverterEnabled);
    ASSERT_EQ(EyeInverterState::RUNNING, getState());

    setEnabled(false);
    for (int i = 0; i < UINT16_MAX * 3; ++i) {
        tick();
    }
    ASSERT_EQ(EyeInverterState::DISABLED, getState());
    ASSERT_FALSE(eyeInverterEnabled);
}

TEST(MagicEye, Animation) {
    cout << endl;


}
