cmake_minimum_required(VERSION 3.10)
SET(CMAKE_SYSTEM_NAME Generic)

project(octoglow-clock-display)

SET(DEVICE "attiny461a")
SET(FREQ "8000000")

SET(CMAKE_C_COMPILER avr-gcc)
SET(CMAKE_CXX_COMPILER avr-g++)

SET(CMAKE_C_FLAGS "-O2 -mmcu=${DEVICE} -DF_CPU=${FREQ}UL -std=c23 -Wl,--gc-sections -Wall -Wextra -pedantic")
SET(CMAKE_CXX_FLAGS "-O2 -mmcu=${DEVICE} -DF_CPU=${FREQ}UL -std=c++23 -Wl,--gc-sections -Wall -Wextra -pedantic -fno-exceptions -fno-rtti")

SET(SOURCES src/main.cpp src/global.hpp src/protocol.hpp
        src/display.cpp src/display.hpp
        src/relay.cpp src/relay.hpp
        src/receiver433.cpp src/receiver433.hpp
        src/i2c-slave.hpp src/i2c-slave.cpp
        src/light-sensor.cpp src/light-sensor.hpp
        common/usi.hpp)

ADD_EXECUTABLE(${PROJECT_NAME} ${SOURCES} ${LIBRARY_SOURCES})
target_include_directories(${PROJECT_NAME} PRIVATE common)

# I2C bootloader (port of twiboot) at the end of flash, see bootloader/
# changing BOOTLOADER_START requires reflashing the bootloader with ISP
SET(BOOTLOADER_START "0x0C80")
SET(BOOTLOADER_TIMEOUT_MS "1000")
SET(BOOTLOADER_APP_VECTOR_NUM "9") # EE_RDY_vect_num, overwritten by the bootloader, must not be used by the app
SET(BOOTLOADER_NAME octoglow-clock-display-bootloader)
SET(BOOTLOADER_SOURCES
        ${CMAKE_SOURCE_DIR}/bootloader/main.cpp
        ${CMAKE_SOURCE_DIR}/bootloader/protocol.cpp ${CMAKE_SOURCE_DIR}/bootloader/protocol.hpp
        ${CMAKE_SOURCE_DIR}/bootloader/usi-slave.cpp ${CMAKE_SOURCE_DIR}/bootloader/usi-slave.hpp
        ${CMAKE_SOURCE_DIR}/common/usi.hpp)

# the linker fails if the application would overlap the bootloader
target_link_options(${PROJECT_NAME} PRIVATE -Wl,--defsym=__TEXT_REGION_LENGTH__=${BOOTLOADER_START})

# built directly with avr-g++, it needs -Os, LTO and no crt startup files
ADD_CUSTOM_COMMAND(
        OUTPUT ${CMAKE_BINARY_DIR}/${BOOTLOADER_NAME}.elf ${CMAKE_BINARY_DIR}/${BOOTLOADER_NAME}.hex
        COMMAND avr-g++ -Os -flto -mmcu=${DEVICE} -DF_CPU=${FREQ}UL -std=c++23 -Wall -Wextra -pedantic
        -fno-exceptions -fno-rtti -fno-threadsafe-statics -ffunction-sections -fdata-sections
        -DTWIBOOT_START=${BOOTLOADER_START} -DTWIBOOT_TIMEOUT_MS=${BOOTLOADER_TIMEOUT_MS}
        -I${CMAKE_SOURCE_DIR}/common
        -nostartfiles -Wl,--relax,--gc-sections,--section-start=.text=${BOOTLOADER_START}
        -Wl,--defsym=__TEXT_REGION_LENGTH__=0x1000
        -o ${CMAKE_BINARY_DIR}/${BOOTLOADER_NAME}.elf
        ${CMAKE_SOURCE_DIR}/bootloader/main.cpp ${CMAKE_SOURCE_DIR}/bootloader/protocol.cpp ${CMAKE_SOURCE_DIR}/bootloader/usi-slave.cpp
        COMMAND avr-objcopy -j .text -j .data -O ihex ${CMAKE_BINARY_DIR}/${BOOTLOADER_NAME}.elf ${CMAKE_BINARY_DIR}/${BOOTLOADER_NAME}.hex
        COMMAND avr-size ${CMAKE_BINARY_DIR}/${BOOTLOADER_NAME}.elf --mcu=${DEVICE} --format=avr
        DEPENDS ${BOOTLOADER_SOURCES}
        VERBATIM)
ADD_CUSTOM_TARGET(bootloader ALL DEPENDS ${CMAKE_BINARY_DIR}/${BOOTLOADER_NAME}.hex SOURCES ${BOOTLOADER_SOURCES})
ADD_DEPENDENCIES(${PROJECT_NAME} bootloader)

# fuses -U lfuse:w:0xe2:m -U hfuse:w:0xdc:m -U efuse:w:0xfe:m (efuse 0xfe = SELFPRGEN, required by the bootloader)

ADD_CUSTOM_COMMAND(TARGET ${PROJECT_NAME} POST_BUILD COMMAND avr-objcopy -O ihex -R.eeprom ${CMAKE_BINARY_DIR}/${PROJECT_NAME} ${CMAKE_BINARY_DIR}/${PROJECT_NAME}.hex)
ADD_CUSTOM_COMMAND(TARGET ${PROJECT_NAME} POST_BUILD COMMAND avr-objcopy -O ihex -j .eeprom --set-section-flags=.eeprom="alloc,load" --change-section-lma .eeprom=0 ${CMAKE_BINARY_DIR}/${PROJECT_NAME} ${CMAKE_BINARY_DIR}/${PROJECT_NAME}.eep)
ADD_CUSTOM_COMMAND(TARGET ${PROJECT_NAME} POST_BUILD COMMAND avr-size ${CMAKE_BINARY_DIR}/${PROJECT_NAME} --mcu=${DEVICE} --format=avr)
# application + bootloader with the vector table patched like the bootloader does, for ISP flashing
ADD_CUSTOM_COMMAND(TARGET ${PROJECT_NAME} POST_BUILD COMMAND python3 ${CMAKE_SOURCE_DIR}/bootloader/merge-with-bootloader.py
        ${CMAKE_BINARY_DIR}/${PROJECT_NAME} ${CMAKE_BINARY_DIR}/${PROJECT_NAME}.hex
        ${CMAKE_BINARY_DIR}/${BOOTLOADER_NAME}.elf ${CMAKE_BINARY_DIR}/${BOOTLOADER_NAME}.hex
        ${BOOTLOADER_START} ${BOOTLOADER_APP_VECTOR_NUM} ${CMAKE_BINARY_DIR}/${PROJECT_NAME}-with-bootloader.hex)

set_property(DIRECTORY PROPERTY ADDITIONAL_MAKE_CLEAN_FILES
        "${CMAKE_BINARY_DIR}/${PROJECT_NAME}.hex"
        "${CMAKE_BINARY_DIR}/${PROJECT_NAME}.eep"
        "${CMAKE_BINARY_DIR}/${PROJECT_NAME}-with-bootloader.hex"
)
