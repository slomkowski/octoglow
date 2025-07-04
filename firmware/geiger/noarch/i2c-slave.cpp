#include "i2c-slave.hpp"
#include "magiceye.hpp"
#include "inverter.hpp"
#include "geiger-counter.hpp"

using namespace octoglow::geiger::protocol;
using namespace octoglow::geiger;

constexpr uint8_t BUFFER_SIZE = 16;

static uint8_t volatile buffer[BUFFER_SIZE];
static volatile uint8_t bytesProcessed = 0;
static volatile uint8_t numberOfBytesToTransmit = 0;

static_assert(sizeof(buffer) >= 4, "buffer has to have at least 4 bytes");
static_assert(sizeof(buffer) >= sizeof(GeigerState) + 2, "buffer has to contain whole GeigerState structure");
static_assert(sizeof(buffer) >= sizeof(DeviceState) + 2, "buffer has to contain whole DeviceState structure");

void i2c::onTransmit(uint8_t volatile *value) {
    *value = buffer[bytesProcessed];
    ++bytesProcessed;
    --numberOfBytesToTransmit;

    // if(numberOfBytesToTransmit == 0 || numberOfBytesToTransmit == 0xff) {
    //     setClockToLow();
    // }
}

void i2c::onStart() {
    bytesProcessed = 0;
}

__attribute__((optimize("O3"), hot))
static inline uint8_t crc8ccittUpdate(const uint8_t inCrc, const uint8_t inData) {
    uint8_t data = inCrc ^ inData;
    for (uint8_t i = 0; i < 8; i++) {
        if ((data & 0x80) != 0) {
            data <<= 1;
            data ^= 0x07;
        } else {
            data <<= 1;
        }
    }
    return data;
}

__attribute__((optimize("O3"), hot))
static inline bool checkCrc8fails() {
    uint8_t calculatedCrcValue = 0;
    for (uint8_t i = 1; i < bytesProcessed; i++) {
        calculatedCrcValue = crc8ccittUpdate(calculatedCrcValue, buffer[i]);
    }

    if (buffer[0] != calculatedCrcValue) {
        buffer[0] = 0;
        buffer[1] = static_cast<uint8_t>(Command::NONE);
        return true;
    }
    return false;
}

__attribute__((optimize("O3"), hot))
static inline void setCrcForSimpleCommand() {
    numberOfBytesToTransmit = 2;
    buffer[0] = crc8ccittUpdate(0, buffer[1]);
}

__attribute__((optimize("O3"), hot))
static inline void setCrcForComplexCommand(const uint8_t payloadLength) {
    uint8_t crcValue = 0;
    for (uint8_t i = 1; i < payloadLength + 2; ++i) {
        crcValue = crc8ccittUpdate(crcValue, buffer[i]);
    }
    buffer[0] = crcValue;
    numberOfBytesToTransmit = payloadLength + 2;
}

static inline void fillBuffer(const void volatile *src, const uint8_t size) {
    for (uint8_t i = 0; i != size; ++i) {
        buffer[i + 2] = *(static_cast<const uint8_t volatile *>(src) + i);
    }
}

void i2c::onReceive(const uint8_t value) {
    if (bytesProcessed == sizeof(buffer) - 1) {
        return;
    }

    buffer[bytesProcessed] = value;
    ++bytesProcessed;

    const auto cmd = static_cast<Command>(buffer[1]);

    if (bytesProcessed == 2) {
        // 1 byte command
        if (cmd == Command::CLEAN_GEIGER_STATE) {
            if (checkCrc8fails()) {
                return;
            }
            geiger_counter::resetCounters();
            setCrcForSimpleCommand();
        } else if (cmd == Command::GET_GEIGER_STATE) {
            //  setClockToHigh();
            if (checkCrc8fails()) {
                //setClockToLow();
                return;
            }
            geiger_counter::updateGeigerState();
            fillBuffer(&geiger_counter::geigerState, sizeof(GeigerState));
            setCrcForComplexCommand(sizeof(GeigerState));
            geiger_counter::geigerState.hasNewCycleStarted = false;
        } else if (cmd == Command::GET_DEVICE_STATE) {
            //  setClockToHigh();
            if (checkCrc8fails()) {
              //  setClockToLow();
                return;
            }
            fillBuffer(&hd::getDeviceState(), sizeof(DeviceState));
            setCrcForComplexCommand(sizeof(DeviceState));
        }
    } else if (bytesProcessed == 3) {
        if (cmd == Command::SET_EYE_DISPLAY_VALUE) {
            if (checkCrc8fails()) {
                return;
            }
            magiceye::setDacOutputValue(buffer[2]);
            setCrcForSimpleCommand();
        } else if (cmd == Command::SET_BRIGHTNESS) {
            if (checkCrc8fails()) {
                return;
            }
            inverter::setBrightness(buffer[2]);
            setCrcForSimpleCommand();
        }
    } else if (bytesProcessed == 4) {
        if (cmd == Command::SET_GEIGER_CONFIGURATION) {
            if (checkCrc8fails()) {
                return;
            }
            geiger_counter::configure(*reinterpret_cast<volatile GeigerConfiguration *>(buffer + 2));
            setCrcForSimpleCommand();
        } else if (cmd == Command::SET_EYE_CONFIGURATION) {
            if (checkCrc8fails()) {
                return;
            }
            magiceye::configure(*reinterpret_cast<volatile EyeConfiguration *>(buffer + 2));
            setCrcForSimpleCommand();
        }
    }
}
