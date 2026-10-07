# Android build: bridge core + JNI layer, linked against the prebuilt static
# libraries from native/build-deps.sh.
cmake_minimum_required(VERSION 3.22)
project(qalcbridge CXX)
set(CMAKE_CXX_STANDARD 17)

set(NATIVE ${CMAKE_CURRENT_SOURCE_DIR}/../../../../native)
set(PREBUILT ${NATIVE}/prebuilt/${ANDROID_ABI})
if(NOT EXISTS ${PREBUILT}/lib/libqalculate.a)
    message(FATAL_ERROR "Missing ${PREBUILT}; run native/build-deps.sh first")
endif()

foreach(lib qalculate mpfr gmp xml2)
    add_library(${lib} STATIC IMPORTED)
    set_target_properties(${lib} PROPERTIES IMPORTED_LOCATION ${PREBUILT}/lib/lib${lib}.a)
endforeach()

add_library(qalcbridge SHARED ${NATIVE}/bridge/bridge_core.cpp jni_bridge.cpp)
target_include_directories(qalcbridge PRIVATE ${NATIVE}/bridge ${PREBUILT}/include ${PREBUILT}/include/libxml2)
# order matters for static linking: users before the libraries they use
target_link_libraries(qalcbridge qalculate xml2 mpfr gmp log)
