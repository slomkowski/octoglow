#include "vfd.hpp"

#include <avr/io.h>
#include <avr/wdt.h>
#include <util/delay.h>
#include <avr/interrupt.h>


static void processCommands(uint8_t *rxbuf, uint8_t *txbuf) {
}

int main() {
    vfd::init();

#if WATCHD0G_ENABLE
    wdt_enable(WDTO_120MS);
#endif

    sei();


    while (true) {

#if WATCHD0G_ENABLE
        wdt_reset();
#endif
    }
}