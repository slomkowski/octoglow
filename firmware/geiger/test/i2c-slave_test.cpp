#include "i2c-slave.hpp"
#include "protocol.hpp"
#include "common.hpp"
#include "magiceye.hpp"
#include "geiger-counter.hpp"

#include <gtest/gtest.h>
#include <iostream>
#include <vector>

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

static uint8_t crc8ccitt(const vector<uint8_t> &bytes) {
    uint8_t crc = 0;
    for (const auto b: bytes) {
        crc ^= b;
        for (int i = 0; i < 8; ++i) {
            crc = (crc & 0x80) ? (crc << 1) ^ 0x07 : crc << 1;
        }
    }
    return crc;
}

static void sendCommand(const vector<uint8_t> &commandAndPayload) {
    onStart();
    onReceive(crc8ccitt(commandAndPayload));
    for (const auto b: commandAndPayload) {
        onReceive(b);
    }
    onStop();
    processDataIfAvailable();
}

TEST(I2C, EnterBootloader) {
    bootloaderRequested = false;

    // wrong magic is ignored
    sendCommand({static_cast<uint8_t>(protocol::Command::ENTER_BOOTLOADER), 'B', 'O', 'O', 'X'});
    ASSERT_FALSE(bootloaderRequested);

    // wrong CRC is ignored
    onStart();
    onReceive(0x55);
    for (const uint8_t b: vector<uint8_t>{static_cast<uint8_t>(protocol::Command::ENTER_BOOTLOADER), 'B', 'O', 'O', 'T'}) {
        onReceive(b);
    }
    onStop();
    processDataIfAvailable();
    ASSERT_FALSE(bootloaderRequested);

    sendCommand({static_cast<uint8_t>(protocol::Command::ENTER_BOOTLOADER), 'B', 'O', 'O', 'T'});
    ASSERT_TRUE(bootloaderRequested);

    // the same reply as flash-over-i2c.sh expects
    ASSERT_EQ(0x5c, crc8ccitt({8, 'B', 'O', 'O', 'T'}));
    onStart();
    assertReadIs(crc8ccitt({8}));
    assertReadIs(8);

    bootloaderRequested = false;
}

/**
 * Master read of the reply, followed by STOP, as the USCI interrupts deliver it.
 */
static vector<uint8_t> readReply(const size_t length) {
    vector<uint8_t> reply;
    onStart();
    for (size_t i = 0; i < length; ++i) {
        uint8_t value;
        onTransmit(&value);
        reply.push_back(value);
    }
    onStop();
    processDataIfAvailable();
    return reply;
}

static vector<uint8_t> simpleReply(const protocol::Command command) {
    const auto c = static_cast<uint8_t>(command);
    return {crc8ccitt({c}), c};
}

static const vector<uint8_t> REJECTED_REPLY = {0, static_cast<uint8_t>(protocol::Command::NONE)};

TEST(I2C, ReadingReplyDoesNotRepeatCommand) {
    sendCommand({static_cast<uint8_t>(protocol::Command::CLEAN_GEIGER_STATE)});

    geiger_counter::hd::numOfCountsCurrentCycle = 33;

    // the reply has the command and its CRC, STOP after it mustn't execute it again
    ASSERT_EQ(simpleReply(protocol::Command::CLEAN_GEIGER_STATE), readReply(2));
    ASSERT_EQ(33, geiger_counter::hd::numOfCountsCurrentCycle);

    // also with the byte the USCI prefetches after the last one
    const auto replyWithPrefetchedByte = readReply(3);
    ASSERT_EQ(simpleReply(protocol::Command::CLEAN_GEIGER_STATE), vector<uint8_t>(replyWithPrefetchedByte.begin(), replyWithPrefetchedByte.begin() + 2));
    ASSERT_EQ(33, geiger_counter::hd::numOfCountsCurrentCycle);
}

TEST(I2C, ReadBeyondBufferReturnsZero) {
    sendCommand({static_cast<uint8_t>(protocol::Command::CLEAN_GEIGER_STATE)});

    const auto reply = readReply(40);

    ASSERT_EQ(simpleReply(protocol::Command::CLEAN_GEIGER_STATE), vector<uint8_t>(reply.begin(), reply.begin() + 2));
    ASSERT_EQ(vector<uint8_t>(24, 0), vector<uint8_t>(reply.begin() + 16, reply.end()));
}

TEST(I2C, LongWriteIsIgnored) {
    currentAdcValue = 1;

    vector<uint8_t> bytes(30, static_cast<uint8_t>(protocol::Command::SET_EYE_DISPLAY_VALUE));
    sendCommand(bytes);

    ASSERT_EQ(1, currentAdcValue);
}

TEST(I2C, WrongCrcIsRejected) {
    currentAdcValue = 1;

    onStart();
    onReceive(0x00);
    onReceive(static_cast<uint8_t>(protocol::Command::SET_EYE_DISPLAY_VALUE));
    onReceive(200);
    onStop();
    processDataIfAvailable();
    processDataIfAvailable();

    ASSERT_EQ(1, currentAdcValue);
    ASSERT_EQ(REJECTED_REPLY, readReply(2));

    // the next command works
    sendCommand({static_cast<uint8_t>(protocol::Command::SET_EYE_DISPLAY_VALUE), 200});
    ASSERT_EQ(200, currentAdcValue);
    ASSERT_EQ(simpleReply(protocol::Command::SET_EYE_DISPLAY_VALUE), readReply(2));
}

TEST(I2C, InvalidEyeModeIsRejected) {
    sendCommand({static_cast<uint8_t>(protocol::Command::SET_EYE_CONFIGURATION), 0, 1});
    assertEq(protocol::EyeDisplayMode::FIXED_VALUE, magiceye::animationMode);

    sendCommand({static_cast<uint8_t>(protocol::Command::SET_EYE_CONFIGURATION), 0, 2});

    assertEq(protocol::EyeDisplayMode::FIXED_VALUE, magiceye::animationMode);
    ASSERT_EQ(REJECTED_REPLY, readReply(2));
}

TEST(I2C, EyeEnabledByAnyNonZeroByte) {
    sendCommand({static_cast<uint8_t>(protocol::Command::SET_EYE_CONFIGURATION), 0, 1});
    assertEq(protocol::EyeInverterState::DISABLED, magiceye::state);

    sendCommand({static_cast<uint8_t>(protocol::Command::SET_EYE_CONFIGURATION), 0x80, 1});
    ASSERT_NE(protocol::EyeInverterState::DISABLED, static_cast<protocol::EyeInverterState>(magiceye::state));
    ASSERT_EQ(simpleReply(protocol::Command::SET_EYE_CONFIGURATION), readReply(2));

    sendCommand({static_cast<uint8_t>(protocol::Command::SET_EYE_CONFIGURATION), 0, 1});
    assertEq(protocol::EyeInverterState::DISABLED, magiceye::state);
}

TEST(I2C, ZeroCycleLengthIsRejected) {
    sendCommand({static_cast<uint8_t>(protocol::Command::SET_GEIGER_CONFIGURATION), 0x2c, 0x01});
    ASSERT_EQ(300, static_cast<uint16_t>(geiger_counter::geigerState.cycleLength));
    ASSERT_EQ(simpleReply(protocol::Command::SET_GEIGER_CONFIGURATION), readReply(2));

    sendCommand({static_cast<uint8_t>(protocol::Command::SET_GEIGER_CONFIGURATION), 0, 0});

    ASSERT_EQ(300, static_cast<uint16_t>(geiger_counter::geigerState.cycleLength));
    ASSERT_EQ(REJECTED_REPLY, readReply(2));
}

TEST(I2C, GetGeigerStateClearsNewCycleFlag) {
    sendCommand({static_cast<uint8_t>(protocol::Command::CLEAN_GEIGER_STATE)});
    readReply(2);

    sendCommand({static_cast<uint8_t>(protocol::Command::GET_GEIGER_STATE)});
    auto reply = readReply(2 + sizeof(protocol::GeigerState));
    ASSERT_EQ(crc8ccitt(vector<uint8_t>(reply.begin() + 1, reply.end())), reply[0]);
    ASSERT_EQ(1, reply[2] & 0b1);

    sendCommand({static_cast<uint8_t>(protocol::Command::GET_GEIGER_STATE)});
    reply = readReply(2 + sizeof(protocol::GeigerState));
    ASSERT_EQ(0, reply[2] & 0b1);
}
