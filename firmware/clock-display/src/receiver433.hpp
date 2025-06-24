#pragma once

#include "protocol.hpp"

namespace octoglow::vfd_clock::receiver433 {
    void init();

    void pool();

    extern protocol::WeatherSensorState currentWeatherSensorState;
}
