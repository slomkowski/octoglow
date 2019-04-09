#include "i2c-slave.hpp"
#include "display.hpp"

#include <gtest/gtest.h>
#include <iostream>
#include <cstdint>

using namespace octoglow::front_display;
using namespace octoglow::front_display::i2c;

static void assertReadIs(uint8_t expected) {
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
    onReceive(0x1);

    onStart();
    assertReadIs(0);
    assertReadIs(0xff);

    onStart();
    onReceive(0x1);

    onStart();
    assertReadIs(0);
    assertReadIs(0);
}

TEST(I2C, ClearDisplay) {
    display::writeStaticText(5, 20, const_cast<char *>("lorem ipsum"));

    onStart();
    onReceive(0x2);

    assertFramebufferIsEmpty();
}

TEST(I2C, SetBrightness) {
    onStart();
    onReceive(0x3);
    onReceive(1);

    ASSERT_EQ(1, display::_brightness);

    onStart();
    onReceive(0x3);
    onReceive(4);

    ASSERT_EQ(4, display::_brightness);
}

TEST(I2C, SetUpperBar) {
    display::clear();

    // all segments enabled
    onStart();
    onReceive(7);
    onReceive(0xff);
    onReceive(0xff);
    onReceive(0x0f);
    ASSERT_EQ(0x0fffff, display::_upperBarBuffer);

    // all segments disabled
    onStart();
    onReceive(7);
    onReceive(0);
    onReceive(0);
    onReceive(0);
    ASSERT_EQ(0, display::_upperBarBuffer);

    onStart();
    onReceive(7);
    onReceive(0xab);
    onReceive(0xcd);
    onReceive(0x0e);

    ASSERT_EQ(0x0ecdab, display::_upperBarBuffer);
}

TEST(I2C, DrawGraphics) {
    display::clear();

    onStart();
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
}

TEST(I2C, WriteStaticText) {
    display::clear();

    onStart();
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

}

TEST(I2C, WriteScrollingText) {
    display::clear();

    onStart();
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
}
