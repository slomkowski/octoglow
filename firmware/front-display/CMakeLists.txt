cmake_minimum_required(VERSION 3.10)
project(octoglow-front-display)

set(CMAKE_VERBOSE_MAKEFILE ON)

SET(FREQ "16000000")

# I2C bootloader (port of twiboot) in the boot section, see bootloader/
# BOOTLOADER_START has to match BOOTSZ fuses (00: 1024 words), changing it requires reflashing with ISP
SET(BOOTLOADER_START "0x1800")
SET(BOOTLOADER_TIMEOUT_MS "1000")

SET(LIBRARY_SOURCES ../noarch/protocol.hpp ../noarch/eeprom.hpp
        ../noarch/display.cpp ../noarch/display.hpp
        ../noarch/Font5x7.hpp
        ../noarch/encoder.cpp ../noarch/encoder.hpp
        ../noarch/i2c-slave.cpp ../noarch/i2c-slave.hpp)

add_subdirectory(avr)
add_subdirectory(test)
