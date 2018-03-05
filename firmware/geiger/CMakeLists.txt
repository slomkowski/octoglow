cmake_minimum_required(VERSION 3.7)
project(octoglow-geiger)

SET(FREQ "5700000")

SET(LIBRARY_SOURCES ../noarch/main.hpp ../noarch/protocol.hpp
        ../noarch/magiceye.cpp ../noarch/magiceye.hpp
        ../noarch/animation.cpp ../noarch/animation.hpp
        ../noarch/inverter.cpp ../noarch/inverter.hpp
        ../noarch/i2c-slave.cpp ../noarch/i2c-slave.hpp
        ../noarch/geiger-counter.cpp ../noarch/geiger-counter.hpp
        ../../lib/libfixmath/libfixmath/fix16.c ../../lib/libfixmath/libfixmath/fix16.h)

add_subdirectory(msp430)
add_subdirectory(test)
add_subdirectory(animationtest)
