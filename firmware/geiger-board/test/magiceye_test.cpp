#include "common.hpp"
#include "magiceye.hpp"
#include "protocol.hpp"

#include <gtest/gtest.h>

#include <iostream>

using namespace std;
using namespace octoglow::geiger::magiceye;
using namespace octoglow::geiger::protocol;

void ::octoglow::geiger::magiceye::hd::enablePreheatRelay(bool enabled) {
    cout << "preheat " << enabled << endl;
}

void ::octoglow::geiger::magiceye::hd::enableMainRelay(bool enabled) {
    cout << "main " << enabled << endl;
}

TEST(MagicEye, HeatingProcedure) {
    cout << endl;

    setEnabled(false);
    ASSERT_EQ(EyeInverterState::DISABLED, getState());
    ASSERT_FALSE(eyeInverterEnabled);

    setEnabled(true);
    ASSERT_EQ(EyeInverterState::PREHEATING, getState());
    tick();
    ASSERT_EQ(EyeInverterState::PREHEATING, getState());
    tick();
    ASSERT_EQ(EyeInverterState::PREHEATING, getState());
    ASSERT_FALSE(eyeInverterEnabled);

    for (int i = 0; i < 50 * 8; ++i) {
        tick();
    }

    ASSERT_EQ(EyeInverterState::POSTHEATING, getState());
    ASSERT_TRUE(eyeInverterEnabled);
    tick();
    ASSERT_EQ(EyeInverterState::POSTHEATING, getState());

    for (int i = 0; i < 50 * 5; ++i) {
        tick();
    }

    ASSERT_EQ(EyeInverterState::RUNNING, getState());
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
    ASSERT_TRUE(eyeInverterEnabled);
    ASSERT_EQ(EyeInverterState::POSTHEATING, getState());

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
