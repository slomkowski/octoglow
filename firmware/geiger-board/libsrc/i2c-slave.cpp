#include "i2c-slave.hpp"
#include "protocol.hpp"
#include "magiceye.hpp"

using namespace octoglow::geiger::protocol;

static volatile Command currentCommand = Command::_UNDEFINED;

void ::octoglow::geiger::i2c::onTransmit(uint8_t volatile *value) {

}

void ::octoglow::geiger::i2c::onStart() {
    currentCommand = Command::_UNDEFINED;
}

// GET_DEVICE_STATE = 0x1,
GET_GEIGER_STATE,
CLEAN_GEIGER_STATE,
SET_EYE_ENABLED,
SET_EYE_MODE

void ::octoglow::geiger::i2c::onReceive(uint8_t value) {
    if(currentCommand == Command::_UNDEFINED) {
        currentCommand = static_cast<Command>(value);
    } else if(currentCommand == Command::CLEAN_GEIGER_STATE) {
        // clean geiger state
    } else if(currentCommand == Command::SET_EYE_ENABLED) {
        magiceye::setEnabled(static_cast<bool>(value));
    } else if(currentCommand==Command::SET_EYE_MODE) {

    }
}


/*

maszyna stanów:
 start zeruje liczniki



 */