#pragma once

#include "protocol.hpp"
#include "inverter.hpp"

#include <inttypes.h>

namespace octoglow::geiger::magiceye {

    void init();

    void tick();

    void configure(const volatile protocol::EyeConfiguration &configuration);

    void setBrightness(uint8_t brightness);

    void setAdcValue(uint8_t v);

    void setEnabled(bool enabled);

    extern protocol::EyeInverterState state;
    extern protocol::EyeDisplayMode animationMode;

    namespace _private {
        constexpr uint16_t desiredAdcValues[] = {
            inverter::eyeAdcVal(0),
            inverter::eyeAdcVal(100),
            inverter::eyeAdcVal(140),
            inverter::eyeAdcVal(180),
            inverter::eyeAdcVal(210),
            inverter::eyeAdcVal(250)
        };

        static_assert(sizeof(desiredAdcValues) / sizeof(desiredAdcValues[0] ==  protocol::MAX_BRIGHTNESS + 1), "invalid number of brightness values");
    }

    namespace hd {
        void enableHeater1(bool enabled);

        void enableHeater2(bool enabled);
    }
}
