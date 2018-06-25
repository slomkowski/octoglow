#pragma once

#include "protocol.hpp"
#include "inverter.hpp"

#include <cstdint>

namespace octoglow {
    namespace geiger {
        namespace magiceye {

            void init();

            void tick();

            void configure(protocol::EyeConfiguration &configuration);

            void setBrightness(uint8_t brightness);

            void setAdcValue(uint8_t v);

            void setEnabled(bool enabled);

            extern protocol::EyeInverterState state;
            extern protocol::EyeDisplayMode animationMode;

            namespace _private {
                const uint16_t desiredAdcValues[] = {
                        octoglow::geiger::inverter::eyeAdcVal(0),
                        inverter::eyeAdcVal(100),
                        inverter::eyeAdcVal(140),
                        inverter::eyeAdcVal(180),
                        inverter::eyeAdcVal(210),
                        inverter::eyeAdcVal(250)
                };
            }

            namespace hd {
                void enableHeater1(bool enabled);

                void enableHeater2(bool enabled);
            }
        }
    }
}
