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

# the flash ends at the bootloader, the vector table is just below it; the linker fails if the application doesn't fit
configure_file(application.ld.in ${CMAKE_CURRENT_BINARY_DIR}/application.ld @ONLY)
target_link_options(${PROJECT_NAME} PRIVATE -T ${CMAKE_CURRENT_BINARY_DIR}/application.ld)
set_target_properties(${PROJECT_NAME} PROPERTIES LINK_DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/application.ld)

SET(BOOTLOADER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../bootloader)
SET(BOOTLOADER_NAME octoglow-geiger-bootloader)
SET(BOOTLOADER_SOURCES
        ${BOOTLOADER_DIR}/main.cpp
        ${BOOTLOADER_DIR}/flash_hd.cpp ${BOOTLOADER_DIR}/flash.hpp
        ${BOOTLOADER_DIR}/twiboot.cpp ${BOOTLOADER_DIR}/twiboot.hpp
        ${BOOTLOADER_DIR}/usci-slave.cpp ${BOOTLOADER_DIR}/usci-slave.hpp
        ${CMAKE_CURRENT_SOURCE_DIR}/../noarch/i2c-slave.hpp)

configure_file(${BOOTLOADER_DIR}/bootloader.ld.in ${CMAKE_CURRENT_BINARY_DIR}/bootloader.ld @ONLY)

# built directly with msp430-elf-g++, it needs -Os, LTO and no crt startup files
ADD_CUSTOM_COMMAND(
        OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/${BOOTLOADER_NAME}.elf ${CMAKE_CURRENT_BINARY_DIR}/${BOOTLOADER_NAME}.hex
        COMMAND msp430-elf-g++ -Os -flto -mmcu=${DEVICE} -std=c++17 -Wall -Wextra -pedantic
        -fno-exceptions -fno-rtti -fno-threadsafe-statics -ffunction-sections -fdata-sections
        -fno-tree-loop-distribute-patterns
        -DTWIBOOT_START=${BOOTLOADER_START} -DTWIBOOT_TIMEOUT_MS=${BOOTLOADER_TIMEOUT_MS}
        -I${BOOTLOADER_DIR} -I${CMAKE_CURRENT_SOURCE_DIR}/../noarch -I${SUPPORT_FILE_DIRECTORY}
        -nostartfiles -T ${CMAKE_CURRENT_BINARY_DIR}/bootloader.ld -Wl,--gc-sections
        -o ${CMAKE_CURRENT_BINARY_DIR}/${BOOTLOADER_NAME}.elf
        ${BOOTLOADER_DIR}/main.cpp ${BOOTLOADER_DIR}/flash_hd.cpp
        ${BOOTLOADER_DIR}/twiboot.cpp ${BOOTLOADER_DIR}/usci-slave.cpp
        COMMAND msp430-elf-objcopy -O ihex ${CMAKE_CURRENT_BINARY_DIR}/${BOOTLOADER_NAME}.elf ${CMAKE_CURRENT_BINARY_DIR}/${BOOTLOADER_NAME}.hex
        COMMAND msp430-elf-size -A ${CMAKE_CURRENT_BINARY_DIR}/${BOOTLOADER_NAME}.elf
        DEPENDS ${BOOTLOADER_SOURCES} ${CMAKE_CURRENT_BINARY_DIR}/bootloader.ld
        VERBATIM)
ADD_CUSTOM_TARGET(bootloader ALL DEPENDS ${CMAKE_CURRENT_BINARY_DIR}/${BOOTLOADER_NAME}.hex SOURCES ${BOOTLOADER_SOURCES})
ADD_DEPENDENCIES(${PROJECT_NAME} bootloader)

ADD_CUSTOM_COMMAND(TARGET ${PROJECT_NAME} POST_BUILD COMMAND msp430-elf-objcopy -O ihex ${CMAKE_CURRENT_BINARY_DIR}/${PROJECT_NAME} ${CMAKE_CURRENT_BINARY_DIR}/${PROJECT_NAME}.hex)
ADD_CUSTOM_COMMAND(TARGET ${PROJECT_NAME} POST_BUILD COMMAND msp430-elf-size ${CMAKE_CURRENT_BINARY_DIR}/${PROJECT_NAME} -B -d)
