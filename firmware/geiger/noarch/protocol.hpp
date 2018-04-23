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

            enum class EyeDisplayMode : uint8_t {
                ANIMATION,
                FIXED_VALUE
            };

            enum class Command : uint8_t {
                _UNDEFINED, // this is only used by state machine
                GET_DEVICE_STATE = 0x1,
                GET_GEIGER_STATE,
                SET_GEIGER_CONFIGURATION,
                CLEAN_GEIGER_STATE,
                SET_EYE_CONFIGURATION,
                SET_EYE_DISPLAY_VALUE
            };

            struct DeviceState {
                uint16_t geigerVoltage;
                uint8_t geigerPwmValue;

                EyeInverterState eyeState;
                EyeDisplayMode eyeAnimationMode;
                uint16_t eyeVoltage;
                uint8_t eyePwmValue;
            }__attribute__((packed));
            static_assert(sizeof(DeviceState) == 8, "invalid size");

            struct GeigerConfiguration {
                uint16_t cycleLength; // in seconds
            }__attribute__((packed));
            static_assert(sizeof(GeigerConfiguration) == 2, "invalid size");

            struct GeigerState {
                bool hasNewCycleStarted;
                uint16_t numOfCountsCurrentCycle;
                uint16_t numOfCountsPreviousCycle;
                uint16_t cycleLength; // in seconds
            }__attribute__((packed));
            static_assert(sizeof(GeigerState) == 7, "invalid size");

            struct EyeConfiguration {
                bool enabled;
                uint8_t brightness;
                EyeDisplayMode mode;
            }__attribute__((packed));
            static_assert(sizeof(EyeConfiguration) == 3, "invalid size");
        }
    }
}
