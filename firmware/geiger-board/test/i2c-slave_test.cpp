#include "i2c-slave.hpp"
#include "protocol.hpp"

using namespace octoglow::geiger;

static protocol::DeviceState deviceState;

protocol::DeviceState &octoglow::geiger::i2c::hd::getDeviceState() {
    return deviceState;
}

//todo testy
