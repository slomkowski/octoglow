#include "geiger-counter.hpp"

#include <msp430.h>

using namespace octoglow::geiger::geiger_counter;

void octoglow::geiger::geiger_counter::init() {
    _state.cycleLength = GEIGER_CYCLE_DEFAULT_LENGTH;

    // P2.2
    P2SEL &= ~BIT2;
    P2IE = BIT2;
    P2IES = BIT2;

    resetCounters();
}

// todo czy to nie jest pomijane, jak są wyłączone przerwania?
__interrupt_vec(PORT2_VECTOR) void PORT2_ISR() {
    ++hd::numOfCountsCurrentCycle;
    P2IFG &= ~BIT2;
}
