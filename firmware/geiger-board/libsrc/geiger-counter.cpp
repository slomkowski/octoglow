#include "geiger-counter.hpp"

namespace octoglow::geiger::geiger_counter::hd {
    volatile uint16_t numOfCountsCurrentCycle;
}

using namespace octoglow::geiger::geiger_counter;

void octoglow::geiger::geiger_counter::tick() {
    // todo używać TICK_TIMER_FREQ do obliczeń
}

octoglow::geiger::protocol::GeigerState &octoglow::geiger::geiger_counter::getState() {
    static protocol::GeigerState state;
    state.numOfCountsCurrentCycle = hd::numOfCountsCurrentCycle;
    return state;
}

void ::octoglow::geiger::geiger_counter::resetCounters() {
    //todo
}



