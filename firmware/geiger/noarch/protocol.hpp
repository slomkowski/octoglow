#pragma once

#include <stdint.h>

namespace octoglow {
    namespace geiger {
        namespace protocol {

            enum class EyeInverterState : uint8_t {
                DISABLED,
                HEATING_LIMITED,
                HEATING_FULL,
                RUNNING
            };

            enum class EyeControllerState : uint8_t {
                ANIMATION,
                FIXED_VALUE
            };

            enum class Command : uint8_t {
                _UNDEFINED, // this is only used by state machine
                GET_DEVICE_STATE = 0x1,
                GET_GEIGER_STATE,
                CLEAN_GEIGER_STATE,
                SET_EYE_ENABLED,
                SET_EYE_MODE
            };

            struct DeviceState {
                uint16_t geigerVoltage;
                uint16_t eyeVoltage;

                uint8_t geigerPwmValue;
                uint8_t eyePwmValue;

                EyeInverterState eyeInverterState;
                EyeControllerState eyeControllerState;
            }__attribute__((packed));
            static_assert(sizeof(DeviceState) == 8, "invalid size");

            struct GeigerState {
                uint16_t numOfCountsCurrentCycle;
                uint16_t numOfCountsPreviousCycle;
                uint16_t cycleLength;
            }__attribute__((packed));
            static_assert(sizeof(GeigerState) == 6, "invalid size");
        }
    }
}
