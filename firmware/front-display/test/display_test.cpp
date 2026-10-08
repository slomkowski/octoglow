#include "display.hpp"
#include "Font5x7.hpp"
#include "protocol.hpp"
#include "stubs.hpp"

#include <gtest/gtest.h>

#include <cstring>
#include <functional>
#include <string>
#include <vector>

using namespace octoglow::front_display;
using namespace octoglow::front_display::display;

constexpr int FRAMEBUFFER_SIZE = NUM_OF_CHARACTERS * COLUMNS_IN_CHARACTER;

static const uint8_t *glyph(const uint8_t code) {
    return Font5x7 + COLUMNS_IN_CHARACTER * (code - ' ');
}

static void assertGlyphAt(const uint8_t position, const uint8_t code) {
    for (int c = 0; c < COLUMNS_IN_CHARACTER; ++c) {
        ASSERT_EQ(glyph(code)[c], _frameBuffer[COLUMNS_IN_CHARACTER * position + c])
                                    << "position " << int(position) << ", column " << c;
    }
}

static void assertCharacterFilledWith(const uint8_t position, const uint8_t value) {
    for (int c = 0; c < COLUMNS_IN_CHARACTER; ++c) {
        ASSERT_EQ(value, _frameBuffer[COLUMNS_IN_CHARACTER * position + c])
                                    << "position " << int(position) << ", column " << c;
    }
}

static std::vector<uint8_t> collectCodes(const char *str, const uint8_t maxLength, const bool inProgramSpace = false) {
    std::vector<uint8_t> codes;
    _forEachUtf8character(str, inProgramSpace, maxLength, &codes,
                          [](void *s, const uint8_t curPos, const uint8_t code) -> void {
                              auto *v = static_cast<std::vector<uint8_t> *>(s);
                              ASSERT_EQ(v->size(), curPos);
                              v->push_back(code);
                          });
    return codes;
}

// _scrollingSlots is declared without size, so it cannot be iterated directly
static std::vector<std::reference_wrapper<_ScrollingSlot>> slots() {
    return {_scrollingSlots[0], _scrollingSlots[1], _scrollingSlots[2]};
}

class DisplayTest : public ::testing::Test {
protected:
    void SetUp() override {
        test::milliseconds = 0;
        clear();
        setBrightness(MAX_BRIGHTNESS);
        for (_ScrollingSlot &slot: slots()) {
            slot.currentShift = 0;
        }
        test::displayPoolCalls = 0;
    }
};

TEST_F(DisplayTest, Clear) {
    writeStaticText(5, 10, "lorem ipsum dolor ");
    setUpperBarContent(0xabcde);
    writeScrollingText(0, 0, 3, "long scrolling text");

    clear();

    for (int i = 0; i < FRAMEBUFFER_SIZE; ++i) {
        ASSERT_EQ(0, _frameBuffer[i]);
    }
    ASSERT_EQ(0u, _upperBarBuffer);
    for (_ScrollingSlot &slot: slots()) {
        ASSERT_EQ(0, slot.length);
        ASSERT_EQ(0, slot.textLength);
    }
}

TEST_F(DisplayTest, SetBrightness) {
    for (uint8_t b = 0; b <= MAX_BRIGHTNESS; ++b) {
        setBrightness(b);
        ASSERT_EQ(b, _brightness);
    }

    setBrightness(MAX_BRIGHTNESS + 1);
    ASSERT_EQ(MAX_BRIGHTNESS, _brightness);

    setBrightness(255);
    ASSERT_EQ(MAX_BRIGHTNESS, _brightness);
}

TEST_F(DisplayTest, SetUpperBarContentKeepsOnly20Bits) {
    setUpperBarContent(0x12345);
    ASSERT_EQ(0x12345u, _upperBarBuffer);

    setUpperBarContent(0xffffffff);
    ASSERT_EQ(0xfffffu, _upperBarBuffer);

    setUpperBarContent(0xfff00000);
    ASSERT_EQ(0u, _upperBarBuffer);
}

TEST_F(DisplayTest, FontGlyphs) {
    // sanity check of the font indexing
    const uint8_t space[] = {0, 0, 0, 0, 0};
    const uint8_t letterA[] = {0x7E, 0x09, 0x09, 0x09, 0x7E};
    const uint8_t digitZero[] = {0x3E, 0x51, 0x49, 0x45, 0x3E};
    const uint8_t degreeSign[] = {0x00, 0x07, 0x05, 0x07, 0x00};

    ASSERT_EQ(0, memcmp(space, glyph(' '), COLUMNS_IN_CHARACTER));
    ASSERT_EQ(0, memcmp(letterA, glyph('A'), COLUMNS_IN_CHARACTER));
    ASSERT_EQ(0, memcmp(digitZero, glyph('0'), COLUMNS_IN_CHARACTER));
    ASSERT_EQ(0, memcmp(degreeSign, glyph(UNICODE_START_CODE + 18), COLUMNS_IN_CHARACTER));
}

TEST_F(DisplayTest, ForEachUtf8character) {
    ASSERT_EQ(5u, collectCodes("lorem", 5).size());
    ASSERT_EQ(5u, collectCodes("lorem", 9).size());
    ASSERT_EQ(3u, collectCodes("lorem", 3).size());
    ASSERT_EQ(0u, collectCodes("lorem", 0).size());
    ASSERT_EQ(0u, collectCodes("", 10).size());
    ASSERT_EQ(5u, collectCodes("lorąę", 9).size());

    const auto codes = collectCodes("aZ ~", 10);
    ASSERT_EQ((std::vector<uint8_t>{'a', 'Z', ' ', '~'}), codes);
}

TEST_F(DisplayTest, ForEachUtf8characterMaxLengthCountsCharactersNotBytes) {
    const auto codes = collectCodes("ąęćx", 3);
    ASSERT_EQ(3u, codes.size());
    ASSERT_EQ(UNICODE_START_CODE + 1, codes[0]);
    ASSERT_EQ(UNICODE_START_CODE + 5, codes[1]);
    ASSERT_EQ(UNICODE_START_CODE + 3, codes[2]);
}

TEST_F(DisplayTest, ForEachUtf8characterNationalCharacters) {
    const auto codes = collectCodes("ĄąĆćĘęŁłŃńÓóŚśŹźŻż°", 50);

    ASSERT_EQ(19u, codes.size());
    for (uint8_t i = 0; i < codes.size(); ++i) {
        ASSERT_EQ(UNICODE_START_CODE + i, codes[i]) << "character " << int(i);
    }
}

TEST_F(DisplayTest, ForEachUtf8characterUnknownCharacter) {
    const auto codes = collectCodes("aéß€b", 50);

    // three-byte characters (like €) are not supported, so only check the two-byte ones
    ASSERT_EQ('a', codes[0]);
    ASSERT_EQ(INVALID_CHARACTER_CODE, codes[1]);
    ASSERT_EQ(INVALID_CHARACTER_CODE, codes[2]);
}

TEST_F(DisplayTest, ForEachUtf8characterProgramSpace) {
    static const char text[] PROGMEM = "zażółć";
    ASSERT_EQ(collectCodes(text, 20, false), collectCodes(text, 20, true));
}

TEST_F(DisplayTest, WriteStaticText) {
    writeStaticText(2, 3, "Ab1");

    assertCharacterFilledWith(0, 0);
    assertCharacterFilledWith(1, 0);
    assertGlyphAt(2, 'A');
    assertGlyphAt(3, 'b');
    assertGlyphAt(4, '1');
    assertCharacterFilledWith(5, 0);
}

TEST_F(DisplayTest, WriteStaticTextTruncatesToMaxLength) {
    memset(_frameBuffer, 0xff, FRAMEBUFFER_SIZE);

    writeStaticText(10, 2, "abcdef");

    assertCharacterFilledWith(9, 0xff);
    assertGlyphAt(10, 'a');
    assertGlyphAt(11, 'b');
    assertCharacterFilledWith(12, 0xff);
}

TEST_F(DisplayTest, WriteStaticTextClearsRestOfField) {
    memset(_frameBuffer, 0xff, FRAMEBUFFER_SIZE);

    writeStaticText(3, 5, "ab");

    assertCharacterFilledWith(2, 0xff);
    assertGlyphAt(3, 'a');
    assertGlyphAt(4, 'b');
    assertCharacterFilledWith(5, 0);
    assertCharacterFilledWith(6, 0);
    assertCharacterFilledWith(7, 0);
}

TEST_F(DisplayTest, WriteStaticTextDoesNotTouchCharacterAfterField) {
    memset(_frameBuffer, 0xff, FRAMEBUFFER_SIZE);

    writeStaticText(3, 5, "ab");

    assertCharacterFilledWith(8, 0xff);
}

TEST_F(DisplayTest, WriteStaticTextEmptyClearsWholeField) {
    memset(_frameBuffer, 0xff, FRAMEBUFFER_SIZE);

    writeStaticText(3, 2, "");

    assertCharacterFilledWith(2, 0xff);
    assertCharacterFilledWith(3, 0);
    assertCharacterFilledWith(4, 0);
    assertCharacterFilledWith(5, 0xff);
}

TEST_F(DisplayTest, WriteStaticTextNationalCharacters) {
    writeStaticText(0, 4, "Łódź");

    assertGlyphAt(0, UNICODE_START_CODE + 6);
    assertGlyphAt(1, UNICODE_START_CODE + 11);
    assertGlyphAt(2, 'd');
    assertGlyphAt(3, UNICODE_START_CODE + 15);
}

TEST_F(DisplayTest, WriteStaticTextLastPosition) {
    writeStaticText(NUM_OF_CHARACTERS - 2, 2, "xy");

    assertGlyphAt(NUM_OF_CHARACTERS - 2, 'x');
    assertGlyphAt(NUM_OF_CHARACTERS - 1, 'y');
}

TEST_F(DisplayTest, WriteStaticTextProgramSpace) {
    static const char text[] PROGMEM = "ółw";

    writeStaticText_P(7, 3, text);

    assertGlyphAt(7, UNICODE_START_CODE + 11);
    assertGlyphAt(8, UNICODE_START_CODE + 7);
    assertGlyphAt(9, 'w');
}

TEST_F(DisplayTest, DrawGraphicsOverride) {
    memset(_frameBuffer, 0x0f, FRAMEBUFFER_SIZE);
    const uint8_t columns[] = {0xf0, 0x00, 0xaa};

    drawGraphics(7, sizeof(columns), false, columns);

    ASSERT_EQ(0x0f, _frameBuffer[6]);
    ASSERT_EQ(0xf0, _frameBuffer[7]);
    ASSERT_EQ(0x00, _frameBuffer[8]);
    ASSERT_EQ(0xaa, _frameBuffer[9]);
    ASSERT_EQ(0x0f, _frameBuffer[10]);
}

TEST_F(DisplayTest, DrawGraphicsSumWithText) {
    memset(_frameBuffer, 0x0f, FRAMEBUFFER_SIZE);
    const uint8_t columns[] = {0xf0, 0x00, 0xaa};

    drawGraphics(7, sizeof(columns), true, columns);

    ASSERT_EQ(0x0f, _frameBuffer[6]);
    ASSERT_EQ(0xff, _frameBuffer[7]);
    ASSERT_EQ(0x0f, _frameBuffer[8]);
    ASSERT_EQ(0xaf, _frameBuffer[9]);
    ASSERT_EQ(0x0f, _frameBuffer[10]);
}

TEST_F(DisplayTest, DrawGraphicsWholeFramebuffer) {
    uint8_t columns[FRAMEBUFFER_SIZE];
    for (int i = 0; i < FRAMEBUFFER_SIZE; ++i) {
        columns[i] = i;
    }

    drawGraphics(0, FRAMEBUFFER_SIZE, false, columns);

    ASSERT_EQ(0, memcmp(columns, _frameBuffer, FRAMEBUFFER_SIZE));
}

TEST_F(DisplayTest, DrawGraphicsProgramSpace) {
    static const uint8_t columns[] PROGMEM = {1, 2, 3, 4};

    drawGraphics_P(100, sizeof(columns), false, columns);

    ASSERT_EQ(0, memcmp(columns, _frameBuffer + 100, sizeof(columns)));
}

TEST_F(DisplayTest, WriteScrollingTextShorterThanWindowFallsBackToStatic) {
    writeScrollingText(1, 4, 6, "abc");

    assertGlyphAt(4, 'a');
    assertGlyphAt(5, 'b');
    assertGlyphAt(6, 'c');
    assertCharacterFilledWith(7, 0);

    ASSERT_EQ(0, _scrollingSlots[1].length);
    ASSERT_EQ(0, _scrollingSlots[1].textLength);
}

TEST_F(DisplayTest, WriteScrollingTextEqualToWindowFallsBackToStatic) {
    writeScrollingText(0, 0, 3, "abc");

    assertGlyphAt(0, 'a');
    assertGlyphAt(2, 'c');
    ASSERT_EQ(0, _scrollingSlots[0].length);
}

TEST_F(DisplayTest, WriteScrollingTextConfiguresSlot) {
    writeScrollingText(2, 3, 4, "zażółć gęślą");

    const auto &slot = _scrollingSlots[2];
    ASSERT_EQ(3, slot.startPosition);
    ASSERT_EQ(4, slot.length);
    ASSERT_EQ(0, slot.currentShift);
    ASSERT_EQ(12, slot.textLength);
    ASSERT_EQ(collectCodes("zażółć gęślą", 100),
              std::vector<uint8_t>(slot.convertedText, slot.convertedText + slot.textLength));

    // nothing is drawn until the display is pooled
    for (int i = 0; i < FRAMEBUFFER_SIZE; ++i) {
        ASSERT_EQ(0, _frameBuffer[i]);
    }
}

TEST_F(DisplayTest, WriteScrollingTextResetsShift) {
    writeScrollingText(0, 0, 2, "abcdef");
    _scrollingSlots[0].currentShift = 13;

    writeScrollingText(0, 0, 2, "ghijkl");

    ASSERT_EQ(0, _scrollingSlots[0].currentShift);
}

TEST_F(DisplayTest, WriteScrollingTextSlotNumberWrapsAround) {
    writeScrollingText(protocol::scroll::NUMBER_OF_SLOTS + 1, 7, 2, "abcdef");

    ASSERT_EQ(7, _scrollingSlots[1].startPosition);
    ASSERT_EQ(6, _scrollingSlots[1].textLength);
    ASSERT_EQ(0, _scrollingSlots[0].textLength);
    ASSERT_EQ(0, _scrollingSlots[2].textLength);
}

TEST_F(DisplayTest, WriteScrollingTextTruncatedToSlotCapacity) {
    const std::string longText(200, 'x');

    writeScrollingText(0, 0, 10, longText.c_str());
    writeScrollingText(1, 0, 10, longText.c_str());
    writeScrollingText(2, 0, 10, longText.c_str());

    ASSERT_EQ(protocol::scroll::SLOT0_MAX_LENGTH, _scrollingSlots[0].textLength);
    ASSERT_EQ(protocol::scroll::SLOT1_MAX_LENGTH, _scrollingSlots[1].textLength);
    ASSERT_EQ(protocol::scroll::SLOT2_MAX_LENGTH, _scrollingSlots[2].textLength);
}

TEST_F(DisplayTest, WriteScrollingTextShortTextDisablesActiveSlot) {
    writeScrollingText(0, 0, 3, "long text");
    ASSERT_EQ(3, _scrollingSlots[0].length);

    writeScrollingText(0, 0, 3, "ab");

    ASSERT_EQ(0, _scrollingSlots[0].length);
    ASSERT_EQ(0, _scrollingSlots[0].textLength);
    assertGlyphAt(0, 'a');
    assertGlyphAt(1, 'b');
    assertCharacterFilledWith(2, 0);
}

TEST_F(DisplayTest, WriteScrollingTextProgramSpace) {
    static const char text[] PROGMEM = "scrolling ółw";

    writeScrollingText_P(0, 0, 5, text);

    ASSERT_EQ(13, _scrollingSlots[0].textLength);
    ASSERT_EQ(UNICODE_START_CODE + 11, _scrollingSlots[0].convertedText[10]);
}

/*
 * Reference model of the scrolling: the text followed by two spaces is repeated endlessly, each character
 * is rendered as its 5 columns followed by 1 empty column. The shift counts only the glyph columns.
 */
static std::vector<uint8_t> expectedScrollingWindow(const std::vector<uint8_t> &codes,
                                                    const uint16_t shift,
                                                    const uint8_t windowLength) {
    std::vector<uint8_t> loop = codes;
    loop.push_back(' ');
    loop.push_back(' ');

    std::vector<uint8_t> window;
    for (unsigned i = (shift / COLUMNS_IN_CHARACTER) * (COLUMNS_IN_CHARACTER + 1) + shift % COLUMNS_IN_CHARACTER;
         window.size() < windowLength * COLUMNS_IN_CHARACTER; ++i) {
        const uint8_t code = loop[(i / (COLUMNS_IN_CHARACTER + 1)) % loop.size()];
        const uint8_t column = i % (COLUMNS_IN_CHARACTER + 1);
        window.push_back(column < COLUMNS_IN_CHARACTER ? glyph(code)[column] : 0);
    }
    return window;
}

TEST_F(DisplayTest, ScrollingMatchesReferenceModel) {
    constexpr uint8_t START = 5;
    constexpr uint8_t WINDOW = 4;
    const char *text = "Ala ma kota°";
    const auto codes = collectCodes(text, 100);

    writeScrollingText(0, START, WINDOW, text);
    auto &slot = _scrollingSlots[0];

    const unsigned period = COLUMNS_IN_CHARACTER * (codes.size() + 2);
    for (unsigned shift = 0; shift < 2 * period; ++shift) {
        ASSERT_EQ(shift % period, slot.currentShift);

        slot.scrollAndLoadIntoFramebuffer();

        const auto expected = expectedScrollingWindow(codes, shift % period, WINDOW);
        const std::vector<uint8_t> actual(_frameBuffer + COLUMNS_IN_CHARACTER * START,
                                          _frameBuffer + COLUMNS_IN_CHARACTER * (START + WINDOW));
        ASSERT_EQ(expected, actual) << "shift " << shift;

        // the area before the window is never touched
        for (int i = 0; i < COLUMNS_IN_CHARACTER * START; ++i) {
            ASSERT_EQ(0, _frameBuffer[i]);
        }
    }
}

// the empty column after the glyph must not be written when the glyph ends exactly at the end of the window
TEST_F(DisplayTest, ScrollingDoesNotWriteAfterWindow) {
    constexpr uint8_t WINDOW = 3;
    writeScrollingText(0, 0, WINDOW, "abcdefgh");
    auto &slot = _scrollingSlots[0];

    for (int shift = 0; shift < COLUMNS_IN_CHARACTER * 10; ++shift) {
        memset(_frameBuffer + COLUMNS_IN_CHARACTER * WINDOW, 0xff, COLUMNS_IN_CHARACTER);

        slot.scrollAndLoadIntoFramebuffer();

        assertCharacterFilledWith(WINDOW, 0xff);
    }
}

TEST_F(DisplayTest, ScrollingDisabledSlotDoesNothing) {
    memset(_frameBuffer, 0xab, FRAMEBUFFER_SIZE);

    for (_ScrollingSlot &slot: slots()) {
        slot.scrollAndLoadIntoFramebuffer();
        ASSERT_EQ(0, slot.currentShift);
    }

    for (int i = 0; i < FRAMEBUFFER_SIZE; ++i) {
        ASSERT_EQ(0xab, _frameBuffer[i]);
    }
}

TEST_F(DisplayTest, PoolCallsHardwareEveryTime) {
    for (int i = 1; i <= 1000; ++i) {
        pool();
        ASSERT_EQ(i, test::displayPoolCalls);
    }
}

TEST_F(DisplayTest, PoolScrollsEvery39Milliseconds) {
    writeScrollingText(0, 0, 2, "abcdef");
    writeScrollingText(2, 10, 3, "ghijklmn");

    // the number of calls doesn't matter, only the time
    for (int ms = 0; ms < 39; ++ms) {
        for (int i = 0; i < 100; ++i) {
            pool();
        }
        ASSERT_EQ(0, _scrollingSlots[0].currentShift) << ms << " ms";
        ++test::milliseconds;
    }

    pool();
    ASSERT_EQ(1, _scrollingSlots[0].currentShift);
    ASSERT_EQ(1, _scrollingSlots[2].currentShift);
    // the first scroll step renders shift 0
    assertGlyphAt(0, 'a');
    assertGlyphAt(10, 'g');

    pool();
    ASSERT_EQ(1, _scrollingSlots[0].currentShift);

    test::milliseconds += 38;
    pool();
    ASSERT_EQ(1, _scrollingSlots[0].currentShift);

    ++test::milliseconds;
    pool();
    ASSERT_EQ(2, _scrollingSlots[0].currentShift);
    ASSERT_EQ(2, _scrollingSlots[2].currentShift);
}

TEST_F(DisplayTest, PoolScrollsAcrossMillisecondsWrapAround) {
    test::milliseconds = 250;
    clear();
    writeScrollingText(0, 0, 2, "abcdef");

    test::milliseconds = static_cast<uint8_t>(250 + 38);
    pool();
    ASSERT_EQ(0, _scrollingSlots[0].currentShift);

    ++test::milliseconds;
    pool();
    ASSERT_EQ(1, _scrollingSlots[0].currentShift);
}

TEST_F(DisplayTest, PoolScrollsOnceAfterLongPause) {
    writeScrollingText(0, 0, 2, "abcdef");

    test::milliseconds += 200;
    pool();
    pool();
    pool();

    ASSERT_EQ(1, _scrollingSlots[0].currentShift);
}

TEST_F(DisplayTest, ClearRestartsScrollInterval) {
    writeScrollingText(0, 0, 2, "abcdef");
    test::milliseconds += 30;

    clear();
    writeScrollingText(0, 0, 2, "abcdef");
    test::milliseconds += 30;
    pool();

    ASSERT_EQ(0, _scrollingSlots[0].currentShift);
}

/*
 * Invalid input and data which doesn't fit on the display.
 */

static const uint8_t INVALID_CHARACTER_GLYPH[] = {0xff, 0x41, 0x41, 0x41, 0xff};

static void assertInvalidCharacterGlyphAt(const uint8_t position) {
    ASSERT_EQ(0, memcmp(INVALID_CHARACTER_GLYPH, _frameBuffer + COLUMNS_IN_CHARACTER * position, COLUMNS_IN_CHARACTER));
}

TEST_F(DisplayTest, WriteStaticTextUnknownCharacterIsDrawnAsEmptyBox) {
    writeStaticText(0, 2, "éx");

    assertInvalidCharacterGlyphAt(0);
    assertGlyphAt(1, 'x');
}

TEST_F(DisplayTest, ForEachUtf8characterControlCharactersAreInvalid) {
    const auto codes = collectCodes("a\nb\t\x01\x1f ", 10);

    ASSERT_EQ((std::vector<uint8_t>{'a', INVALID_CHARACTER_CODE, 'b', INVALID_CHARACTER_CODE,
                                    INVALID_CHARACTER_CODE, INVALID_CHARACTER_CODE, ' '}), codes);
}

TEST_F(DisplayTest, WriteStaticTextControlCharacterIsDrawnAsEmptyBox) {
    writeStaticText(0, 1, "\x01");

    assertInvalidCharacterGlyphAt(0);
}

TEST_F(DisplayTest, ForEachUtf8characterThreeAndFourByteCharactersAreSingleInvalidCharacters) {
    ASSERT_EQ((std::vector<uint8_t>{'a', INVALID_CHARACTER_CODE, 'b'}), collectCodes("a€b", 10));
    ASSERT_EQ((std::vector<uint8_t>{'a', INVALID_CHARACTER_CODE, 'b'}), collectCodes("a😀b", 10));
}

TEST_F(DisplayTest, ForEachUtf8characterStrayContinuationByteIsInvalid) {
    ASSERT_EQ((std::vector<uint8_t>{'a', INVALID_CHARACTER_CODE, 'b'}), collectCodes("a\x80" "b", 10));
}

TEST_F(DisplayTest, ForEachUtf8characterDoesNotReadPastTerminatorOfTruncatedSequence) {
    // the bytes after the terminator must never be decoded
    const char twoByteTruncated[] = {'a', '\xc4', 0, 'X', 'Y', 0};
    const char threeByteTruncated[] = {'a', '\xe2', '\x82', 0, 'X', 'Y', 0};
    const char euroAtTheEnd[] = {'a', '\xe2', '\x82', '\xac', 0, 'X', 'Y', 0};

    ASSERT_EQ((std::vector<uint8_t>{'a', INVALID_CHARACTER_CODE}), collectCodes(twoByteTruncated, 10));
    ASSERT_EQ((std::vector<uint8_t>{'a', INVALID_CHARACTER_CODE}), collectCodes(threeByteTruncated, 10));
    ASSERT_EQ((std::vector<uint8_t>{'a', INVALID_CHARACTER_CODE}), collectCodes(euroAtTheEnd, 10));
}

TEST_F(DisplayTest, ForEachUtf8characterTruncatedSequenceAtTheEndOfMemory) {
    // ASan detects reading past the end of this array
    const char *text = new char[2]{'\xc4', 0};

    ASSERT_EQ((std::vector<uint8_t>{INVALID_CHARACTER_CODE}), collectCodes(text, 10));

    delete[] text;
}

TEST_F(DisplayTest, WriteStaticTextFieldAtTheEndOfDisplay) {
    writeStaticText(NUM_OF_CHARACTERS - 2, 2, "a");

    assertGlyphAt(NUM_OF_CHARACTERS - 2, 'a');
    assertCharacterFilledWith(NUM_OF_CHARACTERS - 1, 0);
}

TEST_F(DisplayTest, WriteStaticTextFieldExceedingDisplayIsClipped) {
    memset(_frameBuffer, 0xff, FRAMEBUFFER_SIZE);

    writeStaticText(NUM_OF_CHARACTERS - 2, 10, "abcdef");

    assertCharacterFilledWith(NUM_OF_CHARACTERS - 3, 0xff);
    assertGlyphAt(NUM_OF_CHARACTERS - 2, 'a');
    assertGlyphAt(NUM_OF_CHARACTERS - 1, 'b');
}

TEST_F(DisplayTest, WriteStaticTextEmptyFieldExceedingDisplayIsClipped) {
    memset(_frameBuffer, 0xff, FRAMEBUFFER_SIZE);

    writeStaticText(NUM_OF_CHARACTERS - 2, 10, "");

    assertCharacterFilledWith(NUM_OF_CHARACTERS - 3, 0xff);
    assertCharacterFilledWith(NUM_OF_CHARACTERS - 2, 0);
    assertCharacterFilledWith(NUM_OF_CHARACTERS - 1, 0);
}

TEST_F(DisplayTest, WriteStaticTextOutsideDisplayIsIgnored) {
    memset(_frameBuffer, 0xff, FRAMEBUFFER_SIZE);
    const std::vector<uint8_t> before(_frameBuffer, _frameBuffer + FRAMEBUFFER_SIZE);

    writeStaticText(NUM_OF_CHARACTERS, 5, "abc");
    writeStaticText(255, 255, "abc");

    ASSERT_EQ(before, std::vector<uint8_t>(_frameBuffer, _frameBuffer + FRAMEBUFFER_SIZE));
}

TEST_F(DisplayTest, DrawGraphicsExceedingDisplayIsClipped) {
    const uint8_t columns[] = {1, 2, 3, 4, 5};

    drawGraphics(FRAMEBUFFER_SIZE - 2, sizeof(columns), false, columns);

    ASSERT_EQ(0, _frameBuffer[FRAMEBUFFER_SIZE - 3]);
    ASSERT_EQ(1, _frameBuffer[FRAMEBUFFER_SIZE - 2]);
    ASSERT_EQ(2, _frameBuffer[FRAMEBUFFER_SIZE - 1]);
}

TEST_F(DisplayTest, DrawGraphicsOutsideDisplayIsIgnored) {
    const uint8_t columns[] = {1, 2, 3, 4, 5};

    drawGraphics(FRAMEBUFFER_SIZE, sizeof(columns), false, columns);
    drawGraphics(250, sizeof(columns), true, columns);

    for (int i = 0; i < FRAMEBUFFER_SIZE; ++i) {
        ASSERT_EQ(0, _frameBuffer[i]);
    }
}

TEST_F(DisplayTest, ScrollingWindowExceedingDisplayIsClipped) {
    writeScrollingText(0, NUM_OF_CHARACTERS - 2, 5, "long scrolling text");
    auto &slot = _scrollingSlots[0];

    ASSERT_EQ(2, slot.length);

    for (int shift = 0; shift < COLUMNS_IN_CHARACTER * 30; ++shift) {
        slot.scrollAndLoadIntoFramebuffer();
    }
}

TEST_F(DisplayTest, ScrollingWindowAtTheEndOfDisplay) {
    writeScrollingText(0, NUM_OF_CHARACTERS - 3, 3, "abcdefgh");
    auto &slot = _scrollingSlots[0];

    for (int shift = 0; shift < COLUMNS_IN_CHARACTER * 30; ++shift) {
        slot.scrollAndLoadIntoFramebuffer();
    }
}

TEST_F(DisplayTest, WriteScrollingTextShortTextExceedingDisplayIsClipped) {
    memset(_frameBuffer, 0xff, FRAMEBUFFER_SIZE);

    writeScrollingText(0, NUM_OF_CHARACTERS - 2, 5, "a");

    assertGlyphAt(NUM_OF_CHARACTERS - 2, 'a');
    assertCharacterFilledWith(NUM_OF_CHARACTERS - 1, 0);
    ASSERT_EQ(0, _scrollingSlots[0].length);
}

TEST_F(DisplayTest, WriteScrollingTextLongerThanClippedWindowScrolls) {
    writeScrollingText(0, NUM_OF_CHARACTERS - 2, 5, "abc");

    ASSERT_EQ(2, _scrollingSlots[0].length);
    ASSERT_EQ(3, _scrollingSlots[0].textLength);
}

TEST_F(DisplayTest, WriteScrollingTextOutsideDisplayIsIgnored) {
    writeScrollingText(0, NUM_OF_CHARACTERS, 5, "long scrolling text");
    writeScrollingText(1, 200, 5, "abc");

    for (int shift = 0; shift < COLUMNS_IN_CHARACTER * 30; ++shift) {
        _scrollingSlots[0].scrollAndLoadIntoFramebuffer();
    }

    for (int i = 0; i < FRAMEBUFFER_SIZE; ++i) {
        ASSERT_EQ(0, _frameBuffer[i]);
    }
}
