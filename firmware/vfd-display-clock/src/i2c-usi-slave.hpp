/****************************************************************************
 * I2C Slave using USI.
 *
 * (C) Andreas Kaiser 2005, inspired by Atmel Application Note 312.
 *---------------------------------------------------------------------------
 * Module interface protocol, intended for message based transmissions:
 *
 * i2c_message_ready() returns the number of bytes available in
 * i2c_wrbuf when a received message is available and the transmission
 * is complete. When there is no message available or a transmission
 * is in progress, 0 is returned. When the message has been processed,
 * call i2c_message_done().
 *
 * Once a master write message has been received, the slave NACKs any
 * further master write access until the message has been processed.
 *
 * To set response data, wait until i2c_reply_ready() returns nonzero,
 * then fill i2c_rdbuf with the data, finally call i2c_reply_done(n).
 * Interrupts are disabled while updating.
 *
 * i2c_idle returns nonzero if there is no I2C activity in progress.
 *
 *---------------------------------------------------------------------------
 *
 * I2c_wrbuf[i2c_wrlen] is the recent master write message.  When
 * i2c_wrlen is nonzero, the slave does not respond to any write
 * messages.
 *
 * i2c_rdbuf[i2c_rdlen] contains valid data available for master
 * reads.  When i2c_rdlen is zero, the slave does not respond to any
 * read messages.
 *
 ****************************************************************************/

#pragma once

#include <stdint.h>

namespace i2c {
    class UsiSlave {
    public:
        UsiSlave(const uint8_t address,
                 volatile uint8_t *const readBuffer,
                 volatile uint8_t *const writeBuffer,
                 const uint8_t writeBufferSize);

        /**
         * Initialize I2C slave.
         */
        bool idle();

        /**
         * If a complete master write message is available, return its length. Otherwise return 0.
         */
        uint8_t messageReady();

        /**
         * Called when a message has been processed.
         */
        void finishMessage();


        /**
         * If the master read buffer may be safely updated, return true, with disabled interrupts.  Otherwise return false.
         */
        bool isReplyReady();

        /**
         * Called when the master read buffer has been updated.
         */
        void finishReply(const uint8_t length);
    };
}