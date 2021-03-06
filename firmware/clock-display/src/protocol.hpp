#pragma once

#include <stdint.h>

namespace octoglow {
    namespace vfd_clock {
        namespace protocol {
            constexpr uint8_t VALID_MEASUREMENT_FLAG = 1 << 1;
            constexpr uint8_t ALREADY_READ_FLAG = 1 << 2;

            constexpr uint8_t UPPER_DOT = 1 << (14 % 8);
            constexpr uint8_t LOWER_DOT = 1 << (13 % 8);

            constexpr uint8_t MAX_BRIGHTNESS = 5;

            enum class Command : uint8_t {
                NONE,
                SET_DISPLAY_CONTENT = 0x1, // 4 ascii chars + dot content
                SET_RELAY,
                SET_BRIGHTNESS,
                GET_WEATHER_SENSOR_STATE
            };

            struct DisplayContent {
                char characters[4];
                uint8_t dotState;
            }__attribute__((packed));

            static_assert(sizeof(DisplayContent) == 5);

            struct RelayState {
                bool relay1enabled;
                bool relay2enabled;
            }__attribute__((packed));

            static_assert(sizeof(RelayState) == 2);

            struct WeatherSensorState {
                uint8_t flags;
                uint8_t rawData[5];
            };

            static_assert(sizeof(WeatherSensorState) == 6);
        }

    }
}
