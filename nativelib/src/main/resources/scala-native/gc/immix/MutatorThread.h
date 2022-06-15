#include <threads.h>
#include <pthread.h>
#include "GCTypes.h"
#include "Allocator.h"
#include "LargeAllocator.h"
#include "State.h"
#include "Safepoint.h"

#ifndef MUTATOR_THREAD_H
#define MUTATOR_THREAD_H

typedef enum MutatorThreadState {
    MutatorThreadState_Working = 0, 
    // Thread is performing of waiting for end of garbage collection
    MutatorThreadState_Synchronized = 1,
    // Thread is inside unmanaged zone and can run alongside GC 
    MutatorThreadState_InSafeZone = 2
} MutatorThreadState;
// _StaticAssert(sizeof(MutatorThreadState) == 1, "Expecting enum to take 1 byte")

thread_local static safepoint_t* scalanative_gc_safepoint;

typedef struct {
    MutatorThreadState state;
    word_t **stackTop;
    word_t **stackBottom;
    safepoint_t* safepoint;
    Allocator allocator;
    LargeAllocator largeAllocator;
} MutatorThread;

typedef struct MutatorThreadNode{
    MutatorThread *value;
    struct MutatorThreadNode *next;
} MutatorThreadNode;

typedef MutatorThreadNode *MutatorThreads;

void MutatorThread_init(word_t **stackBottom);
void MutatorThread_delete(MutatorThread *self);

INLINE static MutatorThreadState MutatorThread_switchState(MutatorThread *self, MutatorThreadState newState){
    MutatorThreadState prev = self->state;
    // printf("Switch state %p, from %d to %d\n", self, prev, newState);
    self->state = newState;
    if(newState == MutatorThreadState_InSafeZone){
        word_t *dummy;
        self->stackTop = &dummy;
    }
    return prev;
}

// INLINE MutatorThreadState MutatorThread_TLS_setState(MutatorThreadState newState){
//     MutatorThreadState previous = currentMutatorThread->state;
//     currentMutatorThread->state = newState;
//     return previous;
// }

void MutatorThreads_init();
void MutatorThreads_add(MutatorThread *node);
void MutatorThreads_remove(MutatorThread *node);

#define MutatorThreads_foreach(list, node)                                     \
    for (MutatorThreads node = list; node != NULL; node = node->next)

#endif // MUTATOR_THREAD_H