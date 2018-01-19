#pragma once

#include <cstdint>

namespace octoglow {
    namespace geiger {
        namespace inverter {
            void init();

            void tick();

            void setEyeEnabled(bool enabled);
        }
    }
}
