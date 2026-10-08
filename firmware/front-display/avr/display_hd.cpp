#include "display.hpp"
#include "main.hpp"
#include "vfd.hpp"

using namespace octoglow::front_display;
using namespace octoglow::front_display::display;
using namespace octoglow::front_display::display::hd;

static uint8_t currentPosition = 0;

void octoglow::front_display::display::init() {
    vfd::initPins();
    vfd::initSlotTimer();
}

void hd::displayPool() {
    vfd::startSlot(vfd::BRIGHTNESS_ON_TICKS[_brightness]);
    vfd::shiftCharacter(currentPosition, _frameBuffer, _upperBarBuffer);

    if (currentPosition == NUM_OF_CHARACTERS - 1) {
        currentPosition = 0;
    } else {
        ++currentPosition;
    }
}
