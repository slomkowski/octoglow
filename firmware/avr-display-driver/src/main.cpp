#include "display.hpp"
#include "speaker.hpp"
#include "encoder.hpp"

#include <avr/io.h>
#include <avr/wdt.h>
#include <util/delay.h>
#include <avr/interrupt.h>
#include <avr/pgmspace.h>

#include <i2c-slave.hpp>

using namespace octoglow::vfd_front;

static void processCommands(uint8_t *rxbuf, uint8_t *txbuf) {
}

int main() {
    vfd::encoder::init();
    display::init();
    speaker::init();

    // i2c
    TWI_Slave_Initialise((unsigned char) ((I2C_SLAVE_ADDRESS << TWI_ADR_BITS) | (1 << TWI_GEN_BIT)), processCommands);
    TWI_Start_Transceiver();

#if WATCHD0G_ENABLE
    wdt_enable(WDTO_120MS);
#endif

    sei();
//    vfd::write("aącćzźżłl żółwia maść ZŻÓOŁWIA MĄKA");
//     vfd::write("abcdefghijklmnoprst");
    display::setBrightness(5);

    display::writeStaticText_P(1, 12, PSTR("xD:"));
    display::writeScrollingText(1, 21, 5, "aącćzźżłl żółwia maść ZŻÓOŁWIA MĄKA");

    display::writeScrollingText_P(0, 4, 10,
                                  PSTR("no i ja się pytam człowieku dumny ty jesteś z siebie zdajesz sobie sprawę z tego co robisz?"
                                               "masz ty wogóle rozum i godnośc człowieka?"));

    while (true) {
        display::pool();
        speaker::pool();
        vfd::encoder::pool();


#if WATCHD0G_ENABLE
        wdt_reset();
#endif
    }
}