#include "common.hpp"
#include "magiceye.hpp"
#include "protocol.hpp"
#include "main.hpp"

#include <gtest/gtest.h>

#include <iostream>

using namespace std;
using namespace octoglow::geiger;
using namespace octoglow::geiger::magiceye;
using namespace octoglow::geiger::protocol;

uint8_t currentAdcValue;

void hd::enableHeater1(const bool enabled) {
    cout << "heater1 " << enabled << endl;
}

void hd::enableHeater2(const bool enabled) {
    cout << "heater2 " << enabled << endl;
}

void magiceye::setDacOutputValue(const uint8_t v) {
    cout << "ADC set to " << static_cast<int>(v) << endl;
    currentAdcValue = v;
}

TEST(MagicEye, HeatingProcedure) {
    cout << endl;
    animationMode = EyeDisplayMode::FIXED_VALUE;

    setEnabled(false);
    assertEq(EyeInverterState::DISABLED, state);
    ASSERT_FALSE(eyeInverterEnabled);

    setEnabled(true);
    assertEq(EyeInverterState::HEATING_LIMITED, state);
    tick();
    assertEq(EyeInverterState::HEATING_LIMITED, state);
    tick();
    assertEq(EyeInverterState::HEATING_LIMITED, state);
    ASSERT_FALSE(eyeInverterEnabled);

    for (int i = 0; i < TICK_TIMER_FREQ * 8; ++i) {
        tick();
    }

    assertEq(EyeInverterState::HEATING_FULL, state);
    ASSERT_FALSE(eyeInverterEnabled);
    tick();
    assertEq(EyeInverterState::HEATING_FULL, state);
    ASSERT_FALSE(eyeInverterEnabled);

    for (int i = 0; i < TICK_TIMER_FREQ * 5; ++i) {
        tick();
    }

    assertEq(EyeInverterState::RUNNING, state);
    ASSERT_TRUE(eyeInverterEnabled);
    for (int i = 0; i < UINT16_MAX * 3; ++i) {
        tick();
    }
    ASSERT_TRUE(eyeInverterEnabled);
    assertEq(EyeInverterState::RUNNING, state);

    setEnabled(false);
    ASSERT_FALSE(eyeInverterEnabled);
    assertEq(EyeInverterState::DISABLED, state);
    tick();
    assertEq(EyeInverterState::DISABLED, state);

    // two seconds - tubes still hot
    for (int i = 0; i < TICK_TIMER_FREQ * 2; ++i) {
        tick();
    }
    setEnabled(true);
    ASSERT_FALSE(eyeInverterEnabled);
    assertEq(EyeInverterState::HEATING_FULL, state);

    for (int i = 0; i < TICK_TIMER_FREQ * 6; ++i) {
        tick();
    }
    ASSERT_TRUE(eyeInverterEnabled);
    assertEq(EyeInverterState::RUNNING, state);
    for (int i = 0; i < UINT16_MAX * 3; ++i) {
        tick();
    }
    ASSERT_TRUE(eyeInverterEnabled);
    assertEq(EyeInverterState::RUNNING, state);

    setEnabled(false);
    for (int i = 0; i < UINT16_MAX * 3; ++i) {
        tick();
    }
    assertEq(EyeInverterState::DISABLED, state);
    ASSERT_FALSE(eyeInverterEnabled);
}

TEST(MagicEye, Animation) {
    cout << endl;
}
