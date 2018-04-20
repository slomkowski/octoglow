#include "i2c-slave.hpp"
#include "protocol.hpp"
#include "common.hpp"
#include "magiceye.hpp"

#include <gtest/gtest.h>
#include <iostream>

using namespace std;
using namespace octoglow::geiger;
using namespace octoglow::geiger::i2c;

static protocol::DeviceState deviceState;

protocol::DeviceState &octoglow::geiger::i2c::hd::getDeviceState() {
    cout << "get device state\n";
    deviceState.eyeControllerState = protocol::EyeControllerState::FIXED_VALUE;
    deviceState.eyeVoltage = 0x1234;
    deviceState.geigerVoltage = 0x5678;
    deviceState.geigerPwmValue = 25;
    return deviceState;
}

//todo testy

void assertReadIs(uint8_t expected) {
    uint8_t readValue;
    onTransmit(&readValue);
    ASSERT_EQ(expected, readValue);
}

TEST(I2C, Basic) {

    // get device state
    onStart();
    onReceive(0x1);

    onStart();
    assertReadIs(0x78);
    assertReadIs(0x56);
    assertReadIs(0x34);
    assertReadIs(0x12);
    assertReadIs(25);
    assertReadIs(0);
    assertReadIs(0);
    assertReadIs(1);

    // get geiger state
    onStart();
    onReceive(0x2);

    onStart();
//    assertReadIs(0x78); todo
//    assertReadIs(0x56);
//    assertReadIs(0x34);
//    assertReadIs(0x12);
//    assertReadIs(25);
//    assertReadIs(0);

    // clean geiger state
    onStart();
    onReceive(0x3);

    // read geiger state again
    onStart();
    onReceive(0x2);

    onStart();
//    assertReadIs(0x78); todo wartości wyzerowane
//    assertReadIs(0x56);
//    assertReadIs(0x34);
//    assertReadIs(0x12);
//    assertReadIs(25);
//    assertReadIs(0);


    // set eye mode
    onStart();
    onReceive(0x4);
    onReceive(1);
    ASSERT_EQ(protocol::EyeInverterState::HEATING_LIMITED, magiceye::getState());

    for (int i = 0; i < 5000 * 2; ++i) {
        magiceye::tick();
    }
    ASSERT_EQ(protocol::EyeInverterState::RUNNING, magiceye::getState());
    ASSERT_TRUE(eyeInverterEnabled);

    onStart();
    onReceive(0x4);
    onReceive(0);
    ASSERT_FALSE(eyeInverterEnabled);

    onStart();
    onReceive(0x5);
    onReceive(1);
    onReceive(123);

    ASSERT_EQ(protocol::EyeControllerState::FIXED_VALUE, magiceye::getControllerState());
    ASSERT_EQ(123, currentAdcValue);

    onStart();
    onReceive(0x5);
    onReceive(1);
    onReceive(12);

    ASSERT_EQ(protocol::EyeControllerState::FIXED_VALUE, magiceye::getControllerState());
    ASSERT_EQ(12, currentAdcValue);

    onStart();
    onReceive(0x5);
    onReceive(0);

    ASSERT_EQ(protocol::EyeControllerState::ANIMATION, magiceye::getControllerState());
    ASSERT_EQ(12, currentAdcValue);
}
