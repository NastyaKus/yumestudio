cmake_minimum_required(VERSION 3.22.1)
project(yume LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(oboe REQUIRED CONFIG)

add_library(yume SHARED
        jni/yume_jni.cpp
        engine/Engine.cpp
        engine/AudioHost.cpp
        dsp/Effects.cpp
        instruments/Synth.cpp)

target_include_directories(yume PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_compile_options(yume PRIVATE -Wall -Wno-unused-parameter -ffast-math -O2)
target_link_libraries(yume PRIVATE oboe::oboe android log)
# Android 15+/16: поддержка устройств со страницами 16 КБ
target_link_options(yume PRIVATE "-Wl,-z,max-page-size=16384")
