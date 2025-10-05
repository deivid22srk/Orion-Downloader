cmake_minimum_required(VERSION 3.22.1)

project("orion_downloader")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -ffast-math -pthread -Wall")

find_library(log-lib log)
find_library(android-lib android)

add_library(orion_downloader SHARED
    jni_bridge.cpp
    download_engine.cpp
)

target_link_libraries(orion_downloader
    ${log-lib}
    ${android-lib}
    z
)
