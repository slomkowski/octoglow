#pragma once

#include "protocol.hpp"

#include <cstdint>

namespace octoglow {
    namespace geiger {
        namespace magiceye {

            void init();

            void tick();

            void configure(protocol::EyeConfiguration &configuration);

            void setAdcValue(uint8_t v);

            void setEnabled(bool enabled);

            extern protocol::EyeInverterState state;
            extern protocol::EyeDisplayMode animationMode;

            namespace hd {
                void enableHeater1(bool enabled);

                void enableHeater2(bool enabled);
            }
        }
    }
}
