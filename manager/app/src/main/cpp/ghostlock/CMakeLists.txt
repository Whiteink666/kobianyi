# GhostLock (CVE-2026-43499, futex PI UAF) - temporary root without unlocking
# the bootloader. Ported from YuKongA/ghostlock-app
# (https://github.com/YuKongA/ghostlock-app) and p2p3p/GhostLock-for-OnePlus.
#
# Unlike DFRoot the exploit is pure userspace C++, so it compiles with the NDK
# and needs nothing from the kernel build. The binary is produced as
# libghostlock.so (AGP only packages lib*.so into the APK) and exec'd from
# nativeLibraryDir with a GLK1 profile document on stdin.

if(NOT ANDROID_ABI STREQUAL "arm64-v8a")
    return()
endif()

set(GL_DIR ${CMAKE_CURRENT_SOURCE_DIR})

file(GLOB_RECURSE GL_SOURCES CONFIGURE_DEPENDS
    "${GL_DIR}/core/*.cpp"
    "${GL_DIR}/core/*.c"
)

add_executable(ghostlock ${GL_SOURCES})
add_dependencies(ghostlock kernelsu)

set_target_properties(ghostlock PROPERTIES
    PREFIX "lib"
    SUFFIX ".so"
    RUNTIME_OUTPUT_DIRECTORY "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}")

target_include_directories(ghostlock PRIVATE "${GL_DIR}/core")

target_compile_definitions(ghostlock PRIVATE
    TARGET_CONFIG_H="kernel/target.h")

# the whole app builds with ANDROID_STL=none, which also turns exceptions off;
# ghostlock uses throw/catch for its fatal errors, so re-enable them here
target_compile_options(ghostlock PRIVATE
    -std=c++23
    -fno-rtti
    -fexceptions
    -O2
    -Wno-unused-parameter
    -Wno-sign-compare
    -Wno-unused-function)

# The app links with -nostdlib++ (ANDROID_STL=none), so the C++ runtime has to
# be pulled in explicitly: libc++ for the STL symbols, libc++abi for the
# exception personality (__cxa_*). Both are static so the binary stays
# self-contained.
target_link_libraries(ghostlock PRIVATE c++ c++abi)

target_link_options(ghostlock PRIVATE
    -pthread
    -static-libstdc++
    -Wl,-z,max-page-size=16384)
