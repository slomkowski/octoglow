#include "i2c-slave.hpp"
#include "display.hpp"

#include <gtest/gtest.h>
#include <iostream>
#include <cstdint>

using namespace octoglow::front_display;
using namespace octoglow::front_display::i2c;

static void assertReadIs(const uint8_t expected) {
    uint8_t readValue;
    onTransmit(&readValue);
    std::cout << "Read value: " << static_cast<int>(readValue) << std::endl;

    ASSERT_EQ(expected, readValue);
}

static void assertFramebufferIsEmpty() {
    for (int i = 0; i < display::NUM_OF_CHARACTERS * display::COLUMNS_IN_CHARACTER; ++i) {
        ASSERT_EQ(0, display::_frameBuffer[i]);
    }
}

uint8_t i2c::crc8ccittUpdate(const uint8_t inCrc, const uint8_t inData) {
    uint8_t data = inCrc ^ inData;
    for (int i = 0; i < 8; i++) {
        if ((data & 0x80) != 0) {
            data <<= 1;
            data ^= 0x07;
        } else {
            data <<= 1;
        }
    }
    return data;
}

TEST(I2C, GetEncoderState) {
    onStart();
    onReceive(0x7);
    onReceive(0x1);

    onStart();
    assertReadIs(152);
    assertReadIs(1);
    assertReadIs(0);
    assertReadIs(255);

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
