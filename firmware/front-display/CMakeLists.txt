cmake_minimum_required(VERSION 3.10)
project(octoglow-front-display)

set(CMAKE_VERBOSE_MAKEFILE ON)

SET(FREQ "16000000")

SET(LIBRARY_SOURCES ../noarch/protocol.hpp
        ../noarch/display.cpp ../noarch/display.hpp
        ../noarch/Font5x7.cpp ../noarch/Font5x7.hpp
        ../noarch/encoder.cpp ../noarch/encoder.hpp
        ../noarch/i2c-slave.cpp ../noarch/i2c-slave.hpp)

add_subdirectory(avr)
add_subdirectory(test)
