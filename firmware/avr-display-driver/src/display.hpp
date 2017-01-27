#pragma once

#include <stdint.h>

namespace octoglow {
    namespace vfd_front {

        namespace display {
            void init();

            void clear();

            void pool();

            void setBrightness(const uint8_t brightness);

            void writeStaticText(const uint8_t position,
                                 const uint8_t maxLength,
                                 char *const text,
                                 const bool textInProgramSpace = false);

            inline void writeStaticText_P(const uint8_t position,
                                          const uint8_t maxLength,
                                          const char *const progmemText) {
                writeStaticText(position, maxLength, const_cast<char *const>(progmemText), true);
            }

            void writeScrollingText(const uint8_t slotNumber,
                                    const uint8_t position,
                                    const uint8_t windowLength,
                                    char *const text,
                                    const bool textInProgramSpace = false);

            inline void writeScrollingText_P(const uint8_t slotNumber,
                                             const uint8_t position,
                                             const uint8_t windowLength,
                                             const char *const progmemText) {
                writeScrollingText(slotNumber, position, windowLength, const_cast<char *const>(progmemText), true);
            }

            void drawGraphics(const uint8_t columnPosition,
                              const uint8_t columnLength,
                              const bool sumWithText,
                              uint8_t *const columnBuffer,
                              const bool bufferInProgramSpace = false);

            inline void drawGraphics_P(const uint8_t columnPosition,
                                       const uint8_t columnLength,
                                       const bool sumWithText,
                                       const uint8_t *const progmemColumnBuffer) {
                drawGraphics(columnPosition, columnLength, sumWithText,
                             const_cast<uint8_t *const>(progmemColumnBuffer), true);
            }

            void setUpperBarContent(uint32_t content);
        }
    }
}