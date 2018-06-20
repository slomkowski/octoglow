#pragma once

#include <stdint.h>

namespace octoglow {
    namespace vfd_clock {
        namespace protocol {

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
                int16_t temperature; // in 0.1 deg C
                uint8_t humidity; // in %
                bool weakBattery;
                bool alreadyRead;
            };

            static_assert(sizeof(WeatherSensorState) == 5);
        }

    }
}
