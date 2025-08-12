#include "encoder.hpp"

namespace octoglow::front_display::encoder {
    ButtonState _currentButtonState = ButtonState::NO_CHANGE;
    volatile int8_t _currentEncoderSteps = 0;
}
