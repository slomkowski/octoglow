#include "i2c-slave.hpp"
#include "protocol.hpp"
#include "magiceye.hpp"
#include "geiger-counter.hpp"

using namespace octoglow::geiger::protocol;
using namespace octoglow::geiger;

static volatile Command currentCommand = Command::_UNDEFINED;
static volatile void *transmittedDataPointer = nullptr;

void ::octoglow::geiger::i2c::onTransmit(uint8_t volatile *value) {

    if (transmittedDataPointer == nullptr) {
        if (currentCommand == Command::GET_GEIGER_STATE) {
            transmittedDataPointer = &geiger_counter::getState();
        } else if (currentCommand == Command::GET_DEVICE_STATE) {
            //todo
        }
    }

    *value = *reinterpret_cast<volatile uint8_t *>(transmittedDataPointer);
    ++transmittedDataPointer;
}

void ::octoglow::geiger::i2c::onStart() {
    currentCommand = Command::_UNDEFINED;
    transmittedDataPointer = nullptr;
}

void ::octoglow::geiger::i2c::onReceive(uint8_t value) {

    static bool eyeControllerStateAlreadySet = false;

    if (currentCommand == Command::_UNDEFINED) {
        if (currentCommand == Command::GET_DEVICE_STATE) { // 1 byte command
            geiger_counter::resetCounters();
            return;
        }

        eyeControllerStateAlreadySet = false;
        currentCommand = static_cast<Command>(value);

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
