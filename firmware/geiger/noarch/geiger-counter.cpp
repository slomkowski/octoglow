#include "geiger-counter.hpp"

#include "inverter.hpp"
#include "protocol.hpp"
#include "main.hpp"

namespace octoglow::geiger::geiger_counter::hd {
    volatile uint16_t numOfCountsCurrentCycle;

    volatile DischargeState dischargeState = DischargeState::WAITING_FOR_RISING_VOLTAGE;
    volatile uint16_t noCyclesSinceLastDischargeStateChange = 0;
}

namespace octoglow::geiger::geiger_counter {
    volatile protocol::GeigerState geigerState;
}

using namespace octoglow::geiger;
using namespace octoglow::geiger::geiger_counter;

// counted in seconds: the cycle can be up to 0xffff s long, the ticks wouldn't fit 16 bits (int on MSP430)
static uint8_t ticksInSecond = 0;
static_assert(octoglow::geiger::TICK_TIMER_FREQ <= UINT8_MAX);
static uint16_t secondsInCycle = 0;

void geiger_counter::tick() {
    if (secondsInCycle >= geigerState.cycleLength) {
        geigerState.numOfCountsCurrentCycle = 0;
        geigerState.numOfCountsPreviousCycle = hd::numOfCountsCurrentCycle;
        hd::numOfCountsCurrentCycle = 0;

        geigerState.hasNewCycleStarted = true;
        geigerState.hasCycleEverCompleted = true;

        ticksInSecond = 0;
        secondsInCycle = 0;
    } else if (++ticksInSecond == TICK_TIMER_FREQ) {
        ticksInSecond = 0;
        ++secondsInCycle;
    }
}

void geiger_counter::pollGeigerCounterState() {
    using namespace hd;
    using namespace inverter;

    if (noCyclesSinceLastDischargeStateChange < usToCycles(2000)) {
        noCyclesSinceLastDischargeStateChange++;
    }

    if ((dischargeState == DischargeState::RECOVERY && noCyclesSinceLastDischargeStateChange > usToCycles(250))
        || (dischargeState == DischargeState::WAITING_FOR_FALLING_VOLTAGE && noCyclesSinceLastDischargeStateChange >
            usToCycles(500))) {
        resetDischargeToDefault();
    }
}


void geiger_counter::updateGeigerState() {
    geigerState.numOfCountsCurrentCycle = hd::numOfCountsCurrentCycle;
    geigerState.currentCycleProgress = secondsInCycle;
}

void geiger_counter::resetCounters() {
    ticksInSecond = 0;
    secondsInCycle = 0;
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
