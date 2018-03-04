#include <iostream>
#include "animation.hpp"

constexpr double GAIN = 1.1;
constexpr uint16_t OSCILLATION_PERIOD = 200;
constexpr uint8_t OSCILLATION_AMPLITUDE = 20;

inline double limitedTriangleWave(int16_t cycle) {
    double outputValue;
    if (cycle < OSCILLATION_PERIOD / 4.0) {
        outputValue = -4.0 * cycle / OSCILLATION_PERIOD * OSCILLATION_AMPLITUDE;
    } else if (cycle < 3.0 / 4.0 * OSCILLATION_PERIOD) {
        outputValue = -OSCILLATION_AMPLITUDE + ( 4.0 * (cycle - (OSCILLATION_PERIOD / 4.0)) / OSCILLATION_PERIOD) * OSCILLATION_AMPLITUDE;
    } else {
        outputValue = OSCILLATION_AMPLITUDE - 4.0 * (cycle - 3.0 / 4.0 * OSCILLATION_PERIOD) / OSCILLATION_PERIOD * OSCILLATION_AMPLITUDE;
    }

    outputValue *= GAIN;

    if(outputValue > OSCILLATION_AMPLITUDE) {
        return OSCILLATION_AMPLITUDE;
    } else if(outputValue < -OSCILLATION_AMPLITUDE) {
        return -OSCILLATION_AMPLITUDE;
    } else {
        return outputValue;
    }
}

constexpr uint8_t NOMINAL_BASE_VALUE = 127;
constexpr uint8_t BASE_STEP = 10;
constexpr uint8_t CYCLES_TO_GO_TO_MAX = 5;
constexpr uint8_t CYCLES_TO_STAY_AT_MAX = 20;
constexpr uint8_t CYCLES_TO_BACK_TO_NORMAL = 50;

enum class CurrentMode {
    NORMAL,
    RAISING_TO_MAX,
    STAY_AT_MAX,
    BACKING_TO_NORMAL
};

uint8_t octoglow::geiger::magiceye::_animate(bool hasBeenGeigerCountInLastCycle) {
    static CurrentMode currentMode = CurrentMode::NORMAL;
    static uint8_t previousValue = 0;
    static uint16_t cycleCounter = 0;
    static uint8_t step;
    static uint8_t base = NOMINAL_BASE_VALUE;

    if (hasBeenGeigerCountInLastCycle) {

        if (currentMode != CurrentMode::NORMAL) {
            base += BASE_STEP;
        }

        cycleCounter = 0;
        currentMode = CurrentMode::RAISING_TO_MAX;
        step = (0xff - previousValue) / CYCLES_TO_GO_TO_MAX;
    }

    if (currentMode == CurrentMode::NORMAL) {

        if(cycleCounter == OSCILLATION_PERIOD) {
            cycleCounter = 0;

            if(base > NOMINAL_BASE_VALUE) {
                base -= BASE_STEP;
            }
        }

        previousValue = base + limitedTriangleWave(cycleCounter);
        ++ cycleCounter;
        return previousValue;
    } else if (currentMode == CurrentMode::RAISING_TO_MAX) {
        if (cycleCounter < CYCLES_TO_GO_TO_MAX) {
            ++cycleCounter;
            previousValue += step;
            return previousValue;
        } else {
            currentMode = CurrentMode::STAY_AT_MAX;
            cycleCounter = 0;
            return previousValue;
        }
    } else if (currentMode == CurrentMode::STAY_AT_MAX) {
        if (cycleCounter < CYCLES_TO_STAY_AT_MAX) {
            ++cycleCounter;
            return previousValue;
        } else {
            currentMode = CurrentMode::BACKING_TO_NORMAL;
            step = (previousValue - base) / CYCLES_TO_BACK_TO_NORMAL;
            return previousValue;
        }
    } else if (currentMode == CurrentMode::BACKING_TO_NORMAL) {

        if (cycleCounter < CYCLES_TO_BACK_TO_NORMAL) {
            ++cycleCounter;
            previousValue -= step;
            return previousValue;
        } else {
            currentMode = CurrentMode::NORMAL;
            cycleCounter = 0;
            return previousValue;
        }
    }

    return base;
}

