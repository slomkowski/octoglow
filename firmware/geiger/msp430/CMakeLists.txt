cmake_minimum_required(VERSION 3.7)
project(octoglow-geiger-msp430)

set(CMAKE_CXX_STANDARD 14)
set(CMAKE_VERBOSE_MAKEFILE ON)

SET(DEVICE "msp430g2553")

SET(CMAKE_C_COMPILER msp430-elf-gcc)
SET(CMAKE_CXX_COMPILER msp430-elf-g++)
SET(SUPPORT_FILE_DIRECTORY /home/michal/projects/octoglow/msp430-gcc-support-files/include)

SET(CMAKE_C_FLAGS "-Os -mmcu=${DEVICE} -DF_CPU=${FREQ}UL -std=gnu99 -Wl,--gc-sections -Wall -Wextra -pedantic")
SET(CMAKE_CXX_FLAGS "-Os -mmcu=${DEVICE} -DF_CPU=${FREQ}UL -std=c++14 -Wl,--gc-sections -Wall -Wextra -pedantic -fno-exceptions -fno-rtti")
#todo delete support line
SET(CMAKE_EXE_LINKER_FLAGS "-L${SUPPORT_FILE_DIRECTORY}")
include_directories(../noarch)
include_directories(../../lib/libfixmath/libfixmath)
include_directories(${SUPPORT_FILE_DIRECTORY})

SET(SOURCES main.cpp magiceye_hd.cpp inverter_hd.cpp i2c-slave_hd.cpp geiger-counter_hd.cpp)

ADD_EXECUTABLE(${PROJECT_NAME} ${SOURCES} ${LIBRARY_SOURCES})

ADD_CUSTOM_COMMAND(TARGET ${PROJECT_NAME} POST_BUILD COMMAND msp430-elf-size ${CMAKE_CURRENT_BINARY_DIR}/${PROJECT_NAME} -B -d)
