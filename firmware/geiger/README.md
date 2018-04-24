# Geiger board

Project is divided to subprojects:
* *noarch* - architecture-agnostic logic of the program. Covered by unit tests under *test*
* *msp430* - hardware-dependent code
* *test* - x86 code of unit tests, requires Google Test
* *animationtest* - x86 simulator of the magic eye, helpful for visualising eye animations. Requires SDL2 library.

## Bus pirate example

Write address *0x24*, read *0x25*.

```
W - enable power supply
P - enable pull-up resistors

[ 0x24 1 [ 0x25 r:8 ] - read device state
[ 0x24 2 [ 0x25 r:7 ]


```

## Magic eye animation simulator - *animationtest*

This program was used to easily test magic eye animation procedure. Uses code from `noarch`. Requires SDL2 library.

* *space* - simulate Geiger count
* *Esc* - exit


## I2C protocol specification

Device is controlled by I2C bus. Following commands are supported.

### Read device status

Send *0x1* then read 8 bytes. Structure `DeviceState` is returned.


### Read Geiger counter status

Send *0x2* then read 7 bytes. Structure `GeigerState` is returned.


### Set Geiger counter configuration

```
0x3 | cycleLength
```

* `cycleLength - 2 bytes - length of the measuring cycle in seconds.


### Clean Geiger counter status

Clears all counters. Send *0x3*.


### Set magic eye configuration

```
0x4 | enabled | brightness | mode
```

* `enabled` - 1 byte: `0` - eye disabled (default), `1` - eye enabled
* `brightness` - 1 byte: `0` - `5`
* `mode` - 1 byte: `0` - animation mode, `1` - fixed value mode


### Set magic eye fixed value

```
0x6 | value
```

* `value` - 1 byte: `0` - `255` - DAC output

