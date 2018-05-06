#include "display.hpp"

#include <gtest/gtest.h>

#include <iostream>

using namespace octoglow::front_display;

void octoglow::front_display::display::hd::displayPool() {
    std::cout << "displayPool" << std::endl;
}

TEST(Display, Clear) {

    display::writeStaticText(5, 10, "lorem ipsum dolor ");

    display::clear();

    for (int i = 0; i < display::NUM_OF_CHARACTERS * display::COLUMNS_IN_CHARACTER; ++i) {
        ASSERT_EQ(0, display::_frameBuffer[i]);
    }
}
