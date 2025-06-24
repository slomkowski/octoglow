#pragma once

#include <inttypes.h>


namespace octoglow::front_display::encoder {

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

    extern ButtonState _currentButtonState;
    extern volatile int8_t _currentEncoderSteps;
}
