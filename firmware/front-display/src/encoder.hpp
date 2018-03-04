#pragma once

#include <stdint.h>


namespace octoglow {
    namespace vfd_front {
        namespace encoder {

            enum class ButtonState : int8_t {
                NO_CHANGE = 0,
                JUST_PRESSED = 1,
                JUST_RELEASED = -1
            };

            void init();

            void pool();

            /*
             * Reads the effective number of steps from last call. Read zeroes the counter.
             */
            int8_t getValueAndClear();

            ButtonState getButtonStateAndClear();
        }
    }
}