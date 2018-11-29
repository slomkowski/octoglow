#pragma once

#include <avr/io.h>
#include <avr/pgmspace.h>

/*
   #######
   ####### Configuration
   #######
*/

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