#include <threads.h>
#include <pthread.h>
#include <ScalaNativeGC.h>
#include "GCTypes.h"
#include "Allocator.h"
#include "LargeAllocator.h"
#include "State.h"
#include "Safepoint.h"
#include <setjmp.h>
#include <stdatomic.h>

#ifndef MUTATOR_THREAD_H
#define MUTATOR_THREAD_H

typedef struct {
    volatile MutatorThreadState state;
    word_t **volatile stackTop;
    volatile atomic_bool isWaiting;
    // immutable fields
    word_t **stackBottom;
    Allocator allocator;
    LargeAllocator largeAllocator;
    pthread_t thread;
} MutatorThread;

typedef struct MutatorThreadNode {
    MutatorThread *value;
    struct MutatorThreadNode *next;
} MutatorThreadNode;

typedef MutatorThreadNode *MutatorThreads;

void MutatorThread_init(word_t **stackBottom);
void MutatorThread_delete(MutatorThread *self);
void MutatorThread_switchState(MutatorThread *self,
                               MutatorThreadState newState);

void MutatorThreads_init();
void MutatorThreads_add(MutatorThread *node);
void MutatorThreads_remove(MutatorThread *node);
void MutatorThreads_lock();
void MutatorThreads_unlock();

#define MutatorThreads_foreach(list, node)                                     \
    for (MutatorThreads node = list; node != NULL; node = node->next)

#endif // MUTATOR_THREAD_H
