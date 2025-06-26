#include "i2c-slave.hpp"
#include "protocol.hpp"
#include "display.hpp"
#include "encoder.hpp"
#include "eeprom.hpp"

using namespace octoglow::front_display::protocol;
using namespace octoglow::front_display;

constexpr uint8_t BUFFER_SIZE = 200;

static uint8_t buffer[BUFFER_SIZE];
static uint8_t bytesProcessed;

static_assert(sizeof(buffer) >= 5, "buffer has to have at least 5 bytes");
static_assert(sizeof(buffer) >= sizeof(encoder::ButtonState) + 2, "buffer has to contain whole ButtonState structure");
static_assert(sizeof(buffer) >= sizeof(EncoderState) + 2, "buffer has to contain whole EncoderState structure");

void i2c::onTransmit(uint8_t volatile *value) {
    *value = buffer[bytesProcessed];
    ++bytesProcessed;
}

void i2c::onStart() {
    bytesProcessed = 0;
}

__attribute__((optimize("O3"), hot))
static bool checkCrc8fails() {
    uint8_t calculatedCrcValue = 0;
    for (uint8_t i = 1; i < bytesProcessed; i++) {
        calculatedCrcValue = i2c::crc8ccittUpdate(calculatedCrcValue, buffer[i]);
    }

    if (buffer[0] != calculatedCrcValue) {
        buffer[0] = 0;
        buffer[1] = static_cast<uint8_t>(Command::NONE);
        return true;
    }
    return false;
}

static inline void setCrcForSimpleCommand() {
    buffer[0] = i2c::crc8ccittUpdate(0, buffer[1]);
}

static void setCrcForComplexCommand(const uint8_t payloadLength) {
    uint8_t crcValue = 0;
    for (uint8_t i = 1; i < payloadLength + 2; ++i) {
        crcValue = i2c::crc8ccittUpdate(crcValue, buffer[i]);
    }
    buffer[0] = crcValue;
}

__attribute__((optimize("O3"), hot))
void i2c::onReceive(const uint8_t value) {
    if (bytesProcessed == sizeof(buffer)) {
        return;
    }

    buffer[bytesProcessed] = value;
    ++bytesProcessed;

    // crc is at buffer 0, command is at 1
    const auto cmd = static_cast<Command>(buffer[1]);

    if (bytesProcessed == 2) {
        if (cmd == Command::CLEAR_DISPLAY) {
            if (checkCrc8fails()) {
                return;
            }
            display::clear();
            setCrcForSimpleCommand();
        } else if (cmd == Command::GET_ENCODER_STATE) {
            if (checkCrc8fails()) {
                return;
            }
            auto *btnState = reinterpret_cast<EncoderState *>(buffer + 2);
            btnState->buttonValue = encoder::getButtonStateAndClear();
            btnState->encoderValue = encoder::getValueAndClear();

            setCrcForComplexCommand(sizeof(EncoderState));
        } else if (cmd == Command::READ_END_YEAR_OF_CONSTRUCTION) {
            if (checkCrc8fails()) {
                return;
            }
            buffer[2] = eeprom::readEndYearOfConstruction();
            setCrcForComplexCommand(1);
        }
    } else if (bytesProcessed == 3) {
        if (cmd == Command::SET_BRIGHTNESS) {
            if (checkCrc8fails()) {
                return;
            }
            display::setBrightness(buffer[2]);
            setCrcForSimpleCommand();
        } else if (cmd == Command::WRITE_END_YEAR_OF_CONSTRUCTION) {
            if (checkCrc8fails()) {
                return;
            }
            eeprom::saveEndYearOfConstruction(buffer[2]);
            setCrcForSimpleCommand();
        }
    } else if (cmd == Command::WRITE_STATIC_TEXT && value == 0 && bytesProcessed >= 4) {
        if (checkCrc8fails()) {
            return;
        }
        display::writeStaticText(buffer[2], buffer[3], reinterpret_cast<char *>(&buffer[4]));
        setCrcForSimpleCommand();
    } else if (cmd == Command::WRITE_SCROLLING_TEXT && value == 0 && bytesProcessed >= 5) {
        if (checkCrc8fails()) {
            return;
        }
        display::writeScrollingText(buffer[2], buffer[3], buffer[4], reinterpret_cast<char *>(&buffer[5]));
        setCrcForSimpleCommand();
    } else if (cmd == Command::DRAW_GRAPHICS && bytesProcessed >= 6 && (bytesProcessed - 5) == buffer[3]) {
        if (checkCrc8fails()) {
            return;
        }
        display::drawGraphics(buffer[2], buffer[3], buffer[4], &buffer[5]);
        setCrcForSimpleCommand();
    } else if (cmd == Command::SET_UPPER_BAR && bytesProcessed == 5) {
        if (checkCrc8fails()) {
            return;
        }
        display::setUpperBarContent(*reinterpret_cast<uint32_t *>(buffer + 2));
        setCrcForSimpleCommand();
    }
}
