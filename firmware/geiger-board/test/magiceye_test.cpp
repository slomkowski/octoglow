#include "common.hpp"
#include "magiceye.hpp"

#include <gtest/gtest.h>

#include <iostream>

using namespace std;
using namespace octoglow::geiger::magiceye;

void ::octoglow::geiger::magiceye::hd::enablePreheatRelay(bool enabled) {
    cout << "preheat " << enabled << endl;
}

void ::octoglow::geiger::magiceye::hd::enableMainRelay(bool enabled) {
    cout << "main " << enabled << endl;
}

TEST(MagicEye, HeatingProcedure) {
    cout << endl;

    setEnabled(false);
    ASSERT_EQ(EyeState::DISABLED, getState());
    ASSERT_FALSE(eyeInverterEnabled);

    setEnabled(true);
    ASSERT_EQ(EyeState::PREHEATING, getState());
    tick();
    ASSERT_EQ(EyeState::PREHEATING, getState());
    tick();
    ASSERT_EQ(EyeState::PREHEATING, getState());
    ASSERT_FALSE(eyeInverterEnabled);

    for (int i = 0; i < 50 * 8; ++i) {
        tick();
    }

    ASSERT_EQ(EyeState::POSTHEATING, getState());
    ASSERT_TRUE(eyeInverterEnabled);
    tick();
    ASSERT_EQ(EyeState::POSTHEATING, getState());

    for (int i = 0; i < 50 * 5; ++i) {
        tick();
    }

    ASSERT_EQ(EyeState::RUNNING, getState());
    for (int i = 0; i < UINT16_MAX * 3; ++i) {
        tick();
    }
    ASSERT_TRUE(eyeInverterEnabled);
    ASSERT_EQ(EyeState::RUNNING, getState());

    setEnabled(false);
    ASSERT_FALSE(eyeInverterEnabled);
    ASSERT_EQ(EyeState::DISABLED, getState());
    tick();
    ASSERT_EQ(EyeState::DISABLED, getState());

    // two seconds - tubes still hot
    for (int i = 0; i < 50 * 2; ++i) {
        tick();
    }
    setEnabled(true);
    ASSERT_TRUE(eyeInverterEnabled);
    ASSERT_EQ(EyeState::POSTHEATING, getState());

    for (int i = 0; i < 50 * 6; ++i) {
        tick();
    }
    ASSERT_TRUE(eyeInverterEnabled);
    ASSERT_EQ(EyeState::RUNNING, getState());
    for (int i = 0; i < UINT16_MAX * 3; ++i) {
        tick();
    }
    ASSERT_TRUE(eyeInverterEnabled);
    ASSERT_EQ(EyeState::RUNNING, getState());

    setEnabled(false);
    for (int i = 0; i < UINT16_MAX * 3; ++i) {
        tick();
    }
    ASSERT_EQ(EyeState::DISABLED, getState());
    ASSERT_FALSE(eyeInverterEnabled);
}
