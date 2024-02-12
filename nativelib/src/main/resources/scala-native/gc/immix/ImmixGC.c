#include "immix_commix/headers/ObjectHeader.h"
#include <stdint.h>
#if defined(SCALANATIVE_GC_IMMIX)

#include <stdio.h>
#include <stdlib.h>
#include <memory.h>
#include "shared/GCTypes.h"
#include "Heap.h"
#include "datastructures/Stack.h"
#include "State.h"
#include "immix_commix/utils/MathUtils.h"
#include "Settings.h"
#include "WeakRefStack.h"
#include "shared/Parsing.h"
#ifdef SCALANATIVE_MULTITHREADING_ENABLED
#include "immix_commix/Synchronizer.h"
#include "GCThreads.h"
#endif
#include "MutatorThread.h"
#include <stdatomic.h>

#include "immix_commix/StackTrace.h"
#include <errno.h>
#ifdef _WIN32
#include <errhandlingapi.h>
#else
#include <signal.h>
#include <pthread.h>
#include <sys/mman.h>
#include <sys/time.h>
#include <unistd.h>
#endif

#ifdef _WIN32
static LONG WINAPI SafepointTrapHandler(EXCEPTION_POINTERS *ex) {
    switch (ex->ExceptionRecord->ExceptionCode) {
    case EXCEPTION_ACCESS_VIOLATION:
        ULONG_PTR addr = ex->ExceptionRecord->ExceptionInformation[1];
        fprintf(
            stderr,
            "Caught exception code %p in GC exception handler, address=%p\n",
            (void *)(uintptr_t)ex->ExceptionRecord->ExceptionCode,
            (void *)addr);
        fflush(stdout);
        StackTrace_PrintStackTrace();
    // pass-through
    default:
        return EXCEPTION_CONTINUE_SEARCH;
    }
}
#else
static void SafepointTrapHandler(int signal, siginfo_t *siginfo, void *uap) {
    printf("In trap handler\n");
    fflush(stdout);
    fprintf(stderr,
            "Unexpected signal %d when accessing memory at address %p, "
            "errno=%d, code=%d\n",
            signal, siginfo->si_addr, siginfo->si_errno, siginfo->si_code);
    fflush(stderr);
    StackTrace_PrintStackTrace();
    fflush(stdout);
    exit(signal);
}
#endif

static void SetupYieldPointTrapHandler(int signal) {
#ifdef _WIN32
    // Call it as first exception handler
    AddVectoredExceptionHandler(1, &SafepointTrapHandler);
#else
    struct sigaction sa, osa;
    memset(&sa, 0, sizeof(struct sigaction));
    sigemptyset(&sa.sa_mask);
    sa.sa_sigaction = &SafepointTrapHandler;
    sa.sa_flags = SA_SIGINFO | SA_RESTART;
    if (sigaction(signal, &sa, &osa) == -1) {
        perror("Error: cannot setup safepoint synchronization handler");
        exit(errno);
    }
    printf("setup handler for %d in %d\n", signal, getpid());
#endif
}

void scalanative_GC_collect();

void scalanative_afterexit() { Stats_OnExit(heap.stats); }

void checkStackDirection(word_t* a) {
    word_t b;
    if (&b > a) {
        printf("Stack grows downward.\n");
    } else {
        printf("Stack grows upward.\n");
    }
}

NOINLINE void scalanative_GC_init() {
    printf("Init GC\n");
    fflush(stdout);
    volatile word_t dummy = 0;
    dummy = (word_t)&dummy;
#ifdef _WIN32
    SetupYieldPointTrapHandler(-1);
#else
    SetupYieldPointTrapHandler(SIGBUS);
    SetupYieldPointTrapHandler(SIGSEGV);
    SetupYieldPointTrapHandler(SIGILL);
    printf("Init GC - handlers ok\n");
    fflush(stdout);
#endif

    Heap_Init(&heap, Settings_MinHeapSize(), Settings_MaxHeapSize());
    printf("Init GC - heap done\n");
    fflush(stdout);
    Stack_Init(&stack, INITIAL_STACK_SIZE);
    Stack_Init(&weakRefStack, INITIAL_STACK_SIZE);
    printf("Init GC - stacks done\n");
    fflush(stdout);
#ifdef SCALANATIVE_MULTITHREADING_ENABLED
    Synchronizer_init();
    fflush(stdout);
    printf("Init GC - synchronizer done\n");
    weakRefsHandlerThread = GCThread_WeakThreadsHandler_Start();
    printf("Init GC - weak refs handler thread done\n");
#endif
    MutatorThreads_init();
    MutatorThread_init((word_t **)dummy); // approximate stack bottom
    customRoots = GC_Roots_Init();
    atexit(scalanative_afterexit);
    printf("Init GC - done\n");

    checkStackDirection((word_t*)&dummy);
    fflush(stdout);
}

INLINE void *scalanative_GC_alloc(void *info, size_t size) {
    size = MathUtils_RoundToNextMultiple(size, ALLOCATION_ALIGNMENT);

    assert(size % ALLOCATION_ALIGNMENT == 0);

    void **alloc;
    if (size >= LARGE_BLOCK_SIZE) {
        alloc = (void **)LargeAllocator_Alloc(&heap, size);
    } else {
        alloc = (void **)Allocator_Alloc(&heap, size);
    }
    *alloc = info;
    return (void *)alloc;
}

INLINE void *scalanative_GC_alloc_small(void *info, size_t size) {
    size = MathUtils_RoundToNextMultiple(size, ALLOCATION_ALIGNMENT);

    void **alloc = (void **)Allocator_Alloc(&heap, size);
    *alloc = info;
    return (void *)alloc;
}

INLINE void *scalanative_GC_alloc_large(void *info, size_t size) {
    size = MathUtils_RoundToNextMultiple(size, ALLOCATION_ALIGNMENT);

    void **alloc = (void **)LargeAllocator_Alloc(&heap, size);
    *alloc = info;
    return (void *)alloc;
}

INLINE void *scalanative_GC_alloc_atomic(void *info, size_t size) {
    return scalanative_GC_alloc(info, size);
}

INLINE void scalanative_GC_collect() { Heap_Collect(&heap, &stack); }

INLINE void scalanative_GC_register_weak_reference_handler(void *handler) {
    WeakRefStack_SetHandler(handler);
}

/* Get the minimum heap size */
/* If the user has set a minimum heap size using the GC_INITIAL_HEAP_SIZE
 * environment variable, */
/* then this size will be returned. */
/* Otherwise, the default minimum heap size will be returned.*/
size_t scalanative_GC_get_init_heapsize() { return Settings_MinHeapSize(); }

/* Get the maximum heap size */
/* If the user has set a maximum heap size using the GC_MAXIMUM_HEAP_SIZE
 * environment variable,*/
/* then this size will be returned.*/
/* Otherwise, the total size of the physical memory (guarded) will be returned*/
size_t scalanative_GC_get_max_heapsize() {
    return Parse_Env_Or_Default("GC_MAXIMUM_HEAP_SIZE", Heap_getMemoryLimit());
}

void scalanative_GC_add_roots(void *addr_low, void *addr_high) {
    AddressRange range = {addr_low, addr_high};
    GC_Roots_Add(customRoots, range);
}

void scalanative_GC_remove_roots(void *addr_low, void *addr_high) {
    AddressRange range = {addr_low, addr_high};
    GC_Roots_RemoveByRange(customRoots, range);
}

typedef void *RoutineArgs;
typedef struct {
    ThreadStartRoutine fn;
    RoutineArgs args;
} WrappedFunctionCallArgs;

#ifdef _WIN32
static ThreadRoutineReturnType WINAPI ProxyThreadStartRoutine(void *args) {
#else
static ThreadRoutineReturnType ProxyThreadStartRoutine(void *args) {
#endif
    volatile word_t stackBottom = 0;
    stackBottom = (word_t)&stackBottom;
    WrappedFunctionCallArgs *wrapped = (WrappedFunctionCallArgs *)args;
    ThreadStartRoutine originalFn = wrapped->fn;
    RoutineArgs originalArgs = wrapped->args;

    free(args);
    MutatorThread_init((Field_t *)stackBottom);
    originalFn(originalArgs);
    MutatorThread_delete(currentMutatorThread);
    return (ThreadRoutineReturnType)0;
}

#ifdef _WIN32
HANDLE scalanative_GC_CreateThread(LPSECURITY_ATTRIBUTES threadAttributes,
                                   SIZE_T stackSize, ThreadStartRoutine routine,
                                   RoutineArgs args, DWORD creationFlags,
                                   DWORD *threadId) {
    WrappedFunctionCallArgs *proxyArgs =
        (WrappedFunctionCallArgs *)malloc(sizeof(WrappedFunctionCallArgs));
    proxyArgs->fn = routine;
    proxyArgs->args = args;
    return CreateThread(threadAttributes, stackSize,
                        (ThreadStartRoutine)&ProxyThreadStartRoutine,
                        (RoutineArgs)proxyArgs, creationFlags, threadId);
}
#else
int scalanative_GC_pthread_create(pthread_t *thread, pthread_attr_t *attr,
                                  ThreadStartRoutine routine,
                                  RoutineArgs args) {
    WrappedFunctionCallArgs *proxyArgs =
        (WrappedFunctionCallArgs *)malloc(sizeof(WrappedFunctionCallArgs));
    proxyArgs->fn = routine;
    proxyArgs->args = args;
    return pthread_create(thread, attr,
                          (ThreadStartRoutine)&ProxyThreadStartRoutine,
                          (RoutineArgs)proxyArgs);
}
#endif

void scalanative_GC_set_mutator_thread_state(GC_MutatorThreadState state) {
    MutatorThread_switchState(currentMutatorThread, state);
}

void scalanative_GC_yield() {
#ifdef SCALANATIVE_MULTITHREADING_ENABLED
    if (atomic_load_explicit(&Synchronizer_stopThreads, memory_order_relaxed))
        Synchronizer_yield();
#endif
}

#endif
