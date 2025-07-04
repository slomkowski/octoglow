cmake_minimum_required(VERSION 3.10)
project(octoglow-geiger)

SET(TIMER_CLOCK_SOURCE_FREQ "5700000")

SET(LIBRARY_SOURCES ../noarch/main.hpp ../noarch/protocol.hpp
        ../noarch/magiceye.cpp ../noarch/magiceye.hpp
        ../noarch/animation.cpp ../noarch/animation.hpp
        ../noarch/inverter.cpp ../noarch/inverter.hpp
        ../noarch/i2c-slave.cpp ../noarch/i2c-slave.hpp
        ../noarch/geiger-counter.cpp ../noarch/geiger-counter.hpp
        ../noarch/FastPID.cpp ../noarch/FastPID.hpp
        ../../lib/libfixmath/libfixmath/fix16.c ../../lib/libfixmath/libfixmath/fix16.h)

add_subdirectory(msp430)
add_subdirectory(test)
add_subdirectory(animationtest)
