#include "main.hpp"
#include "magiceye.hpp"
#include "inverter.hpp"

constexpr uint16_t PREHEAT_TIME_SECONDS = 8;
constexpr uint16_t POSTHEAT_TIME_SECONDS = 5;
constexpr uint16_t MAX_SECONDS_WITHOUT_PREHEAT = 5;

using namespace octoglow::geiger;
using namespace ::octoglow::geiger::magiceye;

static EyeState eyeState = EyeState::DISABLED;
static uint16_t cyclesCounter = UINT16_MAX;

void octoglow::geiger::magiceye::tick() {

    if (eyeState == EyeState::PREHEATING and cyclesCounter >= PREHEAT_TIME_SECONDS * TICK_TIMER_FREQ) {
        hd::enableMainRelay(true);
        eyeState = EyeState::POSTHEATING;
        cyclesCounter = 0;
        inverter::setEyeEnabled(true);
    } else if (eyeState == EyeState::POSTHEATING and cyclesCounter >= POSTHEAT_TIME_SECONDS * TICK_TIMER_FREQ) {
        hd::enableMainRelay(true);
        hd::enablePreheatRelay(false);
        eyeState = EyeState::RUNNING;
        cyclesCounter = 0;
        inverter::setEyeEnabled(true);
    }

    if (cyclesCounter != UINT16_MAX) {
        ++cyclesCounter;
    }
}

void octoglow::geiger::magiceye::setEnabled(bool enabled) {

    if (enabled and eyeState == EyeState::DISABLED) {
        if (cyclesCounter < MAX_SECONDS_WITHOUT_PREHEAT * TICK_TIMER_FREQ) {
            hd::enableMainRelay(true);
            eyeState = EyeState::POSTHEATING;
            inverter::setEyeEnabled(true);
        } else {
            hd::enablePreheatRelay(true);
            eyeState = EyeState::PREHEATING;
        }
        cyclesCounter = 0;
    } else if (!enabled and eyeState != EyeState::DISABLED) {
        inverter::setEyeEnabled(false);
        eyeState = EyeState::DISABLED;
        cyclesCounter = 0;
        hd::enablePreheatRelay(false);
        hd::enableMainRelay(false);
    }
}

octoglow::geiger::magiceye::EyeState octoglow::geiger::magiceye::getState() {
    return eyeState;
}
