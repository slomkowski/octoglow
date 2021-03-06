#include "i2c-slave.hpp"
#include "magiceye.hpp"
#include "geiger-counter.hpp"

#include <cstring>

constexpr uint8_t BUFFER_SIZE = 10;

using namespace octoglow::geiger::protocol;
using namespace octoglow::geiger;

static uint8_t buffer[BUFFER_SIZE];
static volatile uint8_t bytesProcessed;

static volatile uint8_t *transmittedDataPointer = nullptr;
static uint8_t numberOfBytesToTransmit = 0;

void ::octoglow::geiger::i2c::onTransmit(uint8_t volatile *value) {
    *value = *transmittedDataPointer;
    ++transmittedDataPointer;
    --numberOfBytesToTransmit;

    if(numberOfBytesToTransmit == 0 || numberOfBytesToTransmit == 0xff) {
        setClockToLow();
    }
}

void ::octoglow::geiger::i2c::onStart() {
    bytesProcessed = 0;
}

void ::octoglow::geiger::i2c::onReceive(uint8_t value) {

    buffer[bytesProcessed] = value;
    ++bytesProcessed;

    const auto cmd = static_cast<Command >(buffer[0]);

    if (bytesProcessed == 1) { // 1 byte command
        if (cmd == Command::CLEAN_GEIGER_STATE) {
            geiger_counter::resetCounters();
        } else if (cmd == Command::GET_GEIGER_STATE) {
            setClockToHigh();
            auto *state = &geiger_counter::getState();
            static_assert(sizeof(buffer) >= sizeof(protocol::GeigerState), "buffer has to contain whole GeigerState structure");

            std::memcpy(buffer, state, sizeof(protocol::GeigerState));

            transmittedDataPointer = buffer;
            numberOfBytesToTransmit = sizeof(protocol::GeigerState);
            state->hasNewCycleStarted = false;
        } else if (cmd == Command::GET_DEVICE_STATE) {
            setClockToHigh();
            transmittedDataPointer = reinterpret_cast<uint8_t *>(&hd::getDeviceState());
            numberOfBytesToTransmit = sizeof(protocol::DeviceState);
        }
    } else if (bytesProcessed == 2) {
        if (cmd == Command::SET_EYE_DISPLAY_VALUE) {
            magiceye::setAdcValue(buffer[1]);
        } else if (cmd == Command::SET_BRIGHTNESS) {
            magiceye::setBrightness(buffer[1]);
        }
    } else if (bytesProcessed == 3) {
        if (cmd == Command::SET_GEIGER_CONFIGURATION) {
            geiger_counter::configure(*reinterpret_cast<protocol::GeigerConfiguration *>(buffer + 1));
        } else if (cmd == Command::SET_EYE_CONFIGURATION) {
            magiceye::configure(*reinterpret_cast<protocol::EyeConfiguration *>(buffer + 1));
        }
    }

    static_assert(sizeof(buffer) >= 4, "buffer has to have at least 4 bytes");
}
