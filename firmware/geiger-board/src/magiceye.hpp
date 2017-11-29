#pragma once

#include <cstdint>

namespace octoglow {
    namespace geiger {
        namespace magiceye {
            void init();

            void setAdcValue(uint8_t v);
        }
    }
}