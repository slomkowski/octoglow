#!/usr/bin/env bash

avrdude -c usbasp -p t461 -U flash:w:cmake-build-debug/octoglow-clock-display.hex
