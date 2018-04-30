cmake_minimum_required(VERSION 3.7)
project(front-display)

SET(FREQ "16000000")

SET(LIBRARY_SOURCES ../noarch/protocol.hpp
        ../noarch/display.cpp ../noarch/display.hpp
        ../noarch/Font5x7.cpp ../noarch/Font5x7.hpp
        ../noarch/encoder.cpp ../noarch/encoder.hpp)

add_subdirectory(avr)
add_subdirectory(test)
