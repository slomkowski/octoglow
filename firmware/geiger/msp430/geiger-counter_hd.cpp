#include "geiger-counter.hpp"

#include <msp430.h>

#include "inverter.hpp"

using namespace octoglow::geiger::geiger_counter;

void octoglow::geiger::geiger_counter::init() {
    geigerState.cycleLength = GEIGER_CYCLE_DEFAULT_LENGTH;

    P2DIR &= ~BIT2;
    P2REN &= ~BIT2;

    P2SEL &= ~BIT2;
    P2IE = BIT2;

    hd::resetDischargeToDefault();
    resetCounters();
}

inline void setInterruptToRisingEdge() {
    P2IES = 0;
}

inline void setInterruptToFallingEdge() {
    P2IES = BIT2;
}

void hd::resetDischargeToDefault() {
    dischargeState = DischargeState::WAITING_FOR_RISING_VOLTAGE;
    noCyclesSinceLastDischargeStateChange = 0;
    setInterruptToRisingEdge();
}

__interrupt_vec(PORT2_VECTOR) void PORT2_ISR() {
    using namespace hd;
    using namespace octoglow::geiger::inverter;

    if (dischargeState == DischargeState::WAITING_FOR_RISING_VOLTAGE) {
        dischargeState = DischargeState::WAITING_FOR_FALLING_VOLTAGE;
        noCyclesSinceLastDischargeStateChange = 0;
        setInterruptToFallingEdge();
    } else if (dischargeState == DischargeState::WAITING_FOR_FALLING_VOLTAGE) {
        if (noCyclesSinceLastDischargeStateChange > usToCycles(60)) {
            numOfCountsCurrentCycle++;
            dischargeState = DischargeState::RECOVERY;
            noCyclesSinceLastDischargeStateChange = 0;
        } else {
            resetDischargeToDefault();
        }
    }

    P2IFG &= ~BIT2;
}
