#!/usr/bin/env python3
# -*- encoding: utf-8 -*-

"""This utility program sets the I2C clock speed on CP2112 USB-to-SMBus converter."""

import dataclasses
import sys
import struct

import usb.core
import usb.util

VENDOR_ID = 0x10c4
DEVICE_ID = 0xea90

PACKING_STRING = '>BIB?HH?H'


@dataclasses.dataclass
class SMBConfiguration:
    clock_speed: int
    device_address: int
    auto_send_read: bool
    write_timeout: int
    read_timeout: int
    scl_low_timeout: bool
    retry_time: int


if len(sys.argv) < 2:
    print("Usage: %s {clock speed in kHz, between 10 and 400}" % sys.argv[0])
    sys.exit(1)

clock_speed = 1000 * int(sys.argv[1])
assert clock_speed >= 10_000
assert clock_speed <= 400_000

dev_handle = usb.core.find(idVendor=VENDOR_ID, idProduct=DEVICE_ID)

if dev_handle.is_kernel_driver_active(0):
    should_reattach_kernel_driver = True
    dev_handle.detach_kernel_driver(0)
else:
    should_reattach_kernel_driver = False


def get_smbus_configuration() -> SMBConfiguration:
    request_type = usb.util.build_request_type(usb.util.ENDPOINT_IN,
                                               usb.util.CTRL_TYPE_CLASS,
                                               usb.util.CTRL_RECIPIENT_INTERFACE)

    returned = dev_handle.ctrl_transfer(request_type, 1, (0x3 << 8) | 0x06, 0, 64)
    assert len(returned) == 14
    assert returned[0] == 6
    unpacked = struct.unpack_from(PACKING_STRING, returned)[1:]
    return SMBConfiguration(*unpacked)


def set_smbus_configuration(config: SMBConfiguration):
    request_type = usb.util.build_request_type(usb.util.ENDPOINT_OUT,
                                               usb.util.CTRL_TYPE_CLASS,
                                               usb.util.CTRL_RECIPIENT_INTERFACE)

    packed = struct.pack(PACKING_STRING, 0x6, *dataclasses.astuple(config))
    assert len(packed) == 14
    assert packed[0] == 6
    dev_handle.ctrl_transfer(request_type, 9, (0x3 << 8) | 0x06, 0, packed)


smbus_config = get_smbus_configuration()
print("Previous configuration:", smbus_config)

smbus_config.clock_speed = clock_speed

set_smbus_configuration(smbus_config)
new_smbus_config = get_smbus_configuration()
print("Current configuration:", new_smbus_config)

assert new_smbus_config.clock_speed == clock_speed

usb.util.dispose_resources(dev_handle)

if should_reattach_kernel_driver:
    dev_handle.attach_kernel_driver(0)
