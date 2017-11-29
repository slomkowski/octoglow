cmake_minimum_required(VERSION 3.7)
project(geiger_board)

set(CMAKE_CXX_STANDARD 14)

SET(DEVICE "msp430g2553")
SET(FREQ "16000000")

SET(CMAKE_C_COMPILER msp430-elf-gcc)
SET(CMAKE_CXX_COMPILER msp430-elf-g++)

SET(CMAKE_C_FLAGS "-Os -mmcu=${DEVICE} -DF_CPU=${FREQ}UL -std=gnu99 -Wl,--gc-sections -Wall -Wextra -pedantic")
SET(CMAKE_CXX_FLAGS "-Os -mmcu=${DEVICE} -DF_CPU=${FREQ}UL -std=c++14 -Wl,--gc-sections -Wall -Wextra -pedantic -fno-exceptions -fno-rtti")

include_directories(libsrc)

SET(LIBRARY_SOURCES)

SET(SOURCES src/main.cpp src/magiceye.cpp src/magiceye.hpp)

ADD_EXECUTABLE(${PROJECT_NAME} ${SOURCES} ${LIBRARY_SOURCES})

ADD_CUSTOM_COMMAND(TARGET ${PROJECT_NAME} POST_BUILD COMMAND msp430-elf-size ${CMAKE_BINARY_DIR}/${PROJECT_NAME} -B -d)
