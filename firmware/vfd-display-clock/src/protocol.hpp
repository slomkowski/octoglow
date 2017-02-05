#pragma once

#include <stdint.h>

namespace octoglow {
    namespace vfd_clock {
        namespace protocol {

            enum class Command : uint8_t {
                SET_DISPLAY_CONTENT = 0x1, // 4 ascii chars + dot content
                SET_RELAY,
            };

            struct DisplayContent {
                char characters[4];
                uint8_t dotState;
            }__attribute__((packed));

            static_assert(sizeof(DisplayContent) == 5);

            struct RelayState {
                char characters[4];
                uint8_t dotState;
            }__attribute__((packed));

            static_assert(sizeof(RelayState) == 2);
        }

    }
}
