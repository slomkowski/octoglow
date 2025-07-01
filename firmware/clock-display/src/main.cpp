#include "display.hpp"
#include "relay.hpp"
#include "receiver433.hpp"
#include "i2c-slave.hpp"
#include "protocol.hpp"

#include <avr/io.h>
#include <avr/wdt.h>
#include <avr/interrupt.h>
#include <util/crc16.h>

constexpr bool WATCHDOG_ENABLE = true;
constexpr bool VERIFY_INPUT_I2C_COMMANDS_CRC8 = true;


using namespace octoglow::vfd_clock;
using namespace octoglow::vfd_clock::protocol;

static auto currentCommand = Command::NONE;

/**
 *
 * @param dataPayloadLength We include command byte and the following payload. Payload length can be 0, in this case only command byte is taken to the calculation.
 * @return if CRC calculated locally matches the sent one via I2C
 */
static bool checkCrc8(const uint8_t dataPayloadLength) {
    if constexpr (!VERIFY_INPUT_I2C_COMMANDS_CRC8) {
        return true;
    }

    uint8_t calculatedCrcValue = 0;
    for (uint8_t i = 1; i < dataPayloadLength + 2; ++i) {
        calculatedCrcValue = _crc8_ccitt_update(calculatedCrcValue, i2c_wrbuf[i]);
    }
    return i2c_wrbuf[0] == calculatedCrcValue;
}

static void processI2cReadCommands() {
    if (!i2c_reply_ready()) {
        return;
    }

    using namespace octoglow::vfd_clock::protocol;

    i2c_rdbuf[1] = i2c_wrbuf[1];

    static_assert(sizeof(WeatherSensorState) + 2 <= sizeof(i2c_rdbuf));

    switch (currentCommand) {
        case Command::GET_WEATHER_SENSOR_STATE: {
            for (uint8_t i = sizeof(WeatherSensorState) + 1; i != 1; --i) {
                i2c_rdbuf[i] = reinterpret_cast<uint8_t *>(&receiver433::currentWeatherSensorState)[i - 2];
            }
            receiver433::currentWeatherSensorState.flags |= ALREADY_READ_FLAG;

            uint8_t crcValue = 0;
            for (uint8_t i = 1; i < static_cast<uint8_t>(sizeof(WeatherSensorState)) + 2; ++i) {
                crcValue = _crc8_ccitt_update(crcValue, i2c_rdbuf[i]);
            }
            i2c_rdbuf[0] = crcValue;
            i2c_reply_done(sizeof(WeatherSensorState) + 2);
            break;
        }
        case Command::SET_BRIGHTNESS:
        case Command::SET_RELAY:
        case Command::SET_DISPLAY_CONTENT: {
            i2c_rdbuf[0] = _crc8_ccitt_update(0, i2c_rdbuf[1]);
            i2c_reply_done(2);
            break;
        }
        default:
            break;
    }

    currentCommand = Command::NONE;
}

static void processI2cWriteCommands() {
    if (!i2c_message_ready()) {
        return;
    }

    static_assert(sizeof(DisplayContent) + 2 <= sizeof(i2c_wrbuf));
    static_assert(sizeof(RelayState) + 2 <= sizeof(i2c_wrbuf));

    switch (static_cast<Command>(i2c_wrbuf[1])) {
        case Command::SET_DISPLAY_CONTENT: {
            if (!checkCrc8(sizeof(DisplayContent))) {
                break;
            }
            const auto *dc = reinterpret_cast<volatile DisplayContent *>(&i2c_wrbuf[2]);
            display::setDots(dc->dotState, false);
            display::setAllCharacters(dc->characters);
            currentCommand = Command::SET_DISPLAY_CONTENT;
        }
        break;
        case Command::SET_RELAY: {
            if (!checkCrc8(sizeof(RelayState))) {
                break;
            }
            const auto *rs = reinterpret_cast<volatile RelayState *>(&i2c_wrbuf[2]);
            relay::setState(relay::Relay::RELAY_1, rs->relay1enabled);
            relay::setState(relay::Relay::RELAY_2, rs->relay2enabled);
            currentCommand = Command::SET_RELAY;
        }
        break;
        case Command::SET_BRIGHTNESS:
            if (!checkCrc8(1)) {
                break;
            }
            display::setBrightness(i2c_wrbuf[2]);
            currentCommand = Command::SET_BRIGHTNESS;
            break;
        case Command::GET_WEATHER_SENSOR_STATE:
            if (!checkCrc8(0)) {
                break;
            }
            currentCommand = Command::GET_WEATHER_SENSOR_STATE;
            break;
        default:
            currentCommand = Command::NONE;
            break;
    }

    i2c_message_done();
}

[[noreturn]] int main() {
    // pull-up all unused pins
    PORTA |= _BV(PA0) | _BV(PA4) | _BV(PA5);
    PORTA |= _BV(PA4); // 1 wire is not used currently
    PORTB |= _BV(PB1) | _BV(PB4) | _BV(PB5) | _BV(PB7);

    i2c_initialize();
    display::init();
    relay::init();
    receiver433::init();

    if (WATCHDOG_ENABLE) {
        wdt_enable(WDTO_250MS);
    }

    sei();

    while (true) {
        receiver433::pool();

        processI2cReadCommands();
        processI2cWriteCommands();

        if (WATCHDOG_ENABLE) {
            wdt_reset();
        }
    }
}
