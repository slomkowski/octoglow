#!/usr/bin/env python3
"""
Merge the application and the bootloader into a single Intel HEX file for ISP flashing.

The bootloader (ATtiny461A has no boot section) patches the vector table when it writes flash page 0:
the reset vector jumps to the bootloader and the EE_RDY vector jumps to the original
application reset handler. When the application is flashed with ISP, this patching
does not happen, so it is replicated here. Otherwise the bootloader would never run.

usage: merge-with-bootloader.py <app.elf> <app.hex> <bootloader.elf> <bootloader.hex> <bootloader start> <app vector num> <out.hex>
"""

import subprocess
import sys

FLASH_SIZE = 4096


def read_hex(path: str) -> dict[int, int]:
    memory = {}
    base = 0
    with open(path) as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            assert line[0] == ':', f'{path}: invalid line {line}'
            raw = bytes.fromhex(line[1:])
            assert sum(raw) & 0xFF == 0, f'{path}: checksum error in {line}'
            count, addr, rtype, data = raw[0], (raw[1] << 8) | raw[2], raw[3], raw[4:4 + raw[0]]
            if rtype == 0x00:
                for i, b in enumerate(data):
                    memory[base + addr + i] = b
            elif rtype == 0x01:
                break
            elif rtype == 0x02:
                base = ((data[0] << 8) | data[1]) << 4
            elif rtype == 0x04:
                base = ((data[0] << 8) | data[1]) << 16
    return memory


def write_hex(path: str, memory: dict[int, int]) -> None:
    with open(path, 'w') as f:
        for start in range(0, FLASH_SIZE, 16):
            chunk = [memory.get(a) for a in range(start, start + 16)]
            if all(b is None for b in chunk):
                continue
            data = bytes(0xFF if b is None else b for b in chunk)
            record = bytes([len(data), start >> 8, start & 0xFF, 0x00]) + data
            f.write(':' + (record + bytes([-sum(record) & 0xFF])).hex().upper() + '\n')
        f.write(':00000001FF\n')


def rjmp(from_word: int, to_word: int) -> int:
    return 0xC000 | ((to_word - from_word - 1) & 0x0FFF)


def main() -> None:
    app_elf_path, app_path, boot_elf_path, boot_path, boot_start, app_vect_num, out_path = sys.argv[1:]
    boot_start = int(boot_start, 0)
    app_vect_num = int(app_vect_num, 0)

    # the patched reset vector jumps to boot_start, so the bootloader has to begin with code there
    boot_symbols = subprocess.run(['avr-nm', boot_elf_path], check=True, capture_output=True, text=True).stdout
    if f'{boot_start:08x} T init1\n' not in boot_symbols:
        sys.exit(f'bootloader entry (init1) is not at 0x{boot_start:04X}')

    # unused vectors are weak (W) aliases of __bad_interrupt, a real ISR is a global text (T) symbol
    symbols = subprocess.run(['avr-nm', app_elf_path], check=True, capture_output=True, text=True).stdout
    if f'T __vector_{app_vect_num}\n' in symbols:
        sys.exit(f'application defines ISR for vector {app_vect_num}, which is reserved by the bootloader')

    app = read_hex(app_path)
    boot = read_hex(boot_path)

    app_end = max(app) + 1
    if app_end > boot_start:
        sys.exit(f'application ends at 0x{app_end:04X}, overlaps bootloader at 0x{boot_start:04X}')
    if min(boot) < boot_start:
        sys.exit(f'bootloader starts at 0x{min(boot):04X}, expected 0x{boot_start:04X}')

    rst_opcode = app[0] | (app[1] << 8)
    if rst_opcode & 0xF000 != 0xC000:
        sys.exit(f'application reset vector 0x{rst_opcode:04X} is not RJMP')
    app_vect_addr = app_vect_num * 2
    reset_target_word = ((rst_opcode & 0x0FFF) + 1) % (FLASH_SIZE // 2)

    merged = dict(app)
    merged.update(boot)

    for addr, opcode in ((0, rjmp(0, boot_start // 2)),
                         (app_vect_addr, rjmp(app_vect_num, reset_target_word))):
        merged[addr] = opcode & 0xFF
        merged[addr + 1] = opcode >> 8

    write_hex(out_path, merged)


if __name__ == '__main__':
    main()
