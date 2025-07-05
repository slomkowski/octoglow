#pragma once

#include "protocol.hpp"

namespace octoglow::geiger::magiceye {
    void init();

    void tick();

    void configure(const volatile protocol::EyeConfiguration &configuration);

    void setDacOutputValue(uint8_t v);

    void setEnabled(bool enabled);

    extern volatile protocol::EyeInverterState state;
    extern volatile protocol::EyeDisplayMode animationMode;

    namespace hd {
        void enableHeater1(bool enabled);

        void enableHeater2(bool enabled);
    }
}
