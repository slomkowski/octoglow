# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Firmware of the Geiger counter and magic eye board of the Octoglow VFD device
(https://slomkowski.eu/octoglow-vfd-fallout-inspired-display/), C++17 for MSP430G2553 (16 kB flash, 512 B RAM).
The board drives two high-voltage boost inverters (400 V for the Geiger tube, an adjustable one for the magic eye),
counts Geiger discharges and animates the magic eye. It is an I2C slave (`0x18`) of `octoglowd`
(`software/octoglowd`, Kotlin, class `Geiger`), which owns the other side of the protocol.
Depends on `../../lib/libfixmath` (fixed-point PID).

## Commands

Two CMake build directories are used: `cmake-build-msp430` (whole project configured with `msp430-elf-g++`,
Release; host tests and animationtest are skipped automatically) and `cmake-build-debug` (host compiler,
tests). The toolchain is TI msp430-gcc 9.3 in `/opt/ti/mspgcc`.

```bash
cmake -S . -B cmake-build-msp430 -DCMAKE_C_COMPILER=msp430-elf-gcc -DCMAKE_CXX_COMPILER=msp430-elf-g++ -DCMAKE_BUILD_TYPE=Release
cmake --build cmake-build-msp430          # application (.elf + .hex) and bootloader, prints sizes

cmake -S . -B cmake-build-debug
cmake --build cmake-build-debug --target octoglow-geiger-test
./cmake-build-debug/test/octoglow-geiger-test                          # GoogleTest, ASan + UBSan
./cmake-build-debug/test/octoglow-geiger-test --gtest_filter='Twiboot.*'   # single suite/test

./burn.sh               # first installation with the programmer (mspdebug rf2500): application + bootloader
./burn-over-ssh.sh      # build, copy to the `octoglow` host, octoglowd --burn-firmware geiger (deploy octoglowd first)
./flash-over-i2c.sh     # on the host with octoglowd stopped, uses the twiboot tool
```

`animationtest/` is an SDL2 desktop viewer of the magic eye animation (space triggers a count).

## Architecture

- `noarch/` is hardware-independent logic compiled both for MSP430 and for the host tests; `msp430/*_hd.cpp`
  implement the `hd` namespaces / hardware functions declared in `noarch` headers (registers, ISRs).
  Tests provide their own stubs of the `hd` functions inside the test files.
- Timing: Timer_A0 runs the Geiger PWM at 40 kHz; its CCR0 ISR polls the discharge state machine
  (`geiger_counter::pollGeigerCounterState`) and sets `timerTicked` every 10 ms. The main loop runs the
  `tick()` functions at `TICK_TIMER_FREQ` (100 Hz), processes I2C and feeds the watchdog. Timer_A1 drives the eye PWM.
  Timers are clocked from an external 5.7 MHz clock on XIN (`TIMER_CLOCK_SOURCE_FREQ`), MCLK is the DCO at
  16 MHz from the calibration in info memory segment A.
- `inverter`: ADC10 samples both inverter voltages in an ISR; `tick()` regulates both PWM duty cycles with
  `FastPID`. `setPwmOutputsToSafeState()` (geiger PWM low, eye PWM high) must happen before anything else
  at startup and before any reset.
- `geiger-counter`: PORT2 ISR on the tube pulse edges plus the 40 kHz poll implement a discharge state machine
  with time windows; counts are accumulated per configurable cycle (`GeigerState`).
- `magiceye`: heater preheat state machine (`EyeInverterState`) before enabling the eye inverter; the eye value is
  written to an external DAC by bit-banging; in `ANIMATION` mode `animation.cpp` reacts to new counts.
- I2C protocol (`noarch/protocol.hpp`, `noarch/i2c-slave.cpp`): USCI ISRs only fill a 16-byte buffer; the command
  is processed in the main loop after STOP (`processDataIfAvailable`). Frames are `[crc8, command, payload...]`
  (CRC-8 CCITT over everything after byte 0); the reply is `[crc8, command, data...]` read in a separate
  transaction. The command is dispatched by its length (`bytesProcessed`). Packed structs must keep the sizes
  asserted in `protocol.hpp`, octoglowd parses them byte by byte.

## Bootloader

`bootloader/` is a twiboot-compatible I2C bootloader (address `0x19`), see `README.md` and the header comment of
`bootloader/twiboot.hpp` for the protocol details. Points that affect any change in the firmware:

- Flash layout: application `0xC000`-`0xFBDF`, its vector table `0xFBE0`-`0xFBFF` (`msp430/application.ld.in`),
  bootloader `0xFC00`-`0xFFFF` with the real vector table, whose entries jump through the application's one.
  `BOOTLOADER_START` in `CMakeLists.txt` configures both linker scripts, the bootloader and the tests;
  changing it requires reflashing with the programmer. The linker enforces the application size.
- The bootloader is built by a custom command with `-Os -flto -nostartfiles` and its own linker script: no `.data`
  allowed (asserted), `.bss` cleared manually, no interrupts, polled USCI (`bootloader/usci-slave.cpp`).
  Only `bootloader/twiboot.cpp` (protocol) is hardware-independent and host-tested with a simulated flash.
- The application enters it on `ENTER_BOOTLOADER` (`0x08`, payload `BOOT`) and resets 100 ms later by writing
  `WDTCTL` without the password. The command number and CRC are hardcoded in `flash-over-i2c.sh`.
- Information memory (DCO calibration) must never be erased; the bootloader can't update itself.
