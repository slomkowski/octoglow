cmake_minimum_required(VERSION 3.7)
project(front-display-test)

set(CMAKE_CXX_STANDARD 17)

SET(CMAKE_CXX_FLAGS "-g -O0 -std=c++17 -DF_CPU=${FREQ}UL -Wall -Wextra -pedantic")
include_directories(../noarch ../test)

SET(SOURCES main.hpp display_test.cpp i2c-slave_test.cpp)

enable_testing()

find_package(GTest REQUIRED)
include_directories(${GTEST_INCLUDE_DIRS})

ADD_EXECUTABLE(${PROJECT_NAME} ${SOURCES} ${LIBRARY_SOURCES})
target_link_libraries(${PROJECT_NAME} ${GTEST_BOTH_LIBRARIES})

add_test(AllTests ${PROJECT_NAME})
