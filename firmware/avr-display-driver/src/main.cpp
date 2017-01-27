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

static const uint8_t GRAPHICS_HEART[] PROGMEM = {0x00, 0x06, 0x09, 0x11, 0x22, 0x22, 0x11, 0x09, 0x06, 0x00};
static const uint8_t GRAPHICS_STRIKETHROUGH[] PROGMEM = {0x01};

static void showDemoOnDisplay() {
    display::clear();
    display::setBrightness(3);
    display::writeStaticText_P(0, 6, PSTR("Lorem:"));
    display::writeStaticText_P(7, 12, PSTR(__DATE__));

    display::setUpperBarContent(0b111101101110000000);

    display::writeScrollingText_P(0, 21, 10, PSTR("Zażółć gęślą jaźń! no i ja się pytam człowieku dumny "
                                                          "ty jesteś z siebie zdajesz sobie sprawę z tego co robisz?"
                                                          "masz ty wogóle rozum i godnośc człowieka?"));

    display::drawGraphics_P(5 * 34, 10, false, GRAPHICS_HEART);
    for (uint8_t c = 5; c < 25; ++c) {
        display::drawGraphics_P(c, 1, true, GRAPHICS_STRIKETHROUGH);
    }
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

    showDemoOnDisplay();

    while (true) {
        display::pool();
        speaker::pool();
        vfd::encoder::pool();


#if WATCHD0G_ENABLE
        wdt_reset();
#endif
    }
}