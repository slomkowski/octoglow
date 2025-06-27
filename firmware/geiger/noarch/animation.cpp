#include "animation.hpp"

#include <fix16.h>

constexpr int16_t OSCILLATION_AMPLITUDE = 30;
constexpr fix16_t GAIN = F16(1.05);

constexpr int16_t OSCILLATION_PERIOD = 300;
constexpr int16_t NOMINAL_BASE_VALUE = 70;
constexpr int16_t BASE_STEP = 25;
constexpr int16_t CYCLES_TO_GO_TO_MAX = 7;
constexpr int16_t CYCLES_TO_STAY_AT_MAX = 30;
constexpr int16_t CYCLES_TO_BACK_TO_NORMAL = 50;

static inline fix16_t limitedTriangleWave(const int16_t cycle) {
    fix16_t outputValue;
    if (cycle < (OSCILLATION_PERIOD / 4)) {
        outputValue = -fix16_div(fix16_from_int(4 * OSCILLATION_AMPLITUDE * cycle), F16(OSCILLATION_PERIOD));
    } else if (cycle < (3 * OSCILLATION_PERIOD / 4)) {
        outputValue = fix16_sub(
            fix16_div(fix16_from_int(4 * (cycle - (OSCILLATION_PERIOD / 4)) * OSCILLATION_AMPLITUDE),
                      F16(OSCILLATION_PERIOD)), F16(OSCILLATION_AMPLITUDE));
    } else {
        outputValue = fix16_sub(F16(OSCILLATION_AMPLITUDE),
                                fix16_div(
                                    fix16_mul(
                                        F16(4 * OSCILLATION_AMPLITUDE),
                                        fix16_from_int(cycle - (3 * OSCILLATION_PERIOD / 4))),
                                    F16(OSCILLATION_PERIOD)));
    }

    outputValue = fix16_mul(outputValue, GAIN);

    if (outputValue > F16(OSCILLATION_AMPLITUDE)) {
        return F16(OSCILLATION_AMPLITUDE);
    }
    if (outputValue < F16(-OSCILLATION_AMPLITUDE)) {
        return F16(-OSCILLATION_AMPLITUDE);
    }

    return outputValue;
}

enum class CurrentMode {
    NORMAL,
    RAISING_TO_MAX,
    STAY_AT_MAX,
    BACKING_TO_NORMAL
};

static CurrentMode currentMode = CurrentMode::NORMAL;
static int16_t cycleCounter = 0;

static fix16_t previousValue;
static fix16_t step;
static fix16_t base = F16(NOMINAL_BASE_VALUE);

uint8_t octoglow::geiger::magiceye::_animate(const bool hasBeenGeigerCountInLastCycle) {
    if (hasBeenGeigerCountInLastCycle) {
        if (currentMode != CurrentMode::NORMAL and base < F16(NOMINAL_BASE_VALUE + 4 * BASE_STEP)) {
            base = fix16_add(base, F16(BASE_STEP));
        }

        cycleCounter = 0;
        currentMode = CurrentMode::RAISING_TO_MAX;
        step = fix16_div(fix16_sub(F16(0xff), previousValue), F16(CYCLES_TO_GO_TO_MAX));
    }

    if (currentMode == CurrentMode::NORMAL) {
        if (cycleCounter == OSCILLATION_PERIOD) {
            cycleCounter = 0;
        }

        if (cycleCounter % 10 == 0 and base > F16(NOMINAL_BASE_VALUE)) {
            base -= fix16_one;
        }

        previousValue = fix16_add(base, limitedTriangleWave(cycleCounter));
        ++cycleCounter;
    } else if (currentMode == CurrentMode::RAISING_TO_MAX) {
        if (cycleCounter < CYCLES_TO_GO_TO_MAX) {
            ++cycleCounter;
            previousValue = fix16_add(previousValue, step);
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
            step = fix16_div(fix16_sub(previousValue, base), F16(CYCLES_TO_BACK_TO_NORMAL));
        }
    } else if (currentMode == CurrentMode::BACKING_TO_NORMAL) {
        previousValue = fix16_sub(previousValue, step);
        if (cycleCounter < CYCLES_TO_BACK_TO_NORMAL) {
            ++cycleCounter;
        } else {
            currentMode = CurrentMode::NORMAL;
            cycleCounter = 0;
        }
    }

    return fix16_to_int(previousValue);
}
