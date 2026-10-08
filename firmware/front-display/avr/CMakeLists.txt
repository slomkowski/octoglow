cmake_minimum_required(VERSION 3.10)
project(octoglow-front-display-avr)

SET(CMAKE_SYSTEM_NAME Generic)
set(CMAKE_SYSTEM_PROCESSOR avr)
set(CMAKE_CROSS_COMPILING 1)

SET(DEVICE "atmega88p")

SET(CMAKE_C_COMPILER avr-gcc)
SET(CMAKE_CXX_COMPILER avr-g++)

SET(CMAKE_C_FLAGS "-O2 -mmcu=${DEVICE} -DF_CPU=${FREQ}UL -std=c11 -Wl,--gc-sections -Wall -Wextra -pedantic")
SET(CMAKE_CXX_FLAGS "-O2 -mmcu=${DEVICE} -DF_CPU=${FREQ}UL -std=c++17 -Wl,--gc-sections -Wall -Wextra -pedantic -fno-exceptions -fno-rtti")

# -O3 of the Release build type would override -O2 above, the application wouldn't fit below the bootloader;
# the hot display code is optimized with O3 by its attribute anyway
SET(CMAKE_CXX_FLAGS_RELEASE "-DNDEBUG")

include_directories(../noarch ../avr)

SET(SOURCES
        main.cpp main.hpp
        i2c-slave_hd.cpp
        display_hd.cpp
        encoder_hd.cpp
        eeprom_hd.cpp)

ADD_EXECUTABLE(${PROJECT_NAME} ${SOURCES} ${LIBRARY_SOURCES})

SET(BOOTLOADER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../bootloader)
SET(BOOTLOADER_NAME octoglow-front-display-bootloader)
SET(BOOTLOADER_SOURCES
        ${BOOTLOADER_DIR}/main.cpp
        ${BOOTLOADER_DIR}/flash_hd.cpp ${BOOTLOADER_DIR}/flash.hpp
        ${BOOTLOADER_DIR}/screen.cpp ${BOOTLOADER_DIR}/screen.hpp
        ${BOOTLOADER_DIR}/twiboot.cpp ${BOOTLOADER_DIR}/twiboot.hpp
        ${BOOTLOADER_DIR}/twi-slave.cpp ${BOOTLOADER_DIR}/twi-slave.hpp
        ${CMAKE_CURRENT_SOURCE_DIR}/vfd.hpp
        ${CMAKE_CURRENT_SOURCE_DIR}/../noarch/Font5x7.hpp
        ${CMAKE_CURRENT_SOURCE_DIR}/../noarch/i2c-slave.hpp)

# the linker fails if the application would overlap the bootloader
target_link_options(${PROJECT_NAME} PRIVATE -Wl,--defsym=__TEXT_REGION_LENGTH__=${BOOTLOADER_START})

# built directly with avr-g++, it needs -Os, LTO and no crt startup files
ADD_CUSTOM_COMMAND(
        OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/${BOOTLOADER_NAME}.elf ${CMAKE_CURRENT_BINARY_DIR}/${BOOTLOADER_NAME}.hex
        COMMAND avr-g++ -Os -flto -mmcu=${DEVICE} -DF_CPU=${FREQ}UL -std=c++20 -Wall -Wextra -pedantic
        -fno-exceptions -fno-rtti -fno-threadsafe-statics -ffunction-sections -fdata-sections
        -DTWIBOOT_START=${BOOTLOADER_START} -DTWIBOOT_TIMEOUT_MS=${BOOTLOADER_TIMEOUT_MS}
        -I${BOOTLOADER_DIR} -I${CMAKE_CURRENT_SOURCE_DIR} -I${CMAKE_CURRENT_SOURCE_DIR}/../noarch
        -nostartfiles -Wl,--relax,--gc-sections,--section-start=.text=${BOOTLOADER_START}
        -Wl,--defsym=__TEXT_REGION_LENGTH__=0x2000
        -o ${CMAKE_CURRENT_BINARY_DIR}/${BOOTLOADER_NAME}.elf
        ${BOOTLOADER_DIR}/main.cpp ${BOOTLOADER_DIR}/flash_hd.cpp ${BOOTLOADER_DIR}/screen.cpp
        ${BOOTLOADER_DIR}/twiboot.cpp ${BOOTLOADER_DIR}/twi-slave.cpp
        COMMAND avr-objcopy -j .text -j .data -O ihex ${CMAKE_CURRENT_BINARY_DIR}/${BOOTLOADER_NAME}.elf ${CMAKE_CURRENT_BINARY_DIR}/${BOOTLOADER_NAME}.hex
        COMMAND avr-size ${CMAKE_CURRENT_BINARY_DIR}/${BOOTLOADER_NAME}.elf --mcu=${DEVICE} --format=avr
        DEPENDS ${BOOTLOADER_SOURCES}
        VERBATIM)
ADD_CUSTOM_TARGET(bootloader ALL DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/${BOOTLOADER_NAME}.hex SOURCES ${BOOTLOADER_SOURCES})
ADD_DEPENDENCIES(${PROJECT_NAME} bootloader)

# fuses: -U lfuse:w:0xce:m -U hfuse:w:0xdd:m -U efuse:w:0xf8:m
# efuse 0xf8: boot section 1024 words (BOOTSZ = 00), reset starts the bootloader (BOOTRST)

ADD_CUSTOM_COMMAND(TARGET ${PROJECT_NAME} POST_BUILD COMMAND avr-objcopy -O ihex -R.eeprom ${CMAKE_CURRENT_BINARY_DIR}/${PROJECT_NAME} ${CMAKE_CURRENT_BINARY_DIR}/${PROJECT_NAME}.hex)
ADD_CUSTOM_COMMAND(TARGET ${PROJECT_NAME} POST_BUILD COMMAND avr-objcopy -O ihex -j .eeprom --set-section-flags=.eeprom="alloc,load" --change-section-lma .eeprom=0 ${CMAKE_CURRENT_BINARY_DIR}/${PROJECT_NAME} ${CMAKE_CURRENT_BINARY_DIR}/${PROJECT_NAME}.eep)
ADD_CUSTOM_COMMAND(TARGET ${PROJECT_NAME} POST_BUILD COMMAND avr-size ${CMAKE_CURRENT_BINARY_DIR}/${PROJECT_NAME} --mcu=${DEVICE} --format=avr)
# application + bootloader for ISP flashing
ADD_CUSTOM_COMMAND(TARGET ${PROJECT_NAME} POST_BUILD COMMAND python3 ${BOOTLOADER_DIR}/merge-with-bootloader.py
        ${CMAKE_CURRENT_BINARY_DIR}/${PROJECT_NAME}.hex
        ${CMAKE_CURRENT_BINARY_DIR}/${BOOTLOADER_NAME}.elf ${CMAKE_CURRENT_BINARY_DIR}/${BOOTLOADER_NAME}.hex
        ${BOOTLOADER_START} ${CMAKE_CURRENT_BINARY_DIR}/${PROJECT_NAME}-with-bootloader.hex)
