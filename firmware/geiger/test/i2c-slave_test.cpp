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

static volatile protocol::DeviceState deviceState;

void i2c::setClockToHigh() {
    cout << "system clock set to high\n";
}

void i2c::setClockToLow() {
    cout << "system clock set to low\n";
}

volatile protocol::DeviceState &hd::getDeviceState() {
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
    cout << "read " << static_cast<int>(readValue) << endl;
    ASSERT_EQ(expected, readValue);
}

TEST(I2C, ReadCommands) {
    // get device state
    onStart();
    onReceive(7);
    onReceive(0x1);
    onStop();
    processDataIfAvailable();

    cout << "get device state" << endl;
    onStart();
    assertReadIs(242);
    assertReadIs(1);
    assertReadIs(0x78);
    assertReadIs(0x56);
    assertReadIs(25);
    assertReadIs(0);
    assertReadIs(1);
    assertReadIs(0x34);
    assertReadIs(0x12);
    assertReadIs(7);

    // get geiger state

    geiger_counter::updateGeigerState();
    geiger_counter::geigerState.hasNewCycleStarted = true;
    geiger_counter::geigerState.hasCycleEverCompleted = false;
    geiger_counter::geigerState.numOfCountsPreviousCycle = 1231;
    geiger_counter::geigerState.currentCycleProgress = 289;
    geiger_counter::geigerState.cycleLength = 300;
    geiger_counter::hd::numOfCountsCurrentCycle = 52;

    onStart();
    onReceive(14);
    onReceive(0x2);
    onStop();
    processDataIfAvailable();

    cout << "get device state" << endl;
    onStart();
    assertReadIs(108);
    assertReadIs(2);
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
    onReceive(28);
    onReceive(4);
    onStop();
    processDataIfAvailable();

    onStart();
    assertReadIs(28);
    assertReadIs(4);

    // read geiger state again
    onStart();
    onReceive(14);
    onReceive(0x2);
    onStop();
    processDataIfAvailable();

    cout << "get device state" << endl;
    onStart();
    assertReadIs(252);
    assertReadIs(2);
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
    onReceive(14);
    onReceive(0x2);
    onStop();
    processDataIfAvailable();

    cout << "get device state" << endl;
    onStart();
    assertReadIs(133);
    assertReadIs(2);
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
    onReceive(213);
    onReceive(0x5);
    onReceive(1);
    onReceive(0);
    onStop();
    processDataIfAvailable();

    ASSERT_FALSE(eyeInverterEnabled);
    assertEq(protocol::EyeInverterState::HEATING_LIMITED, magiceye::state);

    for (int i = 0; i < 5000 * 2; ++i) {
        magiceye::tick();
    }
    assertEq(protocol::EyeInverterState::RUNNING, magiceye::state);
    assertEq(protocol::EyeDisplayMode::ANIMATION, magiceye::animationMode);
    ASSERT_TRUE(eyeInverterEnabled);
    onStart();
    onReceive(27);
    onReceive(5);
    onStop();
    processDataIfAvailable();

    onStart();
    onReceive(199);
    onReceive(0x5);
    onReceive(0);
    onReceive(1);
    onStop();
    processDataIfAvailable();

    ASSERT_FALSE(eyeInverterEnabled);
    assertEq(protocol::EyeDisplayMode::FIXED_VALUE, magiceye::animationMode);
    onStart();
    onReceive(27);
    onReceive(0x5);
    onStop();
    processDataIfAvailable();

    onStart();
    onReceive(24);
    onReceive(0x6);
    onReceive(123);
    onStop();
    processDataIfAvailable();

    assertEq(protocol::EyeDisplayMode::FIXED_VALUE, magiceye::animationMode);
    ASSERT_EQ(123, currentAdcValue);
    onStart();
    onReceive(18);
    onReceive(6);
    onStop();
    processDataIfAvailable();

    onStart();
    onReceive(90);
    onReceive(0x6);
    onReceive(12);
    onStop();
    processDataIfAvailable();

    assertEq(protocol::EyeDisplayMode::FIXED_VALUE, magiceye::animationMode);
    ASSERT_EQ(12, currentAdcValue);
    onStart();
    onReceive(18);
    onReceive(6);
    onStop();
    processDataIfAvailable();

    onStart();
    onReceive(76);
    onReceive(0x3);
    onReceive(0x12);
    onReceive(0x34);
    onStop();
    processDataIfAvailable();

    geiger_counter::updateGeigerState();
    const uint16_t cycleLength = geiger_counter::geigerState.cycleLength;
    ASSERT_EQ(0x3412, cycleLength);
    const bool hasNewCycleStarted = geiger_counter::geigerState.hasNewCycleStarted;
    ASSERT_TRUE(hasNewCycleStarted);
    onStart();
    onReceive(9);
    onReceive(3);
    onStop();
    processDataIfAvailable();

    onStart();
    onReceive(41);
    onReceive(0x6);
    onReceive(4);
    onReceive(0);
    onStart();
    onReceive(18);
    onReceive(6);
    onStop();
    processDataIfAvailable();
}
