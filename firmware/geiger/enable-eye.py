#!/usr/bin/env python3

import smbus
import sys

GEIGER_ADDR = 0x12

if len(sys.argv) == 1:
    print("Usage:\n%s {0/1}" % sys.argv[0])
    sys.exit(1)

should_enable = sys.argv[1] == "1"

print("Enable: %d" % should_enable)

bus = smbus.SMBus(1)

bus.write_i2c_block_data(GEIGER_ADDR, 5, [1 if should_enable else 0, 3, 0])
