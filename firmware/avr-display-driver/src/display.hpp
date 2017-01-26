#pragma once

#include <stdint.h>

namespace octoglow {
    namespace vfd_front {

        namespace display {
            void init();

            void pool();

            void setBrightness(const uint8_t brightness);

            void writeStaticText(const uint8_t position,
                                 const uint8_t maxLength,
                                 char *const text);

            void writeStaticText_P(const uint8_t position,
                                   const uint8_t maxLength,
                                   const char *const progmemText);

            void writeScrollingText(const uint8_t slotNumber,
                                    const uint8_t position,
                                    const uint8_t windowLength,
                                    char *const text);

            void writeScrollingText_P(const uint8_t slotNumber,
                                      const uint8_t position,
                                      const uint8_t windowLength,
                                      const char *const progmemText);
        }
    }
}