#include <threads.h>
#include <pthread.h>
#include "GCTypes.h"
#include "Allocator.h"
#include "LargeAllocator.h"
#include "State.h"
#include "Safepoint.h"
#include <setjmp.h>
#include <stdatomic.h>

#ifndef MUTATOR_THREAD_H
#define MUTATOR_THREAD_H

typedef enum MutatorThreadState {
    MutatorThreadState_Working = 0,
    // Thread is performing of waiting for end of garbage collection
    MutatorThreadState_Synchronized = 1,
    // Thread is inside unmanaged zone and can run alongside GC
    MutatorThreadState_InSafeZone = 2
} MutatorThreadState;
// _StaticAssert(sizeof(MutatorThreadState) == 1, "Expecting enum to take 1
// byte")

thread_local static safepoint_t *scalanative_gc_safepoint;

typedef struct {
    volatile MutatorThreadState state;
    word_t **stackTop;
    word_t **stackBottom;
    safepoint_t *safepoint;
    Allocator allocator;
    LargeAllocator largeAllocator;
} MutatorThread;

typedef struct MutatorThreadNode {
    MutatorThread *value;
    struct MutatorThreadNode *next;
} MutatorThreadNode;

typedef MutatorThreadNode *MutatorThreads;

void MutatorThread_init(word_t **stackBottom);
void MutatorThread_delete(MutatorThread *self);

INLINE static MutatorThreadState
MutatorThread_switchState(MutatorThread *self, MutatorThreadState newState) {
    if (newState == MutatorThreadState_InSafeZone) {
        // Dumps registers into 'regs' which is on stack
        jmp_buf regs;
        setjmp(regs);
        word_t *dummy;
        self->stackTop = &dummy;
    }
    MutatorThreadState prev = self->state;
    self->state = newState;
    return prev;
}

void MutatorThreads_init();
void MutatorThreads_add(MutatorThread *node);
void MutatorThreads_remove(MutatorThread *node);
void MutatorThreads_lock();
void MutatorThreads_unlock();

#define MutatorThreads_foreach(list, node)                                     \
    for (MutatorThreads node = list; node != NULL; node = node->next)

#endif // MUTATOR_THREAD_H
