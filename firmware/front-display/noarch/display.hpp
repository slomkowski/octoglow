#pragma once

#include <inttypes.h>

namespace octoglow::front_display::display {

    constexpr uint8_t NUM_OF_CHARACTERS = 40;
    constexpr uint8_t COLUMNS_IN_CHARACTER = 5;

    constexpr uint8_t MAX_BRIGHTNESS = 5;

    void init();

    void clear();

    void pool();

    void setBrightness(uint8_t brightness);

    void writeStaticText(uint8_t position,
                         uint8_t maxLength,
                         const char *text,
                         bool textInProgramSpace = false);

    inline void writeStaticText_P(const uint8_t position,
                                  const uint8_t maxLength,
                                  const char *const progmemText) {
        writeStaticText(position, maxLength, const_cast<char *>(progmemText), true);
    }

    void writeScrollingText(uint8_t slotNumber,
                            uint8_t position,
                            uint8_t windowLength,
                            const char *text,
                            bool textInProgramSpace = false);

    inline void writeScrollingText_P(const uint8_t slotNumber,
                                     const uint8_t position,
                                     const uint8_t windowLength,
                                     const char *const progmemText) {
        writeScrollingText(slotNumber, position, windowLength, const_cast<char *>(progmemText), true);
    }

    void drawGraphics(uint8_t columnPosition,
                      uint8_t columnLength,
                      bool sumWithText,
                      const uint8_t *columnBuffer,
                      bool bufferInProgramSpace = false);

    inline void drawGraphics_P(const uint8_t columnPosition,
                               const uint8_t columnLength,
                               const bool sumWithText,
                               const uint8_t *const progmemColumnBuffer) {
        drawGraphics(columnPosition, columnLength, sumWithText,
                     const_cast<uint8_t *>(progmemColumnBuffer), true);
    }

    void setUpperBarContent(uint32_t content);

    void _forEachUtf8character(const char *str,
                               bool stringInProgramSpace,
                               uint8_t maxLength,
                               void *userData,
                               void (*callback)(void *, uint8_t, uint8_t));

    extern uint8_t _frameBuffer[];

    extern uint32_t _upperBarBuffer;

    extern uint8_t _brightness;

    struct _ScrollingSlot {

        uint8_t startPosition;
        uint8_t length;

        uint16_t currentShift;

        uint8_t textLength;
        const uint8_t maxTextLength;
        uint8_t *const convertedText;

        void clear();

        void scrollAndLoadIntoFramebuffer();
    };

    extern _ScrollingSlot _scrollingSlots[];

    namespace hd {
        void displayPool();
    }
}
