#!/usr/bin/env bash

avrdude -c usbasp -p m88p -U flash:w:cmake-build-debug/avr/front-display-avr.hex
