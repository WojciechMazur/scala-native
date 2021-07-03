#define WIN32_LEAN_AND_MEAN
#include <Windows.h>
#include <synchapi.h>

size_t scalanative_sizeof_CriticalSection() { return sizeof(CRITICAL_SECTION); }
size_t scalanative_sizeof_ConditionVariable() {
    return sizeof(CONDITION_VARIABLE);
}