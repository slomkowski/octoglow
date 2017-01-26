#include "display.hpp"

#include "global.hpp"
#include "protocol.hpp"
#include "display-lowlevel.hpp"
#include "Font5x7.hpp"

#include <avr/pgmspace.h>
#include <string.h>

constexpr uint8_t LOOP_NUMBER_OF_SPACES = 2;

using namespace octoglow::vfd_front;

struct ScrollingSlot {

    uint8_t startPosition;
    uint8_t length;

    uint16_t currentShift;

    uint8_t textLength;
    const uint8_t maxTextLength;
    uint8_t *const convertedText;
};

static uint16_t scrollingWaitCounter = 0;

static uint8_t scrolTextBuffer0[scroll::SLOT0_MAX_LENGTH];
static uint8_t scrolTextBuffer1[scroll::SLOT1_MAX_LENGTH];
static uint8_t scrolTextBuffer2[scroll::SLOT2_MAX_LENGTH];

static ScrollingSlot scrollingSlots[3] = {
        {0, 0, 0, 0, scroll::SLOT0_MAX_LENGTH, scrolTextBuffer0},
        {0, 0, 0, 0, scroll::SLOT1_MAX_LENGTH, scrolTextBuffer1},
        {0, 0, 0, 0, scroll::SLOT2_MAX_LENGTH, scrolTextBuffer2}
};

static_assert(sizeof(scrollingSlots) / sizeof(scrollingSlots[0]) == scroll::NUMBER_OF_SLOTS,
              "slot number doesn't match");

static const uint16_t utfMappings[] PROGMEM = {
        0x104, 0x105,
        0x106, 0x107,
        0x118, 0x119,
        0x141, 0x142,
        0x143, 0x144,
        0x0d3, 0x0f3,
        0x15a, 0x15b,
        0x179, 0x17a,
        0x17b, 0x17c
};

static void forEachUtf8character(const char *str,
                                 const bool stringInProgramSpace,
                                 const uint8_t maxLength,
                                 void *const userData,
                                 void (*callback)(void *userData, uint8_t currentPosition, uint8_t characterCode)) {
    uint8_t strIdx = 0;
    uint8_t currPos = 0;

    while (currPos < maxLength) {
        const uint8_t singleAsciiValue = stringInProgramSpace
                                         ? pgm_read_byte(str + strIdx)
                                         : reinterpret_cast<const uint8_t & >(str[strIdx]);

        if (singleAsciiValue == 0) {
            break;
        }

        if (singleAsciiValue < 0x80) {
            strIdx++;
            callback(userData, currPos, singleAsciiValue);
        }
        else {
            const uint8_t secondByteValue = stringInProgramSpace
                                            ? pgm_read_byte(str + strIdx + 1)
                                            : reinterpret_cast<const uint8_t & >(str[strIdx + 1]);
            const uint16_t twoByteUnicodeValue = (secondByteValue & 0x3f) + ((singleAsciiValue & 0x1f) << 6);
            strIdx += 2;

            for (uint8_t offset = 0; offset < 18; ++offset) {
                if (pgm_read_word(&utfMappings[offset]) == twoByteUnicodeValue) {

                    callback(userData, currPos, 126 + offset);
                    break;
                }
            }
        }

        ++currPos;
    }
}


static void loadStaticText(const uint8_t startPosition,
                           const uint8_t maxLength,
                           const char *str,
                           const bool stringInProgramSpace) {

    struct LocalData {
        uint8_t startPosition;
        uint8_t lastPos;
    } local;

    local.startPosition = startPosition;
    local.lastPos = 0;

    forEachUtf8character(str, stringInProgramSpace, maxLength, &local,
                         [](void *s, uint8_t curPos, uint8_t code) -> void {
                             const auto ld = static_cast<LocalData *>(s);

                             memcpy_P(display_lowlevel::frameBuffer +
                                      display_lowlevel::COLUMNS_IN_CHARACTER * (ld->startPosition + curPos),
                                      Font5x7 + display_lowlevel::COLUMNS_IN_CHARACTER * (code - ' '),
                                      display_lowlevel::COLUMNS_IN_CHARACTER);

                             ld->lastPos = curPos;
                         });
    memset(display_lowlevel::frameBuffer + display_lowlevel::COLUMNS_IN_CHARACTER * (startPosition + local.lastPos + 1),
           0,
           display_lowlevel::COLUMNS_IN_CHARACTER * (maxLength - local.lastPos));
}

static void loadScrollingText(const uint8_t slotNumber,
                              const uint8_t startPosition,
                              const uint8_t windowLength,
                              const char *str,
                              const bool stringInProgramSpace) {

    ScrollingSlot &slot = scrollingSlots[slotNumber % scroll::NUMBER_OF_SLOTS];
    slot.startPosition = startPosition;
    slot.length = windowLength;
    slot.currentShift = 0;

    forEachUtf8character(str, stringInProgramSpace, slot.maxTextLength, &slot,
                         [](void *s, uint8_t curPos, uint8_t code) -> void {
                             static_cast<ScrollingSlot *>(s)->convertedText[curPos] = code;
                             static_cast<ScrollingSlot *>(s)->textLength = curPos + 1;
                         });

    if (slot.textLength <= slot.length) {
        // if text is shorter than the window, fall back to static mode
        loadStaticText(slot.startPosition, slot.length, str, stringInProgramSpace);

        // disable slot
        slot.length = 0;
        slot.textLength = 0;
    }
}

static void fillPixelsColumnMode(ScrollingSlot &slot) {

    if (slot.textLength == 0 or slot.length == 0) {
        return;
    }

    uint8_t characterOffset = slot.currentShift / display_lowlevel::COLUMNS_IN_CHARACTER;
    uint8_t columnOffset = slot.currentShift % display_lowlevel::COLUMNS_IN_CHARACTER;
    uint8_t charactersSkpLines = 0;

    for (uint16_t p = 0; p < slot.length * display_lowlevel::COLUMNS_IN_CHARACTER; ++p) {

        const uint8_t op = characterOffset;
        uint8_t character;

        if (op < slot.textLength) {
            character = slot.convertedText[op];
        } else if (op - slot.textLength < LOOP_NUMBER_OF_SPACES) {
            character = ' ';
        } else {
            character = slot.convertedText[(op - LOOP_NUMBER_OF_SPACES) % slot.textLength];
        }

        const uint8_t frameBufferColumn = display_lowlevel::COLUMNS_IN_CHARACTER * slot.startPosition + p;

        display_lowlevel::frameBuffer[frameBufferColumn + charactersSkpLines]
                = pgm_read_byte(Font5x7 + display_lowlevel::COLUMNS_IN_CHARACTER * (character - ' ') + columnOffset);

        ++columnOffset;

        if (columnOffset == display_lowlevel::COLUMNS_IN_CHARACTER) {
            display_lowlevel::frameBuffer[frameBufferColumn + charactersSkpLines + 1] = 0;
            columnOffset = 0;
            ++characterOffset;
            ++charactersSkpLines;
        }
    }

    slot.currentShift++;

    if (slot.currentShift == display_lowlevel::COLUMNS_IN_CHARACTER * (slot.textLength + 2u)) {
        slot.currentShift = 0;
    }
}


void ::octoglow::vfd_front::display::init() {
    display_lowlevel::init();
}

void ::octoglow::vfd_front::display::pool() {

    if (scrollingWaitCounter == 300) {

        for (uint8_t s = 0; s < scroll::NUMBER_OF_SLOTS; ++s) {
            fillPixelsColumnMode(scrollingSlots[s]);
        }

        scrollingWaitCounter = 0;
    }

    ++scrollingWaitCounter;

    display_lowlevel::displayPool();
}

void ::octoglow::vfd_front::display::writeStaticText(const uint8_t position,
                                                     const uint8_t maxLength,
                                                     char *const text) {
    loadStaticText(position, maxLength, text, false);
}

void ::octoglow::vfd_front::display::writeStaticText_P(const uint8_t position,
                                                       const uint8_t maxLength,
                                                       const char *const progmemText) {
    loadStaticText(position, maxLength, progmemText, true);
}

void ::octoglow::vfd_front::display::writeScrollingText(const uint8_t slotNumber,
                                                        const uint8_t position,
                                                        const uint8_t windowLength,
                                                        char *const text) {
    loadScrollingText(slotNumber, position, windowLength, text, false);
}

void ::octoglow::vfd_front::display::writeScrollingText_P(const uint8_t slotNumber,
                                                          const uint8_t position,
                                                          const uint8_t windowLength,
                                                          const char *const progmemText) {
    loadScrollingText(slotNumber, position, windowLength, progmemText, true);
}

void ::octoglow::vfd_front::display::setBrightness(const uint8_t brightness) {
    display_lowlevel::setBrightness(brightness);
}












