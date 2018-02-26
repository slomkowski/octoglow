#include "animation.hpp"

uint8_t octoglow::geiger::magiceye::_animate(bool hasBeenGeigerCountInLastCycle) {
    //todo do animation here

    static uint8_t x = 255;

    x -= 2;

    if (hasBeenGeigerCountInLastCycle) {
        x = 0;
    }

    return x;
}
