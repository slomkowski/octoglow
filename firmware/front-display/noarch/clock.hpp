#pragma once

#include <inttypes.h>

namespace octoglow::front_display::clock {
    /**
     * Milliseconds since the start, wraps around every 256 ms. Counted by the 1 ms timer of the encoder.
     */
    uint8_t milliseconds();
}
