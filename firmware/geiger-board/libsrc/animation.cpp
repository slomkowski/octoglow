#include "animation.hpp"

constexpr double PERIOD = 200;
constexpr double BASE_NOMINAL = 50;
constexpr double AMPLITUDE_STEP = 20;

inline double triangleWave(int16_t cycle, double amplitude) {
    if(cycle < PERIOD / 2) {
        return amplitude * 2.0 * cycle / PERIOD;
    } else {
        return amplitude * 2.0 * (PERIOD - cycle) / PERIOD;
    }
}

constexpr uint16_t CYCLES_TO_GO_TO_MAX = 10;

enum class CurrentMode {
    NORMAL,
    RAISING_TO_MAX,
    STAY_AT_MAX,
    BACKING_TO_NORMAL
};

uint8_t octoglow::geiger::magiceye::_animate(bool hasBeenGeigerCountInLastCycle) {
    //todo do animation here

    static CurrentMode currentMode = CurrentMode::NORMAL;
    static uint8_t previousValue = 0;
    static uint16_t cycleCounter = 0;
    static uint8_t step;


    if(hasBeenGeigerCountInLastCycle) {
        currentMode = CurrentMode::RAISING_TO_MAX;
        step = (255 - previousValue) / CYCLES_TO_GO_TO_MAX;
    }

    if(currentMode == CurrentMode::RAISING_TO_MAX) {
        if (255 - previousValue < step) {
            currentMode = CurrentMode::STAY_AT_MAX;
        } else {

            previousValue += step;
            return previousValue;
        }
    }






    static double base = BASE_NOMINAL;
    static int16_t cycleCounter = 0;

    if (hasBeenGeigerCountInLastCycle and base < 3 * AMPLITUDE_STEP) { //todo przeformułować
        base += AMPLITUDE_STEP;
        cycleCounter = 0;
    }

    if(cycleCounter > PERIOD) {
        cycleCounter = 0;
        if(base > BASE_NOMINAL) {
            base -= BASE_NOMINAL;
        }
    }

    ++cycleCounter;
    return base + triangleWave(cycleCounter, 30);
}

