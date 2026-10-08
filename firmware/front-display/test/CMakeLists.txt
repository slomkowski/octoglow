cmake_minimum_required(VERSION 3.10)
project(octoglow-front-display-test)

set(CMAKE_CXX_STANDARD 17)

SET(CMAKE_CXX_FLAGS "-g -O0 -std=c++17 -DF_CPU=${FREQ}UL -Wall -Wextra -pedantic")

# any memory error or undefined behavior aborts the tests
SET(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fsanitize=address,undefined -fno-sanitize-recover=all -fno-omit-frame-pointer")
include_directories(../noarch ../test ../bootloader)

# the protocol of the bootloader is tested on the host, the flash access is replaced by a stub
add_definitions(-DTWIBOOT_START=${BOOTLOADER_START} -DTWIBOOT_TIMEOUT_MS=${BOOTLOADER_TIMEOUT_MS})
SET(BOOTLOADER_SOURCES ../bootloader/twiboot.cpp ../bootloader/twiboot.hpp ../bootloader/flash.hpp)

SET(SOURCES main.hpp stubs.hpp stubs.cpp display_test.cpp i2c-slave_test.cpp twiboot_test.cpp)

enable_testing()

find_package(GTest REQUIRED)
include_directories(${GTEST_INCLUDE_DIRS})

ADD_EXECUTABLE(${PROJECT_NAME} ${SOURCES} ${LIBRARY_SOURCES} ${BOOTLOADER_SOURCES})
target_link_libraries(${PROJECT_NAME} ${GTEST_BOTH_LIBRARIES})

add_test(AllTests ${PROJECT_NAME})
