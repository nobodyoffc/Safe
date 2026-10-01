cmake_minimum_required(VERSION 3.22.1)
project(fcargon2 C)

# The Argon2 reference implementation (P-H-C/phc-winner-argon2 f57e61e, CC0 / Apache-2.0),
# vendored without its tests, CLI and SSE code. Kdf runs every password through it: the
# same hash as BouncyCastle's, without the seconds a cold ART spends running it in Java.
add_library(fcargon2 SHARED
        argon2_jni.c
        argon2/src/argon2.c
        argon2/src/core.c
        argon2/src/ref.c
        argon2/src/encoding.c
        argon2/src/blake2/blake2b.c)
target_include_directories(fcargon2 PRIVATE argon2/include argon2/src)
# Kdf only asks for one lane, so no threads.
target_compile_definitions(fcargon2 PRIVATE ARGON2_NO_THREADS)
# Optimise debug builds too: unoptimised, a 64 MiB run takes seconds.
target_compile_options(fcargon2 PRIVATE -O3 -Wall)
