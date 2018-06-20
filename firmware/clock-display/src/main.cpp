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

static inline void processI2cCommands() {
    using namespace octoglow::vfd_clock::protocol;

    const auto command = static_cast<Command >(i2c_wrbuf[0]);

    if (i2c_reply_ready()) {

        i2c_rdbuf[0] = i2c_wrbuf[0];

        if (command == Command::GET_WEATHER_SENSOR_STATE) {
            for (uint8_t i = sizeof(protocol::WeatherSensorState); i != 0; --i) {
                i2c_rdbuf[i] = reinterpret_cast<uint8_t *>(&(receiver433::currentWeatherSensorState))[i - 1];
            }
            receiver433::currentWeatherSensorState.alreadyRead = true;
            i2c_reply_done(sizeof(protocol::WeatherSensorState) + 1);
        }

        i2c_wrbuf[0] = 0;
    }

    if (i2c_message_ready()) {

        switch (command) {
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
        }

        i2c_message_done();
    }
}

int main() {
    i2c_initialize();
    display::init();
    relay::init();
    receiver433::init();

    if (WATCHD0G_ENABLE) {
        wdt_enable(WDTO_250MS);
    }

    sei();

    //display::setAllCharacters("2137");
    //display::setDots(protocol::UPPER_DOT);

    while (true) {
        receiver433::pool();

        processI2cCommands();

//        if (receiver433::currentWeatherSensorState.temperature != 0) {
//            char text[5];
//
//            sprintf(text, "%2d %d", receiver433::currentWeatherSensorState.temperature / 10, receiver433::currentWeatherSensorState.temperature % 10);
//            display::setAllCharacters(text);
//            display::setDots(protocol::LOWER_DOT);
//        }

        if (WATCHD0G_ENABLE) {
            wdt_reset();
        }
    }
}