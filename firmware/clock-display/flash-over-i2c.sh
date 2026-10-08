#!/usr/bin/env bash
# Update the clock display firmware over I2C. Run on the host connected to the I2C bus.
# Requires i2c-tools and the twiboot tool from https://github.com/orempel/multiboot_tool
# Stop octoglowd first, it would talk to the device during the update.
#
# usage: flash-over-i2c.sh [app.hex] [i2c bus number]

set -euo pipefail

HEX_FILE=${1:-cmake-build-debug/octoglow-clock-display.hex}
I2C_BUS=${2:-1}

APP_ADDRESS=0x10 # usi::APP_I2C_ADDRESS in common/usi.hpp
BOOTLOADER_ADDRESS=0x11 # usi::BOOTLOADER_I2C_ADDRESS in common/usi.hpp

# ENTER_BOOTLOADER: crc8, command 7, magic 'BOOT'
# if the device already is in the bootloader, the application does not respond, that is fine
i2ctransfer -y "$I2C_BUS" w6@$APP_ADDRESS 0x6c 0x07 0x42 0x4f 0x4f 0x54 r2 || true

# the application resets via watchdog within 250 ms, the bootloader then waits 1 s for the host
for attempt in 1 2 3 4 5 6 7 8; do
    sleep 0.15
    if twiboot -a $BOOTLOADER_ADDRESS -d "/dev/i2c-$I2C_BUS" -p 1 -w "flash:$HEX_FILE"; then
        exit 0
    fi
    echo "bootloader not responding, attempt $attempt" >&2
done

echo "failed to reach the bootloader; power-cycle the device and run this script within 1 s" >&2
exit 1
