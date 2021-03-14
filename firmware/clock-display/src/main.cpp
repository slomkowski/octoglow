#include "display.hpp"
#include "relay.hpp"
#include "receiver433.hpp"
#include "i2c-slave.hpp"
#include "protocol.hpp"

#include <avr/io.h>
#include <avr/wdt.h>
#include <util/delay.h>
#include <avr/interrupt.h>
#include <string.h>
#include <stdio.h>

constexpr bool WATCHD0G_ENABLE = true;

using namespace octoglow::vfd_clock;
using namespace octoglow::vfd_clock::protocol;

static Command currentCommand = Command::NONE;

/*
 * Test Bus Pirate commands:
 * - set 2345 at the display: [ 0x20 1 50 51 52 53  ]
 */
static inline void processI2cCommands() {
    using namespace octoglow::vfd_clock::protocol;

    if (i2c_reply_ready()) {

        i2c_rdbuf[0] = i2c_wrbuf[0];

        static_assert(sizeof(protocol::WeatherSensorState) + 1 <= sizeof(i2c_rdbuf));

        if (currentCommand == Command::GET_WEATHER_SENSOR_STATE) {
            for (uint8_t i = sizeof(protocol::WeatherSensorState); i != 0; --i) {
                i2c_rdbuf[i] = reinterpret_cast<uint8_t *>(&(receiver433::currentWeatherSensorState))[i - 1];
            }
            receiver433::currentWeatherSensorState.flags |= protocol::ALREADY_READ_FLAG;
            i2c_reply_done(sizeof(protocol::WeatherSensorState) + 1);
            currentCommand = Command::NONE;
        }
    }

    if (i2c_message_ready()) {

        switch (static_cast<Command >(i2c_wrbuf[0])) {
            case Command::SET_DISPLAY_CONTENT: {
                auto *dc = (DisplayContent *) (&i2c_wrbuf[1]);
                display::setDots(dc->dotState, false);
                display::setAllCharacters(dc->characters);
            }
                break;
            case Command::SET_RELAY: {
                auto *rs = (RelayState *) (&i2c_wrbuf[1]);
                relay::setState(relay::Relay::RELAY_1, rs->relay1enabled);
                relay::setState(relay::Relay::RELAY_2, rs->relay2enabled);
            }
                break;
            case Command::SET_BRIGHTNESS:
                display::setBrightness(i2c_wrbuf[1]);
                break;
            case Command::GET_WEATHER_SENSOR_STATE:
                currentCommand = Command::GET_WEATHER_SENSOR_STATE;
                break;
        }

        i2c_message_done();
    }
}

#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wmissing-noreturn"

int main() {
    i2c_initialize();
    display::init();
    relay::init();
    receiver433::init();

    if (WATCHD0G_ENABLE) {
        wdt_enable(WDTO_250MS);
    }

    sei();

    while (true) {
        receiver433::pool();

        processI2cCommands();

        if (WATCHD0G_ENABLE) {
            wdt_reset();
        }
    }
}

#pragma clang diagnostic pop