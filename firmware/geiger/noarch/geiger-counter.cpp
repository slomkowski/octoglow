#include "geiger-counter.hpp"
#include "protocol.hpp"
#include "main.hpp"

namespace octoglow::geiger::geiger_counter::hd {
    volatile uint16_t numOfCountsCurrentCycle;
}

namespace octoglow::geiger::geiger_counter {
    protocol::GeigerState _state;
}

static uint16_t numberOfTicks = 0;

using namespace octoglow::geiger;
using namespace octoglow::geiger::geiger_counter;

void geiger_counter::tick() {
    if (numberOfTicks == _state.cycleLength * TICK_TIMER_FREQ) {
        _state.numOfCountsCurrentCycle = 0;
        _state.numOfCountsPreviousCycle = hd::numOfCountsCurrentCycle;
        _state.hasNewCycleStarted = true;
        _state.hasCycleEverCompleted = true;

        hd::numOfCountsCurrentCycle = 0;

        numberOfTicks = 0;
    } else {
        ++numberOfTicks;
    }
}

protocol::GeigerState &geiger_counter::getState() {
    _state.numOfCountsCurrentCycle = hd::numOfCountsCurrentCycle;
    _state.currentCycleProgress = numberOfTicks / TICK_TIMER_FREQ;
    return _state;
}

void geiger_counter::resetCounters() {
    numberOfTicks = 0;
    hd::numOfCountsCurrentCycle = 0;

    _state.hasCycleEverCompleted = false;
    _state.hasNewCycleStarted = true;
    _state.numOfCountsCurrentCycle = 0;
    _state.numOfCountsPreviousCycle = 0;
}

void geiger_counter::configure(const volatile protocol::GeigerConfiguration &configuration) {
    _state.cycleLength = configuration.cycleLength;
    resetCounters();
}
