#include "encoder.hpp"

namespace octoglow::front_display::encoder {
    ButtonState _currentButtonState = ButtonState::JUST_RELEASED;
    volatile int8_t _currentEncoderSteps = 0;
}

int8_t octoglow::front_display::encoder::getValueAndClear() {
    const auto v = _currentEncoderSteps;
    _currentEncoderSteps = 0;
    return v;
}

octoglow::front_display::encoder::ButtonState octoglow::front_display::encoder::getButtonStateAndClear() {
    const auto v = _currentButtonState;
    _currentButtonState = ButtonState::NO_CHANGE;
    return v;
}
