#include "MutatorThread.h"
#include "State.h"
#include <stdlib.h>
#include <stdatomic.h>
#include <ThreadUtil.h>

extern atomic_int activeThreads;
mutex_t threadListsModifiactionLock;

void MutatorThread_init(word_t **stackbottom) {
    MutatorThread *self = (MutatorThread *)malloc(sizeof(MutatorThread));
    self->stackBottom = stackbottom;
    MutatorThreads_add(self);
    currentMutatorThread = self; 

    Allocator_Init(&self->allocator);
    LargeAllocator_Init(&self->largeAllocator, &blockAllocator, heap.bytemap,
                        heap.blockMetaStart, heap.heapStart);
    printf("Self %p, point %p, point ref %p\n", self, self->safepoint, &self->safepoint);
    Safepoint_init(&self->safepoint);
    printf("After Self %p, point %p, point ref %p\n", self, self->safepoint, &self->safepoint);
    scalanative_gc_safepoint = self->safepoint;
    MutatorThread_switchState(self, MutatorThreadState_Working);
}

void MutatorThread_delete(MutatorThread *self) {
    MutatorThread_switchState(self, MutatorThreadState_InSafeZone);
    MutatorThreads_remove(self);
    free(self);
}

// void MutatorThread_setActive(MutatorThread *self, bool newState) {
//     // if (self != NULL && self->isActive != newState) {
//     //     self->isActive = newState;
//     //     int prev = atomic_fetch_add(&activeThreads, newState ? 1 : -1);
//     //     // printf("Thread %p set to %s, activeThreads=%d\n", self,
//     //     //        newState ? "active" : "inactive", activeThreads);
//     // }
//     if (activeThreads == 0) {
//         pthread_cond_broadcast(&canStartCollecting);
//     }
// }

void MutatorThreads_init() { mutex_init(&threadListsModifiactionLock); }

void MutatorThreads_add(MutatorThread *node) {
    if (!node)
        return;
    mutex_lock(&threadListsModifiactionLock);
    MutatorThreadNode *newNode =
        (MutatorThreadNode *)malloc(sizeof(MutatorThreadNode));
    newNode->value = node;
    newNode->next = mutatorThreads;
    mutatorThreads = newNode;
    mutex_unlock(&threadListsModifiactionLock);
}

void MutatorThreads_remove(MutatorThread *node) {
    if (!node)
        return;

    mutex_lock(&threadListsModifiactionLock);
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
    mutex_unlock(&threadListsModifiactionLock);
}
