#include "geiger-counter.hpp"

#include <msp430.h>

/*
 * todo
 * konfiguracja wejścia i przerwania dla zwiększania licznika
 * obsługa przerwania, które inkrementuje licznik
 */

using namespace octoglow::geiger::geiger_counter;


void octoglow::geiger::geiger_counter::init() {
    //todo init geiger counter

    // geiger jest na P2.0

    P2IE |= BIT0;
}

__attribute__ ((interrupt(PORT2_VECTOR))) void PORT2_ISR() {
    ++hd::numOfCountsCurrentCycle;
    P2IFG &= ~BIT0;
}