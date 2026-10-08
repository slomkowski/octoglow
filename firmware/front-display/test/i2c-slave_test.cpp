#include "i2c-slave.hpp"
#include "display.hpp"
#include "Font5x7.hpp"
#include "eeprom.hpp"

#include <gtest/gtest.h>
#include <cstdint>
#include <cstring>

#include "encoder.hpp"
#include "protocol.hpp"
#include "stubs.hpp"

#include <string>
#include <vector>

using namespace octoglow::front_display;
using namespace octoglow::front_display::i2c;

static void assertReadIs(const uint8_t expected) {
    uint8_t readValue;
    onTransmit(&readValue);

    ASSERT_EQ(expected, readValue);
}

static void assertFramebufferIsEmpty() {
    for (int i = 0; i < display::NUM_OF_CHARACTERS * display::COLUMNS_IN_CHARACTER; ++i) {
        ASSERT_EQ(0, display::_frameBuffer[i]);
    }
}

TEST(I2C, GetEncoderState) {
    onStart();
    onReceive(0x7);
    onReceive(0x1);

    onStart();
    assertReadIs(107);
    assertReadIs(1);
    assertReadIs(0);
    assertReadIs(0);

    onStart();
    onReceive(0x7);
    onReceive(0x1);

    onStart();
    assertReadIs(107);
    assertReadIs(1);
    assertReadIs(0);
    assertReadIs(0);

    encoder::_currentEncoderSteps = 3;
    encoder::_currentButtonState = encoder::ButtonState::JUST_PRESSED;

    onStart();
    onReceive(0x7);
    onReceive(0x1);

    onStart();
    assertReadIs(83);
    assertReadIs(1);
    assertReadIs(3);
    assertReadIs(1);

    onStart();
    onReceive(0x7);
    onReceive(0x1);

    onStart();
    assertReadIs(107);
    assertReadIs(1);
    assertReadIs(0);
    assertReadIs(0);
}

TEST(I2C, ClearDisplay) {
    display::writeStaticText(5, 20, const_cast<char *>("lorem ipsum"));

    onStart();
    onReceive(14);
    onReceive(2);

    assertFramebufferIsEmpty();

    onStart();
    assertReadIs(14);
    assertReadIs(2);
}

TEST(I2C, SetBrightness) {
    onStart();
    onReceive(56);
    onReceive(0x3);
    onReceive(1);

    ASSERT_EQ(1, display::_brightness);

    onStart();
    onReceive(35);
    onReceive(0x3);
    onReceive(4);

    ASSERT_EQ(4, display::_brightness);

    onStart();
    assertReadIs(9);
    assertReadIs(3);
}

TEST(I2C, SetUpperBar) {
    display::clear();

    // all segments enabled
    onStart();
    onReceive(179);
    onReceive(7);
    onReceive(0xff);
    onReceive(0xff);
    onReceive(0x0f);
    ASSERT_EQ(0x0fffff, display::_upperBarBuffer);

    // all segments disabled
    onStart();
    onReceive(98);
    onReceive(7);
    onReceive(0);
    onReceive(0);
    onReceive(0);
    ASSERT_EQ(0, display::_upperBarBuffer);

    onStart();
    onReceive(232);
    onReceive(7);
    onReceive(0xab);
    onReceive(0xcd);
    onReceive(0x0e);

    ASSERT_EQ(0x0ecdab, display::_upperBarBuffer);

    onStart();
    assertReadIs(21);
    assertReadIs(7);
}

TEST(I2C, DrawGraphics) {
    display::clear();

    onStart();
    onReceive(219);
    onReceive(6);
    onReceive(1);
    onReceive(5);
    onReceive(0);
    onReceive(0xab);
    onReceive(0xcd);
    onReceive(0xef);
    onReceive(0xab);
    onReceive(0xcd);

    ASSERT_EQ(0, display::_frameBuffer[0]);
    ASSERT_EQ(0xab, display::_frameBuffer[1]);
    ASSERT_EQ(0xcd, display::_frameBuffer[2]);
    ASSERT_EQ(0xef, display::_frameBuffer[3]);
    ASSERT_EQ(0xab, display::_frameBuffer[4]);
    ASSERT_EQ(0xcd, display::_frameBuffer[5]);
    ASSERT_EQ(0, display::_frameBuffer[6]);

    onStart();
    assertReadIs(18);
    assertReadIs(6);
}

TEST(I2C, WriteStaticText) {
    display::clear();

    onStart();
    onReceive(96);
    onReceive(4);
    onReceive(1);
    onReceive(3);
    onReceive('a');
    onReceive('b');
    onReceive('c');

    assertFramebufferIsEmpty();

    onReceive(0);

    for (int pos = 0; pos < 5; ++pos) {
        ASSERT_EQ(0, display::_frameBuffer[pos]);
    }

    ASSERT_EQ(0x20, display::_frameBuffer[5]);
    ASSERT_EQ(0x54, display::_frameBuffer[6]);
    ASSERT_EQ(0x54, display::_frameBuffer[7]);
    ASSERT_EQ(0x54, display::_frameBuffer[8]);
    ASSERT_EQ(0x78, display::_frameBuffer[9]);

    ASSERT_EQ(0x7f, display::_frameBuffer[10]);
    ASSERT_EQ(0x48, display::_frameBuffer[11]);
    ASSERT_EQ(0x44, display::_frameBuffer[12]);
    ASSERT_EQ(0x44, display::_frameBuffer[13]);
    ASSERT_EQ(0x38, display::_frameBuffer[14]);

    ASSERT_EQ(0x38, display::_frameBuffer[15]);
    ASSERT_EQ(0x44, display::_frameBuffer[16]);
    ASSERT_EQ(0x44, display::_frameBuffer[17]);
    ASSERT_EQ(0x44, display::_frameBuffer[18]);
    ASSERT_EQ(0x20, display::_frameBuffer[19]);

    ASSERT_EQ(0, display::_frameBuffer[20]);
    ASSERT_EQ(0, display::_frameBuffer[21]);

    onStart();
    assertReadIs(28);
    assertReadIs(4);
}

TEST(I2C, WriteScrollingText) {
    display::clear();

    onStart();
    onReceive(43);
    onReceive(5);
    onReceive(2);
    onReceive(3);
    onReceive(10);
    onReceive('a');
    onReceive('b');
    onReceive('c');
    onReceive('d');
    onReceive('e');
    onReceive('f');
    onReceive(0);

    ASSERT_EQ(3, display::_scrollingSlots[2].startPosition);
    ASSERT_EQ(6 * 5, display::_scrollingSlots[2].maxTextLength);

    onStart();
    assertReadIs(27);
    assertReadIs(5);
}

TEST(I2C, EndYearOfConstruction) {
    onStart();
    onReceive(209);
    onReceive(9);
    onReceive(20);

    ASSERT_EQ(20, test::endYearOfConstruction);

    onStart();
    assertReadIs(63);
    assertReadIs(9);

    onStart();
    onReceive(214);
    onReceive(9);
    onReceive(21);

    ASSERT_EQ(21, test::endYearOfConstruction);

    onStart();
    assertReadIs(63);
    assertReadIs(9);

    onStart();
    onReceive(56);
    onReceive(8);

    onStart();
    assertReadIs(195);
    assertReadIs(8);
    assertReadIs(21);
}

using protocol::Command;

static uint8_t crc8(const std::vector<uint8_t>::const_iterator begin, const std::vector<uint8_t>::const_iterator end) {
    uint8_t crc = 0;
    for (auto it = begin; it != end; ++it) {
        crc = crc8ccittUpdate(crc, *it);
    }
    return crc;
}

static uint8_t crc8(const std::vector<uint8_t> &data) {
    return crc8(data.begin(), data.end());
}

static void sendRaw(const std::vector<uint8_t> &bytes) {
    onStart();
    for (const auto b: bytes) {
        onReceive(b);
    }
}

// prepends the payload with the valid CRC and sends it
static void sendCommand(const std::vector<uint8_t> &payload) {
    std::vector<uint8_t> bytes{crc8(payload)};
    bytes.insert(bytes.end(), payload.begin(), payload.end());
    sendRaw(bytes);
}

// sends the payload preceded by the CRC which is guaranteed to be invalid
static void sendCommandWithInvalidCrc(const std::vector<uint8_t> &payload) {
    std::vector<uint8_t> bytes{static_cast<uint8_t>(crc8(payload) + 1)};
    bytes.insert(bytes.end(), payload.begin(), payload.end());
    sendRaw(bytes);
}

static std::vector<uint8_t> withText(std::vector<uint8_t> header, const char *text) {
    for (const char *c = text; *c != 0; ++c) {
        header.push_back(static_cast<uint8_t>(*c));
    }
    header.push_back(0);
    return header;
}

static std::vector<uint8_t> readResponse(const size_t length) {
    onStart();
    std::vector<uint8_t> response;
    for (size_t i = 0; i < length; ++i) {
        uint8_t value;
        onTransmit(&value);
        response.push_back(value);
    }
    return response;
}

static void assertResponseIs(const std::vector<uint8_t> &expectedPayload) {
    const auto response = readResponse(expectedPayload.size() + 1);
    ASSERT_EQ(crc8(expectedPayload), response[0]) << "invalid CRC of the response";
    ASSERT_EQ(expectedPayload, std::vector<uint8_t>(response.begin() + 1, response.end()));
}

static void assertCrcErrorResponse() {
    ASSERT_EQ((std::vector<uint8_t>{0, static_cast<uint8_t>(Command::NONE)}), readResponse(2));
}

static uint8_t cmd(const Command c) {
    return static_cast<uint8_t>(c);
}

static std::vector<uint8_t> framebufferCopy() {
    return {display::_frameBuffer,
            display::_frameBuffer + display::NUM_OF_CHARACTERS * display::COLUMNS_IN_CHARACTER};
}

static void fillFramebuffer(const uint8_t value) {
    memset(display::_frameBuffer, value, display::NUM_OF_CHARACTERS * display::COLUMNS_IN_CHARACTER);
}

class I2CCommand : public ::testing::Test {
protected:
    void SetUp() override {
        display::clear();
        display::setBrightness(display::MAX_BRIGHTNESS);
        encoder::_currentEncoderSteps = 0;
        encoder::_currentButtonState = encoder::ButtonState::NO_CHANGE;
        test::endYearOfConstruction = 77;
    }
};

TEST_F(I2CCommand, Crc8IsStandardCrc8) {
    // check value of CRC-8 (poly 0x07, init 0x00), the same as _crc8_ccitt_update() in avr-libc
    const std::string check = "123456789";
    ASSERT_EQ(0xf4, crc8(std::vector<uint8_t>(check.begin(), check.end())));
}

TEST_F(I2CCommand, GetEncoderStateNegativeValue) {
    encoder::_currentEncoderSteps = -2;
    encoder::_currentButtonState = encoder::ButtonState::JUST_RELEASED;

    sendCommand({cmd(Command::GET_ENCODER_STATE)});

    assertResponseIs({cmd(Command::GET_ENCODER_STATE), 0xfe, 0xff});
    ASSERT_EQ(0, encoder::_currentEncoderSteps);
    ASSERT_EQ(encoder::ButtonState::NO_CHANGE, encoder::_currentButtonState);
}

TEST_F(I2CCommand, GetEncoderStateResponseCanBeReadManyTimes) {
    encoder::_currentEncoderSteps = 5;

    sendCommand({cmd(Command::GET_ENCODER_STATE)});

    assertResponseIs({cmd(Command::GET_ENCODER_STATE), 5, 0});
    assertResponseIs({cmd(Command::GET_ENCODER_STATE), 5, 0});
}

TEST_F(I2CCommand, GetEncoderStateInvalidCrcDoesNotClearState) {
    encoder::_currentEncoderSteps = 4;
    encoder::_currentButtonState = encoder::ButtonState::JUST_PRESSED;

    sendCommandWithInvalidCrc({cmd(Command::GET_ENCODER_STATE)});

    assertCrcErrorResponse();
    ASSERT_EQ(4, encoder::_currentEncoderSteps);
    ASSERT_EQ(encoder::ButtonState::JUST_PRESSED, encoder::_currentButtonState);

    sendCommand({cmd(Command::GET_ENCODER_STATE)});
    assertResponseIs({cmd(Command::GET_ENCODER_STATE), 4, 1});
}

TEST_F(I2CCommand, ClearDisplay) {
    fillFramebuffer(0x55);
    display::setUpperBarContent(0xfffff);

    sendCommand({cmd(Command::CLEAR_DISPLAY)});

    assertFramebufferIsEmpty();
    ASSERT_EQ(0u, display::_upperBarBuffer);
    assertResponseIs({cmd(Command::CLEAR_DISPLAY)});
}

TEST_F(I2CCommand, ClearDisplayInvalidCrc) {
    fillFramebuffer(0x55);
    const auto before = framebufferCopy();

    sendCommandWithInvalidCrc({cmd(Command::CLEAR_DISPLAY)});

    ASSERT_EQ(before, framebufferCopy());
    assertCrcErrorResponse();
}

TEST_F(I2CCommand, SetBrightnessIsClamped) {
    sendCommand({cmd(Command::SET_BRIGHTNESS), 200});

    ASSERT_EQ(display::MAX_BRIGHTNESS, display::_brightness);
    assertResponseIs({cmd(Command::SET_BRIGHTNESS)});
}

TEST_F(I2CCommand, SetBrightnessInvalidCrc) {
    sendCommandWithInvalidCrc({cmd(Command::SET_BRIGHTNESS), 2});

    ASSERT_EQ(display::MAX_BRIGHTNESS, display::_brightness);
    assertCrcErrorResponse();
}

TEST_F(I2CCommand, SetUpperBarIgnoresBitsAbove20) {
    // 20 bits are sent as 3 bytes, little endian
    sendCommand({cmd(Command::SET_UPPER_BAR), 0x21, 0x43, 0xf5});

    ASSERT_EQ(0x54321u, display::_upperBarBuffer);
    assertResponseIs({cmd(Command::SET_UPPER_BAR)});
}

TEST_F(I2CCommand, SetUpperBarInvalidCrc) {
    sendCommandWithInvalidCrc({cmd(Command::SET_UPPER_BAR), 0xff, 0xff, 0xff});

    ASSERT_EQ(0u, display::_upperBarBuffer);
    assertCrcErrorResponse();
}

TEST_F(I2CCommand, DrawGraphicsSumWithText) {
    fillFramebuffer(0x0f);

    sendCommand({cmd(Command::DRAW_GRAPHICS), 10, 3, 1, 0xf0, 0x00, 0x80});

    ASSERT_EQ(0x0f, display::_frameBuffer[9]);
    ASSERT_EQ(0xff, display::_frameBuffer[10]);
    ASSERT_EQ(0x0f, display::_frameBuffer[11]);
    ASSERT_EQ(0x8f, display::_frameBuffer[12]);
    ASSERT_EQ(0x0f, display::_frameBuffer[13]);
    assertResponseIs({cmd(Command::DRAW_GRAPHICS)});
}

TEST_F(I2CCommand, DrawGraphicsSingleColumn) {
    sendCommand({cmd(Command::DRAW_GRAPHICS), 199, 1, 0, 0x7f});

    ASSERT_EQ(0x7f, display::_frameBuffer[199]);
    assertResponseIs({cmd(Command::DRAW_GRAPHICS)});
}

TEST_F(I2CCommand, DrawGraphicsZeroColumnPosition) {
    // zero bytes in the header must not be mistaken for anything else
    sendCommand({cmd(Command::DRAW_GRAPHICS), 0, 2, 0, 0, 0x11});

    ASSERT_EQ(0x00, display::_frameBuffer[0]);
    ASSERT_EQ(0x11, display::_frameBuffer[1]);
    assertResponseIs({cmd(Command::DRAW_GRAPHICS)});
}

TEST_F(I2CCommand, DrawGraphicsIsExecutedOnlyAfterAllColumnsArrive) {
    const std::vector<uint8_t> payload{cmd(Command::DRAW_GRAPHICS), 0, 4, 0, 1, 2, 3, 4};

    onStart();
    onReceive(crc8(payload));
    for (size_t i = 0; i < payload.size() - 1; ++i) {
        onReceive(payload[i]);
    }
    assertFramebufferIsEmpty();

    onReceive(payload.back());
    ASSERT_EQ(4, display::_frameBuffer[3]);
}

TEST_F(I2CCommand, DrawGraphicsInvalidCrc) {
    sendCommandWithInvalidCrc({cmd(Command::DRAW_GRAPHICS), 0, 2, 0, 0xff, 0xff});

    assertFramebufferIsEmpty();
    assertCrcErrorResponse();
}

TEST_F(I2CCommand, WriteStaticTextAtPositionZero) {
    sendCommand(withText({cmd(Command::WRITE_STATIC_TEXT), 0, 2}, "ab"));

    ASSERT_EQ(0x20, display::_frameBuffer[0]);
    ASSERT_EQ(0x7f, display::_frameBuffer[5]);
    assertResponseIs({cmd(Command::WRITE_STATIC_TEXT)});
}

TEST_F(I2CCommand, WriteStaticTextUtf8) {
    const char *text = "15°C ŻÓŁW";

    sendCommand(withText({cmd(Command::WRITE_STATIC_TEXT), 10, 20}, text));

    // the same as calling the display function directly
    const auto viaI2c = framebufferCopy();
    display::clear();
    display::writeStaticText(10, 20, text);
    ASSERT_EQ(framebufferCopy(), viaI2c);
    assertResponseIs({cmd(Command::WRITE_STATIC_TEXT)});
}

TEST_F(I2CCommand, WriteStaticTextInvalidCrc) {
    sendCommandWithInvalidCrc(withText({cmd(Command::WRITE_STATIC_TEXT), 0, 10}, "abc"));

    assertFramebufferIsEmpty();
    assertCrcErrorResponse();
}

TEST_F(I2CCommand, WriteScrollingTextSlotZeroAtPositionZero) {
    sendCommand(withText({cmd(Command::WRITE_SCROLLING_TEXT), protocol::scroll::SLOT0, 0, 5}, "zażółć gęślą jaźń"));

    const auto &slot = display::_scrollingSlots[0];
    ASSERT_EQ(0, slot.startPosition);
    ASSERT_EQ(5, slot.length);
    ASSERT_EQ(17, slot.textLength);
    ASSERT_EQ('z', slot.convertedText[0]);
    ASSERT_EQ('a', slot.convertedText[1]);
    ASSERT_EQ(display::UNICODE_START_CODE + 17, slot.convertedText[2]);
    assertResponseIs({cmd(Command::WRITE_SCROLLING_TEXT)});
}

TEST_F(I2CCommand, WriteScrollingTextLongestText) {
    const std::string text(protocol::scroll::SLOT0_MAX_LENGTH, 'q');

    sendCommand(withText({cmd(Command::WRITE_SCROLLING_TEXT), 0, 0, 20}, text.c_str()));

    ASSERT_EQ(protocol::scroll::SLOT0_MAX_LENGTH, display::_scrollingSlots[0].textLength);
    assertResponseIs({cmd(Command::WRITE_SCROLLING_TEXT)});
}

TEST_F(I2CCommand, WriteScrollingTextShortTextIsStatic) {
    sendCommand(withText({cmd(Command::WRITE_SCROLLING_TEXT), 1, 2, 10}, "abc"));

    ASSERT_EQ(0, display::_scrollingSlots[1].length);
    ASSERT_EQ(0x20, display::_frameBuffer[10]);
    assertResponseIs({cmd(Command::WRITE_SCROLLING_TEXT)});
}

TEST_F(I2CCommand, WriteScrollingTextInvalidCrc) {
    sendCommandWithInvalidCrc(withText({cmd(Command::WRITE_SCROLLING_TEXT), 1, 0, 3}, "long text"));

    ASSERT_EQ(0, display::_scrollingSlots[1].textLength);
    assertCrcErrorResponse();
}

TEST_F(I2CCommand, EndYearOfConstructionInvalidCrc) {
    sendCommandWithInvalidCrc({cmd(Command::WRITE_END_YEAR_OF_CONSTRUCTION), 30});

    ASSERT_EQ(77, test::endYearOfConstruction);
    assertCrcErrorResponse();

    sendCommandWithInvalidCrc({cmd(Command::READ_END_YEAR_OF_CONSTRUCTION)});
    assertCrcErrorResponse();
}

TEST_F(I2CCommand, EndYearOfConstructionRoundTrip) {
    sendCommand({cmd(Command::WRITE_END_YEAR_OF_CONSTRUCTION), 25});
    assertResponseIs({cmd(Command::WRITE_END_YEAR_OF_CONSTRUCTION)});

    sendCommand({cmd(Command::READ_END_YEAR_OF_CONSTRUCTION)});
    assertResponseIs({cmd(Command::READ_END_YEAR_OF_CONSTRUCTION), 25});
}

TEST_F(I2CCommand, UnknownCommandIsIgnored) {
    fillFramebuffer(0x33);
    const auto before = framebufferCopy();

    sendCommand({0x55, 1, 2, 3, 4, 5, 6, 0});
    sendCommand({cmd(Command::NONE), 0, 0, 0});

    ASSERT_EQ(before, framebufferCopy());
    ASSERT_EQ(display::MAX_BRIGHTNESS, display::_brightness);

    // the next command works normally
    sendCommand({cmd(Command::CLEAR_DISPLAY)});
    assertFramebufferIsEmpty();
}

TEST_F(I2CCommand, InterruptedCommandIsDiscardedOnStart) {
    onStart();
    onReceive(crc8({cmd(Command::SET_BRIGHTNESS), 1}));
    onReceive(cmd(Command::SET_BRIGHTNESS));

    sendCommand({cmd(Command::SET_UPPER_BAR), 1, 0, 0});

    ASSERT_EQ(display::MAX_BRIGHTNESS, display::_brightness);
    ASSERT_EQ(1u, display::_upperBarBuffer);
}

TEST_F(I2CCommand, TooLongFrameIsIgnored) {
    // static text without the terminating zero, longer than the receive buffer
    std::vector<uint8_t> payload{cmd(Command::WRITE_STATIC_TEXT), 0, 40};
    payload.resize(1000, 'x');

    sendCommand(payload);

    assertFramebufferIsEmpty();

    sendCommand({cmd(Command::SET_BRIGHTNESS), 3});
    ASSERT_EQ(3, display::_brightness);
    assertResponseIs({cmd(Command::SET_BRIGHTNESS)});
}

TEST_F(I2CCommand, ExtraBytesAfterCommandAreIgnored) {
    const std::vector<uint8_t> payload{cmd(Command::SET_BRIGHTNESS), 2};
    sendRaw({crc8(payload), payload[0], payload[1], 4, 4, 4});

    ASSERT_EQ(2, display::_brightness);
    assertResponseIs({cmd(Command::SET_BRIGHTNESS)});
}

TEST_F(I2CCommand, WriteStaticTextExceedingDisplayIsClipped) {
    sendCommand(withText({cmd(Command::WRITE_STATIC_TEXT), 35, 20}, "abcdefghij"));

    ASSERT_EQ(0x20, display::_frameBuffer[5 * 35]);
    assertResponseIs({cmd(Command::WRITE_STATIC_TEXT)});
}

TEST_F(I2CCommand, WriteStaticTextOutsideDisplayIsIgnored) {
    sendCommand(withText({cmd(Command::WRITE_STATIC_TEXT), 200, 200}, "abc"));

    assertFramebufferIsEmpty();
    assertResponseIs({cmd(Command::WRITE_STATIC_TEXT)});
}

TEST_F(I2CCommand, WriteStaticTextTruncatedUtf8) {
    sendCommand({cmd(Command::WRITE_STATIC_TEXT), 0, 5, 'a', 0xc4, 0});

    ASSERT_EQ(0x20, display::_frameBuffer[0]);
    ASSERT_EQ(0xff, display::_frameBuffer[5]);
    assertResponseIs({cmd(Command::WRITE_STATIC_TEXT)});
}

TEST_F(I2CCommand, DrawGraphicsExceedingDisplayIsClipped) {
    sendCommand({cmd(Command::DRAW_GRAPHICS), 198, 4, 0, 1, 2, 3, 4});

    ASSERT_EQ(1, display::_frameBuffer[198]);
    ASSERT_EQ(2, display::_frameBuffer[199]);
    assertResponseIs({cmd(Command::DRAW_GRAPHICS)});
}

TEST_F(I2CCommand, WriteScrollingTextExceedingDisplayIsClipped) {
    sendCommand(withText({cmd(Command::WRITE_SCROLLING_TEXT), 0, 38, 10}, "long scrolling text"));

    ASSERT_EQ(2, display::_scrollingSlots[0].length);
    assertResponseIs({cmd(Command::WRITE_SCROLLING_TEXT)});

    for (int i = 0; i < 300 * 30; ++i) {
        display::pool();
    }
}

TEST_F(I2CCommand, ReadingPastTheBufferReturnsZeros) {
    sendCommand({cmd(Command::READ_END_YEAR_OF_CONSTRUCTION)});

    const auto response = readResponse(1000);

    ASSERT_EQ(cmd(Command::READ_END_YEAR_OF_CONSTRUCTION), response[1]);
    ASSERT_EQ(77, response[2]);
    for (size_t i = 200; i < response.size(); ++i) {
        ASSERT_EQ(0, response[i]) << "byte " << i;
    }
}
