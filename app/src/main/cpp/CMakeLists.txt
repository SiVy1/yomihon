cmake_minimum_required(VERSION 3.22.1)

project(yomihon_torrent)

add_library(
    yomihon_torrent
    SHARED
    native_torrent_bridge.cpp
)

find_library(
    log-lib
    log
)

target_link_libraries(
    yomihon_torrent
    ${log-lib}
)

