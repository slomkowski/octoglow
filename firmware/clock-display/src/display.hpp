#pragma once

#include "protocol.hpp"

namespace octoglow::vfd_clock::display {
    enum class ReceiverUpdateFlag : uint8_t {
        DISABLED,
        VALID,
        INVALID
    };

    void init();

    void setCharacter(uint8_t position, char character, bool shouldReloadDisplay = true);

    void setAllCharacters(const volatile char *characters);

    void setBrightness(uint8_t brightness);

    void setDots(uint8_t newDotState, bool shouldReloadDisplay = true);

    void setReceiverUpdateFlag(ReceiverUpdateFlag flag);
}
