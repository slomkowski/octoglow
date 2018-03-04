# Geiger board

todo jak skompilować projekt

* msp430 toolchain
* gtest
* gcc


# Bus pirate example

Write address *0x24*, read *0x25*.

```
W - enable power supply
P - enable pull-up resistors

[ 0x24 1 [ 0x25 r:8 ] - read device state
[ 0x24 2 [ 0x25 r:6 ]


```

## Animation test - `animationtest`

This program was used to easily test magic eye animation procedure. Uses code from `libsrc`. Requires SDL2 library.

* space - simulate Geiger count
* Esc - exit

## Animation idea

wartość średnia

## I2C protocol specification


status:
napięcie geiger
status przetwornicy eye
napięcie przetwornicy eye
status kontrolera eye: animation, fixed val
wartość kontrolera eye, jeśli fixed val


status geiger:
ilość zliczeń w bieżącym cyklu
ilość zliczeń w poprzednim cyklu
dł. cyklu w s


jakie komendy:
odczyt statusu:
odczyt statusu geiger
włączenie/wyłączenie oka
reset licznika geigera
tryb pracy oka: tryb + wartość dla fixed



## Read device status

Send *0x1* then read 8 bytes. Structure `DeviceState` is returned.


## Read Geiger counter status

Send *0x2* then read 6 bytes. Structure `GeigerState` is returned.


## Clean Geiger counter status

Clears all counters. Send `0x3`.


## Enable/disable magic eye 

```
0x4 | enabled

enabled:
0 - eye disabled (default)
1 - eye enabled
```

# Set magic eye animation mode

```
0x5 | mode | value

mode:
0 - animation. value is ignored
1 - fixed value

value:
0-255 - DAC output
```
