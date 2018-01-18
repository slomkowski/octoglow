#pragma once

#include <cstdint>

namespace octoglow{
    namespace geiger {
        /**
         * Defines, how often main tick call is called. tick() functions of various modules assume
         * that they are called this often.
         */
        constexpr uint16_t TICK_TIMER_FREQ = 50; // 50 Hz
    }
}
