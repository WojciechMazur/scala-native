#include <string.h>
#if defined(SCALANATIVE_GC_COMMIX)
#include "shared/ScalaNativeGC.h"
#include "MutatorThread.h"
#include "State.h"
#include <stdlib.h>
#include <stdatomic.h>
#include <setjmp.h>
#include <shared/ThreadUtil.h>
#include <assert.h>

static rwlock_t threadListsModificationLock;

void MutatorThread_init(Field_t *stackbottom) {
    MutatorThread *self = (MutatorThread *)malloc(sizeof(MutatorThread));
    memset(self, 0, sizeof(MutatorThread));
    currentMutatorThread = self;

    self->stackBottom = stackbottom;
#ifdef SCALANATIVE_GC_USE_YIELDPOINT_TRAPS
#ifdef _WIN32
    self->wakeupEvent = CreateEvent(NULL, true, false, NULL);
    if (self->wakeupEvent == NULL) {
        fprintf(stderr, "Failed to setup mutator thread: errno=%lu\n",
                GetLastError());
        exit(1);
    }
#else
    self->thread = pthread_self();
#endif
#endif // SCALANATIVE_GC_USE_YIELDPOINT_TRAPS
    MutatorThread_switchState(self, GC_MutatorThreadState_Managed);
    Allocator_Init(&self->allocator, &blockAllocator, heap.bytemap,
                   heap.blockMetaStart, heap.heapStart);

    LargeAllocator_Init(&self->largeAllocator, &blockAllocator, heap.bytemap,
                        heap.blockMetaStart, heap.heapStart);
    MutatorThreads_add(self);
    atomic_fetch_add(&mutatorThreadsCount, 1);
    // Following init operations might trigger GC, needs to be executed after
    // acknownleding the new thread in MutatorThreads_add
    Allocator_InitCursors(&self->allocator);
#ifdef SCALANATIVE_MULTITHREADING_ENABLED
    // Stop if there is ongoing GC_collection
    scalanative_GC_yield();
#endif
}

void MutatorThread_delete(MutatorThread *self) {
    MutatorThread_switchState(self, GC_MutatorThreadState_Unmanaged);
    MutatorThreads_remove(self);
    atomic_fetch_add(&mutatorThreadsCount, -1);
#if defined(SCALANATIVE_GC_USE_YIELDPOINT_TRAPS) && defined(_WIN32)
    CloseHandle(self->wakeupEvent);
#endif
    free(self);
}

typedef word_t **stackptr_t;

NOINLINE NO_OPTIMIZE static stackptr_t MutatorThread_approximateStackTop() {
    volatile word_t sp;
#if GNUC_PREREQ(4, 0)
    sp = (word_t)__builtin_frame_address(0);
#else
    sp = (word_t)&sp;
#endif
    /* Also force stack to grow if necessary. Otherwise the later accesses might
     * cause the kernel to think we're doing something wrong. */
    return (stackptr_t)sp;
}

void MutatorThread_switchState(MutatorThread *self,
                               GC_MutatorThreadState newState) {
    assert(self != NULL);
    intptr_t newStackTop = 0;
    if (newState == GC_MutatorThreadState_Unmanaged) {
        // Dump registers to allow for their marking later
        __builtin_unwind_init();
        jmp_buf regs;
        word_t *i = (word_t *)&regs[0];
        int8_t *lim = (int8_t *)(&regs[0]) + sizeof(regs);
        // setjmp doesn't always clear all of the buffer.               */
        // That tends to preserve garbage. Clear it.                   */
        memset((void *)&regs[0], 0, sizeof(regs));
        (void)setjmp(regs);
        newStackTop = (intptr_t)MutatorThread_approximateStackTop();
    }
    atomic_store_explicit(&self->stackTop, newStackTop, memory_order_release);
    self->state = newState;
}

void MutatorThreads_lockRead() {
    rwlock_lockRead(&threadListsModificationLock);
}
void MutatorThreads_unlockRead() {
    rwlock_unlockRead(&threadListsModificationLock);
}

static void MutatorThreads_lockWrite() {
    rwlock_lockWrite(&threadListsModificationLock);
}
static void MutatorThreads_unlockWrite() {
    rwlock_unlockWrite(&threadListsModificationLock);
}

void MutatorThreads_init() { rwlock_init(&threadListsModificationLock); }

void MutatorThreads_add(MutatorThread *node) {
    if (!node)
        return;
    MutatorThreadNode *newNode =
        (MutatorThreadNode *)malloc(sizeof(MutatorThreadNode));
    newNode->value = node;
    MutatorThreads_lockWrite();
    newNode->next = mutatorThreads;
    mutatorThreads = newNode;
    MutatorThreads_unlockWrite();
}

void MutatorThreads_remove(MutatorThread *node) {
    if (!node)
        return;

    MutatorThreads_lockWrite();
    MutatorThreads current = mutatorThreads;
    if (current->value == node) { // expected is at head
        mutatorThreads = current->next;
        free(current);
    } else {
        while (current->next && current->next->value != node) {
            current = current->next;
        }
        MutatorThreads next = current->next;
        if (next) {
            current->next = next->next;
            free(next);
        }
    }
    MutatorThreads_unlockWrite();
}

#endif