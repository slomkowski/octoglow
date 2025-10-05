#pragma once

#include "protocol.hpp"

namespace octoglow::geiger::geiger_counter {
    constexpr uint16_t GEIGER_CYCLE_DEFAULT_LENGTH = 300; // seconds

    void init();

    void resetCounters();

    void configure(const volatile protocol::GeigerConfiguration &configuration);

    void updateGeigerState();

    extern volatile protocol::GeigerState geigerState;

    /**
     * This should be called TICK_TIMER_FREQ.
     */
    void tick();

    /**
     * This should be called from PWM timer interrupt, 40 kHz.
     */
    void pollGeigerCounterState();

    enum class DischargeState : uint8_t {
        WAITING_FOR_RISING_VOLTAGE,
        WAITING_FOR_FALLING_VOLTAGE,
        RECOVERY,
    };

    namespace hd {
        extern volatile uint16_t numOfCountsCurrentCycle;

        extern volatile DischargeState dischargeState;
        extern volatile uint16_t noCyclesSinceLastDischargeStateChange;

        void resetDischargeToDefault();
    }
}
