#pragma once

#include "encoder.hpp"

#include <inttypes.h>


namespace octoglow::front_display::protocol {
    enum class Command : uint8_t {
        NONE,
        GET_ENCODER_STATE = 1,
        CLEAR_DISPLAY,
        SET_BRIGHTNESS,
        WRITE_STATIC_TEXT,
        WRITE_SCROLLING_TEXT,
        DRAW_GRAPHICS,
        SET_UPPER_BAR,
        READ_END_YEAR_OF_CONSTRUCTION,
        WRITE_END_YEAR_OF_CONSTRUCTION,
    };

    struct EncoderState {
        int8_t encoderValue;
        encoder::ButtonState buttonValue;
    }__attribute__((packed));

    static_assert(sizeof(EncoderState) == 2, "invalid size");

    namespace text {
        constexpr uint8_t MODE = 't';
    }

    namespace scroll {
        constexpr uint8_t NUMBER_OF_SLOTS = 3;

        constexpr uint8_t MODE = 's';

        constexpr uint8_t SLOT0 = '0';
        constexpr uint8_t SLOT1 = '1';
        constexpr uint8_t SLOT2 = '2';

        constexpr uint8_t SLOT0_MAX_LENGTH = 150;
        constexpr uint8_t SLOT1_MAX_LENGTH = 70;
        constexpr uint8_t SLOT2_MAX_LENGTH = 30;
    }

    namespace pixel {
        constexpr uint8_t MODE_OVERRIDE = 'p';
        constexpr uint8_t MODE_SUM = 'P';
    }
}
