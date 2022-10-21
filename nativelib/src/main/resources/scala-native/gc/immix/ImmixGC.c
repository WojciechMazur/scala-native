#include <ScalaNativeGC.h>
#include <stdio.h>
#include <stdlib.h>
#include <memory.h>
#include "GCTypes.h"
#include "Heap.h"
#include "datastructures/Stack.h"
#include "Marker.h"
#include "Log.h"
#include "Object.h"
#include "State.h"
#include "utils/MathUtils.h"
#include "Constants.h"
#include "Settings.h"
#include "WeakRefStack.h"

#ifdef SCALANATIVE_MULTITHREADING_ENABLED
#include "Synchronizer.h"
#endif
#include "MutatorThread.h"
#include <stdatomic.h>

// Stack boottom of the main thread
extern word_t **__stack_bottom;

volatile safepoint_t *scalanative_gc_safepoint;

void scalanative_collect();

void scalanative_afterexit() { Stats_OnExit(heap.stats); }

NOINLINE void scalanative_init() {
    Heap_Init(&heap, Settings_MinHeapSize(), Settings_MaxHeapSize());
    Stack_Init(&stack, INITIAL_STACK_SIZE);
    Stack_Init(&weakRefStack, INITIAL_STACK_SIZE);
#ifdef SCALANATIVE_MULTITHREADING_ENABLED
    Synchronizer_init();
#endif
    MutatorThreads_init();
    MutatorThread_init(__stack_bottom);
    atexit(scalanative_afterexit);
}

INLINE void *scalanative_alloc(void *info, size_t size) {
    size = MathUtils_RoundToNextMultiple(size, ALLOCATION_ALIGNMENT);

    void **alloc = (void **)Heap_Alloc(&heap, size);
    *alloc = info;
    return (void *)alloc;
}

INLINE void *scalanative_alloc_small(void *info, size_t size) {
    size = MathUtils_RoundToNextMultiple(size, ALLOCATION_ALIGNMENT);

    void **alloc = (void **)Heap_AllocSmall(&heap, size);
    *alloc = info;
    return (void *)alloc;
}

INLINE void *scalanative_alloc_large(void *info, size_t size) {
    size = MathUtils_RoundToNextMultiple(size, ALLOCATION_ALIGNMENT);
    void **alloc = (void **)Heap_AllocLarge(&heap, size);
    *alloc = info;
    return (void *)alloc;
}

INLINE void *scalanative_alloc_atomic(void *info, size_t size) {
    return scalanative_alloc(info, size);
}

INLINE void scalanative_collect() { Heap_Collect(&heap, &stack); }

INLINE void scalanative_register_weak_reference_handler(void *handler) {
    WeakRefStack_SetHandler(handler);
}

typedef void *(*ThreadStartRoutine)(void *);
typedef void *RoutineArgs;
typedef struct {
    ThreadStartRoutine fn;
    RoutineArgs args;
} WrappedFunctionCallArgs;

static void ProxyThreadStartRoutine(void *args) {
    WrappedFunctionCallArgs *wrapped = (WrappedFunctionCallArgs *)args;
    ThreadStartRoutine originalFn = wrapped->fn;
    RoutineArgs originalArgs = wrapped->args;
    int stackBottom = 0;

    MutatorThread_init((Field_t *)&stackBottom);
    originalFn(originalArgs);
    MutatorThread_delete(currentMutatorThread);
    free(args);
}

#ifdef _WIN32
Handle scalanative_CreateThread(SecurityAttributes *threadAttributes,
                                UWORD stackSize, ThreadStartRoutine routine,
                                RoutineArgs args, DWORD, creationFlags,
                                DWORD *threadId) {
    WrappedFunctionCallArgs *proxyArgs =
        (WrappedFunctionCallArgs *)malloc(sizeof(WrappedFunctionCallArgs));
    proxyArgs->fn = routine;
    proxyArgs->args = args;
    return CreateThread(threadAttributes, stackSize,
                        (ThreadStartRoutine)&ProxyThreadStartRoutine,
                        (RoutineArgs)proxyArgs, creationFlags, threadId)
}
#else
int scalanative_pthread_create(pthread_t *thread, pthread_attr_t *attr,
                               ThreadStartRoutine routine, RoutineArgs args) {
    WrappedFunctionCallArgs *proxyArgs =
        (WrappedFunctionCallArgs *)malloc(sizeof(WrappedFunctionCallArgs));
    proxyArgs->fn = routine;
    proxyArgs->args = args;
    return pthread_create(thread, attr,
                          (ThreadStartRoutine)&ProxyThreadStartRoutine,
                          (RoutineArgs)proxyArgs);
}
#endif

void scalanative_setMutatorThreadState(MutatorThreadState state) {
    MutatorThread_switchState(currentMutatorThread, state);
}
