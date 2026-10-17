# DirtyFrag Root (CVE-2026-43284) - temporary root without unlocking the bootloader.
# Ported from diabl0w/DFRoot (https://github.com/diabl0w/DFRoot), MIT-ish upstream.
#
# The exploit needs raw arm64 syscalls and page-cache layout assumptions, so it is
# only built for arm64-v8a. Two executables are produced, both named lib*.so because
# AGP only packages lib*.so into the APK:
#   libdfroot.so    - the exploit entry point, exec'd from nativeLibraryDir
#   libbootstrap.so - run by dfroot.ko through call_usermodehelper, starts ksud

if(NOT ANDROID_ABI STREQUAL "arm64-v8a")
    return()
endif()

enable_language(ASM)

# Manager package owning the staged ksud/bootstrap. Injected from Gradle; the
# default keeps the upstream path when the project is built without it.
if(NOT DF_PACKAGE)
    set(DF_PACKAGE "com.whiteink.furryroot")
endif()

set(DF_DIR   ${CMAKE_CURRENT_SOURCE_DIR})
set(DF_STAGE ${CMAKE_CURRENT_BINARY_DIR}/stage)
# ninja 的工作目录（顶层 build 目录）。把 ko / splicehelper 也放一份到这里，
# .incbin 按当前目录搜索时就能命中，不只依赖 -I。
set(DF_CWD   ${CMAKE_BINARY_DIR})

# dfroot.ko is a kernel module and cannot be built by the NDK. It is compiled per KMI
# in CI (see .github/workflows) and dropped into cpp/dfroot/ko/. Missing modules fall
# back to an empty placeholder so local builds still work; running the exploit on that
# KMI will then fail with "Unsupported kernel".
set(DF_KMIS
    android12-5.10
    android13-5.10
    android13-5.15
    android14-5.15
    android14-6.1
    android15-6.6
    android16-6.12
    android17-6.18
)

file(MAKE_DIRECTORY "${DF_STAGE}/ko")
foreach(kmi IN LISTS DF_KMIS)
    set(DF_KO_SRC "${DF_DIR}/ko/dfroot-${kmi}.ko")
    if(NOT EXISTS "${DF_KO_SRC}")
        if(NOT DF_KO_WARNED)
            message(WARNING "dfroot: no prebuilt ko in ${DF_DIR}/ko, using empty placeholders")
            set(DF_KO_WARNED TRUE)
        endif()
        set(DF_KO_SRC "${DF_DIR}/ko/.placeholder")
        if(NOT EXISTS "${DF_KO_SRC}")
            file(WRITE "${DF_KO_SRC}" "")
        endif()
    endif()
    configure_file("${DF_KO_SRC}" "${DF_STAGE}/ko/dfroot-${kmi}.ko" COPYONLY)
endforeach()

# splicehelper is a freestanding static binary, embedded into libdfroot.so and used as
# the crash_dump bridge that can reach vendor_file labelled files.
add_custom_command(
    OUTPUT "${DF_STAGE}/splicehelper"
    COMMAND "${CMAKE_C_COMPILER}"
            --target=${CMAKE_C_COMPILER_TARGET}
            --sysroot=${CMAKE_SYSROOT}
            "${DF_DIR}/splicehelper.c"
            -o "${DF_STAGE}/splicehelper"
            -nodefaultlibs -nostartfiles -ffreestanding -static
    COMMAND "${CMAKE_STRIP}" "${DF_STAGE}/splicehelper"
    COMMAND "${CMAKE_COMMAND}" -E copy_if_different
            "${DF_STAGE}/splicehelper" "${DF_CWD}/splicehelper"
    DEPENDS "${DF_DIR}/splicehelper.c"
    COMMENT "Building splicehelper"
)
add_custom_target(dfroot_splicehelper DEPENDS "${DF_STAGE}/splicehelper")

# ko/splicehelper 都要在编译前躺进 stage 目录，.configure 阶段复制不一定生效
# （CI 上 ko 是在 configure 之后才下载到位的），所以放到构建期同步
add_custom_target(dfroot_stage_sync
    COMMAND "${CMAKE_COMMAND}" -E make_directory "${DF_STAGE}/ko"
    COMMAND "${CMAKE_COMMAND}" -E copy_directory "${DF_DIR}/ko" "${DF_STAGE}/ko"
    COMMAND "${CMAKE_COMMAND}" -E make_directory "${DF_CWD}/ko"
    COMMAND "${CMAKE_COMMAND}" -E copy_directory "${DF_DIR}/ko" "${DF_CWD}/ko"
)
add_dependencies(dfroot_splicehelper dfroot_stage_sync)

add_executable(dfroot exp.c elf_parser.c libcxx.S)
add_dependencies(dfroot dfroot_splicehelper)
set_target_properties(dfroot PROPERTIES
    PREFIX "lib"
    SUFFIX ".so"
    RUNTIME_OUTPUT_DIRECTORY "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}")

# .incbin resolves "ko/*.ko" and "splicehelper" against the -I search path.
target_include_directories(dfroot PRIVATE "${DF_DIR}" "${DF_STAGE}")
target_compile_options(dfroot PRIVATE
    -std=gnu11
    -fno-stack-protector
    -fomit-frame-pointer)
target_compile_definitions(dfroot PRIVATE DF_PACKAGE="${DF_PACKAGE}")
set_source_files_properties(exp.c PROPERTIES
    OBJECT_DEPENDS "${DF_STAGE}/splicehelper")

find_library(df_log_lib log)
target_link_libraries(dfroot ${df_log_lib})
target_link_options(dfroot PRIVATE -Wl,-z,max-page-size=16384)

add_executable(bootstrap bootstrap.c)
target_compile_definitions(bootstrap PRIVATE DF_PACKAGE="${DF_PACKAGE}")
set_target_properties(bootstrap PROPERTIES
    PREFIX "lib"
    SUFFIX ".so"
    RUNTIME_OUTPUT_DIRECTORY "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}")
target_link_options(bootstrap PRIVATE -Wl,-z,max-page-size=16384)
