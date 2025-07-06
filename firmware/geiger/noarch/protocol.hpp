#pragma once

#include <stdint.h>

namespace octoglow::geiger::protocol {
    constexpr uint8_t MAX_BRIGHTNESS = 5;

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
        NONE,
        GET_DEVICE_STATE = 0x1,
        GET_GEIGER_STATE,
        SET_GEIGER_CONFIGURATION,
        CLEAN_GEIGER_STATE,
        SET_EYE_CONFIGURATION,
        SET_EYE_DISPLAY_VALUE,
        SET_BRIGHTNESS,
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
        bool hasNewCycleStarted: 1;
        bool hasCycleEverCompleted: 1;

        uint16_t numOfCountsCurrentCycle;
        uint16_t numOfCountsPreviousCycle;
        uint16_t currentCycleProgress; // in seconds
        uint16_t cycleLength; // in seconds
    }__attribute__((packed));

    static_assert(sizeof(GeigerState) == 9, "invalid size");

    struct EyeConfiguration {
        bool enabled;
        EyeDisplayMode mode;
    }__attribute__((packed));

    static_assert(sizeof(EyeConfiguration) == 2, "invalid size");
}
