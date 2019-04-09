#!/usr/bin/env python3

import smbus
import sys

GEIGER_ADDR = 0x12

if len(sys.argv) == 1:
    print("Usage:\n%s {0/1}" % sys.argv[0])
    sys.exit(1)

brightness = int(sys.argv[1])

print("Brightness: %d" % brightness)

bus = smbus.SMBus(4)

bus.write_i2c_block_data(GEIGER_ADDR, 7, [brightness])
