#!/usr/bin/env python3

import smbus
import sys

if len(sys.argv) == 1:
    print("Usage:\n%s {value}" % sys.argv[0])
    sys.exit(1)

value = int(sys.argv[1])

print("ADC: %d" % value)

bus = smbus.SMBus(4)

bus.write_i2c_block_data(0x4f, 0x12, [value, 0])
