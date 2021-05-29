// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 1)
#include <stdatomic.h>
#include <stdint.h>
#include <stdlib.h>

// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 14)
/**
 * Init
 * */
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 18)
void scalanative_atomic_init_byte(int8_t *atm, int8_t init_value) {
    *atm = ATOMIC_VAR_INIT(init_value);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 18)
void scalanative_atomic_init_short(int16_t *atm, int16_t init_value) {
    *atm = ATOMIC_VAR_INIT(init_value);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 18)
void scalanative_atomic_init_int(int32_t *atm, int32_t init_value) {
    *atm = ATOMIC_VAR_INIT(init_value);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 18)
void scalanative_atomic_init_long(int64_t *atm, int64_t init_value) {
    *atm = ATOMIC_VAR_INIT(init_value);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 18)
void scalanative_atomic_init_ubyte(uint8_t *atm, uint8_t init_value) {
    *atm = ATOMIC_VAR_INIT(init_value);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 18)
void scalanative_atomic_init_ushort(uint16_t *atm, uint16_t init_value) {
    *atm = ATOMIC_VAR_INIT(init_value);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 18)
void scalanative_atomic_init_uint(uint32_t *atm, uint32_t init_value) {
    *atm = ATOMIC_VAR_INIT(init_value);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 18)
void scalanative_atomic_init_ulong(uint64_t *atm, uint64_t init_value) {
    *atm = ATOMIC_VAR_INIT(init_value);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 18)
void scalanative_atomic_init_char(char *atm, char init_value) {
    *atm = ATOMIC_VAR_INIT(init_value);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 18)
void scalanative_atomic_init_uchar(unsigned char *atm,
                                   unsigned char init_value) {
    *atm = ATOMIC_VAR_INIT(init_value);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 18)
void scalanative_atomic_init_csize(size_t *atm, size_t init_value) {
    *atm = ATOMIC_VAR_INIT(init_value);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 22)

/**
 * Load and store
 * */
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 27)
int8_t scalanative_atomic_load_byte(_Atomic(int8_t) * atm) {
    return atomic_load(atm);
}

void scalanative_atomic_store_byte(_Atomic(int8_t) * atm, int8_t val) {
    atomic_store(atm, val);
}

int8_t scalanative_atomic_exchange_byte(_Atomic(int8_t) * atm, int8_t val) {
    return atomic_exchange(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 27)
int16_t scalanative_atomic_load_short(_Atomic(int16_t) * atm) {
    return atomic_load(atm);
}

void scalanative_atomic_store_short(_Atomic(int16_t) * atm, int16_t val) {
    atomic_store(atm, val);
}

int16_t scalanative_atomic_exchange_short(_Atomic(int16_t) * atm, int16_t val) {
    return atomic_exchange(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 27)
int32_t scalanative_atomic_load_int(_Atomic(int32_t) * atm) {
    return atomic_load(atm);
}

void scalanative_atomic_store_int(_Atomic(int32_t) * atm, int32_t val) {
    atomic_store(atm, val);
}

int32_t scalanative_atomic_exchange_int(_Atomic(int32_t) * atm, int32_t val) {
    return atomic_exchange(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 27)
int64_t scalanative_atomic_load_long(_Atomic(int64_t) * atm) {
    return atomic_load(atm);
}

void scalanative_atomic_store_long(_Atomic(int64_t) * atm, int64_t val) {
    atomic_store(atm, val);
}

int64_t scalanative_atomic_exchange_long(_Atomic(int64_t) * atm, int64_t val) {
    return atomic_exchange(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 27)
uint8_t scalanative_atomic_load_ubyte(_Atomic(uint8_t) * atm) {
    return atomic_load(atm);
}

void scalanative_atomic_store_ubyte(_Atomic(uint8_t) * atm, uint8_t val) {
    atomic_store(atm, val);
}

uint8_t scalanative_atomic_exchange_ubyte(_Atomic(uint8_t) * atm, uint8_t val) {
    return atomic_exchange(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 27)
uint16_t scalanative_atomic_load_ushort(_Atomic(uint16_t) * atm) {
    return atomic_load(atm);
}

void scalanative_atomic_store_ushort(_Atomic(uint16_t) * atm, uint16_t val) {
    atomic_store(atm, val);
}

uint16_t scalanative_atomic_exchange_ushort(_Atomic(uint16_t) * atm,
                                            uint16_t val) {
    return atomic_exchange(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 27)
uint32_t scalanative_atomic_load_uint(_Atomic(uint32_t) * atm) {
    return atomic_load(atm);
}

void scalanative_atomic_store_uint(_Atomic(uint32_t) * atm, uint32_t val) {
    atomic_store(atm, val);
}

uint32_t scalanative_atomic_exchange_uint(_Atomic(uint32_t) * atm,
                                          uint32_t val) {
    return atomic_exchange(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 27)
uint64_t scalanative_atomic_load_ulong(_Atomic(uint64_t) * atm) {
    return atomic_load(atm);
}

void scalanative_atomic_store_ulong(_Atomic(uint64_t) * atm, uint64_t val) {
    atomic_store(atm, val);
}

uint64_t scalanative_atomic_exchange_ulong(_Atomic(uint64_t) * atm,
                                           uint64_t val) {
    return atomic_exchange(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 27)
char scalanative_atomic_load_char(_Atomic(char) * atm) {
    return atomic_load(atm);
}

void scalanative_atomic_store_char(_Atomic(char) * atm, char val) {
    atomic_store(atm, val);
}

char scalanative_atomic_exchange_char(_Atomic(char) * atm, char val) {
    return atomic_exchange(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 27)
unsigned char scalanative_atomic_load_uchar(_Atomic(unsigned char) * atm) {
    return atomic_load(atm);
}

void scalanative_atomic_store_uchar(_Atomic(unsigned char) * atm,
                                    unsigned char val) {
    atomic_store(atm, val);
}

unsigned char scalanative_atomic_exchange_uchar(_Atomic(unsigned char) * atm,
                                                unsigned char val) {
    return atomic_exchange(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 27)
size_t scalanative_atomic_load_csize(_Atomic(size_t) * atm) {
    return atomic_load(atm);
}

void scalanative_atomic_store_csize(_Atomic(size_t) * atm, size_t val) {
    atomic_store(atm, val);
}

size_t scalanative_atomic_exchange_csize(_Atomic(size_t) * atm, size_t val) {
    return atomic_exchange(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 39)

/**
 * Compare and Swap
 * */
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 45)
int scalanative_atomic_compare_and_swap_strong_byte(_Atomic(int8_t) * atm,
                                                    int8_t *expected,
                                                    int8_t desired) {
    return atomic_compare_exchange_strong(atm, expected, desired);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 45)
int scalanative_atomic_compare_and_swap_weak_byte(_Atomic(int8_t) * atm,
                                                  int8_t *expected,
                                                  int8_t desired) {
    return atomic_compare_exchange_weak(atm, expected, desired);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 45)
int scalanative_atomic_compare_and_swap_strong_short(_Atomic(int16_t) * atm,
                                                     int16_t *expected,
                                                     int16_t desired) {
    return atomic_compare_exchange_strong(atm, expected, desired);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 45)
int scalanative_atomic_compare_and_swap_weak_short(_Atomic(int16_t) * atm,
                                                   int16_t *expected,
                                                   int16_t desired) {
    return atomic_compare_exchange_weak(atm, expected, desired);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 45)
int scalanative_atomic_compare_and_swap_strong_int(_Atomic(int32_t) * atm,
                                                   int32_t *expected,
                                                   int32_t desired) {
    return atomic_compare_exchange_strong(atm, expected, desired);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 45)
int scalanative_atomic_compare_and_swap_weak_int(_Atomic(int32_t) * atm,
                                                 int32_t *expected,
                                                 int32_t desired) {
    return atomic_compare_exchange_weak(atm, expected, desired);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 45)
int scalanative_atomic_compare_and_swap_strong_long(_Atomic(int64_t) * atm,
                                                    int64_t *expected,
                                                    int64_t desired) {
    return atomic_compare_exchange_strong(atm, expected, desired);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 45)
int scalanative_atomic_compare_and_swap_weak_long(_Atomic(int64_t) * atm,
                                                  int64_t *expected,
                                                  int64_t desired) {
    return atomic_compare_exchange_weak(atm, expected, desired);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 45)
int scalanative_atomic_compare_and_swap_strong_ubyte(_Atomic(uint8_t) * atm,
                                                     uint8_t *expected,
                                                     uint8_t desired) {
    return atomic_compare_exchange_strong(atm, expected, desired);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 45)
int scalanative_atomic_compare_and_swap_weak_ubyte(_Atomic(uint8_t) * atm,
                                                   uint8_t *expected,
                                                   uint8_t desired) {
    return atomic_compare_exchange_weak(atm, expected, desired);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 45)
int scalanative_atomic_compare_and_swap_strong_ushort(_Atomic(uint16_t) * atm,
                                                      uint16_t *expected,
                                                      uint16_t desired) {
    return atomic_compare_exchange_strong(atm, expected, desired);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 45)
int scalanative_atomic_compare_and_swap_weak_ushort(_Atomic(uint16_t) * atm,
                                                    uint16_t *expected,
                                                    uint16_t desired) {
    return atomic_compare_exchange_weak(atm, expected, desired);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 45)
int scalanative_atomic_compare_and_swap_strong_uint(_Atomic(uint32_t) * atm,
                                                    uint32_t *expected,
                                                    uint32_t desired) {
    return atomic_compare_exchange_strong(atm, expected, desired);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 45)
int scalanative_atomic_compare_and_swap_weak_uint(_Atomic(uint32_t) * atm,
                                                  uint32_t *expected,
                                                  uint32_t desired) {
    return atomic_compare_exchange_weak(atm, expected, desired);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 45)
int scalanative_atomic_compare_and_swap_strong_ulong(_Atomic(uint64_t) * atm,
                                                     uint64_t *expected,
                                                     uint64_t desired) {
    return atomic_compare_exchange_strong(atm, expected, desired);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 45)
int scalanative_atomic_compare_and_swap_weak_ulong(_Atomic(uint64_t) * atm,
                                                   uint64_t *expected,
                                                   uint64_t desired) {
    return atomic_compare_exchange_weak(atm, expected, desired);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 45)
int scalanative_atomic_compare_and_swap_strong_char(_Atomic(char) * atm,
                                                    char *expected,
                                                    char desired) {
    return atomic_compare_exchange_strong(atm, expected, desired);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 45)
int scalanative_atomic_compare_and_swap_weak_char(_Atomic(char) * atm,
                                                  char *expected,
                                                  char desired) {
    return atomic_compare_exchange_weak(atm, expected, desired);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 45)
int scalanative_atomic_compare_and_swap_strong_uchar(_Atomic(unsigned char) *
                                                         atm,
                                                     unsigned char *expected,
                                                     unsigned char desired) {
    return atomic_compare_exchange_strong(atm, expected, desired);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 45)
int scalanative_atomic_compare_and_swap_weak_uchar(_Atomic(unsigned char) * atm,
                                                   unsigned char *expected,
                                                   unsigned char desired) {
    return atomic_compare_exchange_weak(atm, expected, desired);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 45)
int scalanative_atomic_compare_and_swap_strong_csize(_Atomic(size_t) * atm,
                                                     size_t *expected,
                                                     size_t desired) {
    return atomic_compare_exchange_strong(atm, expected, desired);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 45)
int scalanative_atomic_compare_and_swap_weak_csize(_Atomic(size_t) * atm,
                                                   size_t *expected,
                                                   size_t desired) {
    return atomic_compare_exchange_weak(atm, expected, desired);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 50)

/**
 * Arithmetics
 * */
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
int8_t scalanative_atomic_add_byte(_Atomic(int8_t) * atm, int8_t val) {
    return atomic_fetch_add(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
int16_t scalanative_atomic_add_short(_Atomic(int16_t) * atm, int16_t val) {
    return atomic_fetch_add(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
int32_t scalanative_atomic_add_int(_Atomic(int32_t) * atm, int32_t val) {
    return atomic_fetch_add(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
int64_t scalanative_atomic_add_long(_Atomic(int64_t) * atm, int64_t val) {
    return atomic_fetch_add(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
uint8_t scalanative_atomic_add_ubyte(_Atomic(uint8_t) * atm, uint8_t val) {
    return atomic_fetch_add(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
uint16_t scalanative_atomic_add_ushort(_Atomic(uint16_t) * atm, uint16_t val) {
    return atomic_fetch_add(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
uint32_t scalanative_atomic_add_uint(_Atomic(uint32_t) * atm, uint32_t val) {
    return atomic_fetch_add(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
uint64_t scalanative_atomic_add_ulong(_Atomic(uint64_t) * atm, uint64_t val) {
    return atomic_fetch_add(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
char scalanative_atomic_add_char(_Atomic(char) * atm, char val) {
    return atomic_fetch_add(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
unsigned char scalanative_atomic_add_uchar(_Atomic(unsigned char) * atm,
                                           unsigned char val) {
    return atomic_fetch_add(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
size_t scalanative_atomic_add_csize(_Atomic(size_t) * atm, size_t val) {
    return atomic_fetch_add(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
int8_t scalanative_atomic_sub_byte(_Atomic(int8_t) * atm, int8_t val) {
    return atomic_fetch_sub(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
int16_t scalanative_atomic_sub_short(_Atomic(int16_t) * atm, int16_t val) {
    return atomic_fetch_sub(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
int32_t scalanative_atomic_sub_int(_Atomic(int32_t) * atm, int32_t val) {
    return atomic_fetch_sub(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
int64_t scalanative_atomic_sub_long(_Atomic(int64_t) * atm, int64_t val) {
    return atomic_fetch_sub(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
uint8_t scalanative_atomic_sub_ubyte(_Atomic(uint8_t) * atm, uint8_t val) {
    return atomic_fetch_sub(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
uint16_t scalanative_atomic_sub_ushort(_Atomic(uint16_t) * atm, uint16_t val) {
    return atomic_fetch_sub(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
uint32_t scalanative_atomic_sub_uint(_Atomic(uint32_t) * atm, uint32_t val) {
    return atomic_fetch_sub(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
uint64_t scalanative_atomic_sub_ulong(_Atomic(uint64_t) * atm, uint64_t val) {
    return atomic_fetch_sub(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
char scalanative_atomic_sub_char(_Atomic(char) * atm, char val) {
    return atomic_fetch_sub(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
unsigned char scalanative_atomic_sub_uchar(_Atomic(unsigned char) * atm,
                                           unsigned char val) {
    return atomic_fetch_sub(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
size_t scalanative_atomic_sub_csize(_Atomic(size_t) * atm, size_t val) {
    return atomic_fetch_sub(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
int8_t scalanative_atomic_and_byte(_Atomic(int8_t) * atm, int8_t val) {
    return atomic_fetch_and(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
int16_t scalanative_atomic_and_short(_Atomic(int16_t) * atm, int16_t val) {
    return atomic_fetch_and(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
int32_t scalanative_atomic_and_int(_Atomic(int32_t) * atm, int32_t val) {
    return atomic_fetch_and(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
int64_t scalanative_atomic_and_long(_Atomic(int64_t) * atm, int64_t val) {
    return atomic_fetch_and(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
uint8_t scalanative_atomic_and_ubyte(_Atomic(uint8_t) * atm, uint8_t val) {
    return atomic_fetch_and(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
uint16_t scalanative_atomic_and_ushort(_Atomic(uint16_t) * atm, uint16_t val) {
    return atomic_fetch_and(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
uint32_t scalanative_atomic_and_uint(_Atomic(uint32_t) * atm, uint32_t val) {
    return atomic_fetch_and(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
uint64_t scalanative_atomic_and_ulong(_Atomic(uint64_t) * atm, uint64_t val) {
    return atomic_fetch_and(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
char scalanative_atomic_and_char(_Atomic(char) * atm, char val) {
    return atomic_fetch_and(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
unsigned char scalanative_atomic_and_uchar(_Atomic(unsigned char) * atm,
                                           unsigned char val) {
    return atomic_fetch_and(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
size_t scalanative_atomic_and_csize(_Atomic(size_t) * atm, size_t val) {
    return atomic_fetch_and(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
int8_t scalanative_atomic_or_byte(_Atomic(int8_t) * atm, int8_t val) {
    return atomic_fetch_or(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
int16_t scalanative_atomic_or_short(_Atomic(int16_t) * atm, int16_t val) {
    return atomic_fetch_or(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
int32_t scalanative_atomic_or_int(_Atomic(int32_t) * atm, int32_t val) {
    return atomic_fetch_or(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
int64_t scalanative_atomic_or_long(_Atomic(int64_t) * atm, int64_t val) {
    return atomic_fetch_or(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
uint8_t scalanative_atomic_or_ubyte(_Atomic(uint8_t) * atm, uint8_t val) {
    return atomic_fetch_or(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
uint16_t scalanative_atomic_or_ushort(_Atomic(uint16_t) * atm, uint16_t val) {
    return atomic_fetch_or(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
uint32_t scalanative_atomic_or_uint(_Atomic(uint32_t) * atm, uint32_t val) {
    return atomic_fetch_or(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
uint64_t scalanative_atomic_or_ulong(_Atomic(uint64_t) * atm, uint64_t val) {
    return atomic_fetch_or(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
char scalanative_atomic_or_char(_Atomic(char) * atm, char val) {
    return atomic_fetch_or(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
unsigned char scalanative_atomic_or_uchar(_Atomic(unsigned char) * atm,
                                          unsigned char val) {
    return atomic_fetch_or(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
size_t scalanative_atomic_or_csize(_Atomic(size_t) * atm, size_t val) {
    return atomic_fetch_or(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
int8_t scalanative_atomic_xor_byte(_Atomic(int8_t) * atm, int8_t val) {
    return atomic_fetch_xor(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
int16_t scalanative_atomic_xor_short(_Atomic(int16_t) * atm, int16_t val) {
    return atomic_fetch_xor(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
int32_t scalanative_atomic_xor_int(_Atomic(int32_t) * atm, int32_t val) {
    return atomic_fetch_xor(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
int64_t scalanative_atomic_xor_long(_Atomic(int64_t) * atm, int64_t val) {
    return atomic_fetch_xor(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
uint8_t scalanative_atomic_xor_ubyte(_Atomic(uint8_t) * atm, uint8_t val) {
    return atomic_fetch_xor(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
uint16_t scalanative_atomic_xor_ushort(_Atomic(uint16_t) * atm, uint16_t val) {
    return atomic_fetch_xor(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
uint32_t scalanative_atomic_xor_uint(_Atomic(uint32_t) * atm, uint32_t val) {
    return atomic_fetch_xor(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
uint64_t scalanative_atomic_xor_ulong(_Atomic(uint64_t) * atm, uint64_t val) {
    return atomic_fetch_xor(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
char scalanative_atomic_xor_char(_Atomic(char) * atm, char val) {
    return atomic_fetch_xor(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
unsigned char scalanative_atomic_xor_uchar(_Atomic(unsigned char) * atm,
                                           unsigned char val) {
    return atomic_fetch_xor(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 56)
size_t scalanative_atomic_xor_csize(_Atomic(size_t) * atm, size_t val) {
    return atomic_fetch_xor(atm, val);
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 61)

// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 63)
memory_order scalanative_atomic_memory_order_relaxed() {
    return memory_order_relaxed;
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 63)
memory_order scalanative_atomic_memory_order_consume() {
    return memory_order_consume;
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 63)
memory_order scalanative_atomic_memory_order_acquire() {
    return memory_order_acquire;
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 63)
memory_order scalanative_atomic_memory_order_release() {
    return memory_order_release;
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 63)
memory_order scalanative_atomic_memory_order_acq_rel() {
    return memory_order_acq_rel;
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 63)
memory_order scalanative_atomic_memory_order_seq_cst() {
    return memory_order_seq_cst;
}
// ###sourceLocation(file:
// "/home/wmazur/projects/scalacenter/scala-native/scala-native/nativelib/src/main/resources/scala-native/atomic.c.gyb",
// line: 67)

