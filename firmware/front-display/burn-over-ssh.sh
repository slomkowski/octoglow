#!/usr/bin/env bash
# Updates the firmware over I2C using octoglowd on the octoglow host:
# copies the hex to octoglow:/tmp/, stops octoglowd, runs octoglowd --burn-firmware, starts octoglowd again.
# The device name is the name of this directory, as octoglowd --burn-firmware expects.
#
# usage: burn-over-ssh.sh [app.hex]
#   without an argument, the project is built first and cmake-build-avr/avr/octoglow-<device>-avr.hex is used

set -euo pipefail

OCTOGLOW_HOST='octoglow'
OCTOGLOWD_DIR='/home/octoglow/octoglowd'

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)
DEVICE=$(basename "$SCRIPT_DIR")
BUILD_DIR="$SCRIPT_DIR/cmake-build-avr"

if [[ $# -eq 0 ]]; then
    cmake --build "$BUILD_DIR" --target "octoglow-$DEVICE-avr"
    HEX_FILE="$BUILD_DIR/avr/octoglow-$DEVICE-avr.hex"
else
    HEX_FILE=$1
fi

if [[ ! -f $HEX_FILE ]]; then
    echo "$HEX_FILE not found" >&2
    exit 1
fi

REMOTE_HEX_FILE="/tmp/$(basename "$HEX_FILE")"

ls -l "$HEX_FILE"
scp "$HEX_FILE" "$OCTOGLOW_HOST:$REMOTE_HEX_FILE"

ssh "$OCTOGLOW_HOST" bash -s -- "$DEVICE" "$REMOTE_HEX_FILE" "$OCTOGLOWD_DIR" <<'EOF'
set -euo pipefail
device=$1
hex_file=$2
octoglowd_dir=$3

cd "$octoglowd_dir"

# an older octoglowd.jar ignores the arguments and would start a second daemon
if ! python3 -c 'import sys, zipfile; names = zipfile.ZipFile("octoglowd.jar").namelist(); sys.exit("eu/slomkowski/octoglow/octoglowd/firmware/FirmwareBurner.class" not in names)'; then
    echo "octoglowd.jar on $(hostname) doesn't support --burn-firmware, deploy it first (software/octoglowd/deploy.sh)" >&2
    exit 1
fi

# a jar which supports --burn-firmware, but not this device, would fail after octoglowd is stopped
if ! java -Xmx64m -jar octoglowd.jar --help | grep -- "$device" > /dev/null; then
    echo "octoglowd.jar on $(hostname) doesn't support --burn-firmware $device, deploy it first (software/octoglowd/deploy.sh)" >&2
    exit 1
fi

# "not running" is not an error here
supervisorctl stop octoglowd || true

# start the daemon again even if burning fails
trap 'supervisorctl start octoglowd' EXIT

java -Xmx64m -jar octoglowd.jar --burn-firmware "$device" "$hex_file"
EOF
