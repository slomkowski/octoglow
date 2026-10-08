/*
 * Shows "BOOT" (as 8, 0, 0 and a mirrored 7) while the bootloader is running,
 * the dots blink while the host talks to the bootloader, like the activity LED of twiboot.
 */
#pragma once

#include <inttypes.h>

namespace octoglow::vfd_clock::bootloader::display {
    void init();

    /**
     * Called every protocol::TIMER_TICK_MS.
     * @param i2cActivity the bootloader was addressed since the last tick
     */
    void onTimerTick(bool i2cActivity);
}
