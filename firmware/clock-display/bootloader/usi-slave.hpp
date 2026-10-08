/*
 * Polled I2C slave using USI, port of twiboot v3.2 by Olaf Rempel, GPLv2.
 * Interrupts can't be used, the vector table belongs to the application.
 * SCL is held low after each byte until it's processed (clock stretching).
 */
#pragma once

#include <inttypes.h>

namespace octoglow::vfd_clock::bootloader::usislave {
    void init();

    /**
     * Processes pending start, stop and counter overflow events.
     */
    void poll();

    void disable();
}
