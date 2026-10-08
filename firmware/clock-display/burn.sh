#!/usr/bin/env bash

# application + bootloader, see README.md; efuse 0xfe enables self-programming used by the bootloader
avrdude -c usbasp -p t461 -U efuse:w:0xfe:m -U flash:w:cmake-build-debug/octoglow-clock-display-with-bootloader.hex
