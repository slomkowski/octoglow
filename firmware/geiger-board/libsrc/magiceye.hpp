#pragma once

#include "protocol.hpp"

#include <cstdint>

namespace octoglow {
    namespace geiger {
        namespace magiceye {

            void init();

            void tick();

            void setEnabled(bool enabled);

            void setAdcValue(uint8_t v);

            void setControllerState(protocol::EyeControllerState state);

            protocol::EyeControllerState getControllerState();

            protocol::EyeInverterState getState();

            namespace hd {
                void enablePreheatRelay(bool enabled);

                void enableMainRelay(bool enabled);
            }

            uint8_t _animate(bool hasBeenGeigerCountInLastCycle);
        }
    }
}
