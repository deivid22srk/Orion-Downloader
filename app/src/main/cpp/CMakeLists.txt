cmake_minimum_required(VERSION 3.22.1)

project("orion_downloader")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -ffast-math -pthread -Wall")
set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -O3 -DNDEBUG -fvisibility=hidden")

add_library(
    orion_downloader
    SHARED
    download_engine.cpp
    jni_bridge.cpp
)

target_link_libraries(
    orion_downloader
    android
    log
    z
)
