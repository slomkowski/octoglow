#include "main.hpp"
#include "magiceye.hpp"
#include "inverter.hpp"

#include <msp430.h>

#define DAC_CE BIT1
#define DAC_IN BIT3
#define DAC_LATCH BIT2
#define DAC_CLK BIT5

#define RELAY_PREHEAT BIT1
#define RELAY_MAIN BIT3

constexpr uint16_t PREHEAT_TIME_SECONDS = 8;
constexpr uint16_t POSTHEAT_TIME_SECONDS = 5;
constexpr uint16_t MAX_SECONDS_WITHOUT_PREHEAT = 5;

using namespace octoglow::geiger;
using namespace ::octoglow::geiger::magiceye;

static EyeState eyeState = EyeState::DISABLED;
static uint16_t cyclesCounter = 0;

static void enablePreheatRelay(bool enabled) {
    if (enabled) {
        P1OUT |= RELAY_PREHEAT;
    } else {
        P1OUT &= ~RELAY_PREHEAT;
    }
}

static void enableMainRelay(bool enabled) {
    if (enabled) {
        P1OUT |= RELAY_MAIN;
    } else {
        P1OUT &= ~RELAY_MAIN;
    }
}

void ::octoglow::geiger::magiceye::init() {

    // init relays

    P1DIR |= RELAY_MAIN | RELAY_PREHEAT;
    enableMainRelay(false);
    enablePreheatRelay(true);

    // init DAC

    P1SEL &= ~DAC_LATCH;
    P1SEL2 &= ~DAC_LATCH;
    P1DIR |= DAC_LATCH;

    P2SEL &= ~(DAC_CE | DAC_IN | DAC_CLK);
    P2SEL2 &= ~(DAC_CE | DAC_IN | DAC_CLK);
    P2DIR |= DAC_CE | DAC_IN | DAC_CLK;

    P2OUT |= DAC_CE;

    setAdcValue(127); // set to half value
}

void ::octoglow::geiger::magiceye::setAdcValue(uint8_t v) {

    const uint8_t order[] = {0, 1, 2, 3, 4, 5, 7, 6};

    P2OUT &= ~DAC_CE;

    for (uint8_t i = 0; i != 8; ++i) {

        if (v & (1 << order[i])) {
            P2OUT |= DAC_IN;
        } else {
            P2OUT &= ~DAC_IN;
        }

        __nop();
        __nop();

        P2OUT |= DAC_CLK;
        __nop();
        __nop();
        P2OUT &= ~DAC_CLK;
    }

    P1OUT |= DAC_LATCH;
    __nop();
    __nop();
    P1OUT &= ~DAC_LATCH;

    __nop();
    __nop();
    P2OUT |= DAC_CE;
}

void octoglow::geiger::magiceye::tick() {

    if(eyeState == EyeState::PREHEATING and cyclesCounter >= PREHEAT_TIME_SECONDS * TICK_TIMER_FREQ) {
        enableMainRelay(true);
        eyeState = EyeState::POSTHEATING;
        cyclesCounter = 0;
        inverter::setEyeEnabled(true);
    }else if(eyeState == EyeState::POSTHEATING and cyclesCounter >= POSTHEAT_TIME_SECONDS * TICK_TIMER_FREQ) {
        enableMainRelay(true);
        enablePreheatRelay(false);
        eyeState = EyeState::RUNNING;
        cyclesCounter = 0;
        inverter::setEyeEnabled(true);
    }

    if(cyclesCounter != UINT16_MAX) {
        ++cyclesCounter;
    }
}

void octoglow::geiger::magiceye::setEnabled(bool enabled) {

    if(enabled and eyeState == EyeState::DISABLED) {
        if(cyclesCounter < MAX_SECONDS_WITHOUT_PREHEAT * TICK_TIMER_FREQ) {
            enableMainRelay(true);
            eyeState = EyeState::POSTHEATING;
            inverter::setEyeEnabled(true);
        } else {
            enablePreheatRelay(true);
            eyeState = EyeState::PREHEATING;
        }
        cyclesCounter = 0;
    } else if(!enabled and eyeState != EyeState::DISABLED) {
        inverter::setEyeEnabled(false);
        eyeState = EyeState::DISABLED;
        cyclesCounter = 0;
        enablePreheatRelay(false);
        enableMainRelay(false);
    }
}

octoglow::geiger::magiceye::EyeState octoglow::geiger::magiceye::getState() {
    return eyeState;
}
