cmake_minimum_required(VERSION 3.10)
SET(CMAKE_SYSTEM_NAME Generic)

project(octoglow-clock-display)

SET(DEVICE "attiny461a")
SET(FREQ "8000000")

SET(CMAKE_C_COMPILER avr-gcc)
SET(CMAKE_CXX_COMPILER avr-g++)

SET(CMAKE_C_FLAGS "-O2 -mmcu=${DEVICE} -DF_CPU=${FREQ}UL -std=c23 -Wl,--gc-sections -Wall -Wextra -pedantic")
SET(CMAKE_CXX_FLAGS "-O2 -mmcu=${DEVICE} -DF_CPU=${FREQ}UL -std=c++17 -Wl,--gc-sections -Wall -Wextra -pedantic -fno-exceptions -fno-rtti")

SET(SOURCES src/main.cpp src/global.hpp src/protocol.hpp
        src/display.cpp src/display.hpp
        src/relay.cpp src/relay.hpp
        src/receiver433.cpp src/receiver433.hpp
        src/i2c-slave.hpp src/i2c-slave.cpp
        src/light-sensor.cpp src/light-sensor.hpp)

ADD_EXECUTABLE(${PROJECT_NAME} ${SOURCES} ${LIBRARY_SOURCES})

# fuses -U lfuse:w:0xe2:m -U hfuse:w:0xdc:m -U efuse:w:0xff:m

ADD_CUSTOM_COMMAND(TARGET ${PROJECT_NAME} POST_BUILD COMMAND avr-objcopy -O ihex -R.eeprom ${CMAKE_BINARY_DIR}/${PROJECT_NAME} ${CMAKE_BINARY_DIR}/${PROJECT_NAME}.hex)
ADD_CUSTOM_COMMAND(TARGET ${PROJECT_NAME} POST_BUILD COMMAND avr-objcopy -O ihex -j .eeprom --set-section-flags=.eeprom="alloc,load" --change-section-lma .eeprom=0 ${CMAKE_BINARY_DIR}/${PROJECT_NAME} ${CMAKE_BINARY_DIR}/${PROJECT_NAME}.eep)
ADD_CUSTOM_COMMAND(TARGET ${PROJECT_NAME} POST_BUILD COMMAND avr-size ${CMAKE_BINARY_DIR}/${PROJECT_NAME} --mcu=${DEVICE} --format=avr)
