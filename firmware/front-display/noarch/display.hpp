#pragma once

#include <stdint.h>

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
                         char *text,
                         bool textInProgramSpace = false);

    inline void writeStaticText_P(const uint8_t position,
                                  const uint8_t maxLength,
                                  const char *const progmemText) {
        writeStaticText(position, maxLength, const_cast<char *const>(progmemText), true);
    }

    void writeScrollingText(uint8_t slotNumber,
                            uint8_t position,
                            uint8_t windowLength,
                            char *text,
                            bool textInProgramSpace = false);

    inline void writeScrollingText_P(const uint8_t slotNumber,
                                     const uint8_t position,
                                     const uint8_t windowLength,
                                     const char *const progmemText) {
        writeScrollingText(slotNumber, position, windowLength, const_cast<char *const>(progmemText), true);
    }

    void drawGraphics(uint8_t columnPosition,
                      uint8_t columnLength,
                      bool sumWithText,
                      uint8_t *columnBuffer,
                      bool bufferInProgramSpace = false);

    inline void drawGraphics_P(const uint8_t columnPosition,
                               const uint8_t columnLength,
                               const bool sumWithText,
                               const uint8_t *const progmemColumnBuffer) {
        drawGraphics(columnPosition, columnLength, sumWithText,
                     const_cast<uint8_t *const>(progmemColumnBuffer), true);
    }

    void setUpperBarContent(uint32_t content);

    extern uint8_t _frameBuffer[];

    extern uint32_t _upperBarBuffer;

    extern uint8_t _brightness;

    namespace hd {
        void displayPool();
    }
}
