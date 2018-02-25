#pragma once

#include "protocol.hpp"

namespace octoglow {
    namespace geiger {
        namespace geiger_counter {
            void init();

            void resetCounters();

            /**
             * @return GeigerState structure is statically initialized, it's always available
             */
            protocol::GeigerState &getState();

            /**
             * This should be called TICK_TIMER_FREQ.
             */
            void tick();
        }
    }
}