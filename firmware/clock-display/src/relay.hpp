#pragma once

#include "global.hpp"

namespace octoglow::vfd_clock::relay {
    enum class Relay : uint8_t {
        RELAY_1,
        RELAY_2
    };

    void init();

    void setState(Relay relay, bool enabled);
}
