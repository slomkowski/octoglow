#!/usr/bin/env bash

# application + bootloader, see README.md; prog erases the main flash, load writes the bootloader to its own segments
sudo mspdebug rf2500 \
    "prog cmake-build-msp430/msp430/octoglow-geiger-msp430" \
    "load cmake-build-msp430/msp430/octoglow-geiger-bootloader.elf" \
    "verify cmake-build-msp430/msp430/octoglow-geiger-bootloader.elf"
