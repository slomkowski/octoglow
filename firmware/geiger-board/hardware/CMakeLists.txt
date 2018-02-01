cmake_minimum_required(VERSION 3.7)
project(geiger_hardware)

set(CMAKE_CXX_STANDARD 14)
set(CMAKE_VERBOSE_MAKEFILE ON)

SET(DEVICE "msp430g2553")

SET(CMAKE_C_COMPILER msp430-elf-gcc)
SET(CMAKE_CXX_COMPILER msp430-elf-g++)
SET(SUPPORT_FILE_DIRECTORY /home/michal/projects/vfd-display/msp430-gcc-support-files/include)

SET(CMAKE_C_FLAGS "-Os -mmcu=${DEVICE} -DF_CPU=${FREQ}UL -std=gnu99 -Wl,--gc-sections -Wall -Wextra -pedantic")
SET(CMAKE_CXX_FLAGS "-Os -mmcu=${DEVICE} -DF_CPU=${FREQ}UL -std=c++14 -Wl,--gc-sections -Wall -Wextra -pedantic -fno-exceptions -fno-rtti")
SET(CMAKE_EXE_LINKER_FLAGS "-L${SUPPORT_FILE_DIRECTORY}")
include_directories(../libsrc)
include_directories(${SUPPORT_FILE_DIRECTORY})

SET(LIBRARY_SOURCES ../libsrc/main.hpp
        ../libsrc/magiceye.cpp ../libsrc/magiceye.hpp
        ../libsrc/inverter.hpp ../libsrc/inverter.cpp)

SET(SOURCES main.cpp magiceye_hd.cpp inverter_hd.cpp)

ADD_EXECUTABLE(${PROJECT_NAME} ${SOURCES} ${LIBRARY_SOURCES})

ADD_CUSTOM_COMMAND(TARGET ${PROJECT_NAME} POST_BUILD COMMAND msp430-elf-size ${CMAKE_CURRENT_BINARY_DIR}/${PROJECT_NAME} -B -d)
