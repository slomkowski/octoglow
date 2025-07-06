#pragma once

#include <inttypes.h>
#include <gtest/gtest.h>
#include "protocol.hpp"

extern bool eyeInverterEnabled;

extern uint8_t currentAdcValue;

static inline void assertEq(const octoglow::geiger::protocol::EyeInverterState expected, const octoglow::geiger::protocol::EyeInverterState actual) {
    ASSERT_EQ(expected, actual);
}


static inline void assertEq(const octoglow::geiger::protocol::EyeDisplayMode expected, const octoglow::geiger::protocol::EyeDisplayMode actual) {
    ASSERT_EQ(expected, actual);
}
