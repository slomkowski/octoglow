#include "light-sensor.hpp"

#include <avr/io.h>
#include <avr/interrupt.h>

constexpr uint8_t ADC_SAMPLES_SIZE = 10;

static uint16_t adcBuffer[ADC_SAMPLES_SIZE];
static uint8_t adcBufferIndex = 0;

void octoglow::vfd_clock::lightsensor::init() {
    PORTA &= ~_BV(PA4); // disable pull-up
    DDRA &= ~_BV(PA4); // Set PA4 as input

    DIDR0 |= _BV(ADC3D); // disable digital input buffer for ADC4
    ADMUX = _BV(MUX1) | _BV(MUX0);

    ADCSRA = _BV(ADEN) | _BV(ADSC) | _BV(ADATE) | _BV(ADIE) | _BV(ADPS2) | _BV(ADPS1) | _BV(ADPS0);
}

ISR(ADC_vect) {
    adcBuffer[adcBufferIndex] = ADCW;
    adcBufferIndex = (adcBufferIndex + 1) % ADC_SAMPLES_SIZE;
}

uint16_t octoglow::vfd_clock::lightsensor::getMeasurement() {
    uint16_t sum = 0;

    for (uint8_t i = 0; i < ADC_SAMPLES_SIZE; i++) {
        sum += adcBuffer[i];
    }

    return sum / ADC_SAMPLES_SIZE;
}
