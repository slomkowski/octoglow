#pragma once

#include <cstdint>

namespace octoglow {
    namespace geiger {
        namespace inverter {
            void init();

            void loop();

            void setEyeEnabled(bool enabled);
        }
    }
}