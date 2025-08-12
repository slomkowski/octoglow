#include "display.hpp"
#include "encoder.hpp"

#include <gtest/gtest.h>

#include <iostream>

using namespace octoglow::front_display;

void display::hd::displayPool() {
    std::cout << "displayPool" << std::endl;
}

int8_t encoder::getValueAndClear() {
    const auto v = _currentEncoderSteps;
    _currentEncoderSteps = 0;
    return v;
}

encoder::ButtonState encoder::getButtonStateAndClear() {
    const auto v = _currentButtonState;
    _currentButtonState = ButtonState::NO_CHANGE;
    return v;
}


TEST(Display, Clear) {
    display::writeStaticText(5, 10, const_cast<char *>("lorem ipsum dolor "));

    display::clear();

    for (int i = 0; i < display::NUM_OF_CHARACTERS * display::COLUMNS_IN_CHARACTER; ++i) {
        ASSERT_EQ(0, display::_frameBuffer[i]);
    }
}

TEST(Display, ForEachUtf8character) {
    int numOfCalls = 0;
    display::_forEachUtf8character("lorem", false, 5, &numOfCalls,
                                   [](void *s, uint8_t, uint8_t) -> void {
                                       *static_cast<int *>(s) += 1;
                                   });
    ASSERT_EQ(5, numOfCalls);

    numOfCalls = 0;
    display::_forEachUtf8character("lorem", false, 9, &numOfCalls,
                                   [](void *s, uint8_t, uint8_t) -> void {
                                       *static_cast<int *>(s) += 1;
                                   });
    ASSERT_EQ(5, numOfCalls);

    numOfCalls = 0;
    display::_forEachUtf8character("lorąę", false, 9, &numOfCalls,
                                   [](void *s, uint8_t, uint8_t) -> void {
                                       *static_cast<int *>(s) += 1;
                                   });
    ASSERT_EQ(5, numOfCalls);
}
