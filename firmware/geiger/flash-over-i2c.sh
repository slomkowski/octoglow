#!/usr/bin/env bash
# Update the Geiger counter firmware over I2C. Run on the host connected to the I2C bus.
# Requires i2c-tools, binutils (objcopy) and the twiboot tool from https://github.com/orempel/multiboot_tool
# Stop octoglowd first, it would talk to the device during the update.
#
# usage: flash-over-i2c.sh [app.hex] [i2c bus number]

set -euo pipefail

HEX_FILE=${1:-cmake-build-msp430/msp430/octoglow-geiger-msp430.hex}
I2C_BUS=${2:-1}

APP_ADDRESS=0x18 # i2c::SLAVE_ADDRESS in noarch/i2c-slave.hpp
BOOTLOADER_ADDRESS=0x19 # i2c::BOOTLOADER_ADDRESS in noarch/i2c-slave.hpp
FLASH_START=0xc000 # twiboot::FLASH_START in bootloader/twiboot.hpp

# the addresses of the bootloader's protocol are relative to the start of the flash
RELATIVE_HEX_FILE=$(mktemp --suffix=.hex)
trap 'rm -f "$RELATIVE_HEX_FILE"' EXIT
objcopy -I ihex -O ihex --change-addresses -$FLASH_START "$HEX_FILE" "$RELATIVE_HEX_FILE"

# ENTER_BOOTLOADER: crc8, command 8, magic 'BOOT'; the application processes the command after STOP
# if the device already is in the bootloader, the application does not respond, that is fine
if i2ctransfer -y "$I2C_BUS" w6@$APP_ADDRESS 0x5c 0x08 0x42 0x4f 0x4f 0x54; then
    sleep 0.01
    i2ctransfer -y "$I2C_BUS" r2@$APP_ADDRESS || true
fi

# the application resets within 100 ms, the bootloader then waits 1 s for the host
for attempt in 1 2 3 4 5 6 7 8; do
    sleep 0.15
    if twiboot -a $BOOTLOADER_ADDRESS -d "/dev/i2c-$I2C_BUS" -p 1 -w "flash:$RELATIVE_HEX_FILE"; then
        exit 0
    fi
    echo "bootloader not responding, attempt $attempt" >&2
done

echo "failed to reach the bootloader; power-cycle the device and run this script within 1 s" >&2
exit 1
