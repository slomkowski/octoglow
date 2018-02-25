# Geiger board

todo jak skompilować projekt

* msp430 toolchain
* gtest
* gcc

## I2C protocol sprecification


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
