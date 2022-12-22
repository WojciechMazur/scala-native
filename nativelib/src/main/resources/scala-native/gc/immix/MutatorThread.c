#include "MutatorThread.h"
#include "State.h"
#include <stdlib.h>
#include <stdatomic.h>
#include <ThreadUtil.h>

static mutex_t threadListsModifiactionLock;

void MutatorThread_init(Field_t *stackbottom) {
    MutatorThread *self = (MutatorThread *)malloc(sizeof(MutatorThread));
    memset(self, 0, sizeof(MutatorThread));
    currentMutatorThread = self;

    self->stackBottom = stackbottom;
    self->thread = pthread_self();
    MutatorThread_switchState(self, MutatorThreadState_Managed);
    Allocator_Init(&self->allocator);
    LargeAllocator_Init(&self->largeAllocator);
    MutatorThreads_add(self);
    // Following init operations might trigger GC, needs to be executed after
    // acknownleding the new thread in MutatorThreads_add
    Allocator_InitCursors(&self->allocator);
}

void MutatorThread_delete(MutatorThread *self) {
    MutatorThread_switchState(self, MutatorThreadState_Unmanaged);
    MutatorThreads_remove(self);
    free(self);
}

void MutatorThread_switchState(MutatorThread *self,
                               MutatorThreadState newState) {
    if (newState == MutatorThreadState_Unmanaged) {
        // Dumps registers into 'regs' which is on stack
        jmp_buf regs;
        setjmp(regs);
        word_t *dummy;
        self->stackTop = &dummy;
    } else {
        self->stackTop = NULL;
    }
    self->state = newState;
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

void MutatorThreads_lock() { mutex_lock(&threadListsModifiactionLock); }

void MutatorThreads_unlock() { mutex_unlock(&threadListsModifiactionLock); }
