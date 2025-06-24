cmake_minimum_required(VERSION 3.10)
project(front-display-avr)

SET(CMAKE_SYSTEM_NAME Generic)
set(CMAKE_SYSTEM_PROCESSOR avr)
set(CMAKE_CROSS_COMPILING 1)

SET(DEVICE "atmega88")

SET(CMAKE_C_COMPILER avr-gcc)
SET(CMAKE_CXX_COMPILER avr-g++)

SET(CMAKE_C_FLAGS "-O2 -mmcu=${DEVICE} -DF_CPU=${FREQ}UL -std=c11 -Wl,--gc-sections -Wall -Wextra -pedantic")
SET(CMAKE_CXX_FLAGS "-O2 -mmcu=${DEVICE} -DF_CPU=${FREQ}UL -std=c++17 -Wl,--gc-sections -Wall -Wextra -pedantic -fno-exceptions -fno-rtti")

include_directories(../noarch ../avr)

SET(SOURCES
        main.cpp main.hpp
        i2c-slave_hd.cpp
        display_hd.cpp
        encoder_hd.cpp)

ADD_EXECUTABLE(${PROJECT_NAME} ${SOURCES} ${LIBRARY_SOURCES})

# fuses: -U lfuse:w:0xce:m -U hfuse:w:0xdd:m -U efuse:w:0xf9:m

ADD_CUSTOM_COMMAND(TARGET ${PROJECT_NAME} POST_BUILD COMMAND avr-objcopy -O ihex -R.eeprom ${CMAKE_CURRENT_BINARY_DIR}/${PROJECT_NAME} ${CMAKE_CURRENT_BINARY_DIR}/${PROJECT_NAME}.hex)
ADD_CUSTOM_COMMAND(TARGET ${PROJECT_NAME} POST_BUILD COMMAND avr-objcopy -O ihex -j .eeprom --set-section-flags=.eeprom="alloc,load" --change-section-lma .eeprom=0 ${CMAKE_CURRENT_BINARY_DIR}/${PROJECT_NAME} ${CMAKE_CURRENT_BINARY_DIR}/${PROJECT_NAME}.eep)
ADD_CUSTOM_COMMAND(TARGET ${PROJECT_NAME} POST_BUILD COMMAND avr-size ${CMAKE_CURRENT_BINARY_DIR}/${PROJECT_NAME} --mcu=${DEVICE} --format=avr)
