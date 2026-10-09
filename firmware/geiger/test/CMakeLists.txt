cmake_minimum_required(VERSION 3.10)
project(octoglow-geiger-test)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_VERBOSE_MAKEFILE ON)

SET(CMAKE_CXX_FLAGS "-g -O0 -std=c++17 -DTIMER_CLOCK_SOURCE_FREQ=${TIMER_CLOCK_SOURCE_FREQ}UL -Wall -Wextra -pedantic")

# any memory error or undefined behavior aborts the tests
SET(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fsanitize=address,undefined -fno-sanitize-recover=all -fno-omit-frame-pointer")
include_directories(../noarch)
include_directories(../bootloader)
include_directories(../../lib/libfixmath/libfixmath)

# the protocol of the bootloader is tested on the host, the flash is simulated in twiboot_test.cpp
add_definitions(-DTWIBOOT_START=${BOOTLOADER_START} -DTWIBOOT_TIMEOUT_MS=${BOOTLOADER_TIMEOUT_MS})
SET(BOOTLOADER_SOURCES ../bootloader/twiboot.cpp ../bootloader/twiboot.hpp ../bootloader/flash.hpp)

SET(SOURCES common.hpp magiceye_test.cpp inverter_test.cpp i2c-slave_test.cpp twiboot_test.cpp)

enable_testing()

find_package(GTest REQUIRED)
include_directories(${GTEST_INCLUDE_DIRS})

ADD_EXECUTABLE(${PROJECT_NAME} ${SOURCES} ${LIBRARY_SOURCES} ${BOOTLOADER_SOURCES})
target_link_libraries(${PROJECT_NAME} ${GTEST_BOTH_LIBRARIES})

add_test(AllTests ${PROJECT_NAME})
