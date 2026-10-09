#include "geiger-counter.hpp"
#include "inverter.hpp"
#include "main.hpp"

#include <gtest/gtest.h>

using namespace octoglow::geiger;
using namespace octoglow::geiger::geiger_counter;
using namespace octoglow::geiger::protocol;

static int resetDischargeCalls = 0;

void hd::resetDischargeToDefault() {
    ++resetDischargeCalls;
    hd::dischargeState = DischargeState::WAITING_FOR_RISING_VOLTAGE;
    hd::noCyclesSinceLastDischargeStateChange = 0;
}

static void configureCycleLength(const uint16_t seconds) {
    const GeigerConfiguration configuration{seconds};
    configure(configuration);
}

static void tickSeconds(const uint32_t seconds) {
    for (uint32_t i = 0; i < seconds * TICK_TIMER_FREQ; ++i) {
        tick();
    }
}

static GeigerState currentState() {
    updateGeigerState();
    GeigerState state;
    state.hasNewCycleStarted = geigerState.hasNewCycleStarted;
    state.hasCycleEverCompleted = geigerState.hasCycleEverCompleted;
    state.numOfCountsCurrentCycle = geigerState.numOfCountsCurrentCycle;
    state.numOfCountsPreviousCycle = geigerState.numOfCountsPreviousCycle;
    state.currentCycleProgress = geigerState.currentCycleProgress;
    state.cycleLength = geigerState.cycleLength;
    return state;
}

TEST(GeigerCounter, ConfigureResetsCounters) {
    configureCycleLength(60);
    hd::numOfCountsCurrentCycle = 17;
    tickSeconds(10);

    configureCycleLength(30);

    const auto state = currentState();
    ASSERT_EQ(30, state.cycleLength);
    ASSERT_EQ(0, state.numOfCountsCurrentCycle);
    ASSERT_EQ(0, state.numOfCountsPreviousCycle);
    ASSERT_EQ(0, state.currentCycleProgress);
    ASSERT_TRUE(state.hasNewCycleStarted);
    ASSERT_FALSE(state.hasCycleEverCompleted);
}

TEST(GeigerCounter, CycleEnds) {
    configureCycleLength(10);
    geigerState.hasNewCycleStarted = false;

    hd::numOfCountsCurrentCycle = 5;
    tickSeconds(7);

    auto state = currentState();
    ASSERT_EQ(5, state.numOfCountsCurrentCycle);
    ASSERT_EQ(7, state.currentCycleProgress);
    ASSERT_FALSE(state.hasNewCycleStarted);
    ASSERT_FALSE(state.hasCycleEverCompleted);

    hd::numOfCountsCurrentCycle = 12;
    tickSeconds(3);
    state = currentState();
    ASSERT_EQ(10, state.currentCycleProgress) << "the last tick of the cycle";
    ASSERT_FALSE(state.hasCycleEverCompleted);

    tick();

    state = currentState();
    ASSERT_EQ(0, state.numOfCountsCurrentCycle);
    ASSERT_EQ(12, state.numOfCountsPreviousCycle);
    ASSERT_EQ(0, state.currentCycleProgress);
    ASSERT_TRUE(state.hasNewCycleStarted);
    ASSERT_TRUE(state.hasCycleEverCompleted);
    ASSERT_EQ(0, hd::numOfCountsCurrentCycle);

    // the next cycle has the same length
    geigerState.hasNewCycleStarted = false;
    hd::numOfCountsCurrentCycle = 3;
    tickSeconds(10);
    ASSERT_FALSE(currentState().hasNewCycleStarted);
    tick();
    state = currentState();
    ASSERT_TRUE(state.hasNewCycleStarted);
    ASSERT_EQ(3, state.numOfCountsPreviousCycle);
}

TEST(GeigerCounter, CycleLongerThan16BitTicks) {
    // 1000 s = 100000 ticks, doesn't fit the 16 bits of int on MSP430
    configureCycleLength(1000);
    geigerState.hasNewCycleStarted = false;
    hd::numOfCountsCurrentCycle = 1;

    tickSeconds(999);
    auto state = currentState();
    ASSERT_FALSE(state.hasNewCycleStarted);
    ASSERT_EQ(999, state.currentCycleProgress);

    tickSeconds(1);
    tick();
    state = currentState();
    ASSERT_TRUE(state.hasNewCycleStarted);
    ASSERT_EQ(1, state.numOfCountsPreviousCycle);
}

TEST(GeigerCounter, MaximumCycleLength) {
    configureCycleLength(0xffff);
    geigerState.hasNewCycleStarted = false;

    tickSeconds(0xffff);
    ASSERT_EQ(0xffff, currentState().currentCycleProgress);
    ASSERT_FALSE(currentState().hasNewCycleStarted);

    tick();
    ASSERT_TRUE(currentState().hasNewCycleStarted);

    configureCycleLength(GEIGER_CYCLE_DEFAULT_LENGTH);
}

TEST(GeigerCounter, DischargeTimeouts) {
    hd::resetDischargeToDefault();
    resetDischargeCalls = 0;

    // waiting for the rising edge never times out
    for (int i = 0; i < 1000; ++i) {
        pollGeigerCounterState();
    }
    ASSERT_EQ(0, resetDischargeCalls);

    // the falling edge has to come within 500 us
    hd::dischargeState = DischargeState::WAITING_FOR_FALLING_VOLTAGE;
    hd::noCyclesSinceLastDischargeStateChange = 0;
    for (uint16_t i = 0; i < inverter::usToCycles(500); ++i) {
        pollGeigerCounterState();
    }
    ASSERT_EQ(DischargeState::WAITING_FOR_FALLING_VOLTAGE, static_cast<DischargeState>(hd::dischargeState));
    pollGeigerCounterState();
    ASSERT_EQ(DischargeState::WAITING_FOR_RISING_VOLTAGE, static_cast<DischargeState>(hd::dischargeState));
    ASSERT_EQ(1, resetDischargeCalls);

    // the recovery takes 250 us
    hd::dischargeState = DischargeState::RECOVERY;
    hd::noCyclesSinceLastDischargeStateChange = 0;
    for (uint16_t i = 0; i < inverter::usToCycles(250); ++i) {
        pollGeigerCounterState();
    }
    ASSERT_EQ(DischargeState::RECOVERY, static_cast<DischargeState>(hd::dischargeState));
    pollGeigerCounterState();
    ASSERT_EQ(DischargeState::WAITING_FOR_RISING_VOLTAGE, static_cast<DischargeState>(hd::dischargeState));
    ASSERT_EQ(2, resetDischargeCalls);
}
