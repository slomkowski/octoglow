#pragma once

#include <cstdint>

namespace octoglow {
    namespace geiger {
        namespace magiceye {

            enum class EyeState {
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
        }
    }
}