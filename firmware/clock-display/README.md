# Octoglow VFD - clock display board firmware

Compiled under *avr-gcc*, *CMake* required. *burn.sh* - flashing.

More details under https://slomkowski.eu/octoglow-vfd-fallout-inspired-display/

## Bootloader

The firmware can be updated over I2C. The bootloader in `bootloader/` is a C++ port of
[twiboot](https://github.com/orempel/twiboot) (GPLv2) reduced to ATtiny461A with USI, compatible with its protocol
and host tool. ATtiny461A has no boot section, so the bootloader lives at `0x0C40`-`0x0FFF` and patches the
vector table: the reset vector jumps to the bootloader and the `EE_RDY` vector (must not be used by the
application) jumps to the application. The application is limited to 3136 bytes, enforced by the linker.
USI pins, register settings and both I2C addresses are shared by the application and the bootloader in `common/usi.hpp`.

* After every reset, the bootloader listens on I2C address `0x11` for 1 s, then starts the application.
  Meanwhile, the display shows `BOOT` (8, 0, 0 and T as a mirrored 7), the dots blink while the host talks to it.
* The application enters the bootloader on command `ENTER_BOOTLOADER` (`0x07`) with payload `BOOT`:
  it replies, stops feeding the watchdog and resets within 250 ms.

First installation (ISP): `burn.sh` writes `octoglow-clock-display-with-bootloader.hex` (application + bootloader
with the vector table already patched) and sets `efuse` to `0xfe` (self-programming enabled).
Note that flashing only `octoglow-clock-display.hex` with ISP erases the bootloader.

Update over I2C: `flash-over-i2c.sh <app.hex> <bus>` on the host, with *octoglowd* stopped.
