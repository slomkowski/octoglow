cmake_minimum_required(VERSION 3.5)
SET(CMAKE_SYSTEM_NAME Generic)

project(avr_display_driver)

SET(DEVICE "atmega88")
SET(FREQ "16000000")

SET(CMAKE_C_COMPILER avr-gcc)
SET(CMAKE_CXX_COMPILER avr-g++)

SET(CMAKE_C_FLAGS "-O2 -mmcu=${DEVICE} -DF_CPU=${FREQ}UL -std=gnu99 -Wl,--gc-sections -Wall -Wextra -pedantic")
SET(CMAKE_CXX_FLAGS "-O2 -mmcu=${DEVICE} -DF_CPU=${FREQ}UL -std=c++11 -Wl,--gc-sections -Wall -Wextra -pedantic -fno-exceptions -fno-rtti")

include_directories(libsrc)

SET(LIBRARY_SOURCES libsrc/i2c-slave.cpp libsrc/i2c-slave.hpp)

SET(SOURCES src/main.cpp src/global.hpp src/display.cpp src/display.hpp src/speaker.cpp src/speaker.hpp src/protocol.hpp src/Font5x7.cpp src/Font5x7.hpp src/encoder.cpp src/encoder.hpp src/display-lowlevel.cpp src/display-lowlevel.hpp)

ADD_EXECUTABLE(${PROJECT_NAME} ${SOURCES} ${LIBRARY_SOURCES})

# todo add commands for burning fuses -U lfuse:w:0xce:m -U hfuse:w:0xdd:m -U efuse:w:0xf9:m
# and programming

ADD_CUSTOM_COMMAND(TARGET ${PROJECT_NAME} POST_BUILD COMMAND avr-objcopy -O ihex -R.eeprom ${CMAKE_BINARY_DIR}/${PROJECT_NAME} ${CMAKE_BINARY_DIR}/${PROJECT_NAME}.hex)
ADD_CUSTOM_COMMAND(TARGET ${PROJECT_NAME} POST_BUILD COMMAND avr-objcopy -O ihex -j .eeprom --set-section-flags=.eeprom="alloc,load" --change-section-lma .eeprom=0 ${CMAKE_BINARY_DIR}/${PROJECT_NAME} ${CMAKE_BINARY_DIR}/${PROJECT_NAME}.eep)
ADD_CUSTOM_COMMAND(TARGET ${PROJECT_NAME} POST_BUILD COMMAND avr-size ${CMAKE_BINARY_DIR}/${PROJECT_NAME} --mcu=${DEVICE} --format=avr)
