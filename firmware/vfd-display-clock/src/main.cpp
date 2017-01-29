#include "display.hpp"
#include "relay.hpp"

#include <avr/io.h>
#include <avr/wdt.h>
#include <util/delay.h>
#include <avr/interrupt.h>

using namespace octoglow::vfd_clock;

static void processCommands(uint8_t *rxbuf, uint8_t *txbuf) {
}

int main() {
    display::init();
    relay::init();

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