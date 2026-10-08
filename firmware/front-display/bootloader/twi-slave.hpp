/*
 * Polled I2C slave using the TWI peripheral, port of twiboot v3.2 by Olaf Rempel, GPLv2.
 * Interrupts aren't used, the vector table belongs to the application.
 * SCL is held low until each byte is processed (clock stretching).
 */
#pragma once

#include <inttypes.h>

namespace octoglow::front_display::bootloader::twislave {
    void init();

    /**
     * Processes the pending TWI event, if any.
     */
    void poll();

    void disable();

    /**
     * @return true if the bootloader was addressed since the last call
     */
    bool takeActivityFlag();
}
