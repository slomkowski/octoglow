#include "i2c-slave.hpp"
#include "protocol.hpp"
#include "common.hpp"
#include "magiceye.hpp"
#include "geiger-counter.hpp"

#include <gtest/gtest.h>
#include <iostream>

using namespace std;
using namespace octoglow::geiger;
using namespace octoglow::geiger::i2c;

static protocol::DeviceState deviceState;

void i2c::setClockToHigh() {
    cout << "system clock set to high\n";
}

void i2c::setClockToLow() {
    cout << "system clock set to low\n";
}

protocol::DeviceState &hd::getDeviceState() {
    cout << "get device state\n";
    deviceState.geigerVoltage = 0x5678;
    deviceState.geigerPwmValue = 25;

    deviceState.eyeState = protocol::EyeInverterState::DISABLED;
    deviceState.eyeAnimationMode = protocol::EyeDisplayMode::FIXED_VALUE;
    deviceState.eyeVoltage = 0x1234;
    deviceState.eyePwmValue = 7;

    return deviceState;
}

static void assertReadIs(const uint8_t expected) {
    uint8_t readValue;
    onTransmit(&readValue);
    ASSERT_EQ(expected, readValue);
}

TEST(I2C, ReadCommands) {

    // get device state
    onStart();
    onReceive(0x1);

    onStart();
    assertReadIs(0x78);
    assertReadIs(0x56);
    assertReadIs(25);
    assertReadIs(0);
    assertReadIs(1);
    assertReadIs(0x34);
    assertReadIs(0x12);
    assertReadIs(7);

    // get geiger state

    geiger_counter::getState().hasNewCycleStarted = true;
    geiger_counter::getState().hasCycleEverCompleted = false;
    geiger_counter::getState().numOfCountsPreviousCycle = 1231;
    geiger_counter::getState().currentCycleProgress = 289;
    geiger_counter::getState().cycleLength = 300;
    geiger_counter::hd::numOfCountsCurrentCycle = 52;

    onStart();
    onReceive(0x2);
    onStart();
    assertReadIs(1);
    assertReadIs(52);
    assertReadIs(0);
    assertReadIs(0xcf);
    assertReadIs(0x04);
    assertReadIs(0); // hardware drives the number of ticks
    assertReadIs(0);
    assertReadIs(0x2c);
    assertReadIs(0x1);


    // clean geiger state
    onStart();
    onReceive(0x4);

    // read geiger state again
    onStart();
    onReceive(0x2);
    onStart();
    assertReadIs(1);
    assertReadIs(0);
    assertReadIs(0);
    assertReadIs(0);
    assertReadIs(0);
    assertReadIs(0);
    assertReadIs(0);
    assertReadIs(0x2c);
    assertReadIs(0x1);

    onStart();
    onReceive(0x2);
    onStart();
    assertReadIs(0); // calling getState() causes the new cycle-bit to be reset
    assertReadIs(0);
    assertReadIs(0);
    assertReadIs(0);
    assertReadIs(0);
    assertReadIs(0);
    assertReadIs(0);
    assertReadIs(0x2c);
    assertReadIs(0x1);
}

TEST(I2C, WriteCommands) {

    // set eye configuration
    onStart();
    onReceive(0x5);
    onReceive(1);
    onReceive(0);
    ASSERT_FALSE(eyeInverterEnabled);
    ASSERT_EQ(protocol::EyeInverterState::HEATING_LIMITED, magiceye::state);

    for (int i = 0; i < 5000 * 2; ++i) {
        magiceye::tick();
    }
    ASSERT_EQ(protocol::EyeInverterState::RUNNING, magiceye::state);
    ASSERT_EQ(protocol::EyeDisplayMode::ANIMATION, magiceye::animationMode);
    ASSERT_TRUE(eyeInverterEnabled);

    onStart();
    onReceive(0x5);
    onReceive(0);
    onReceive(1);
    ASSERT_FALSE(eyeInverterEnabled);
    ASSERT_EQ(protocol::EyeDisplayMode::FIXED_VALUE, magiceye::animationMode);


    onStart();
    onReceive(0x6);
    onReceive(123);
    ASSERT_EQ(protocol::EyeDisplayMode::FIXED_VALUE, magiceye::animationMode);
    ASSERT_EQ(123, currentAdcValue);

    onStart();
    onReceive(0x6);
    onReceive(12);
    ASSERT_EQ(protocol::EyeDisplayMode::FIXED_VALUE, magiceye::animationMode);
    ASSERT_EQ(12, currentAdcValue);


    onStart();
    onReceive(0x3);
    onReceive(0x12);
    onReceive(0x34);
    ASSERT_EQ(0x3412, geiger_counter::getState().cycleLength);
    ASSERT_TRUE(geiger_counter::getState().hasNewCycleStarted);

    onStart();
    onReceive(0x6);
    onReceive(4);
    onReceive(0);
}
