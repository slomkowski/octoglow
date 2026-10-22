#include "rng.hpp"

using namespace octoglow::geiger;

static uint8_t buffer[rng::BUFFER_SIZE_BYTES];

static volatile uint16_t bitsWritten = 0; // written only by the producer
static volatile uint16_t bitsRead = 0; // written only by the consumer

static bool previousTimestampValid = false;
static uint16_t previousTimestamp;

static bool firstIntervalValid = false;
static uint16_t firstInterval;

static bool invertComparison = false;

void rng::reset() {
    previousTimestampValid = false;
    firstIntervalValid = false;
    invertComparison = false;
    bitsRead = bitsWritten;
}

static inline void pushBit(const bool bit) {
    const uint16_t written = bitsWritten;
    if (static_cast<uint16_t>(written - bitsRead) >= rng::BUFFER_SIZE_BITS) {
        return;
    }

    const uint16_t position = written % rng::BUFFER_SIZE_BITS;
    const uint8_t mask = 0x80 >> (position % 8);
    if (bit) {
        buffer[position / 8] |= mask;
    } else {
        buffer[position / 8] &= ~mask;
    }

    bitsWritten = written + 1;
}

void rng::onDischarge(const uint16_t timestamp) {
    if (!previousTimestampValid) {
        previousTimestamp = timestamp;
        previousTimestampValid = true;
        return;
    }

    const uint16_t interval = timestamp - previousTimestamp;
    previousTimestamp = timestamp;

    if (!firstIntervalValid) {
        firstInterval = interval;
        firstIntervalValid = true;
        return;
    }

    firstIntervalValid = false;

    if (interval == firstInterval) {
        return;
    }

    pushBit((firstInterval > interval) != invertComparison);
    invertComparison = !invertComparison;
}

uint16_t rng::numOfAvailableBits() {
    return bitsWritten - bitsRead;
}

void rng::takeBits(protocol::RandomBits &output) {
    constexpr uint8_t maxBits = sizeof(output.bits) * 8;

    const uint16_t read = bitsRead;
    const uint16_t available = bitsWritten - read;
    const uint8_t count = available < maxBits ? available : maxBits;

    for (uint8_t i = 0; i != sizeof(output.bits); ++i) {
        output.bits[i] = 0;
    }

    for (uint8_t i = 0; i != count; ++i) {
        const uint16_t position = static_cast<uint16_t>(read + i) % BUFFER_SIZE_BITS;
        if (buffer[position / 8] & (0x80 >> (position % 8))) {
            output.bits[i / 8] |= 0x80 >> (i % 8);
        }
    }

    output.numOfBits = count;
    bitsRead = read + count;
}
