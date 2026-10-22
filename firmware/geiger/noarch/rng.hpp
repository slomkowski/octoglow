#pragma once

#include "protocol.hpp"

/**
 * Random bit generator using the Geiger tube as the entropy source.
 *
 * Each pair of consecutive, non-overlapping intervals between the discharges gives one bit: 1 if the first interval
 * is longer, 0 if shorter, equal intervals are discarded. The intervals are independent and identically distributed,
 * so both outcomes are equally probable; the intervals are measured modulo 2^16 timestamp units, which keeps
 * this property. The sense of the comparison is inverted on every other bit (as HotBits does), which cancels
 * the bias caused by a slowly changing count rate. One bit needs two discharges.
 *
 * The bits are stored in a ring buffer: the PORT2 ISR is the only producer, the main loop the only consumer,
 * each side writes only its own 16-bit index, so no critical section is needed. When the buffer is full,
 * new bits are discarded. The bits are removed when read, so the same bits are never returned twice.
 */
namespace octoglow::geiger::rng {
    /**
     * 512 bits. With the background of 20-30 CPM it fills in about 35-50 minutes.
     * The RAM is the limit: 208 bytes are used statically, the stack needs up to about 200 bytes.
     */
    constexpr uint16_t BUFFER_SIZE_BYTES = 64;
    constexpr uint16_t BUFFER_SIZE_BITS = BUFFER_SIZE_BYTES * 8;

    // the indices are free-running 16-bit counters, the bit position is the index modulo the buffer size
    static_assert((BUFFER_SIZE_BITS & (BUFFER_SIZE_BITS - 1)) == 0, "buffer size has to be a power of 2");

    /**
     * Clears the buffer and the pending intervals. Only for the initialization, not safe with the ISR running.
     */
    void reset();

    /**
     * Called from the ISR on every registered discharge.
     * @param timestamp free-running counter, wraps at 2^16
     */
    void onDischarge(uint16_t timestamp);

    uint16_t numOfAvailableBits();

    /**
     * Moves up to protocol::RANDOM_BITS_MAX_BYTES * 8 oldest bits to the output, MSB first.
     * The unused bits of the output are zeroed.
     */
    void takeBits(protocol::RandomBits &output);
}
