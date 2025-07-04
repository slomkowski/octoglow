#pragma once

#include "protocol.hpp"
#include "inverter.hpp"

#include <inttypes.h>

namespace octoglow::geiger::magiceye {

    void init();

    void tick();

    void configure(const volatile protocol::EyeConfiguration &configuration);

    void setDacOutputValue(uint8_t v);

    void setEnabled(bool enabled);

    extern protocol::EyeInverterState state;
    extern protocol::EyeDisplayMode animationMode;

    namespace hd {
        void enableHeater1(bool enabled);

        void enableHeater2(bool enabled);
    }
}
