# Octoglow VFD - main display board firmware

More details under https://slomkowski.eu/octoglow-vfd-fallout-inspired-display/

Compiled under *avr-gcc*, *CMake* required. Tests (`test/`) run on the host with GoogleTest, ASan and UBSan.

## Display timing

The display is multiplexed: each of the 40 characters gets a 130 µs slot timed by Timer1 (192 Hz for the whole
display). The brightness is the on-time of CL (PB2, OC1B), cleared by Timer1 in hardware, so neither the brightness
nor the refresh rate depends on the generated code. The scrolling text moves by one column every 39 ms, counted
by the 1 ms tick of Timer0. The values reproduce the firmware which timed both by the code (the -O2 build).

## Bootloader

The firmware can be updated over I2C. The bootloader in `bootloader/` is a C++ port of
[twiboot](https://github.com/orempel/twiboot) (GPLv2) for ATmega88P with the TWI peripheral, compatible with its
protocol and host tool, like the one of the clock display. It lives in the boot section at `0x1800`-`0x1FFF`
(`BOOTSZ` = 1024 words), `BOOTRST` fuse makes every reset start it, so the vector table isn't patched.
The application is limited to 6144 bytes, enforced by the linker. The protocol logic (`bootloader/twiboot.cpp`)
is covered by the host tests.

* After every reset, the bootloader listens on I2C address `0x15` for 1 s, then starts the application.
  Meanwhile, the display shows `BOOTLOADER`, the upper bar blinks while the host talks to it.
* The application enters the bootloader on command `ENTER_BOOTLOADER` (`0x0a`) with payload `BOOT`:
  it replies, shows `Entering bootloader`, stops feeding the watchdog and resets within 250 ms.

First installation (ISP): `burn.sh` writes `octoglow-front-display-avr-with-bootloader.hex` (application + bootloader)
and sets `efuse` to `0xf8` (`BOOTRST` programmed, `BOOTSZ` = 1024 words).
Note that flashing only `octoglow-front-display-avr.hex` with ISP erases the bootloader.

Update over I2C:

* `burn-over-ssh.sh` builds the firmware and burns it with `octoglowd --burn-firmware front-display` on the `octoglow` host.
* `flash-over-i2c.sh <app.hex> <bus>` on the host, with *octoglowd* stopped, uses the twiboot tool.
