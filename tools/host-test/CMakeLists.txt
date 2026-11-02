cmake_minimum_required(VERSION 3.18)
project(yume_host_test CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
if (NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE RelWithDebInfo)
endif ()

set(CPP ${CMAKE_CURRENT_SOURCE_DIR}/../../app/src/main/cpp)
add_executable(engine_test
        engine_test.cpp
        ${CPP}/engine/Engine.cpp
        ${CPP}/dsp/Effects.cpp
        ${CPP}/instruments/Synth.cpp)
target_include_directories(engine_test PRIVATE ${CPP})
target_compile_options(engine_test PRIVATE -Wall -Wextra -Wno-unused-parameter -fsanitize=address,undefined)
target_link_options(engine_test PRIVATE -fsanitize=address,undefined)
find_package(Threads REQUIRED)
target_link_libraries(engine_test PRIVATE Threads::Threads)
