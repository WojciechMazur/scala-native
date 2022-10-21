#ifndef SCALA_NATIVE_GC_H
#define SCALA_NATIVE_GC_H
#include <stdlib.h>
#include <stdbool.h>
#include "GCTypes.h"

typedef void *(*ThreadStartRoutine)(void *);
typedef void *RoutineArgs;

#if defined(_WIN32) || defined(WIN32)
// Boehm on Windows needs User32.lib linked
#pragma comment(lib, "User32.lib")
#else
#include <pthread.h>
#endif

void scalanative_init();
void *scalanative_alloc(void *info, size_t size);
void *scalanative_alloc_small(void *info, size_t size);
void *scalanative_alloc_large(void *info, size_t size);
void *scalanative_alloc_atomic(void *info, size_t size);
void scalanative_collect();
void scalanative_register_weak_reference_handler(void *handler);

// Functions used to create a new thread supporting multithreading support in
// the garbage collector. Would execute a proxy startup routine to register
// newly created thread upon startup and unregister it from the GC upon
// termination.
#ifdef _WIN32
Handle scalanative_CreateThread(SecurityAttributes *threadAttributes,
                                UWORD stackSize, ThreadStartRoutine routine,
                                RoutineArgs args, DWORD, creationFlags,
                                DWORD *threadId);
#else
int scalanative_pthread_create(pthread_t *thread, pthread_attr_t *attr,
                               ThreadStartRoutine routine, RoutineArgs args);
#endif

// Current type of execution by given threadin foreign scope be included in the
// stop-the-world mechanism, as they're assumed to not modify the state of the
// GC. Upon conversion from Managed to Unmanged state calling thread shall dump
// the contents of the register to the stack and save the top address of the
// stack.
typedef enum scalanative_MutatorThreadState {
    /*  Thread executes Scala Native code using GC following cooperative mode -
     *  it periodically polls for synchronization events.
     */
    MutatorThreadState_Managed = 0,
    /*  Thread executes foreign code (syscalls, C functions) and is not able to
     *  modify the state of the GC. Upon synchronization event garbage collector
     *  would ignore this thread. Upon returning from foreign execution thread
     *  would stop until synchronization event would finish.
     */
    MutatorThreadState_Unmanaged = 1
} MutatorThreadState;
void scalanative_setMutatorThreadState(MutatorThreadState);

#endif // SCALA_NATIVE_GC_H
