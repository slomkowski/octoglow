cmake_minimum_required(VERSION 3.7)
project(geiger)

SET(FREQ "5700000")

SET(LIBRARY_SOURCES ../libsrc/main.hpp ../libsrc/protocol.hpp
        ../libsrc/magiceye.cpp ../libsrc/magiceye.hpp
        ../libsrc/animation.cpp ../libsrc/animation.hpp
        ../libsrc/inverter.cpp ../libsrc/inverter.hpp
        ../libsrc/i2c-slave.cpp ../libsrc/i2c-slave.hpp
        ../libsrc/geiger-counter.cpp ../libsrc/geiger-counter.hpp)

add_subdirectory(hardware)
add_subdirectory(test)
add_subdirectory(animationtest)
