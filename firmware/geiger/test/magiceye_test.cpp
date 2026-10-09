#include "common.hpp"
#include "magiceye.hpp"
#include "protocol.hpp"
#include "main.hpp"
#include "animation.hpp"
#include "geiger-counter.hpp"

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
    // the state left by the other tests decays
    for (int i = 0; i < 3000; ++i) {
        _animate(false);
    }

    // the base value 70 oscillates by +-30 without counts
    for (int i = 0; i < 1000; ++i) {
        const int value = _animate(false);
        ASSERT_GE(value, 70 - 30) << "cycle " << i;
        ASSERT_LE(value, 70 + 30) << "cycle " << i;
    }

    // a count raises the eye to the maximum in 7 cycles
    int value = _animate(true);
    for (int i = 0; i < 7; ++i) {
        value = _animate(false);
    }
    ASSERT_EQ(0xff, value);

    // stays there and goes back to the base value
    for (int i = 0; i < 30; ++i) {
        ASSERT_EQ(0xff, _animate(false));
    }
    ASSERT_EQ(0xff, _animate(false)) << "the step back is computed";
    int previous = 0xff;
    for (int i = 0; i < 50; ++i) {
        value = _animate(false);
        ASSERT_LT(value, previous) << "cycle " << i;
        previous = value;
    }
    ASSERT_NEAR(70, value, 1);
    ASSERT_NEAR(70, _animate(false), 1) << "doesn't go below the base value when switching back to the oscillation";
    ASSERT_NEAR(70, _animate(false), 1);

    // counts in a row raise the base, it never overflows the 8-bit DAC value
    for (int i = 0; i < 20; ++i) {
        _animate(true);
        for (int j = 0; j < 20; ++j) {
            value = _animate(false);
            ASSERT_GE(value, 0);
            ASSERT_LE(value, 0xff);
        }
    }

    // and slowly decays back to the oscillation around 70
    for (int i = 0; i < 5000; ++i) {
        value = _animate(false);
    }
    for (int i = 0; i < 300; ++i) {
        value = _animate(false);
        ASSERT_GE(value, 70 - 30);
        ASSERT_LE(value, 70 + 30);
    }
}

static void configureEye(const bool enabled, const EyeDisplayMode mode) {
    const EyeConfiguration configuration{enabled, mode};
    configure(configuration);
}

TEST(MagicEye, AnimationModeFollowsCounts) {
    configureEye(false, EyeDisplayMode::ANIMATION);
    geiger_counter::resetCounters();
    tick();

    currentAdcValue = 0;
    geiger_counter::hd::numOfCountsCurrentCycle += 1;
    for (int i = 0; i < 8; ++i) {
        tick();
    }
    ASSERT_EQ(0xff, currentAdcValue);

    // the DAC isn't touched in the fixed value mode
    configureEye(false, EyeDisplayMode::FIXED_VALUE);
    currentAdcValue = 42;
    geiger_counter::hd::numOfCountsCurrentCycle += 1;
    for (int i = 0; i < 100; ++i) {
        tick();
    }
    ASSERT_EQ(42, currentAdcValue);
}
