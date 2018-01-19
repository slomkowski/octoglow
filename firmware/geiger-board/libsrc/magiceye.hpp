#pragma once

#include <cstdint>

namespace octoglow {
    namespace geiger {
        namespace magiceye {

            enum class EyeState : uint16_t {
                DISABLED,
                PREHEATING,
                POSTHEATING,
                RUNNING
            };

            void init();

            void tick();

            void setAdcValue(uint8_t v);

            void setEnabled(bool enabled);

            EyeState getState();

            namespace hd {
                void enablePreheatRelay(bool enabled);

                void enableMainRelay(bool enabled);
            }
        }
    }
}