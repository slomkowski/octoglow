#pragma once

#include "protocol.hpp"

namespace octoglow {
    namespace geiger {
        namespace geiger_counter {

            constexpr uint16_t GEIGER_CYCLE_DEFAULT_LENGTH = 300; // seconds

            void init();

            void resetCounters();

            void configure(protocol::GeigerConfiguration &configuration);

            /**
             * @return GeigerState structure is statically initialized, it's always available
             */
            protocol::GeigerState &getState();

            extern protocol::GeigerState _state;

            /**
             * This should be called TICK_TIMER_FREQ.
             */
            void tick();

            namespace hd {
                extern volatile uint16_t numOfCountsCurrentCycle;
            }
        }
    }
}