#include "rng.hpp"
#include "i2c-slave.hpp"
#include "protocol.hpp"

#include <gtest/gtest.h>
#include <cmath>
#include <random>
#include <vector>

using namespace std;
using namespace octoglow::geiger;
using namespace octoglow::geiger::protocol;

static uint16_t timestamp = 0;

static void discharge(const uint16_t interval) {
    timestamp += interval;
    rng::onDischarge(timestamp);
}

/**
 * The first discharge only sets the reference timestamp.
 */
static void startWithDischarge() {
    rng::reset();
    timestamp = 1000;
    rng::onDischarge(timestamp);
}

static vector<bool> takeAllBits() {
    vector<bool> result;
    RandomBits randomBits;
    do {
        rng::takeBits(randomBits);
        EXPECT_LE(randomBits.numOfBits, RANDOM_BITS_MAX_BYTES * 8);
        for (uint8_t i = 0; i != RANDOM_BITS_MAX_BYTES * 8; ++i) {
            const bool bit = randomBits.bits[i / 8] & (0x80 >> (i % 8));
            if (i < randomBits.numOfBits) {
                result.push_back(bit);
            } else {
                EXPECT_FALSE(bit) << "unused bits have to be zero";
            }
        }
    } while (randomBits.numOfBits != 0);
    return result;
}

TEST(Rng, OneBitFromTwoIntervals) {
    startWithDischarge();
    ASSERT_EQ(0, rng::numOfAvailableBits());

    discharge(500);
    ASSERT_EQ(0, rng::numOfAvailableBits());
    discharge(300); // first longer: 1
    ASSERT_EQ(1, rng::numOfAvailableBits());

    // inverted comparison
    discharge(500);
    discharge(300); // first longer, inverted: 0

    discharge(300);
    discharge(500); // first shorter: 0

    discharge(300);
    discharge(500); // first shorter, inverted: 1

    ASSERT_EQ((vector<bool>{true, false, false, true}), takeAllBits());
    ASSERT_EQ(0, rng::numOfAvailableBits());
}

TEST(Rng, EqualIntervalsDiscarded) {
    startWithDischarge();

    discharge(400);
    discharge(400);
    ASSERT_EQ(0, rng::numOfAvailableBits());

    // the pairs don't overlap, the next pair starts fresh and the comparison isn't inverted
    discharge(900);
    discharge(100);
    ASSERT_EQ((vector<bool>{true}), takeAllBits());
}

TEST(Rng, IntervalsLongerThanTimestampRange) {
    startWithDischarge();

    // 70000 and 66000 wrap to 4464 and 464, the order is kept
    discharge(static_cast<uint16_t>(70000));
    discharge(static_cast<uint16_t>(66000));
    ASSERT_EQ((vector<bool>{true}), takeAllBits());
}

TEST(Rng, TakeBitsInChunks) {
    startWithDischarge();

    constexpr unsigned NUM_BITS = 300;
    vector<bool> expected;
    for (unsigned i = 0; i != NUM_BITS; ++i) {
        const bool bit = (i * 7 + i / 3) % 5 < 2;
        const bool inverted = i % 2 == 1;
        if (bit != inverted) {
            discharge(200);
            discharge(100);
        } else {
            discharge(100);
            discharge(200);
        }
        expected.push_back(bit);
    }
    ASSERT_EQ(NUM_BITS, rng::numOfAvailableBits());

    RandomBits randomBits;
    rng::takeBits(randomBits);
    ASSERT_EQ(104, randomBits.numOfBits);
    ASSERT_EQ(NUM_BITS - 104, rng::numOfAvailableBits());

    vector<bool> result;
    for (uint8_t i = 0; i != 104; ++i) {
        result.push_back(randomBits.bits[i / 8] & (0x80 >> (i % 8)));
    }
    const auto rest = takeAllBits();
    result.insert(result.end(), rest.begin(), rest.end());

    ASSERT_EQ(expected, result);
}

TEST(Rng, FullBufferDropsNewBits) {
    startWithDischarge();

    for (unsigned i = 0; i != rng::BUFFER_SIZE_BITS + 100; ++i) {
        discharge(200);
        discharge(100);
    }
    ASSERT_EQ(rng::BUFFER_SIZE_BITS, rng::numOfAvailableBits());

    // the stored bits alternate because of the inversion
    const auto bits = takeAllBits();
    ASSERT_EQ(rng::BUFFER_SIZE_BITS, bits.size());
    for (unsigned i = 0; i != bits.size(); ++i) {
        ASSERT_EQ(i % 2 == 0, bits[i]) << "bit " << i;
    }

    // after reading there's space again
    discharge(200);
    discharge(100);
    ASSERT_EQ(1, rng::numOfAvailableBits());
}

TEST(Rng, IndexWrapAround) {
    startWithDischarge();

    // the 16-bit indices wrap after 65536 bits
    vector<bool> expected;
    for (unsigned i = 0; i != 70000; ++i) {
        const bool first = (i / 3) % 2 == 0;
        if (first) {
            discharge(200);
            discharge(100);
        } else {
            discharge(100);
            discharge(200);
        }
        expected.push_back(first != (i % 2 == 1));

        if (rng::numOfAvailableBits() >= 104) {
            RandomBits randomBits;
            rng::takeBits(randomBits);
            ASSERT_EQ(104, randomBits.numOfBits);
            for (uint8_t b = 0; b != 104; ++b) {
                ASSERT_EQ(expected[b], static_cast<bool>(randomBits.bits[b / 8] & (0x80 >> (b % 8))));
            }
            expected.erase(expected.begin(), expected.begin() + 104);
        }
    }
    ASSERT_EQ(expected, takeAllBits());
}

TEST(Rng, PoissonProcessIsUnbiased) {
    startWithDischarge();

    // 25 counts per minute, timestamps at 40 kHz, the rate drifts by 20 %
    mt19937 generator(1234);
    double rate = 25.0 / 60.0 / 40000.0;

    unsigned ones = 0;
    unsigned total = 0;
    for (unsigned i = 0; i != 200000; ++i) {
        rate *= 1.000001;
        exponential_distribution<double> distribution(rate);
        double interval = distribution(generator);
        timestamp += static_cast<uint64_t>(interval);
        rng::onDischarge(timestamp);

        if (rng::numOfAvailableBits() == rng::BUFFER_SIZE_BITS) {
            for (const bool bit: takeAllBits()) {
                ones += bit;
                ++total;
            }
        }
    }

    ASSERT_GT(total, 99000u);
    // 5 standard deviations
    ASSERT_NEAR(0.5, static_cast<double>(ones) / total, 5 * 0.5 / sqrt(total));
}

TEST(Rng, I2cCommand) {
    startWithDischarge();
    for (unsigned i = 0; i != 120; ++i) {
        discharge(200);
        discharge(100);
    }

    auto crc8 = [](const vector<uint8_t> &data) {
        uint8_t crc = 0;
        for (const uint8_t byte: data) {
            crc ^= byte;
            for (uint8_t i = 0; i < 8; ++i) {
                crc = crc & 0x80 ? (crc << 1) ^ 0x07 : crc << 1;
            }
        }
        return crc;
    };

    auto readReply = [&] {
        const uint8_t cmd = static_cast<uint8_t>(Command::GET_RANDOM_BITS);
        i2c::onStart();
        i2c::onReceive(crc8({cmd}));
        i2c::onReceive(cmd);
        i2c::onStop();
        i2c::processDataIfAvailable();

        vector<uint8_t> reply(2 + sizeof(RandomBits));
        i2c::onStart();
        for (auto &byte: reply) {
            i2c::onTransmit(&byte);
        }
        i2c::onStop();

        EXPECT_EQ(crc8(vector<uint8_t>(reply.begin() + 1, reply.end())), reply[0]);
        EXPECT_EQ(cmd, reply[1]);
        return reply;
    };

    // 120 bits alternating 1 0 1 0 (the same interval order, inverted every other bit)
    auto reply = readReply();
    ASSERT_EQ(104, reply[2]);
    for (unsigned i = 0; i != RANDOM_BITS_MAX_BYTES; ++i) {
        ASSERT_EQ(0xaa, reply[3 + i]);
    }

    reply = readReply();
    ASSERT_EQ(16, reply[2]);
    ASSERT_EQ(0xaa, reply[3]);
    ASSERT_EQ(0xaa, reply[4]);
    for (unsigned i = 2; i != RANDOM_BITS_MAX_BYTES; ++i) {
        ASSERT_EQ(0, reply[3 + i]);
    }

    reply = readReply();
    ASSERT_EQ(0, reply[2]);
}
