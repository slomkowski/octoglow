#pragma once

#include "protocol.hpp"

namespace octoglow::geiger::geiger_counter {
    constexpr uint16_t GEIGER_CYCLE_DEFAULT_LENGTH = 300; // seconds

    void init();

    void resetCounters();

    void configure(const volatile protocol::GeigerConfiguration &configuration);

    void updateGeigerState();

    extern volatile protocol::GeigerState geigerState;

    /**
             * This should be called TICK_TIMER_FREQ.
             */
    void tick();

    namespace hd {
        extern volatile uint16_t numOfCountsCurrentCycle;
    }
}
