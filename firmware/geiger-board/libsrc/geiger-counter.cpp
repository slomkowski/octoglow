#include "geiger-counter.hpp"

void octoglow::geiger::geiger_counter::tick() {
    // todo używać TICK_TIMER_FREQ do obliczeń
}

octoglow::geiger::protocol::GeigerState &octoglow::geiger::geiger_counter::getState() {
    static protocol::GeigerState state;

    return state;
}

void ::octoglow::geiger::geiger_counter::resetCounters() {
    //todo
}


