# Host build of the bridge core against the system libqalculate, for tests.
cmake_minimum_required(VERSION 3.22)
project(qalcbridge_host CXX)
set(CMAKE_CXX_STANDARD 17)

find_package(PkgConfig REQUIRED)
pkg_check_modules(QALC REQUIRED IMPORTED_TARGET libqalculate)

add_library(bridge_core STATIC bridge_core.cpp)
target_include_directories(bridge_core PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})
target_link_libraries(bridge_core PUBLIC PkgConfig::QALC)

add_executable(bridge_test tests/bridge_test.cpp)
target_link_libraries(bridge_test PRIVATE bridge_core)
target_compile_definitions(bridge_test PRIVATE FIXTURES="${CMAKE_CURRENT_SOURCE_DIR}/tests/fixtures")
