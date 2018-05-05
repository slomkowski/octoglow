#include "i2c-slave.hpp"
#include "protocol.hpp"
#include "display.hpp"
#include "encoder.hpp"

using namespace octoglow::front_display::protocol;
using namespace octoglow::front_display;

constexpr uint8_t BUFFER_SIZE = 200;

static uint8_t buffer[BUFFER_SIZE];
static uint8_t bytesProcessed;

void octoglow::front_display::i2c::onTransmit(uint8_t volatile *value) {
    *value = buffer[bytesProcessed];
    ++bytesProcessed;
}

void octoglow::front_display::i2c::onStart() {
    bytesProcessed = 0;
}

void octoglow::front_display::i2c::onReceive(uint8_t value) {
    if (bytesProcessed == sizeof(buffer)) {
        return;
    }

    buffer[bytesProcessed] = value;
    ++bytesProcessed;

    const auto cmd = static_cast<Command >(buffer[0]);

    if (bytesProcessed == 1) {
        if (cmd == Command::CLEAR_DISPLAY) {
            display::clear();
        } else if (cmd == Command::GET_ENCODER_STATE) {
            auto *btnState = reinterpret_cast<EncoderState *>(buffer);
            static_assert(sizeof(buffer) >= sizeof(EncoderState), "buffer has to contain whole ButtonState structure");
            btnState->buttonValue = encoder::getButtonStateAndClear();
            btnState->encoderValue = encoder::getValueAndClear();
        }
    } else if (cmd == Command::SET_BRIGHTNESS && bytesProcessed == 2) {
        display::setBrightness(buffer[1]);
    } else if (cmd == Command::WRITE_STATIC_TEXT && value == 0 && bytesProcessed >= 3) {
        display::writeStaticText(buffer[1], buffer[2], reinterpret_cast<char *>(&buffer[3]));
    } else if (cmd == Command::WRITE_SCROLLING_TEXT && value == 0 && bytesProcessed >= 4) {
        display::writeScrollingText(buffer[1], buffer[2], buffer[3], reinterpret_cast<char *>(&buffer[4]));
    } else if (cmd == Command::DRAW_GRAPHICS && bytesProcessed >= 5 && (bytesProcessed - 4) == buffer[2]) {
        display::drawGraphics(buffer[1], buffer[2], buffer[3], &buffer[4]);
    } else if (cmd == Command::SET_UPPER_BAR && bytesProcessed == 4) {
        display::setUpperBarContent(*reinterpret_cast<uint32_t *>(buffer + 1));
    }

    static_assert(sizeof(buffer) >= 4, "buffer has to have at least 4 bytes");
}
