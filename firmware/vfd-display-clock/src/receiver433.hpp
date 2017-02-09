#pragma once

#include "protocol.hpp"

#include <stdint.h>

namespace octoglow {
    namespace vfd_clock {
        namespace receiver433 {
            void init();

            void pool();

            extern octoglow::vfd_clock::protocol::WeatherSensorState currentWeatherSensorState;
        }
    }
}

