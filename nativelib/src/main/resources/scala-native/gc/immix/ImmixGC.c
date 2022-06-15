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
#include "MultithreadingSupport.h"
#endif
#include "MutatorThread.h"
#include <stdatomic.h>

// TODO IT should probalby thread-local
extern word_t **__stack_bottom;

void scalanative_collect();

void scalanative_afterexit() { Stats_OnExit(heap.stats); }

NOINLINE void scalanative_init() {
    Heap_Init(&heap, Settings_MinHeapSize(), Settings_MaxHeapSize());
    Stack_Init(&stack, INITIAL_STACK_SIZE);
    Stack_Init(&weakRefStack, INITIAL_STACK_SIZE);
#ifdef SCALANATIVE_MULTITHREADING_ENABLED
    scalanative_init_yieldpoint();
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
    printf("AllocLarge %zu\n", size);
    void **alloc = (void **)Heap_AllocLarge(&heap, size);
    *alloc = info;
    return (void *)alloc;
}

INLINE void *scalanative_alloc_atomic(void *info, size_t size) {
    return scalanative_alloc(info, size);
}

INLINE void scalanative_collect() {
    // printf("Starting GC\n");
    Heap_Collect(&heap, &stack); 
    // printf("Finished GC\n");
}

INLINE MutatorThreadState scalanative_gc_switch_mutator_thread_state(MutatorThreadState newState){
    return MutatorThread_switchState(currentMutatorThread, newState);
}

INLINE void scalanative_register_weak_reference_handler(void *handler) {
    WeakRefStack_SetHandler(handler);
}
