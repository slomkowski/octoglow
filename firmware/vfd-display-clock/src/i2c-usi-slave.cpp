#include "i2c-usi-slave.hpp"

#include <avr/io.h>
#include <avr/interrupt.h>

#define DEBUG 0

#if DEBUG
#define SetD(x)  PORTD &= ~(1<<(x))
#define ClrD(x)  PORTD |=  (1<<(x))
#define StateD() PORTD = (PORTD & ~0x3F) | ~(usi_state & 0x3F)
#else
#define SetD(x)
#define ClrD(x)
#define StateD()
#endif

static uint8_t i2c_address;
static uint8_t i2c_wrbuf_len;

static volatile uint8_t *i2c_rdbuf;
static volatile uint8_t i2c_rdlen, i2c_rdptr;
static volatile uint8_t *i2c_wrbuf;
static volatile uint8_t i2c_wrlen;

static volatile uint8_t usi_state;

#define USI_Stopped     0
#define USI_CheckAddress (1<<0)    // address received, read and compare
#define USI_SendData     (1<<1)    // master read mode: write data
#define USI_RequestReply (1<<2)    // master read mode: sample ack
#define USI_CheckReply     (1<<3)    // master read mode: test for ack/nack
#define USI_RequestData     (1<<4)    // master write mode: prepare to receive data
#define USI_SendAck     (1<<5)    // master write mode: read data and send ack/nack

#define USI_MasterRead     (USI_SendData | USI_RequestReply | USI_CheckReply)
#define USI_MasterWrite  (USI_RequestData | USI_SendAck)

/****************************************************************************
 * Hardware dependencies.
 ****************************************************************************/

#if defined(__AVR_ATtiny2313__)
#define USI_DDR             DDRB
#define USI_PORT            PORTB
#define USI_PIN             PINB
#define USI_SDA        	PB5
#define USI_SCL        	PB7
#elif defined(__AVR_ATtiny25__) | defined(__AVR_ATtiny45__) | defined(__AVR_ATtiny85__)
#define USI_DDR             DDRB
#define USI_PORT            PORTB
#define USI_PIN             PINB
#define USI_SDA        	PB0
#define USI_SCL        	PB2
#elif defined(__AVR_ATtiny26__)
#define USI_DDR             DDRB
#define USI_PORT            PORTB
#define USI_PIN             PINB
#define USI_SDA            PB0
#define USI_SCL            PB2
#elif defined(__AVR_ATmega165__) | \
    defined(__AVR_ATmega325__) | defined(__AVR_ATmega3250__) | \
    defined(__AVR_ATmega645__) | defined(__AVR_ATmega6450__) | \
    defined(__AVR_ATmega329__) | defined(__AVR_ATmega3290__) | \
    defined(__AVR_ATmega649__) | defined(__AVR_ATmega6490__)
#define USI_DDR             DDRE
#define USI_PORT            PORTE
#define USI_PIN             PINE
#define USI_SDA        	PE5
#define USI_SCL        	PE4
#elif defined(__AVR_ATmega169__)
#define USI_DDR             DDRE
#define USI_PORT            PORTE
#define USI_PIN             PINE
#define USI_SDA        	PE5
#define USI_SCL        	PE4
#else
#error Unknown AVR type
#endif

#define setInput()            USI_DDR &= ~(1<<USI_SDA)
#define setOutput()            USI_DDR |= (1<<USI_SDA)

// set USI control register, TWI mode, auto-extend mode for SCL when overflow interupt enabled
#define setUSICR(start, ovf)        USICR = (start)<<USISIE | (ovf)<<USIOIE | 1<<USIWM1|(ovf)<<USIWM0 | 1<<USICS1|0<<USICS0|0<<USICLK | 0<<USITC

// set USI status register, clear overflow flag, optionally clear other flags, set counter
#define setUSISR(start, stop, bits)    USISR = (start)<<USISIF | 1<<USIOIF | (stop)<<USIPF | 0<<USIDC | (16-2*(bits))<<USICNT0

#define enableStart_disableOverflow()    setUSICR(1,0)
#define enableStart_enableOverflow()    setUSICR(1,1)

#define clearStartStop_setCounter(n)    setUSISR(1,1,n)
#define setTransmit(n)            setOutput(), setUSISR(0,0,n)
#define setReceive(n)            setInput(), setUSISR(0,0,n)

static void usi_reset(void) {
    usi_state = USI_Stopped;
    setInput();
    enableStart_disableOverflow();
    clearStartStop_setCounter(1);
}

/****************************************************************************
 * USI start condition detected.
 * Start the state machine.
 * Prepare to receive the slave address.
 ****************************************************************************/
ISR(USI_STRT_vect) {
    SetD(6);

    setInput();

    while (bit_is_set(USI_PIN, USI_SCL)) {
        //
        // Wait while SCL is high and test for a stop condition.  Do
        // not use the hardware stop condition detector, as it could
        // still be set from prior activities.  There is no place were
        // we can reset the stop condition without a possible race
        // condition and we could be caught here waiting for the next
        // transmission.
        //
        if (bit_is_set(USI_PIN, USI_SDA)) {
            usi_reset();
            return;
        }
    }

    //
    // There is some chance for a race condition.  When the start
    // condition, which triggered this interrupt, gets aborted and the
    // next start sequence is seen before we reset the interrupt flag,
    // the second start sequence gets unnnoticed and thus NACKed.
    // Since the master likely retries transmissions, nothing gets
    // lost however.
    //
    clearStartStop_setCounter(8);
    enableStart_enableOverflow();
    usi_state = USI_CheckAddress;

    ClrD(6);
    StateD();
}

/****************************************************************************
 * USI counter overflow interrupt.
 * The USI state machine.
 ****************************************************************************/
ISR(USI_OVF_vect) {
    if (usi_state == USI_CheckAddress) {
        //
        // Slave address received.
        //
        uint8_t addr = USIDR;
        if (addr == 0 || (addr & ~1) == i2c_address) {    // global address or ours?
            if (addr & 1) {                // master read mode?
                if (i2c_rdlen == 0)            // abort if buffer empty
                    goto abort;
                i2c_rdptr = 0;
                usi_state = USI_SendData;
            } else {                    // master write mode
                if (i2c_wrlen != 0)            // abort if buffer full
                    goto abort;
                i2c_rdlen = 0;                // ignore subsequent master reads
                usi_state = USI_RequestData;
            }
            USIDR = 0;                    // ACK = send 1 bit low
            setTransmit(1);
        } else
            goto abort;

    } else if (usi_state == USI_SendData) {
        //
        // Master read mode.
        // Send data byte from master read buffer.
        //
        send:
        USIDR = i2c_rdbuf[i2c_rdptr];
        if (++i2c_rdptr >= i2c_rdlen)            // repeat data on overflow
            i2c_rdptr = 0;
        setTransmit(8);                    // send 8 bits
        usi_state = USI_RequestReply;

    } else if (usi_state == USI_RequestReply) {
        //
        // Master read mode.
        // Expect acknowledge from master.
        //
        USIDR = 0;                    // receive 1 bit
        setReceive(1);
        usi_state = USI_CheckReply;

    } else if (usi_state == USI_CheckReply) {
        //
        // Master read mode.
        // Check received acknowledge bit.
        //
        if (USIDR)                    // abort on NACK
            goto abort;
        goto send;                    // else send next byte

    } else if (usi_state == USI_RequestData) {
        //
        // Master write mode.
        // Expect data from master.
        //
        setReceive(8);                    // receive 8 bits
        usi_state = USI_SendAck;

    } else if (usi_state == USI_SendAck) {
        //
        // Master write mode.
        // Store data, send acknowledge.
        //
        if (i2c_wrlen >= i2c_wrbuf_len)            // abort on overflow
            goto abort;
        i2c_wrbuf[i2c_wrlen++] = USIDR;
        USIDR = 0;                    // ACK = send 1 bit low
        setTransmit(1);
        usi_state = USI_RequestData;
    }
    StateD();
    return;

    abort:
    usi_reset();
    SetD(6);
    StateD();
}

bool i2c::UsiSlave::idle() {
    return usi_state == USI_Stopped;
}

uint8_t i2c::UsiSlave::messageReady() {
    uint8_t r = i2c_wrlen;
    cli();                        // avoid race condition
    if (usi_state != USI_Stopped) {            // transmission still pending
        if (bit_is_set(USISR, USIPF)) {            // and stop condition flag set?
            usi_reset();                // 	then terminate transmission
        } else if (usi_state & USI_MasterWrite) {    // or in master write transmission?
            r = 0;                    //	yes, USI busy
        }
    }
    sei();
    return r;
}

void i2c::UsiSlave::finishMessage() {
    i2c_wrlen = 0;
}

bool i2c::UsiSlave::isReplyReady() {
    cli();
    if (usi_state & USI_MasterRead) {
        sei();
        return false;
    }
    return true;
}

void i2c::UsiSlave::finishReply(const uint8_t length) {
    i2c_rdlen = length;
    sei();
}

i2c::UsiSlave::UsiSlave(const uint8_t address,
                        volatile uint8_t *const readBuffer,
                        volatile uint8_t *const writeBuffer,
                        const uint8_t writeBufferSize) {
    i2c_rdbuf = readBuffer;
    i2c_wrbuf = writeBuffer;
    i2c_address = address;
    i2c_wrbuf_len = writeBufferSize;

    USI_PORT |= _BV(USI_SCL);                // SCL inactive
    USI_PORT |= _BV(USI_SDA);                // SDA inactive
    USI_DDR |= _BV(USI_SCL);                // SCL output

    i2c_rdlen = 0;
    i2c_wrlen = 0;

    usi_reset();
}













