cmake_minimum_required(VERSION 3.18.1)

project("pty")

add_library(pty SHARED pty.c)

# Android 15+ devices may run a 16 KB kernel page size. ELF segments aligned to 4 KB
# cannot be mapped there, so libpty.so would fail to load and the terminal would be
# dead on those devices. Force 16 KB alignment for the shared object.
target_link_options(pty PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)

target_link_libraries(pty log)
