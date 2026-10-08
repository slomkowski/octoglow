#include "usi-slave.hpp"
#include "protocol.hpp"
#include "usi.hpp"

#include <avr/io.h>

using namespace octoglow::vfd_clock;
using namespace octoglow::vfd_clock::bootloader;

constexpr uint8_t I2C_ADDRESS = usi::BOOTLOADER_I2C_ADDRESS;

// the lower nibble of usiState holds the state, the upper bits are flags
enum class State : uint8_t {
    IDLE = 0x00, // wait for start condition
    SLA = 0x01, // wait for slave address
    SLAW_ACK = 0x02, // ACK slave address + write (master writes)
    SLAR_ACK = 0x03, // ACK slave address + read (master reads)
    NAK = 0x04, // send NAK
    DATW = 0x05, // receive data
    DATW_ACK = 0x06, // transmit ACK for received data
    DATR = 0x07, // transmit data
    DATR_ACK = 0x08, // receive ACK for transmitted data
};

constexpr uint8_t STATE_MASK = 0x0f;
constexpr uint8_t WAIT_FOR_ACK = 0x10; // count only the ACK bit (2 SCL edges)
constexpr uint8_t ENABLE_SDA_OUTPUT = 0x20; // slave drives SDA
constexpr uint8_t ENABLE_SCL_HOLD = 0x40; // hold SCL low after counter overflow

static constexpr uint8_t operator|(const State state, const uint8_t flags) {
    return static_cast<uint8_t>(state) | flags;
}

constexpr uint8_t ACK = 0x00;
constexpr uint8_t NAK = 0x80;

static uint8_t usiState;
static uint8_t byteCounter;

static void processEvent(uint8_t usisr) {
    const uint8_t data = USIDR;
    auto state = static_cast<State>(usiState & STATE_MASK);

    if (usisr & _BV(USISIF)) {
        // start condition, wait until SCL goes low
        while (usi::isSclHigh()) {
        }

        usiState = State::SLA | ENABLE_SCL_HOLD;
        state = State::IDLE;
    }

    if (usisr & _BV(USIPF)) {
        // stop condition
        usiState = static_cast<uint8_t>(State::IDLE);
        state = State::IDLE;
    }

    switch (state) {
        case State::IDLE:
            break;

        case State::SLA:
            byteCounter = 0;
            if (data == ((I2C_ADDRESS << 1) | 0x00)) {
                usiState = State::SLAW_ACK | WAIT_FOR_ACK | ENABLE_SDA_OUTPUT | ENABLE_SCL_HOLD;
                USIDR = ACK;
            } else if (data == ((I2C_ADDRESS << 1) | 0x01)) {
                usiState = State::SLAR_ACK | WAIT_FOR_ACK | ENABLE_SDA_OUTPUT | ENABLE_SCL_HOLD;
                USIDR = ACK;
            } else {
                usiState = State::NAK | WAIT_FOR_ACK | ENABLE_SDA_OUTPUT | ENABLE_SCL_HOLD;
                USIDR = NAK;
            }
            break;

        case State::SLAW_ACK:
        case State::DATW_ACK:
            // sent ACK, wait for (more) data
            usiState = State::DATW | ENABLE_SCL_HOLD;
            break;

        case State::DATW:
            if (protocol::onDataWrite(byteCounter++, data)) {
                usiState = State::DATW_ACK | WAIT_FOR_ACK | ENABLE_SDA_OUTPUT | ENABLE_SCL_HOLD;
                USIDR = ACK;
            } else {
                usiState = State::NAK | WAIT_FOR_ACK | ENABLE_SDA_OUTPUT | ENABLE_SCL_HOLD;
                USIDR = NAK;
            }
            break;

        case State::DATR_ACK:
            if (data & 0x01) {
                // master sent NAK, end of transmission
                usiState = static_cast<uint8_t>(State::IDLE);
                break;
            }
            [[fallthrough]];

        case State::SLAR_ACK:
            USIDR = protocol::onDataRead(byteCounter++);
            usiState = State::DATR | ENABLE_SDA_OUTPUT | ENABLE_SCL_HOLD;
            break;

        case State::DATR:
            // sent data, receive ACK/NAK from master
            usiState = State::DATR_ACK | WAIT_FOR_ACK | ENABLE_SCL_HOLD;
            USIDR = NAK;
            break;

        case State::NAK:
        default:
            usiState = static_cast<uint8_t>(State::IDLE);
            break;
    }

    if (usiState & ENABLE_SDA_OUTPUT) {
        usi::setSdaOutput();
    } else {
        usi::setSdaInput();
    }

    if (usiState & ENABLE_SCL_HOLD) {
        USICR = usi::controlRegister(true);
    } else {
        USICR = usi::controlRegister(false);
    }

    // clear start, overflow and stop flags, set counter to 1 bit (ACK) or 8 bits (data)
    usisr &= _BV(USISIF) | _BV(USIOIF) | _BV(USIPF);
    USISR = usisr | ((usiState & WAIT_FOR_ACK) ? usi::counterForBits(1) : usi::counterForBits(8));
}

void usislave::init() {
    usi::initPins();

    processEvent(0);
}

void usislave::poll() {
    const uint8_t usisr = USISR;
    if (usisr & (_BV(USISIF) | _BV(USIOIF) | _BV(USIPF))) {
        processEvent(usisr);
    }
}

void usislave::disable() {
    USICR = 0;
}
