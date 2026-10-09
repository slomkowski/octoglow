/*
 * Polled I2C slave using USCI_B0, the same pins as the application.
 * Interrupts aren't used, the vector table jumps to the application.
 * The hardware holds SCL low until each byte is processed (clock stretching).
 */
#pragma once

namespace octoglow::geiger::bootloader::usci_slave {
    void init();

    /**
     * Processes the pending USCI events, if any.
     */
    void poll();

    /**
     * Puts USCI_B0 and its pins into the reset state.
     */
    void disable();
}
