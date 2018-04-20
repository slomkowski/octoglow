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
                void enableHeater1(bool enabled);

                void enableHeater2(bool enabled);
            }
        }
    }
}
