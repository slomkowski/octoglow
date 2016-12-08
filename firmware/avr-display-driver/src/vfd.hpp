#pragma once

#include <stdint.h>

namespace vfd {
    void init();

    void pool();

    void setBrightness(const uint8_t brightness);

    void write(const char *text);
}
