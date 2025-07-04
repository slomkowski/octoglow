cmake_minimum_required(VERSION 3.10)
project(octoglow-geiger-msp430)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_VERBOSE_MAKEFILE ON)

SET(DEVICE "msp430g2553")

SET(CMAKE_C_COMPILER msp430-elf-gcc)
SET(CMAKE_CXX_COMPILER msp430-elf-g++)
SET(SUPPORT_FILE_DIRECTORY /opt/ti/mspgcc/include/)

SET(COMMON_GCC_FLAGS "-O2 \
     -mmcu=${DEVICE} \
     -DTIMER_CLOCK_SOURCE_FREQ=${TIMER_CLOCK_SOURCE_FREQ}UL \
     -DFIXMATH_OPTIMIZE_8BIT \
     -ffunction-sections \
     -fdata-sections \
     -Wl,--gc-sections \
     -Wall \
     -Wextra \
     -pedantic")

SET(CMAKE_C_FLAGS "${COMMON_GCC_FLAGS} -std=c11")
SET(CMAKE_CXX_FLAGS "${COMMON_GCC_FLAGS} -std=c++17 -fno-exceptions -fno-rtti")
SET(CMAKE_EXE_LINKER_FLAGS "-L${SUPPORT_FILE_DIRECTORY}")
include_directories(../noarch)
include_directories(../../lib/libfixmath/libfixmath)
include_directories(${SUPPORT_FILE_DIRECTORY})

SET(SOURCES main.cpp magiceye_hd.cpp inverter_hd.cpp i2c-slave_hd.cpp geiger-counter_hd.cpp)

ADD_EXECUTABLE(${PROJECT_NAME} ${SOURCES} ${LIBRARY_SOURCES})

ADD_CUSTOM_COMMAND(TARGET ${PROJECT_NAME} POST_BUILD COMMAND msp430-elf-size ${CMAKE_CURRENT_BINARY_DIR}/${PROJECT_NAME} -B -d)
