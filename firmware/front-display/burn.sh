#!/usr/bin/env bash

# application + bootloader, see README.md; efuse 0xf8: boot section 1024 words, reset starts the bootloader
avrdude -c usbasp -p m88p -U efuse:w:0xf8:m -U flash:w:cmake-build-debug/avr/octoglow-front-display-avr-with-bootloader.hex
