# Octoglow VFD - Geiger counter and magic eye board firmware

Written in C++ for MSP430. Depends on *libfixmath* to perform fixed-point arithmetic.

More details under https://slomkowski.eu/octoglow-vfd-fallout-inspired-display/

Tests (`test/`) run on the host with GoogleTest, ASan and UBSan.

## Bootloader

The firmware can be updated over I2C. The bootloader in `bootloader/` is a C++ port of
[twiboot](https://github.com/orempel/twiboot) (GPLv2) for MSP430G2553 with USCI_B0, compatible with its protocol,
like the ones of the clock display and the front display. MSP430 has no boot section, so the bootloader lives at the end
of the flash (`0xFC00`-`0xFFFF`, `BOOTLOADER_START` in `CMakeLists.txt`) together with the real vector table.
The reset vector starts the bootloader, the other vectors jump through the vector table of the application,
which is linked just below the bootloader (`0xFBE0`, see `msp430/application.ld.in`); it costs 3 cycles per interrupt.
The bootloader is never erased. The application is limited to 15 kB including its vector table, enforced by the linker.
The protocol logic (`bootloader/twiboot.cpp`) is covered by the host tests.

* After every reset, the bootloader listens on I2C address `0x19` for 1 s, then starts the application.
  The PWM outputs of the inverters and the heaters of the magic eye are switched off meanwhile.
* The application enters the bootloader on command `ENTER_BOOTLOADER` (`0x08`) with payload `BOOT`:
  it replies and resets (invalid watchdog password) 100 ms later.
* The addresses of the protocol are relative to the start of the flash (`0xC000`), the host subtracts it.
  The page has 64 bytes, the 512-byte segment is erased when its first page is written. The page is written after STOP,
  the host waits 30 ms before reading it back.
* Writing page 0 erases the vector table of the application, its reset vector is written as the last page.
  Without it, the bootloader doesn't time out, so an interrupted update can be repeated.
* The information memory isn't touched, the segment A holds the DCO calibration both the bootloader and the
  application rely on.

First installation (programmer): `burn.sh` writes the application and the bootloader.
Note that programming only the application with `mspdebug prog` erases the bootloader.

Update over I2C:

* `burn-over-ssh.sh` builds the firmware and burns it with `octoglowd --burn-firmware geiger` on the `octoglow` host.
* `flash-over-i2c.sh <app.hex> <bus>` on the host, with *octoglowd* stopped, uses the twiboot tool.
