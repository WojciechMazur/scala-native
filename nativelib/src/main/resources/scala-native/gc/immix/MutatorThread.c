#include "MutatorThread.h"
#include "State.h"
#include <stdlib.h>
#include <stdatomic.h>
#include <ThreadUtil.h>

mutex_t threadListsModifiactionLock;

void MutatorThread_init(word_t **stackbottom) {
    MutatorThread *self = (MutatorThread *)malloc(sizeof(MutatorThread));
    Safepoint_init(&self->safepoint);
    MutatorThread_switchState(self, MutatorThreadState_Working);

    currentMutatorThread = self; 
    scalanative_gc_safepoint = self->safepoint;
    
    self->stackBottom = stackbottom;
    Allocator_Init(&self->allocator);
    LargeAllocator_Init(&self->largeAllocator, &blockAllocator, heap.bytemap,
                        heap.blockMetaStart, heap.heapStart);
    MutatorThreads_add(self);
}

void MutatorThread_delete(MutatorThread *self) {
    MutatorThread_switchState(self, MutatorThreadState_InSafeZone);
    MutatorThreads_remove(self);
    free(self);
}

void MutatorThreads_init() { mutex_init(&threadListsModifiactionLock); }

void MutatorThreads_add(MutatorThread *node) {
    if (!node)
        return;
    MutatorThreads_lock();
    MutatorThreadNode *newNode =
        (MutatorThreadNode *)malloc(sizeof(MutatorThreadNode));
    newNode->value = node;
    newNode->next = mutatorThreads;
    mutatorThreads = newNode;
    MutatorThreads_unlock();
}

void MutatorThreads_remove(MutatorThread *node) {
    if (!node)
        return;

    MutatorThreads_lock();
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
    MutatorThreads_unlock();
}

void MutatorThreads_lock(){
    mutex_lock(&threadListsModifiactionLock);
}

void MutatorThreads_unlock(){
    mutex_unlock(&threadListsModifiactionLock);
}
