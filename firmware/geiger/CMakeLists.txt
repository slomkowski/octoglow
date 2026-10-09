cmake_minimum_required(VERSION 3.10)
project(octoglow-geiger)

SET(TIMER_CLOCK_SOURCE_FREQ "5700000")

# I2C bootloader (port of twiboot) at the end of the flash, see bootloader/
# changing BOOTLOADER_START requires reflashing with the programmer, it has to be at the segment (512 bytes) boundary
SET(BOOTLOADER_START "0xfc00")
SET(BOOTLOADER_TIMEOUT_MS "1000")

SET(LIBRARY_SOURCES ../noarch/main.hpp ../noarch/protocol.hpp
        ../noarch/magiceye.cpp ../noarch/magiceye.hpp
        ../noarch/animation.cpp ../noarch/animation.hpp
        ../noarch/inverter.cpp ../noarch/inverter.hpp
        ../noarch/i2c-slave.cpp ../noarch/i2c-slave.hpp
        ../noarch/geiger-counter.cpp ../noarch/geiger-counter.hpp
        ../noarch/FastPID.cpp ../noarch/FastPID.hpp
        ../../lib/libfixmath/libfixmath/fix16.c ../../lib/libfixmath/libfixmath/fix16.h)

add_subdirectory(msp430)

# the host tests can't be built when the whole project is configured with msp430-elf-g++ (e.g. a CLion MSP430 profile)
if (CMAKE_CXX_COMPILER MATCHES "msp430-elf-g\\+\\+$")
    message(STATUS "Host tests skipped, the compiler is ${CMAKE_CXX_COMPILER}")
else ()
    add_subdirectory(test)
    add_subdirectory(animationtest)
endif ()
