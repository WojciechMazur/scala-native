// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 1)
#include <stdatomic.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>


// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 8)
  memory_order scalanative_atomic_memory_order_relaxed(){
    return memory_order_relaxed;
  }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 8)
  memory_order scalanative_atomic_memory_order_consume(){
    return memory_order_consume;
  }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 8)
  memory_order scalanative_atomic_memory_order_acquire(){
    return memory_order_acquire;
  }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 8)
  memory_order scalanative_atomic_memory_order_release(){
    return memory_order_release;
  }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 8)
  memory_order scalanative_atomic_memory_order_acq_rel(){
    return memory_order_acq_rel;
  }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 8)
  memory_order scalanative_atomic_memory_order_seq_cst(){
    return memory_order_seq_cst;
  }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 12)

// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 28)

// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 30)
  void scalanative_atomic_init_byte(int8_t* atm, int8_t init_value) {
    *atm = ATOMIC_VAR_INIT(init_value);
  }

  int8_t scalanative_atomic_load_byte(_Atomic(int8_t)* atm) {
      return atomic_load(atm);
  }

  int8_t scalanative_atomic_load_explicit_byte(_Atomic(int8_t)* atm, memory_order memoryOrder) {
      return atomic_load_explicit(atm, memoryOrder);
  }

  void scalanative_atomic_store_byte(_Atomic(int8_t)* atm, int8_t val) {
    atomic_store(atm, val);
  }

    void scalanative_atomic_store_explicit_byte(_Atomic(int8_t)* atm, int8_t val, memory_order memoryOrder) {
    atomic_store_explicit(atm, val, memoryOrder);
  }

  int8_t scalanative_atomic_exchange_byte(_Atomic(int8_t)* atm, int8_t val) {
    return atomic_exchange(atm, val);
  }

    int8_t scalanative_atomic_exchange_explicit_byte(_Atomic(int8_t)* atm, int8_t val, memory_order memoryOrder) {
    return atomic_exchange_explicit(atm, val, memoryOrder);
  }


// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 60)
    bool scalanative_atomic_compare_exchange_strong_byte(_Atomic(int8_t)* atm, int8_t* expected, int8_t desired) {
        return atomic_compare_exchange_strong(atm, expected, desired);
    }

    bool scalanative_atomic_compare_exchange_strong_explicit_byte(_Atomic(int8_t)* atm, int8_t* expected, int8_t desired, memory_order onSucc, memory_order onFail) {
      return atomic_compare_exchange_strong_explicit(atm, expected, desired, onSucc, onFail);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 60)
    bool scalanative_atomic_compare_exchange_weak_byte(_Atomic(int8_t)* atm, int8_t* expected, int8_t desired) {
        return atomic_compare_exchange_weak(atm, expected, desired);
    }

    bool scalanative_atomic_compare_exchange_weak_explicit_byte(_Atomic(int8_t)* atm, int8_t* expected, int8_t desired, memory_order onSucc, memory_order onFail) {
      return atomic_compare_exchange_weak_explicit(atm, expected, desired, onSucc, onFail);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 68)

// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  int8_t scalanative_atomic_fetch_add_byte(_Atomic(int8_t)* atm, int8_t val) {
    return atomic_fetch_add(atm, val);
  }
  int8_t scalanative_atomic_fetch_add_explicit_byte(_Atomic(int8_t)* atm, int8_t val, memory_order memoryOrder) {
      return atomic_fetch_add_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  int8_t scalanative_atomic_fetch_sub_byte(_Atomic(int8_t)* atm, int8_t val) {
    return atomic_fetch_sub(atm, val);
  }
  int8_t scalanative_atomic_fetch_sub_explicit_byte(_Atomic(int8_t)* atm, int8_t val, memory_order memoryOrder) {
      return atomic_fetch_sub_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  int8_t scalanative_atomic_fetch_and_byte(_Atomic(int8_t)* atm, int8_t val) {
    return atomic_fetch_and(atm, val);
  }
  int8_t scalanative_atomic_fetch_and_explicit_byte(_Atomic(int8_t)* atm, int8_t val, memory_order memoryOrder) {
      return atomic_fetch_and_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  int8_t scalanative_atomic_fetch_or_byte(_Atomic(int8_t)* atm, int8_t val) {
    return atomic_fetch_or(atm, val);
  }
  int8_t scalanative_atomic_fetch_or_explicit_byte(_Atomic(int8_t)* atm, int8_t val, memory_order memoryOrder) {
      return atomic_fetch_or_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  int8_t scalanative_atomic_fetch_xor_byte(_Atomic(int8_t)* atm, int8_t val) {
    return atomic_fetch_xor(atm, val);
  }
  int8_t scalanative_atomic_fetch_xor_explicit_byte(_Atomic(int8_t)* atm, int8_t val, memory_order memoryOrder) {
      return atomic_fetch_xor_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 30)
  void scalanative_atomic_init_short(int16_t* atm, int16_t init_value) {
    *atm = ATOMIC_VAR_INIT(init_value);
  }

  int16_t scalanative_atomic_load_short(_Atomic(int16_t)* atm) {
      return atomic_load(atm);
  }

  int16_t scalanative_atomic_load_explicit_short(_Atomic(int16_t)* atm, memory_order memoryOrder) {
      return atomic_load_explicit(atm, memoryOrder);
  }

  void scalanative_atomic_store_short(_Atomic(int16_t)* atm, int16_t val) {
    atomic_store(atm, val);
  }

    void scalanative_atomic_store_explicit_short(_Atomic(int16_t)* atm, int16_t val, memory_order memoryOrder) {
    atomic_store_explicit(atm, val, memoryOrder);
  }

  int16_t scalanative_atomic_exchange_short(_Atomic(int16_t)* atm, int16_t val) {
    return atomic_exchange(atm, val);
  }

    int16_t scalanative_atomic_exchange_explicit_short(_Atomic(int16_t)* atm, int16_t val, memory_order memoryOrder) {
    return atomic_exchange_explicit(atm, val, memoryOrder);
  }


// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 60)
    bool scalanative_atomic_compare_exchange_strong_short(_Atomic(int16_t)* atm, int16_t* expected, int16_t desired) {
        return atomic_compare_exchange_strong(atm, expected, desired);
    }

    bool scalanative_atomic_compare_exchange_strong_explicit_short(_Atomic(int16_t)* atm, int16_t* expected, int16_t desired, memory_order onSucc, memory_order onFail) {
      return atomic_compare_exchange_strong_explicit(atm, expected, desired, onSucc, onFail);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 60)
    bool scalanative_atomic_compare_exchange_weak_short(_Atomic(int16_t)* atm, int16_t* expected, int16_t desired) {
        return atomic_compare_exchange_weak(atm, expected, desired);
    }

    bool scalanative_atomic_compare_exchange_weak_explicit_short(_Atomic(int16_t)* atm, int16_t* expected, int16_t desired, memory_order onSucc, memory_order onFail) {
      return atomic_compare_exchange_weak_explicit(atm, expected, desired, onSucc, onFail);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 68)

// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  int16_t scalanative_atomic_fetch_add_short(_Atomic(int16_t)* atm, int16_t val) {
    return atomic_fetch_add(atm, val);
  }
  int16_t scalanative_atomic_fetch_add_explicit_short(_Atomic(int16_t)* atm, int16_t val, memory_order memoryOrder) {
      return atomic_fetch_add_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  int16_t scalanative_atomic_fetch_sub_short(_Atomic(int16_t)* atm, int16_t val) {
    return atomic_fetch_sub(atm, val);
  }
  int16_t scalanative_atomic_fetch_sub_explicit_short(_Atomic(int16_t)* atm, int16_t val, memory_order memoryOrder) {
      return atomic_fetch_sub_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  int16_t scalanative_atomic_fetch_and_short(_Atomic(int16_t)* atm, int16_t val) {
    return atomic_fetch_and(atm, val);
  }
  int16_t scalanative_atomic_fetch_and_explicit_short(_Atomic(int16_t)* atm, int16_t val, memory_order memoryOrder) {
      return atomic_fetch_and_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  int16_t scalanative_atomic_fetch_or_short(_Atomic(int16_t)* atm, int16_t val) {
    return atomic_fetch_or(atm, val);
  }
  int16_t scalanative_atomic_fetch_or_explicit_short(_Atomic(int16_t)* atm, int16_t val, memory_order memoryOrder) {
      return atomic_fetch_or_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  int16_t scalanative_atomic_fetch_xor_short(_Atomic(int16_t)* atm, int16_t val) {
    return atomic_fetch_xor(atm, val);
  }
  int16_t scalanative_atomic_fetch_xor_explicit_short(_Atomic(int16_t)* atm, int16_t val, memory_order memoryOrder) {
      return atomic_fetch_xor_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 30)
  void scalanative_atomic_init_int(int32_t* atm, int32_t init_value) {
    *atm = ATOMIC_VAR_INIT(init_value);
  }

  int32_t scalanative_atomic_load_int(_Atomic(int32_t)* atm) {
      return atomic_load(atm);
  }

  int32_t scalanative_atomic_load_explicit_int(_Atomic(int32_t)* atm, memory_order memoryOrder) {
      return atomic_load_explicit(atm, memoryOrder);
  }

  void scalanative_atomic_store_int(_Atomic(int32_t)* atm, int32_t val) {
    atomic_store(atm, val);
  }

    void scalanative_atomic_store_explicit_int(_Atomic(int32_t)* atm, int32_t val, memory_order memoryOrder) {
    atomic_store_explicit(atm, val, memoryOrder);
  }

  int32_t scalanative_atomic_exchange_int(_Atomic(int32_t)* atm, int32_t val) {
    return atomic_exchange(atm, val);
  }

    int32_t scalanative_atomic_exchange_explicit_int(_Atomic(int32_t)* atm, int32_t val, memory_order memoryOrder) {
    return atomic_exchange_explicit(atm, val, memoryOrder);
  }


// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 60)
    bool scalanative_atomic_compare_exchange_strong_int(_Atomic(int32_t)* atm, int32_t* expected, int32_t desired) {
        return atomic_compare_exchange_strong(atm, expected, desired);
    }

    bool scalanative_atomic_compare_exchange_strong_explicit_int(_Atomic(int32_t)* atm, int32_t* expected, int32_t desired, memory_order onSucc, memory_order onFail) {
      return atomic_compare_exchange_strong_explicit(atm, expected, desired, onSucc, onFail);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 60)
    bool scalanative_atomic_compare_exchange_weak_int(_Atomic(int32_t)* atm, int32_t* expected, int32_t desired) {
        return atomic_compare_exchange_weak(atm, expected, desired);
    }

    bool scalanative_atomic_compare_exchange_weak_explicit_int(_Atomic(int32_t)* atm, int32_t* expected, int32_t desired, memory_order onSucc, memory_order onFail) {
      return atomic_compare_exchange_weak_explicit(atm, expected, desired, onSucc, onFail);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 68)

// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  int32_t scalanative_atomic_fetch_add_int(_Atomic(int32_t)* atm, int32_t val) {
    return atomic_fetch_add(atm, val);
  }
  int32_t scalanative_atomic_fetch_add_explicit_int(_Atomic(int32_t)* atm, int32_t val, memory_order memoryOrder) {
      return atomic_fetch_add_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  int32_t scalanative_atomic_fetch_sub_int(_Atomic(int32_t)* atm, int32_t val) {
    return atomic_fetch_sub(atm, val);
  }
  int32_t scalanative_atomic_fetch_sub_explicit_int(_Atomic(int32_t)* atm, int32_t val, memory_order memoryOrder) {
      return atomic_fetch_sub_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  int32_t scalanative_atomic_fetch_and_int(_Atomic(int32_t)* atm, int32_t val) {
    return atomic_fetch_and(atm, val);
  }
  int32_t scalanative_atomic_fetch_and_explicit_int(_Atomic(int32_t)* atm, int32_t val, memory_order memoryOrder) {
      return atomic_fetch_and_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  int32_t scalanative_atomic_fetch_or_int(_Atomic(int32_t)* atm, int32_t val) {
    return atomic_fetch_or(atm, val);
  }
  int32_t scalanative_atomic_fetch_or_explicit_int(_Atomic(int32_t)* atm, int32_t val, memory_order memoryOrder) {
      return atomic_fetch_or_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  int32_t scalanative_atomic_fetch_xor_int(_Atomic(int32_t)* atm, int32_t val) {
    return atomic_fetch_xor(atm, val);
  }
  int32_t scalanative_atomic_fetch_xor_explicit_int(_Atomic(int32_t)* atm, int32_t val, memory_order memoryOrder) {
      return atomic_fetch_xor_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 30)
  void scalanative_atomic_init_long(int64_t* atm, int64_t init_value) {
    *atm = ATOMIC_VAR_INIT(init_value);
  }

  int64_t scalanative_atomic_load_long(_Atomic(int64_t)* atm) {
      return atomic_load(atm);
  }

  int64_t scalanative_atomic_load_explicit_long(_Atomic(int64_t)* atm, memory_order memoryOrder) {
      return atomic_load_explicit(atm, memoryOrder);
  }

  void scalanative_atomic_store_long(_Atomic(int64_t)* atm, int64_t val) {
    atomic_store(atm, val);
  }

    void scalanative_atomic_store_explicit_long(_Atomic(int64_t)* atm, int64_t val, memory_order memoryOrder) {
    atomic_store_explicit(atm, val, memoryOrder);
  }

  int64_t scalanative_atomic_exchange_long(_Atomic(int64_t)* atm, int64_t val) {
    return atomic_exchange(atm, val);
  }

    int64_t scalanative_atomic_exchange_explicit_long(_Atomic(int64_t)* atm, int64_t val, memory_order memoryOrder) {
    return atomic_exchange_explicit(atm, val, memoryOrder);
  }


// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 60)
    bool scalanative_atomic_compare_exchange_strong_long(_Atomic(int64_t)* atm, int64_t* expected, int64_t desired) {
        return atomic_compare_exchange_strong(atm, expected, desired);
    }

    bool scalanative_atomic_compare_exchange_strong_explicit_long(_Atomic(int64_t)* atm, int64_t* expected, int64_t desired, memory_order onSucc, memory_order onFail) {
      return atomic_compare_exchange_strong_explicit(atm, expected, desired, onSucc, onFail);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 60)
    bool scalanative_atomic_compare_exchange_weak_long(_Atomic(int64_t)* atm, int64_t* expected, int64_t desired) {
        return atomic_compare_exchange_weak(atm, expected, desired);
    }

    bool scalanative_atomic_compare_exchange_weak_explicit_long(_Atomic(int64_t)* atm, int64_t* expected, int64_t desired, memory_order onSucc, memory_order onFail) {
      return atomic_compare_exchange_weak_explicit(atm, expected, desired, onSucc, onFail);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 68)

// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  int64_t scalanative_atomic_fetch_add_long(_Atomic(int64_t)* atm, int64_t val) {
    return atomic_fetch_add(atm, val);
  }
  int64_t scalanative_atomic_fetch_add_explicit_long(_Atomic(int64_t)* atm, int64_t val, memory_order memoryOrder) {
      return atomic_fetch_add_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  int64_t scalanative_atomic_fetch_sub_long(_Atomic(int64_t)* atm, int64_t val) {
    return atomic_fetch_sub(atm, val);
  }
  int64_t scalanative_atomic_fetch_sub_explicit_long(_Atomic(int64_t)* atm, int64_t val, memory_order memoryOrder) {
      return atomic_fetch_sub_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  int64_t scalanative_atomic_fetch_and_long(_Atomic(int64_t)* atm, int64_t val) {
    return atomic_fetch_and(atm, val);
  }
  int64_t scalanative_atomic_fetch_and_explicit_long(_Atomic(int64_t)* atm, int64_t val, memory_order memoryOrder) {
      return atomic_fetch_and_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  int64_t scalanative_atomic_fetch_or_long(_Atomic(int64_t)* atm, int64_t val) {
    return atomic_fetch_or(atm, val);
  }
  int64_t scalanative_atomic_fetch_or_explicit_long(_Atomic(int64_t)* atm, int64_t val, memory_order memoryOrder) {
      return atomic_fetch_or_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  int64_t scalanative_atomic_fetch_xor_long(_Atomic(int64_t)* atm, int64_t val) {
    return atomic_fetch_xor(atm, val);
  }
  int64_t scalanative_atomic_fetch_xor_explicit_long(_Atomic(int64_t)* atm, int64_t val, memory_order memoryOrder) {
      return atomic_fetch_xor_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 30)
  void scalanative_atomic_init_ubyte(uint8_t* atm, uint8_t init_value) {
    *atm = ATOMIC_VAR_INIT(init_value);
  }

  uint8_t scalanative_atomic_load_ubyte(_Atomic(uint8_t)* atm) {
      return atomic_load(atm);
  }

  uint8_t scalanative_atomic_load_explicit_ubyte(_Atomic(uint8_t)* atm, memory_order memoryOrder) {
      return atomic_load_explicit(atm, memoryOrder);
  }

  void scalanative_atomic_store_ubyte(_Atomic(uint8_t)* atm, uint8_t val) {
    atomic_store(atm, val);
  }

    void scalanative_atomic_store_explicit_ubyte(_Atomic(uint8_t)* atm, uint8_t val, memory_order memoryOrder) {
    atomic_store_explicit(atm, val, memoryOrder);
  }

  uint8_t scalanative_atomic_exchange_ubyte(_Atomic(uint8_t)* atm, uint8_t val) {
    return atomic_exchange(atm, val);
  }

    uint8_t scalanative_atomic_exchange_explicit_ubyte(_Atomic(uint8_t)* atm, uint8_t val, memory_order memoryOrder) {
    return atomic_exchange_explicit(atm, val, memoryOrder);
  }


// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 60)
    bool scalanative_atomic_compare_exchange_strong_ubyte(_Atomic(uint8_t)* atm, uint8_t* expected, uint8_t desired) {
        return atomic_compare_exchange_strong(atm, expected, desired);
    }

    bool scalanative_atomic_compare_exchange_strong_explicit_ubyte(_Atomic(uint8_t)* atm, uint8_t* expected, uint8_t desired, memory_order onSucc, memory_order onFail) {
      return atomic_compare_exchange_strong_explicit(atm, expected, desired, onSucc, onFail);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 60)
    bool scalanative_atomic_compare_exchange_weak_ubyte(_Atomic(uint8_t)* atm, uint8_t* expected, uint8_t desired) {
        return atomic_compare_exchange_weak(atm, expected, desired);
    }

    bool scalanative_atomic_compare_exchange_weak_explicit_ubyte(_Atomic(uint8_t)* atm, uint8_t* expected, uint8_t desired, memory_order onSucc, memory_order onFail) {
      return atomic_compare_exchange_weak_explicit(atm, expected, desired, onSucc, onFail);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 68)

// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  uint8_t scalanative_atomic_fetch_add_ubyte(_Atomic(uint8_t)* atm, uint8_t val) {
    return atomic_fetch_add(atm, val);
  }
  uint8_t scalanative_atomic_fetch_add_explicit_ubyte(_Atomic(uint8_t)* atm, uint8_t val, memory_order memoryOrder) {
      return atomic_fetch_add_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  uint8_t scalanative_atomic_fetch_sub_ubyte(_Atomic(uint8_t)* atm, uint8_t val) {
    return atomic_fetch_sub(atm, val);
  }
  uint8_t scalanative_atomic_fetch_sub_explicit_ubyte(_Atomic(uint8_t)* atm, uint8_t val, memory_order memoryOrder) {
      return atomic_fetch_sub_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  uint8_t scalanative_atomic_fetch_and_ubyte(_Atomic(uint8_t)* atm, uint8_t val) {
    return atomic_fetch_and(atm, val);
  }
  uint8_t scalanative_atomic_fetch_and_explicit_ubyte(_Atomic(uint8_t)* atm, uint8_t val, memory_order memoryOrder) {
      return atomic_fetch_and_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  uint8_t scalanative_atomic_fetch_or_ubyte(_Atomic(uint8_t)* atm, uint8_t val) {
    return atomic_fetch_or(atm, val);
  }
  uint8_t scalanative_atomic_fetch_or_explicit_ubyte(_Atomic(uint8_t)* atm, uint8_t val, memory_order memoryOrder) {
      return atomic_fetch_or_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  uint8_t scalanative_atomic_fetch_xor_ubyte(_Atomic(uint8_t)* atm, uint8_t val) {
    return atomic_fetch_xor(atm, val);
  }
  uint8_t scalanative_atomic_fetch_xor_explicit_ubyte(_Atomic(uint8_t)* atm, uint8_t val, memory_order memoryOrder) {
      return atomic_fetch_xor_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 30)
  void scalanative_atomic_init_ushort(uint16_t* atm, uint16_t init_value) {
    *atm = ATOMIC_VAR_INIT(init_value);
  }

  uint16_t scalanative_atomic_load_ushort(_Atomic(uint16_t)* atm) {
      return atomic_load(atm);
  }

  uint16_t scalanative_atomic_load_explicit_ushort(_Atomic(uint16_t)* atm, memory_order memoryOrder) {
      return atomic_load_explicit(atm, memoryOrder);
  }

  void scalanative_atomic_store_ushort(_Atomic(uint16_t)* atm, uint16_t val) {
    atomic_store(atm, val);
  }

    void scalanative_atomic_store_explicit_ushort(_Atomic(uint16_t)* atm, uint16_t val, memory_order memoryOrder) {
    atomic_store_explicit(atm, val, memoryOrder);
  }

  uint16_t scalanative_atomic_exchange_ushort(_Atomic(uint16_t)* atm, uint16_t val) {
    return atomic_exchange(atm, val);
  }

    uint16_t scalanative_atomic_exchange_explicit_ushort(_Atomic(uint16_t)* atm, uint16_t val, memory_order memoryOrder) {
    return atomic_exchange_explicit(atm, val, memoryOrder);
  }


// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 60)
    bool scalanative_atomic_compare_exchange_strong_ushort(_Atomic(uint16_t)* atm, uint16_t* expected, uint16_t desired) {
        return atomic_compare_exchange_strong(atm, expected, desired);
    }

    bool scalanative_atomic_compare_exchange_strong_explicit_ushort(_Atomic(uint16_t)* atm, uint16_t* expected, uint16_t desired, memory_order onSucc, memory_order onFail) {
      return atomic_compare_exchange_strong_explicit(atm, expected, desired, onSucc, onFail);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 60)
    bool scalanative_atomic_compare_exchange_weak_ushort(_Atomic(uint16_t)* atm, uint16_t* expected, uint16_t desired) {
        return atomic_compare_exchange_weak(atm, expected, desired);
    }

    bool scalanative_atomic_compare_exchange_weak_explicit_ushort(_Atomic(uint16_t)* atm, uint16_t* expected, uint16_t desired, memory_order onSucc, memory_order onFail) {
      return atomic_compare_exchange_weak_explicit(atm, expected, desired, onSucc, onFail);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 68)

// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  uint16_t scalanative_atomic_fetch_add_ushort(_Atomic(uint16_t)* atm, uint16_t val) {
    return atomic_fetch_add(atm, val);
  }
  uint16_t scalanative_atomic_fetch_add_explicit_ushort(_Atomic(uint16_t)* atm, uint16_t val, memory_order memoryOrder) {
      return atomic_fetch_add_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  uint16_t scalanative_atomic_fetch_sub_ushort(_Atomic(uint16_t)* atm, uint16_t val) {
    return atomic_fetch_sub(atm, val);
  }
  uint16_t scalanative_atomic_fetch_sub_explicit_ushort(_Atomic(uint16_t)* atm, uint16_t val, memory_order memoryOrder) {
      return atomic_fetch_sub_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  uint16_t scalanative_atomic_fetch_and_ushort(_Atomic(uint16_t)* atm, uint16_t val) {
    return atomic_fetch_and(atm, val);
  }
  uint16_t scalanative_atomic_fetch_and_explicit_ushort(_Atomic(uint16_t)* atm, uint16_t val, memory_order memoryOrder) {
      return atomic_fetch_and_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  uint16_t scalanative_atomic_fetch_or_ushort(_Atomic(uint16_t)* atm, uint16_t val) {
    return atomic_fetch_or(atm, val);
  }
  uint16_t scalanative_atomic_fetch_or_explicit_ushort(_Atomic(uint16_t)* atm, uint16_t val, memory_order memoryOrder) {
      return atomic_fetch_or_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  uint16_t scalanative_atomic_fetch_xor_ushort(_Atomic(uint16_t)* atm, uint16_t val) {
    return atomic_fetch_xor(atm, val);
  }
  uint16_t scalanative_atomic_fetch_xor_explicit_ushort(_Atomic(uint16_t)* atm, uint16_t val, memory_order memoryOrder) {
      return atomic_fetch_xor_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 30)
  void scalanative_atomic_init_uint(uint32_t* atm, uint32_t init_value) {
    *atm = ATOMIC_VAR_INIT(init_value);
  }

  uint32_t scalanative_atomic_load_uint(_Atomic(uint32_t)* atm) {
      return atomic_load(atm);
  }

  uint32_t scalanative_atomic_load_explicit_uint(_Atomic(uint32_t)* atm, memory_order memoryOrder) {
      return atomic_load_explicit(atm, memoryOrder);
  }

  void scalanative_atomic_store_uint(_Atomic(uint32_t)* atm, uint32_t val) {
    atomic_store(atm, val);
  }

    void scalanative_atomic_store_explicit_uint(_Atomic(uint32_t)* atm, uint32_t val, memory_order memoryOrder) {
    atomic_store_explicit(atm, val, memoryOrder);
  }

  uint32_t scalanative_atomic_exchange_uint(_Atomic(uint32_t)* atm, uint32_t val) {
    return atomic_exchange(atm, val);
  }

    uint32_t scalanative_atomic_exchange_explicit_uint(_Atomic(uint32_t)* atm, uint32_t val, memory_order memoryOrder) {
    return atomic_exchange_explicit(atm, val, memoryOrder);
  }


// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 60)
    bool scalanative_atomic_compare_exchange_strong_uint(_Atomic(uint32_t)* atm, uint32_t* expected, uint32_t desired) {
        return atomic_compare_exchange_strong(atm, expected, desired);
    }

    bool scalanative_atomic_compare_exchange_strong_explicit_uint(_Atomic(uint32_t)* atm, uint32_t* expected, uint32_t desired, memory_order onSucc, memory_order onFail) {
      return atomic_compare_exchange_strong_explicit(atm, expected, desired, onSucc, onFail);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 60)
    bool scalanative_atomic_compare_exchange_weak_uint(_Atomic(uint32_t)* atm, uint32_t* expected, uint32_t desired) {
        return atomic_compare_exchange_weak(atm, expected, desired);
    }

    bool scalanative_atomic_compare_exchange_weak_explicit_uint(_Atomic(uint32_t)* atm, uint32_t* expected, uint32_t desired, memory_order onSucc, memory_order onFail) {
      return atomic_compare_exchange_weak_explicit(atm, expected, desired, onSucc, onFail);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 68)

// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  uint32_t scalanative_atomic_fetch_add_uint(_Atomic(uint32_t)* atm, uint32_t val) {
    return atomic_fetch_add(atm, val);
  }
  uint32_t scalanative_atomic_fetch_add_explicit_uint(_Atomic(uint32_t)* atm, uint32_t val, memory_order memoryOrder) {
      return atomic_fetch_add_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  uint32_t scalanative_atomic_fetch_sub_uint(_Atomic(uint32_t)* atm, uint32_t val) {
    return atomic_fetch_sub(atm, val);
  }
  uint32_t scalanative_atomic_fetch_sub_explicit_uint(_Atomic(uint32_t)* atm, uint32_t val, memory_order memoryOrder) {
      return atomic_fetch_sub_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  uint32_t scalanative_atomic_fetch_and_uint(_Atomic(uint32_t)* atm, uint32_t val) {
    return atomic_fetch_and(atm, val);
  }
  uint32_t scalanative_atomic_fetch_and_explicit_uint(_Atomic(uint32_t)* atm, uint32_t val, memory_order memoryOrder) {
      return atomic_fetch_and_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  uint32_t scalanative_atomic_fetch_or_uint(_Atomic(uint32_t)* atm, uint32_t val) {
    return atomic_fetch_or(atm, val);
  }
  uint32_t scalanative_atomic_fetch_or_explicit_uint(_Atomic(uint32_t)* atm, uint32_t val, memory_order memoryOrder) {
      return atomic_fetch_or_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  uint32_t scalanative_atomic_fetch_xor_uint(_Atomic(uint32_t)* atm, uint32_t val) {
    return atomic_fetch_xor(atm, val);
  }
  uint32_t scalanative_atomic_fetch_xor_explicit_uint(_Atomic(uint32_t)* atm, uint32_t val, memory_order memoryOrder) {
      return atomic_fetch_xor_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 30)
  void scalanative_atomic_init_ulong(uint64_t* atm, uint64_t init_value) {
    *atm = ATOMIC_VAR_INIT(init_value);
  }

  uint64_t scalanative_atomic_load_ulong(_Atomic(uint64_t)* atm) {
      return atomic_load(atm);
  }

  uint64_t scalanative_atomic_load_explicit_ulong(_Atomic(uint64_t)* atm, memory_order memoryOrder) {
      return atomic_load_explicit(atm, memoryOrder);
  }

  void scalanative_atomic_store_ulong(_Atomic(uint64_t)* atm, uint64_t val) {
    atomic_store(atm, val);
  }

    void scalanative_atomic_store_explicit_ulong(_Atomic(uint64_t)* atm, uint64_t val, memory_order memoryOrder) {
    atomic_store_explicit(atm, val, memoryOrder);
  }

  uint64_t scalanative_atomic_exchange_ulong(_Atomic(uint64_t)* atm, uint64_t val) {
    return atomic_exchange(atm, val);
  }

    uint64_t scalanative_atomic_exchange_explicit_ulong(_Atomic(uint64_t)* atm, uint64_t val, memory_order memoryOrder) {
    return atomic_exchange_explicit(atm, val, memoryOrder);
  }


// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 60)
    bool scalanative_atomic_compare_exchange_strong_ulong(_Atomic(uint64_t)* atm, uint64_t* expected, uint64_t desired) {
        return atomic_compare_exchange_strong(atm, expected, desired);
    }

    bool scalanative_atomic_compare_exchange_strong_explicit_ulong(_Atomic(uint64_t)* atm, uint64_t* expected, uint64_t desired, memory_order onSucc, memory_order onFail) {
      return atomic_compare_exchange_strong_explicit(atm, expected, desired, onSucc, onFail);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 60)
    bool scalanative_atomic_compare_exchange_weak_ulong(_Atomic(uint64_t)* atm, uint64_t* expected, uint64_t desired) {
        return atomic_compare_exchange_weak(atm, expected, desired);
    }

    bool scalanative_atomic_compare_exchange_weak_explicit_ulong(_Atomic(uint64_t)* atm, uint64_t* expected, uint64_t desired, memory_order onSucc, memory_order onFail) {
      return atomic_compare_exchange_weak_explicit(atm, expected, desired, onSucc, onFail);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 68)

// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  uint64_t scalanative_atomic_fetch_add_ulong(_Atomic(uint64_t)* atm, uint64_t val) {
    return atomic_fetch_add(atm, val);
  }
  uint64_t scalanative_atomic_fetch_add_explicit_ulong(_Atomic(uint64_t)* atm, uint64_t val, memory_order memoryOrder) {
      return atomic_fetch_add_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  uint64_t scalanative_atomic_fetch_sub_ulong(_Atomic(uint64_t)* atm, uint64_t val) {
    return atomic_fetch_sub(atm, val);
  }
  uint64_t scalanative_atomic_fetch_sub_explicit_ulong(_Atomic(uint64_t)* atm, uint64_t val, memory_order memoryOrder) {
      return atomic_fetch_sub_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  uint64_t scalanative_atomic_fetch_and_ulong(_Atomic(uint64_t)* atm, uint64_t val) {
    return atomic_fetch_and(atm, val);
  }
  uint64_t scalanative_atomic_fetch_and_explicit_ulong(_Atomic(uint64_t)* atm, uint64_t val, memory_order memoryOrder) {
      return atomic_fetch_and_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  uint64_t scalanative_atomic_fetch_or_ulong(_Atomic(uint64_t)* atm, uint64_t val) {
    return atomic_fetch_or(atm, val);
  }
  uint64_t scalanative_atomic_fetch_or_explicit_ulong(_Atomic(uint64_t)* atm, uint64_t val, memory_order memoryOrder) {
      return atomic_fetch_or_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  uint64_t scalanative_atomic_fetch_xor_ulong(_Atomic(uint64_t)* atm, uint64_t val) {
    return atomic_fetch_xor(atm, val);
  }
  uint64_t scalanative_atomic_fetch_xor_explicit_ulong(_Atomic(uint64_t)* atm, uint64_t val, memory_order memoryOrder) {
      return atomic_fetch_xor_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 30)
  void scalanative_atomic_init_char(char* atm, char init_value) {
    *atm = ATOMIC_VAR_INIT(init_value);
  }

  char scalanative_atomic_load_char(_Atomic(char)* atm) {
      return atomic_load(atm);
  }

  char scalanative_atomic_load_explicit_char(_Atomic(char)* atm, memory_order memoryOrder) {
      return atomic_load_explicit(atm, memoryOrder);
  }

  void scalanative_atomic_store_char(_Atomic(char)* atm, char val) {
    atomic_store(atm, val);
  }

    void scalanative_atomic_store_explicit_char(_Atomic(char)* atm, char val, memory_order memoryOrder) {
    atomic_store_explicit(atm, val, memoryOrder);
  }

  char scalanative_atomic_exchange_char(_Atomic(char)* atm, char val) {
    return atomic_exchange(atm, val);
  }

    char scalanative_atomic_exchange_explicit_char(_Atomic(char)* atm, char val, memory_order memoryOrder) {
    return atomic_exchange_explicit(atm, val, memoryOrder);
  }


// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 60)
    bool scalanative_atomic_compare_exchange_strong_char(_Atomic(char)* atm, char* expected, char desired) {
        return atomic_compare_exchange_strong(atm, expected, desired);
    }

    bool scalanative_atomic_compare_exchange_strong_explicit_char(_Atomic(char)* atm, char* expected, char desired, memory_order onSucc, memory_order onFail) {
      return atomic_compare_exchange_strong_explicit(atm, expected, desired, onSucc, onFail);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 60)
    bool scalanative_atomic_compare_exchange_weak_char(_Atomic(char)* atm, char* expected, char desired) {
        return atomic_compare_exchange_weak(atm, expected, desired);
    }

    bool scalanative_atomic_compare_exchange_weak_explicit_char(_Atomic(char)* atm, char* expected, char desired, memory_order onSucc, memory_order onFail) {
      return atomic_compare_exchange_weak_explicit(atm, expected, desired, onSucc, onFail);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 68)

// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  char scalanative_atomic_fetch_add_char(_Atomic(char)* atm, char val) {
    return atomic_fetch_add(atm, val);
  }
  char scalanative_atomic_fetch_add_explicit_char(_Atomic(char)* atm, char val, memory_order memoryOrder) {
      return atomic_fetch_add_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  char scalanative_atomic_fetch_sub_char(_Atomic(char)* atm, char val) {
    return atomic_fetch_sub(atm, val);
  }
  char scalanative_atomic_fetch_sub_explicit_char(_Atomic(char)* atm, char val, memory_order memoryOrder) {
      return atomic_fetch_sub_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  char scalanative_atomic_fetch_and_char(_Atomic(char)* atm, char val) {
    return atomic_fetch_and(atm, val);
  }
  char scalanative_atomic_fetch_and_explicit_char(_Atomic(char)* atm, char val, memory_order memoryOrder) {
      return atomic_fetch_and_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  char scalanative_atomic_fetch_or_char(_Atomic(char)* atm, char val) {
    return atomic_fetch_or(atm, val);
  }
  char scalanative_atomic_fetch_or_explicit_char(_Atomic(char)* atm, char val, memory_order memoryOrder) {
      return atomic_fetch_or_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  char scalanative_atomic_fetch_xor_char(_Atomic(char)* atm, char val) {
    return atomic_fetch_xor(atm, val);
  }
  char scalanative_atomic_fetch_xor_explicit_char(_Atomic(char)* atm, char val, memory_order memoryOrder) {
      return atomic_fetch_xor_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 30)
  void scalanative_atomic_init_uchar(unsigned char* atm, unsigned char init_value) {
    *atm = ATOMIC_VAR_INIT(init_value);
  }

  unsigned char scalanative_atomic_load_uchar(_Atomic(unsigned char)* atm) {
      return atomic_load(atm);
  }

  unsigned char scalanative_atomic_load_explicit_uchar(_Atomic(unsigned char)* atm, memory_order memoryOrder) {
      return atomic_load_explicit(atm, memoryOrder);
  }

  void scalanative_atomic_store_uchar(_Atomic(unsigned char)* atm, unsigned char val) {
    atomic_store(atm, val);
  }

    void scalanative_atomic_store_explicit_uchar(_Atomic(unsigned char)* atm, unsigned char val, memory_order memoryOrder) {
    atomic_store_explicit(atm, val, memoryOrder);
  }

  unsigned char scalanative_atomic_exchange_uchar(_Atomic(unsigned char)* atm, unsigned char val) {
    return atomic_exchange(atm, val);
  }

    unsigned char scalanative_atomic_exchange_explicit_uchar(_Atomic(unsigned char)* atm, unsigned char val, memory_order memoryOrder) {
    return atomic_exchange_explicit(atm, val, memoryOrder);
  }


// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 60)
    bool scalanative_atomic_compare_exchange_strong_uchar(_Atomic(unsigned char)* atm, unsigned char* expected, unsigned char desired) {
        return atomic_compare_exchange_strong(atm, expected, desired);
    }

    bool scalanative_atomic_compare_exchange_strong_explicit_uchar(_Atomic(unsigned char)* atm, unsigned char* expected, unsigned char desired, memory_order onSucc, memory_order onFail) {
      return atomic_compare_exchange_strong_explicit(atm, expected, desired, onSucc, onFail);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 60)
    bool scalanative_atomic_compare_exchange_weak_uchar(_Atomic(unsigned char)* atm, unsigned char* expected, unsigned char desired) {
        return atomic_compare_exchange_weak(atm, expected, desired);
    }

    bool scalanative_atomic_compare_exchange_weak_explicit_uchar(_Atomic(unsigned char)* atm, unsigned char* expected, unsigned char desired, memory_order onSucc, memory_order onFail) {
      return atomic_compare_exchange_weak_explicit(atm, expected, desired, onSucc, onFail);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 68)

// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  unsigned char scalanative_atomic_fetch_add_uchar(_Atomic(unsigned char)* atm, unsigned char val) {
    return atomic_fetch_add(atm, val);
  }
  unsigned char scalanative_atomic_fetch_add_explicit_uchar(_Atomic(unsigned char)* atm, unsigned char val, memory_order memoryOrder) {
      return atomic_fetch_add_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  unsigned char scalanative_atomic_fetch_sub_uchar(_Atomic(unsigned char)* atm, unsigned char val) {
    return atomic_fetch_sub(atm, val);
  }
  unsigned char scalanative_atomic_fetch_sub_explicit_uchar(_Atomic(unsigned char)* atm, unsigned char val, memory_order memoryOrder) {
      return atomic_fetch_sub_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  unsigned char scalanative_atomic_fetch_and_uchar(_Atomic(unsigned char)* atm, unsigned char val) {
    return atomic_fetch_and(atm, val);
  }
  unsigned char scalanative_atomic_fetch_and_explicit_uchar(_Atomic(unsigned char)* atm, unsigned char val, memory_order memoryOrder) {
      return atomic_fetch_and_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  unsigned char scalanative_atomic_fetch_or_uchar(_Atomic(unsigned char)* atm, unsigned char val) {
    return atomic_fetch_or(atm, val);
  }
  unsigned char scalanative_atomic_fetch_or_explicit_uchar(_Atomic(unsigned char)* atm, unsigned char val, memory_order memoryOrder) {
      return atomic_fetch_or_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  unsigned char scalanative_atomic_fetch_xor_uchar(_Atomic(unsigned char)* atm, unsigned char val) {
    return atomic_fetch_xor(atm, val);
  }
  unsigned char scalanative_atomic_fetch_xor_explicit_uchar(_Atomic(unsigned char)* atm, unsigned char val, memory_order memoryOrder) {
      return atomic_fetch_xor_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 30)
  void scalanative_atomic_init_intptr_t(intptr_t* atm, intptr_t init_value) {
    *atm = ATOMIC_VAR_INIT(init_value);
  }

  intptr_t scalanative_atomic_load_intptr_t(_Atomic(intptr_t)* atm) {
      return atomic_load(atm);
  }

  intptr_t scalanative_atomic_load_explicit_intptr_t(_Atomic(intptr_t)* atm, memory_order memoryOrder) {
      return atomic_load_explicit(atm, memoryOrder);
  }

  void scalanative_atomic_store_intptr_t(_Atomic(intptr_t)* atm, intptr_t val) {
    atomic_store(atm, val);
  }

    void scalanative_atomic_store_explicit_intptr_t(_Atomic(intptr_t)* atm, intptr_t val, memory_order memoryOrder) {
    atomic_store_explicit(atm, val, memoryOrder);
  }

  intptr_t scalanative_atomic_exchange_intptr_t(_Atomic(intptr_t)* atm, intptr_t val) {
    return atomic_exchange(atm, val);
  }

    intptr_t scalanative_atomic_exchange_explicit_intptr_t(_Atomic(intptr_t)* atm, intptr_t val, memory_order memoryOrder) {
    return atomic_exchange_explicit(atm, val, memoryOrder);
  }


// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 60)
    bool scalanative_atomic_compare_exchange_strong_intptr_t(_Atomic(intptr_t)* atm, intptr_t* expected, intptr_t desired) {
        return atomic_compare_exchange_strong(atm, expected, desired);
    }

    bool scalanative_atomic_compare_exchange_strong_explicit_intptr_t(_Atomic(intptr_t)* atm, intptr_t* expected, intptr_t desired, memory_order onSucc, memory_order onFail) {
      return atomic_compare_exchange_strong_explicit(atm, expected, desired, onSucc, onFail);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 60)
    bool scalanative_atomic_compare_exchange_weak_intptr_t(_Atomic(intptr_t)* atm, intptr_t* expected, intptr_t desired) {
        return atomic_compare_exchange_weak(atm, expected, desired);
    }

    bool scalanative_atomic_compare_exchange_weak_explicit_intptr_t(_Atomic(intptr_t)* atm, intptr_t* expected, intptr_t desired, memory_order onSucc, memory_order onFail) {
      return atomic_compare_exchange_weak_explicit(atm, expected, desired, onSucc, onFail);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 68)

// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  intptr_t scalanative_atomic_fetch_add_intptr_t(_Atomic(intptr_t)* atm, intptr_t val) {
    return atomic_fetch_add(atm, val);
  }
  intptr_t scalanative_atomic_fetch_add_explicit_intptr_t(_Atomic(intptr_t)* atm, intptr_t val, memory_order memoryOrder) {
      return atomic_fetch_add_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  intptr_t scalanative_atomic_fetch_sub_intptr_t(_Atomic(intptr_t)* atm, intptr_t val) {
    return atomic_fetch_sub(atm, val);
  }
  intptr_t scalanative_atomic_fetch_sub_explicit_intptr_t(_Atomic(intptr_t)* atm, intptr_t val, memory_order memoryOrder) {
      return atomic_fetch_sub_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  intptr_t scalanative_atomic_fetch_and_intptr_t(_Atomic(intptr_t)* atm, intptr_t val) {
    return atomic_fetch_and(atm, val);
  }
  intptr_t scalanative_atomic_fetch_and_explicit_intptr_t(_Atomic(intptr_t)* atm, intptr_t val, memory_order memoryOrder) {
      return atomic_fetch_and_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  intptr_t scalanative_atomic_fetch_or_intptr_t(_Atomic(intptr_t)* atm, intptr_t val) {
    return atomic_fetch_or(atm, val);
  }
  intptr_t scalanative_atomic_fetch_or_explicit_intptr_t(_Atomic(intptr_t)* atm, intptr_t val, memory_order memoryOrder) {
      return atomic_fetch_or_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  intptr_t scalanative_atomic_fetch_xor_intptr_t(_Atomic(intptr_t)* atm, intptr_t val) {
    return atomic_fetch_xor(atm, val);
  }
  intptr_t scalanative_atomic_fetch_xor_explicit_intptr_t(_Atomic(intptr_t)* atm, intptr_t val, memory_order memoryOrder) {
      return atomic_fetch_xor_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 30)
  void scalanative_atomic_init_csize(size_t* atm, size_t init_value) {
    *atm = ATOMIC_VAR_INIT(init_value);
  }

  size_t scalanative_atomic_load_csize(_Atomic(size_t)* atm) {
      return atomic_load(atm);
  }

  size_t scalanative_atomic_load_explicit_csize(_Atomic(size_t)* atm, memory_order memoryOrder) {
      return atomic_load_explicit(atm, memoryOrder);
  }

  void scalanative_atomic_store_csize(_Atomic(size_t)* atm, size_t val) {
    atomic_store(atm, val);
  }

    void scalanative_atomic_store_explicit_csize(_Atomic(size_t)* atm, size_t val, memory_order memoryOrder) {
    atomic_store_explicit(atm, val, memoryOrder);
  }

  size_t scalanative_atomic_exchange_csize(_Atomic(size_t)* atm, size_t val) {
    return atomic_exchange(atm, val);
  }

    size_t scalanative_atomic_exchange_explicit_csize(_Atomic(size_t)* atm, size_t val, memory_order memoryOrder) {
    return atomic_exchange_explicit(atm, val, memoryOrder);
  }


// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 60)
    bool scalanative_atomic_compare_exchange_strong_csize(_Atomic(size_t)* atm, size_t* expected, size_t desired) {
        return atomic_compare_exchange_strong(atm, expected, desired);
    }

    bool scalanative_atomic_compare_exchange_strong_explicit_csize(_Atomic(size_t)* atm, size_t* expected, size_t desired, memory_order onSucc, memory_order onFail) {
      return atomic_compare_exchange_strong_explicit(atm, expected, desired, onSucc, onFail);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 60)
    bool scalanative_atomic_compare_exchange_weak_csize(_Atomic(size_t)* atm, size_t* expected, size_t desired) {
        return atomic_compare_exchange_weak(atm, expected, desired);
    }

    bool scalanative_atomic_compare_exchange_weak_explicit_csize(_Atomic(size_t)* atm, size_t* expected, size_t desired, memory_order onSucc, memory_order onFail) {
      return atomic_compare_exchange_weak_explicit(atm, expected, desired, onSucc, onFail);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 68)

// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  size_t scalanative_atomic_fetch_add_csize(_Atomic(size_t)* atm, size_t val) {
    return atomic_fetch_add(atm, val);
  }
  size_t scalanative_atomic_fetch_add_explicit_csize(_Atomic(size_t)* atm, size_t val, memory_order memoryOrder) {
      return atomic_fetch_add_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  size_t scalanative_atomic_fetch_sub_csize(_Atomic(size_t)* atm, size_t val) {
    return atomic_fetch_sub(atm, val);
  }
  size_t scalanative_atomic_fetch_sub_explicit_csize(_Atomic(size_t)* atm, size_t val, memory_order memoryOrder) {
      return atomic_fetch_sub_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  size_t scalanative_atomic_fetch_and_csize(_Atomic(size_t)* atm, size_t val) {
    return atomic_fetch_and(atm, val);
  }
  size_t scalanative_atomic_fetch_and_explicit_csize(_Atomic(size_t)* atm, size_t val, memory_order memoryOrder) {
      return atomic_fetch_and_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  size_t scalanative_atomic_fetch_or_csize(_Atomic(size_t)* atm, size_t val) {
    return atomic_fetch_or(atm, val);
  }
  size_t scalanative_atomic_fetch_or_explicit_csize(_Atomic(size_t)* atm, size_t val, memory_order memoryOrder) {
      return atomic_fetch_or_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 70)
  size_t scalanative_atomic_fetch_xor_csize(_Atomic(size_t)* atm, size_t val) {
    return atomic_fetch_xor(atm, val);
  }
  size_t scalanative_atomic_fetch_xor_explicit_csize(_Atomic(size_t)* atm, size_t val, memory_order memoryOrder) {
      return atomic_fetch_xor_explicit(atm, val, memoryOrder);
    }
// ###sourceLocation(file: "nativelib/src/main/resources/scala-native/atomic.c.gyb", line: 78)


void scalanative_atomic_flag_init(atomic_flag* atm, bool init_value) {
  atomic_flag zero = ATOMIC_FLAG_INIT;
  *atm = zero;
}

bool scalanative_atomic_flag_test_and_set(atomic_flag* obj) {
  return atomic_flag_test_and_set(obj);
}

bool scalanative_atomic_flag_test_and_set_explicit(atomic_flag* obj, memory_order order) {
  return atomic_flag_test_and_set_explicit(obj, order);
}

void scalanative_atomic_flag_test_and_clear(atomic_flag* obj) {
  return atomic_flag_clear(obj);
}

void scalanative_atomic_flag_test_and_clear_explicit(atomic_flag* obj, memory_order order) {
  return atomic_flag_clear_explicit(obj, order);
}