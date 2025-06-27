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

void magiceye::setAdcValue(const uint8_t v) {
    cout << "ADC set to " << static_cast<int>(v) << endl;
    currentAdcValue = v;
}

TEST(MagicEye, HeatingProcedure) {
    cout << endl;
    animationMode = EyeDisplayMode::FIXED_VALUE;

    setEnabled(false);
    ASSERT_EQ(EyeInverterState::DISABLED, magiceye::state);
    ASSERT_FALSE(eyeInverterEnabled);

    setEnabled(true);
    ASSERT_EQ(EyeInverterState::HEATING_LIMITED, magiceye::state);
    tick();
    ASSERT_EQ(EyeInverterState::HEATING_LIMITED, magiceye::state);
    tick();
    ASSERT_EQ(EyeInverterState::HEATING_LIMITED, magiceye::state);
    ASSERT_FALSE(eyeInverterEnabled);

    for (int i = 0; i < TICK_TIMER_FREQ * 8; ++i) {
        tick();
    }

    ASSERT_EQ(EyeInverterState::HEATING_FULL, magiceye::state);
    ASSERT_FALSE(eyeInverterEnabled);
    tick();
    ASSERT_EQ(EyeInverterState::HEATING_FULL, magiceye::state);
    ASSERT_FALSE(eyeInverterEnabled);

    for (int i = 0; i < TICK_TIMER_FREQ * 5; ++i) {
        tick();
    }

    ASSERT_EQ(EyeInverterState::RUNNING, magiceye::state);
    ASSERT_TRUE(eyeInverterEnabled);
    for (int i = 0; i < UINT16_MAX * 3; ++i) {
        tick();
    }
    ASSERT_TRUE(eyeInverterEnabled);
    ASSERT_EQ(EyeInverterState::RUNNING, magiceye::state);

    setEnabled(false);
    ASSERT_FALSE(eyeInverterEnabled);
    ASSERT_EQ(EyeInverterState::DISABLED, magiceye::state);
    tick();
    ASSERT_EQ(EyeInverterState::DISABLED, magiceye::state);

    // two seconds - tubes still hot
    for (int i = 0; i < TICK_TIMER_FREQ * 2; ++i) {
        tick();
    }
    setEnabled(true);
    ASSERT_FALSE(eyeInverterEnabled);
    ASSERT_EQ(EyeInverterState::HEATING_FULL, magiceye::state);

    for (int i = 0; i < TICK_TIMER_FREQ * 6; ++i) {
        tick();
    }
    ASSERT_TRUE(eyeInverterEnabled);
    ASSERT_EQ(EyeInverterState::RUNNING, magiceye::state);
    for (int i = 0; i < UINT16_MAX * 3; ++i) {
        tick();
    }
    ASSERT_TRUE(eyeInverterEnabled);
    ASSERT_EQ(EyeInverterState::RUNNING, magiceye::state);

    setEnabled(false);
    for (int i = 0; i < UINT16_MAX * 3; ++i) {
        tick();
    }
    ASSERT_EQ(EyeInverterState::DISABLED, magiceye::state);
    ASSERT_FALSE(eyeInverterEnabled);
}

TEST(MagicEye, Animation) {
    cout << endl;
}
