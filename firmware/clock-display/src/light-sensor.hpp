#pragma once

#include "global.hpp"

namespace octoglow::vfd_clock::lightsensor {
    void init();

    uint16_t getMeasurement();
}