#include "geiger-counter.hpp"
#include "protocol.hpp"
#include "main.hpp"

namespace octoglow::geiger::geiger_counter::hd {
    volatile uint16_t numOfCountsCurrentCycle;
}

namespace octoglow::geiger::geiger_counter {
    volatile protocol::GeigerState geigerState;
}

static uint16_t numberOfTicks = 0;

using namespace octoglow::geiger;
using namespace octoglow::geiger::geiger_counter;

void geiger_counter::tick() {
    if (numberOfTicks == geigerState.cycleLength * TICK_TIMER_FREQ) {
        geigerState.numOfCountsCurrentCycle = 0;
        geigerState.numOfCountsPreviousCycle = hd::numOfCountsCurrentCycle;
        hd::numOfCountsCurrentCycle = 0;

        geigerState.hasNewCycleStarted = true;
        geigerState.hasCycleEverCompleted = true;

        numberOfTicks = 0;
    } else {
        ++numberOfTicks;
    }
}

void geiger_counter::updateGeigerState() {
    geigerState.numOfCountsCurrentCycle = hd::numOfCountsCurrentCycle;
    geigerState.currentCycleProgress = numberOfTicks / TICK_TIMER_FREQ;
}

void geiger_counter::resetCounters() {
    numberOfTicks = 0;
    hd::numOfCountsCurrentCycle = 0;

    geigerState.hasCycleEverCompleted = false;
    geigerState.hasNewCycleStarted = true;
    geigerState.numOfCountsCurrentCycle = 0;
    geigerState.numOfCountsPreviousCycle = 0;
}

void geiger_counter::configure(const volatile protocol::GeigerConfiguration &configuration) {
    geigerState.cycleLength = configuration.cycleLength;
    resetCounters();
}
