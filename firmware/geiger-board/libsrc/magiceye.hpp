#pragma once

#include "protocol.hpp"

#include <cstdint>

namespace octoglow {
    namespace geiger {
        namespace magiceye {

            void init();

            void tick();

            void setAdcValue(uint8_t v);

            void setEnabled(bool enabled);

            void setControllerState(protocol::EyeControllerState state);

            protocol::EyeInverterState getState();

            namespace hd {
                void enablePreheatRelay(bool enabled);

                void enableMainRelay(bool enabled);
            }
        }
    }
}