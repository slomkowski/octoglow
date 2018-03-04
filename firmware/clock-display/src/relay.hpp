#pragma once

#include <stdint-gcc.h>

namespace octoglow {
    namespace vfd_clock {
        namespace relay {

            enum class Relay : uint8_t {
                RELAY_1,
                RELAY_2
            };

            void init();

            void setState(Relay relay, bool enabled);
        }
    }
}
