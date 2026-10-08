#include "display.hpp"
#include "main.hpp"
#include "vfd.hpp"

using namespace octoglow::front_display;
using namespace octoglow::front_display::display;
using namespace octoglow::front_display::display::hd;

static uint8_t currentPosition = 0;

void octoglow::front_display::display::init() {
    vfd::initPins();
}

static inline void __attribute__((optimize("O3"), hot, always_inline)) holdCharacterOnDisplayInputs(const uint8_t position) {
    vfd::showCharacter(position, _frameBuffer, _brightness, _upperBarBuffer);
}

void hd::displayPool() {
    holdCharacterOnDisplayInputs(currentPosition);

    if (currentPosition == NUM_OF_CHARACTERS - 1) {
        currentPosition = 0;
    } else {
        ++currentPosition;
    }
}
