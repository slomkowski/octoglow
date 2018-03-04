#include "i2c-slave.hpp"
#include "protocol.hpp"
#include "magiceye.hpp"
#include "geiger-counter.hpp"

using namespace octoglow::geiger::protocol;
using namespace octoglow::geiger;

static volatile Command currentCommand = Command::_UNDEFINED;
static volatile char *transmittedDataPointer = nullptr;

void ::octoglow::geiger::i2c::onTransmit(uint8_t volatile *value) {
    *value = *reinterpret_cast<volatile uint8_t *>(transmittedDataPointer);
    ++transmittedDataPointer;
}

void ::octoglow::geiger::i2c::onStart() {
    currentCommand = Command::_UNDEFINED;
}

void ::octoglow::geiger::i2c::onReceive(uint8_t value) {

    static bool eyeControllerStateAlreadySet = false;

    if (currentCommand == Command::_UNDEFINED) {
        const auto cmd = static_cast<Command >(value);
        if (cmd == Command::CLEAN_GEIGER_STATE) { // 1 byte command
            geiger_counter::resetCounters();
            return;
        } else if (cmd == Command::GET_GEIGER_STATE) {
            transmittedDataPointer = reinterpret_cast<char *>(&geiger_counter::getState());
            return;
        } else if (cmd == Command::GET_DEVICE_STATE) {
            transmittedDataPointer = reinterpret_cast<char *>(&hd::getDeviceState());
            return;
        }

        eyeControllerStateAlreadySet = false;
        currentCommand = cmd;

    } else if (currentCommand == Command::SET_EYE_ENABLED) { // 2 byte command
        magiceye::setEnabled(value != 0);
    } else if (currentCommand == Command::SET_EYE_MODE) { // 3 byte command
        if (!eyeControllerStateAlreadySet) {
            magiceye::setControllerState(static_cast<EyeControllerState>(value));
            eyeControllerStateAlreadySet = true;
        } else {
            magiceye::setAdcValue(value);
        }
    }
}
