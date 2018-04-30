#include "display.hpp"

#include <gtest/gtest.h>

#include <iostream>

void octoglow::front_display::display::hd::displayPool() {
    std::cout << "displayPool" << std::endl;
}

TEST(Display, Basic) {
    ASSERT_EQ(1, 1);
}
