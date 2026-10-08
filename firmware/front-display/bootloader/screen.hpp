/*
 * Shows "BOOTLOADER" while the bootloader is running, the upper bar blinks while the host talks
 * to the bootloader, like the activity LED of twiboot.
 */
#pragma once

#include <inttypes.h>

namespace octoglow::front_display::bootloader::screen {
    void init();

    /**
     * Shows the next character, the display is multiplexed, so it has to be called continuously.
     */
    void poll();

    /**
     * Called every twiboot::TIMER_TICK_MS.
     * @param i2cActivity the bootloader was addressed since the last tick
     */
    void onTimerTick(bool i2cActivity);
}
