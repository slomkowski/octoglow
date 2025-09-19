#include "geiger-counter.hpp"

#include <msp430.h>

using namespace octoglow::geiger::geiger_counter;

void octoglow::geiger::geiger_counter::init() {
    geigerState.cycleLength = GEIGER_CYCLE_DEFAULT_LENGTH;

    P2DIR &= ~BIT2;
    P2REN &= ~BIT2;

    P2SEL &= ~BIT2;
    P2IE = BIT2;
    P2IES = 0;

    resetCounters();
}

__interrupt_vec(PORT2_VECTOR) void PORT2_ISR() {

    


    
    hd::numOfCountsCurrentCycle++;
    P2IFG &= ~BIT2;
}
