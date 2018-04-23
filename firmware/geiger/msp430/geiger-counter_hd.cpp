#include "geiger-counter.hpp"

#include <msp430.h>

using namespace octoglow::geiger::geiger_counter;

void octoglow::geiger::geiger_counter::init() {
    _state.cycleLength = GEIGER_CYCLE_DEFAULT_LENGTH;

    P2IE |= BIT2;
}

__attribute__ ((interrupt(PORT2_VECTOR))) void PORT2_ISR() {
    ++hd::numOfCountsCurrentCycle;
    P2IFG &= ~BIT2;
}
