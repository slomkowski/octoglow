#pragma once

#include <avr/io.h>
#include <avr/pgmspace.h>

/*
   #######
   ####### Configuration
   #######
*/

#define I2C_SLAVE_ADDRESS 0x40
#define I2C_BUFFER_SIZE 10      // Reserves memory for the drivers transceiver buffer.

#define WATCHDOG_ENABLE 0

#include <stdint.h>

/*
   #######
   ####### Useful macros
   #######
*/

#define PORT(x) XPORT(x)
#define XPORT(x) (PORT##x)
// *** Pin
#define PIN(x) XPIN(x)
#define XPIN(x) (PIN##x)
// *** DDR
#define DDR(x) XDDR(x)
#define XDDR(x) (DDR##x)