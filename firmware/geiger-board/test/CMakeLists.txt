cmake_minimum_required(VERSION 3.7)
project(geiger_test)

set(CMAKE_CXX_STANDARD 14)
set(CMAKE_VERBOSE_MAKEFILE ON)

SET(CMAKE_CXX_FLAGS "-g -O0 -std=c++14 -DF_CPU=${FREQ}UL -Wall -Wextra -pedantic")
include_directories(../libsrc)

SET(LIBRARY_SOURCES ../libsrc/main.hpp ../libsrc/protocol.hpp
        ../libsrc/magiceye.cpp ../libsrc/magiceye.hpp
        ../libsrc/inverter.cpp ../libsrc/inverter.hpp
        ../libsrc/i2c-slave.cpp ../libsrc/i2c-slave.hpp
        ../libsrc/geiger-counter.cpp ../libsrc/geiger-counter.hpp)

SET(SOURCES common.hpp magiceye_test.cpp inverter_test.cpp)

enable_testing()

find_package(GTest REQUIRED)
include_directories(${GTEST_INCLUDE_DIRS})

ADD_EXECUTABLE(${PROJECT_NAME} ${SOURCES} ${LIBRARY_SOURCES})
target_link_libraries(${PROJECT_NAME} ${GTEST_BOTH_LIBRARIES})

add_test(AllTests ${PROJECT_NAME})
