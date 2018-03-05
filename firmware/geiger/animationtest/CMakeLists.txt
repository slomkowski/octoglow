cmake_minimum_required(VERSION 3.7)
project(octoglow-geiger-animationtest)

set(CMAKE_CXX_STANDARD 14)
set(CMAKE_VERBOSE_MAKEFILE ON)

SET(CMAKE_CXX_FLAGS "-g -O0 -std=c++14 -DF_CPU=${FREQ}UL -Wall -Wextra -pedantic -ansi -Wmissing-declarations -Winit-self -Woverloaded-virtual -Wuninitialized")
include_directories(../noarch)
include_directories(../../lib/libfixmath/libfixmath)

INCLUDE(FindPkgConfig)

PKG_SEARCH_MODULE(SDL2 REQUIRED sdl2)
INCLUDE_DIRECTORIES(${SDL2_INCLUDE_DIRS} ${SDL2IMAGE_INCLUDE_DIRS})

SET(SOURCES main.cpp
        ../noarch/animation.cpp ../noarch/animation.hpp
        ../../lib/libfixmath/libfixmath/fix16.hpp ../../lib/libfixmath/libfixmath/fix16.h ../../lib/libfixmath/libfixmath/fix16.c)

ADD_EXECUTABLE(${PROJECT_NAME} ${SOURCES})
target_link_libraries(${PROJECT_NAME} ${SDL2_LIBRARIES})
