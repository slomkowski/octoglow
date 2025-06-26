#!/usr/bin/env bash

avrdude -c usbasp -p m88p -U flash:w:avr/octoglow-front-display-avr.hex
