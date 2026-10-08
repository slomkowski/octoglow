#include "protocol.hpp"
#include "Font5x7.hpp"
#include "display.hpp"
#include "main.hpp"

#include <string.h>

using namespace octoglow::front_display::display;
using namespace octoglow::front_display::protocol;

constexpr uint8_t LOOP_NUMBER_OF_SPACES = 2;

static uint16_t scrollingWaitCounter = 0;

constexpr uint8_t SCROL_TEXT_BUFFER_TRAILING_OVERHEAD = 4;
static uint8_t scrolTextBuffer0[scroll::SLOT0_MAX_LENGTH + SCROL_TEXT_BUFFER_TRAILING_OVERHEAD];
static uint8_t scrolTextBuffer1[scroll::SLOT1_MAX_LENGTH + SCROL_TEXT_BUFFER_TRAILING_OVERHEAD];
static uint8_t scrolTextBuffer2[scroll::SLOT2_MAX_LENGTH + SCROL_TEXT_BUFFER_TRAILING_OVERHEAD];

namespace octoglow::front_display::display {
    uint8_t _frameBuffer[NUM_OF_CHARACTERS * COLUMNS_IN_CHARACTER];
    uint32_t _upperBarBuffer = 0l;
    uint8_t _brightness = MAX_BRIGHTNESS;

    _ScrollingSlot _scrollingSlots[3] = {
            {0, 0, 0, 0, scroll::SLOT0_MAX_LENGTH, scrolTextBuffer0},
            {0, 0, 0, 0, scroll::SLOT1_MAX_LENGTH, scrolTextBuffer1},
            {0, 0, 0, 0, scroll::SLOT2_MAX_LENGTH, scrolTextBuffer2}
    };

    static_assert(sizeof(_scrollingSlots) / sizeof(_scrollingSlots[0]) == scroll::NUMBER_OF_SLOTS,
                  "slot number doesn't match");
}


void _ScrollingSlot::clear() {
    startPosition = 0;
    length = 0;
    textLength = 0;
}

void _ScrollingSlot::scrollAndLoadIntoFramebuffer() {
    if (this->textLength == 0 or this->length == 0) {
        return;
    }

    uint8_t characterOffset = this->currentShift / COLUMNS_IN_CHARACTER;
    uint8_t columnOffset = this->currentShift % COLUMNS_IN_CHARACTER;
    uint8_t charactersSkpLines = 0;

    for (uint8_t p = 0; p + charactersSkpLines < this->length * COLUMNS_IN_CHARACTER; ++p) {

        const uint8_t op = characterOffset;
        uint8_t character;

        if (op < this->textLength) {
            character = this->convertedText[op];
        } else if (op - this->textLength < LOOP_NUMBER_OF_SPACES) {
            character = ' ';
        } else {
            character = this->convertedText[(op - LOOP_NUMBER_OF_SPACES) % this->textLength];
        }

        const uint8_t frameBufferColumn = COLUMNS_IN_CHARACTER * this->startPosition + p;

        _frameBuffer[frameBufferColumn + charactersSkpLines]
                = pgm_read_byte(Font5x7 + COLUMNS_IN_CHARACTER * (character - ' ') + columnOffset);

        ++columnOffset;

        if (columnOffset == COLUMNS_IN_CHARACTER) {
            // the empty column between characters, unless the glyph ends exactly at the end of the window
            if (p + charactersSkpLines + 1 < this->length * COLUMNS_IN_CHARACTER) {
                _frameBuffer[frameBufferColumn + charactersSkpLines + 1] = 0;
            }
            columnOffset = 0;
            ++characterOffset;
            ++charactersSkpLines;
        }
    }

    this->currentShift++;

    if (this->currentShift == COLUMNS_IN_CHARACTER * (this->textLength + 2u)) {
        this->currentShift = 0;
    }
}

static const uint16_t utfMappings[] PROGMEM = {
        0x104, 0x105,
        0x106, 0x107,
        0x118, 0x119,
        0x141, 0x142,
        0x143, 0x144,
        0x0d3, 0x0f3,
        0x15a, 0x15b,
        0x179, 0x17a,
        0x17b, 0x17c,
        0xb0 // degree sign
};

static inline uint8_t readByte(const char *const str, const uint8_t idx, const bool stringInProgramSpace) {
    return stringInProgramSpace
           ? pgm_read_byte(str + idx)
           : reinterpret_cast<const uint8_t &>(str[idx]);
}

static uint8_t utf8SequenceLength(const uint8_t firstByte) {
    if ((firstByte & 0xe0) == 0xc0) {
        return 2;
    } else if ((firstByte & 0xf0) == 0xe0) {
        return 3;
    } else if ((firstByte & 0xf8) == 0xf0) {
        return 4;
    }
    // stray continuation byte or invalid value
    return 1;
}

void octoglow::front_display::display::_forEachUtf8character(const char *str,
                                 const bool stringInProgramSpace,
                                 const uint8_t maxLength,
                                 void *const userData,
                                 void (*callback)(void *, uint8_t, uint8_t)) {
    uint8_t strIdx = 0;
    uint8_t currPos = 0;

    while (currPos < maxLength) {
        const uint8_t singleAsciiValue = readByte(str, strIdx, stringInProgramSpace);

        if (singleAsciiValue == 0) {
            break;
        }

        if (singleAsciiValue < 0x80) {
            strIdx++;
            // control characters have no glyphs
            callback(userData, currPos, singleAsciiValue < ' ' ? INVALID_CHARACTER_CODE : singleAsciiValue);
        } else {
            const uint8_t sequenceLength = utf8SequenceLength(singleAsciiValue);

            // the sequence can be truncated by the end of the string
            uint8_t bytesRead = 1;
            while (bytesRead < sequenceLength && readByte(str, strIdx + bytesRead, stringInProgramSpace) != 0) {
                ++bytesRead;
            }

            uint8_t code = INVALID_CHARACTER_CODE;

            if (sequenceLength == 2 && bytesRead == 2) {
                const uint8_t secondByteValue = readByte(str, strIdx + 1, stringInProgramSpace);
                const uint16_t twoByteUnicodeValue = (secondByteValue & 0x3f) + ((singleAsciiValue & 0x1f) << 6);

                constexpr uint8_t NUMBER_OF_NATIONAL_CHARACTERS = sizeof(utfMappings) / sizeof(utfMappings[0]);
                for (uint8_t offset = 0; offset < NUMBER_OF_NATIONAL_CHARACTERS; ++offset) {
                    if (pgm_read_word(&utfMappings[offset]) == twoByteUnicodeValue) {
                        code = UNICODE_START_CODE + offset;
                        break;
                    }
                }
            }

            strIdx += bytesRead;

            callback(userData, currPos, code);

            if (bytesRead != sequenceLength) {
                break;
            }
        }

        ++currPos;
    }
}


void octoglow::front_display::display::writeStaticText(const uint8_t position,
                                                       uint8_t maxLength,
                                                       const char *const text,
                                                       const bool textInProgramSpace) {
    if (position >= NUM_OF_CHARACTERS) {
        return;
    }

    if (maxLength > NUM_OF_CHARACTERS - position) {
        maxLength = NUM_OF_CHARACTERS - position;
    }

    struct LocalData {
        uint8_t startPosition;
        uint8_t textLength;
    } local{position, 0};

    _forEachUtf8character(text, textInProgramSpace, maxLength, &local,
                         [](void *s, const uint8_t curPos, const uint8_t code) -> void {
                             const auto ld = static_cast<LocalData *>(s);

                             memcpy_P(_frameBuffer +
                                      COLUMNS_IN_CHARACTER * (ld->startPosition + curPos),
                                      Font5x7 + COLUMNS_IN_CHARACTER * (code - ' '),
                                      COLUMNS_IN_CHARACTER);

                             ld->textLength = curPos + 1;
                         });

    if (maxLength > local.textLength) {
        memset(_frameBuffer + COLUMNS_IN_CHARACTER * (position + local.textLength),
               0,
               COLUMNS_IN_CHARACTER * (maxLength - local.textLength));
    }
}

void octoglow::front_display::display::writeScrollingText(const uint8_t slotNumber,
                                                          const uint8_t position,
                                                          const uint8_t windowLength,
                                                          const char *const text,
                                                          const bool textInProgramSpace) {

    _ScrollingSlot &slot = _scrollingSlots[slotNumber % scroll::NUMBER_OF_SLOTS];
    slot.startPosition = position;
    // the window is clipped to the display, the window outside of the display has zero length
    slot.length = position >= NUM_OF_CHARACTERS
                  ? 0
                  : (windowLength > NUM_OF_CHARACTERS - position ? NUM_OF_CHARACTERS - position : windowLength);
    slot.currentShift = 0;

    _forEachUtf8character(text, textInProgramSpace, slot.maxTextLength, &slot,
                         [](void *s, const uint8_t curPos, const uint8_t code) -> void {
                             static_cast<_ScrollingSlot *>(s)->convertedText[curPos] = code;
                             static_cast<_ScrollingSlot *>(s)->textLength = curPos + 1;
                         });

    if (slot.textLength <= slot.length) {
        // if the text is shorter than the window, fall back to static mode
        writeStaticText(slot.startPosition, slot.length, text, textInProgramSpace);

        // disable slot
        slot.length = 0;
        slot.textLength = 0;
    }
}

void octoglow::front_display::display::clear() {
    for (auto &scrollingSlot : _scrollingSlots) {
        scrollingSlot.clear();
    }

    scrollingWaitCounter = 0;

    _upperBarBuffer = 0;

    memset(_frameBuffer, 0,
           COLUMNS_IN_CHARACTER * NUM_OF_CHARACTERS);
}

void octoglow::front_display::display::pool() {

    if (scrollingWaitCounter == 300) {

        for (auto &scrollingSlot : _scrollingSlots) {
            scrollingSlot.scrollAndLoadIntoFramebuffer();
        }

        scrollingWaitCounter = 0;
    }

    ++scrollingWaitCounter;

    hd::displayPool();
}

void octoglow::front_display::display::setBrightness(const uint8_t brightness) {
    _brightness = brightness > MAX_BRIGHTNESS ? MAX_BRIGHTNESS : brightness;
}

void octoglow::front_display::display::drawGraphics(const uint8_t columnPosition,
                                                    uint8_t columnLength,
                                                    const bool sumWithText,
                                                    const uint8_t *const columnBuffer,
                                                    const bool bufferInProgramSpace) {
    constexpr uint8_t NUM_OF_COLUMNS = NUM_OF_CHARACTERS * COLUMNS_IN_CHARACTER;

    if (columnPosition >= NUM_OF_COLUMNS) {
        return;
    }

    if (columnLength > NUM_OF_COLUMNS - columnPosition) {
        columnLength = NUM_OF_COLUMNS - columnPosition;
    }

    for (uint8_t p = 0; p < columnLength; ++p) {
        const uint8_t columnContent = bufferInProgramSpace
                                      ? pgm_read_byte(columnBuffer + p)
                                      : columnBuffer[p];

        if (sumWithText) {
            _frameBuffer[columnPosition + p] |= columnContent;
        } else {
            _frameBuffer[columnPosition + p] = columnContent;
        }
    }
}

void octoglow::front_display::display::setUpperBarContent(const uint32_t content) {
    _upperBarBuffer = 0b11111111111111111111ul & content;
}
