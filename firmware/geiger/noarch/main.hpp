#pragma once

#include <inttypes.h>

namespace octoglow::geiger {
    /**
         * Defines how often the main tick call is called. tick() functions of various modules assume
         * that they are called this often.
         */
    constexpr uint16_t TICK_TIMER_FREQ = 100; // 50 Hz

    extern volatile bool timerTicked;
}
