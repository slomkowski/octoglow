#include "animation.hpp"

#include <fix16.hpp>
#include <iostream>
using namespace std;

constexpr int16_t OSCILLATION_PERIOD = 200;
const Fix16 OSCILLATION_AMPLITUDE = F16(30);
const Fix16 OSCILLATION_AMPLITUDE_MINUS = OSCILLATION_AMPLITUDE * F16(-1);
const Fix16 GAIN = F16(1.05);

constexpr int16_t NOMINAL_BASE_VALUE = 127;
constexpr int16_t BASE_STEP = 25;
constexpr int16_t CYCLES_TO_GO_TO_MAX = 5;
constexpr int16_t CYCLES_TO_STAY_AT_MAX = 20;
constexpr int16_t CYCLES_TO_BACK_TO_NORMAL = 35;

inline Fix16 limitedTriangleWave(int16_t cycle) {
    Fix16 outputValue;
    if (cycle < (OSCILLATION_PERIOD / 4)) {
        outputValue = OSCILLATION_AMPLITUDE_MINUS * F16(4) * cycle / OSCILLATION_PERIOD;
    } else if (cycle < (3 * OSCILLATION_PERIOD / 4)) {
        outputValue = ((Fix16(int16_t(4)) * int16_t(cycle - (OSCILLATION_PERIOD / 4)) / OSCILLATION_PERIOD) * OSCILLATION_AMPLITUDE) - OSCILLATION_AMPLITUDE;
    } else {
        outputValue = OSCILLATION_AMPLITUDE - (OSCILLATION_AMPLITUDE * F16(4) * int16_t(cycle - (3 * OSCILLATION_PERIOD / 4)) / OSCILLATION_PERIOD);
    }

    outputValue *= GAIN;

    if (outputValue > OSCILLATION_AMPLITUDE) {
        return OSCILLATION_AMPLITUDE;
    } else if (outputValue < OSCILLATION_AMPLITUDE_MINUS) {
        return OSCILLATION_AMPLITUDE_MINUS;
    } else {
        return outputValue;
    }
}

enum class CurrentMode {
    NORMAL,
    RAISING_TO_MAX,
    STAY_AT_MAX,
    BACKING_TO_NORMAL
};

uint8_t octoglow::geiger::magiceye::_animate(bool hasBeenGeigerCountInLastCycle) {
    static CurrentMode currentMode = CurrentMode::NORMAL;
    static Fix16 previousValue;
    static int16_t cycleCounter = 0;
    static Fix16 step;
    static Fix16 base = F16(NOMINAL_BASE_VALUE);

    if (hasBeenGeigerCountInLastCycle) {

        if (currentMode != CurrentMode::NORMAL and base < int16_t(NOMINAL_BASE_VALUE + 4 * BASE_STEP)) {
            base += BASE_STEP;
        }

        cycleCounter = 0;
        currentMode = CurrentMode::RAISING_TO_MAX;
        step = (Fix16(int16_t(0xff)) - previousValue) / CYCLES_TO_GO_TO_MAX;
    }

    if (currentMode == CurrentMode::NORMAL) {

        if (cycleCounter == OSCILLATION_PERIOD) {
            cycleCounter = 0;
        }

        if (cycleCounter % 10 == 0 and base > NOMINAL_BASE_VALUE) {
            base -= F16(1);
        }

        previousValue = base + limitedTriangleWave(cycleCounter);
        ++cycleCounter;

    } else if (currentMode == CurrentMode::RAISING_TO_MAX) {
        if (cycleCounter < CYCLES_TO_GO_TO_MAX) {
            ++cycleCounter;
            previousValue += step;
        } else {
            currentMode = CurrentMode::STAY_AT_MAX;
            cycleCounter = 0;
        }
    } else if (currentMode == CurrentMode::STAY_AT_MAX) {
        if (cycleCounter < CYCLES_TO_STAY_AT_MAX) {
            ++cycleCounter;
        } else {
            cycleCounter = 0;
            currentMode = CurrentMode::BACKING_TO_NORMAL;
            step = (previousValue - base) / CYCLES_TO_BACK_TO_NORMAL;
        }
    } else if (currentMode == CurrentMode::BACKING_TO_NORMAL) {
        previousValue -= step;
        if (cycleCounter < CYCLES_TO_BACK_TO_NORMAL) {
            ++cycleCounter;
        } else {
            currentMode = CurrentMode::NORMAL;
            cycleCounter = 0;
        }
    }

    return int16_t(previousValue);
}

