/*
 * I2C bootloader for the Geiger counter board, port of twiboot v3.2 by Olaf Rempel
 * (https://github.com/orempel/twiboot), GPLv2, see LICENSE in this directory.
 *
 * Lives at the end of the flash of MSP430G2553 (TWIBOOT_START-0xffff) with the real vector table.
 * The reset vector starts the bootloader, the other vectors jump through the vector table of the application,
 * which is linked just below the bootloader (twiboot::APPLICATION_VECTORS). The bootloader is never erased.
 *
 * Linked with -nostartfiles: there's no .data, .bss is cleared here, no interrupts are used.
 *
 * After reset, the bootloader waits TWIBOOT_TIMEOUT_MS for the host, then starts the application.
 * Any valid command from the host stops the countdown. Without the application, the bootloader waits forever.
 */

#include "twiboot.hpp"
#include "usci-slave.hpp"

#include <msp430.h>

using namespace octoglow::geiger::bootloader;

constexpr uint32_t MCLK_FREQ = 8000000;

// Timer_A0 counts SMCLK / 8 in the up mode
constexpr uint16_t TIMER_TICKS_PER_PERIOD = static_cast<uint32_t>(twiboot::TIMER_TICK_MS) * MCLK_FREQ / 8 / 1000;

// the flash timing generator needs 257-476 kHz
constexpr uint8_t FLASH_CLOCK_DIVIDER = 20;

static_assert(MCLK_FREQ / FLASH_CLOCK_DIVIDER >= 257000 and MCLK_FREQ / FLASH_CLOCK_DIVIDER <= 476000);
static_assert(FLASH_CLOCK_DIVIDER <= 64);

#define PWM_GEIGER BIT2 // P1.2
#define PWM_EYE BIT1 // P2.1
#define HEATER_1 BIT3 // P1.3, active low
#define HEATER_2 BIT4 // P1.4

extern "C" uint8_t __bssstart[];
extern "C" uint8_t __bssend[];

/**
 * The inverters and the heaters of the magic eye are switched off, the same as the application does
 * before anything else (inverter::setPwmOutputsToSafeState()). They stay so until the application starts.
 */
static void setOutputsToSafeState() {
    P1OUT = (P1OUT & ~(PWM_GEIGER | HEATER_2)) | HEATER_1;
    P1DIR |= PWM_GEIGER | HEATER_1 | HEATER_2;

    P2OUT |= PWM_EYE;
    P2DIR |= PWM_EYE;
}

[[noreturn]] static void startApplication() {
    const uint16_t resetVector = *reinterpret_cast<const uint16_t *>(twiboot::APPLICATION_RESET_VECTOR);
    asm volatile ("br %0" :: "r"(resetVector));
    __builtin_unreachable();
}

extern "C" [[noreturn]] __attribute__((used)) void bootloaderMain() {
    WDTCTL = WDTPW | WDTHOLD;

    setOutputsToSafeState();

    // -nostartfiles, no memset() either
    for (volatile uint8_t *p = __bssstart; p != __bssend; ++p) {
        *p = 0;
    }

    // MCLK = SMCLK = DCO, the calibration is in the information memory segment A, the application also relies on it
    BCSCTL2 = 0;
    BCSCTL1 = CALBC1_8MHZ;
    DCOCTL = CALDCO_8MHZ;

    FCTL2 = FWKEY | FSSEL_1 | (FLASH_CLOCK_DIVIDER - 1);

    TA0CCR0 = TIMER_TICKS_PER_PERIOD - 1;
    TA0CTL = TASSEL_2 | ID_3 | MC_1 | TACLR;

    twiboot::init();
    usci_slave::init();

    while (!twiboot::shouldStartApplication()) {
        usci_slave::poll();

        if (TA0CCTL0 & CCIFG) {
            TA0CCTL0 &= ~CCIFG;
            twiboot::onTimerTick();
        }
    }

    // the application expects the reset state of the peripherals it doesn't configure itself
    usci_slave::disable();
    TA0CTL = TACLR;
    TA0CCR0 = 0;
    TA0CCTL0 = 0;

    startApplication();
}

/**
 * Entry point, the reset vector.
 */
extern "C" [[noreturn]] void _start() __attribute__((naked, used, section(".text.start")));

void _start() {
    asm volatile ("mov #__stack, r1\n"
                  "br #bootloaderMain");
}

/*
 * The application's interrupt handlers are called through its vector table. Each trampoline adds 3 cycles.
 */

#define STRINGIFY(x) #x
#define TO_STRING(x) STRINGIFY(x)

#define TRAMPOLINE(n) \
    extern "C" void trampoline##n() __attribute__((naked, used)); \
    void trampoline##n() { \
        asm volatile ("br &(" TO_STRING(TWIBOOT_START) " - 32 + 2 * " #n ")"); \
    }

TRAMPOLINE(0)
TRAMPOLINE(1)
TRAMPOLINE(2)
TRAMPOLINE(3)
TRAMPOLINE(4)
TRAMPOLINE(5)
TRAMPOLINE(6)
TRAMPOLINE(7)
TRAMPOLINE(8)
TRAMPOLINE(9)
TRAMPOLINE(10)
TRAMPOLINE(11)
TRAMPOLINE(12)
TRAMPOLINE(13)
TRAMPOLINE(14)

static_assert(twiboot::APPLICATION_VECTORS == TWIBOOT_START - 32, "hardcoded in TRAMPOLINE");

using Vector = void (*)();

__attribute__((section(".vectors"), used))
static Vector const vectors[twiboot::NUMBER_OF_VECTORS] = {
    trampoline0, trampoline1, trampoline2, trampoline3, trampoline4, trampoline5, trampoline6, trampoline7,
    trampoline8, trampoline9, trampoline10, trampoline11, trampoline12, trampoline13, trampoline14,
    _start,
};
