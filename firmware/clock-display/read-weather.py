#!/usr/bin/env python3

import smbus

CLOCK_DISPLAY_ADDR = 0x10

bus = smbus.SMBus(1)

tempLo, tempHi, humidity, weakBattery = bus.read_block_data(CLOCK_DISPLAY_ADDR, 4)

temperature = (256.0 * tempHi + tempLo) / 10.0

print(u"Temperature: %.1f \u00B0C" % temperature)
print(u"Humidity: %d%%" % humidity)
print(u"Weak battery: %d" % weakBattery)
