#include "main.hpp"
#include "magiceye.hpp"
#include "inverter.hpp"
#include "geiger-counter.hpp"
#include "animation.hpp"

constexpr uint16_t PREHEAT_TIME_SECONDS = 8;
constexpr uint16_t POSTHEAT_TIME_SECONDS = 5;
constexpr uint16_t MAX_SECONDS_WITHOUT_PREHEAT = 5;

using namespace octoglow::geiger;
using namespace octoglow::geiger::magiceye;
using namespace octoglow::geiger::protocol;

static EyeInverterState eyeState = EyeInverterState::DISABLED;
static EyeControllerState eyeControllerState = EyeControllerState::ANIMATION;
static uint16_t cyclesCounter = UINT16_MAX;


void octoglow::geiger::magiceye::tick() {

    if (eyeState == EyeInverterState::HEATING_LIMITED and cyclesCounter >= PREHEAT_TIME_SECONDS * TICK_TIMER_FREQ) {
        hd::enableHeater2(true);
        eyeState = EyeInverterState::HEATING_FULL;
        cyclesCounter = 0;
    } else if (eyeState == EyeInverterState::HEATING_FULL and cyclesCounter >= POSTHEAT_TIME_SECONDS * TICK_TIMER_FREQ) {
        eyeState = EyeInverterState::RUNNING;
        cyclesCounter = 0;
        inverter::setEyeEnabled(true);
    }

    if (cyclesCounter != UINT16_MAX) {
        ++cyclesCounter;
    }

    if (eyeControllerState == EyeControllerState::ANIMATION) {
        static uint16_t previousValue = UINT16_MAX;
        const uint16_t currentValue = geiger_counter::getState().numOfCountsCurrentCycle;

        setAdcValue(_animate(currentValue > previousValue));

        previousValue = currentValue;
    }
}

void octoglow::geiger::magiceye::setEnabled(bool enabled) {

    if (enabled and eyeState == EyeInverterState::DISABLED) {
        if (cyclesCounter < MAX_SECONDS_WITHOUT_PREHEAT * TICK_TIMER_FREQ) {
            hd::enableHeater1(true);
            hd::enableHeater2(true);
            eyeState = EyeInverterState::HEATING_FULL;
        } else {
            hd::enableHeater1(true);
            eyeState = EyeInverterState::HEATING_LIMITED;
        }
        cyclesCounter = 0;
    } else if (!enabled and eyeState != EyeInverterState::DISABLED) {
        inverter::setEyeEnabled(false);
        eyeState = EyeInverterState::DISABLED;
        cyclesCounter = 0;
        hd::enableHeater1(false);
        hd::enableHeater2(false);
    }
}

octoglow::geiger::protocol::EyeInverterState octoglow::geiger::magiceye::getState() {
    return eyeState;
}

void ::octoglow::geiger::magiceye::setControllerState(octoglow::geiger::protocol::EyeControllerState state) {
    eyeControllerState = state;
}

protocol::EyeControllerState octoglow::geiger::magiceye::getControllerState() {
    return eyeControllerState;
}
