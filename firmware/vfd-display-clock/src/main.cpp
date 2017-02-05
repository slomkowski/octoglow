#include "display.hpp"
#include "relay.hpp"
#include "receiver433.hpp"

#include <avr/io.h>
#include <avr/wdt.h>
#include <util/delay.h>
#include <avr/interrupt.h>

using namespace octoglow::vfd_clock;

constexpr uint8_t I2C_ADDRESS = 0x43;

constexpr uint8_t I2C_WRITE_BUFFER_LENGTH = 8;
constexpr uint8_t I2C_READ_BUFFER_LENGTH = 5;

static volatile uint8_t i2cWriteBuffer[I2C_WRITE_BUFFER_LENGTH];
static volatile uint8_t i2cReadBuffer[I2C_READ_BUFFER_LENGTH];

int main() {
    display::init();
    relay::init();
    receiver433::init();

#if WATCHD0G_ENABLE
    wdt_enable(WDTO_120MS);
#endif

    sei();


    // display::setAllCharacters("2137");

    //display::setDots(display::UPPER_DOT);

    while (true) {

#if WATCHD0G_ENABLE
        wdt_reset();
#endif
    }
}