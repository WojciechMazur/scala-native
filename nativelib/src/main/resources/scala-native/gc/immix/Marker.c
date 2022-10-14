#include <stdio.h>
#include <setjmp.h>
#include "Marker.h"
#include "Object.h"
#include "Log.h"
#include "State.h"
#include "datastructures/Stack.h"
#include "headers/ObjectHeader.h"
#include "Block.h"
#include "WeakRefStack.h"
#include <stdatomic.h>

extern word_t *__modules;
extern int __modules_size;

#define LAST_FIELD_OFFSET -1

static inline void Marker_markField(Heap *heap, Stack *stack, Field_t field);
static inline void Marker_markLockWords(Heap *heap, Stack *stack,
                                        Object *object);

void Marker_markObject(Heap *heap, Stack *stack, Bytemap *bytemap,
                       Object *object, ObjectMeta *objectMeta) {
    assert(ObjectMeta_IsAllocated(objectMeta));
    assert(object->rtti != NULL);

    if (Object_IsWeakReference(object)) {
        // Added to the WeakReference stack for additional later visit
        Stack_Push(&weakRefStack, object);
    }

    Marker_markLockWords(heap, stack, object);

    assert(Object_Size(object) != 0);
    Object_Mark(heap, object, objectMeta);
    Stack_Push(stack, object);
}

static inline void Marker_markLockWords(Heap *heap, Stack *stack,
                                        Object *object) {
    Field_t rttiLock = object->rtti->rt.lockWord;
    if (Field_isInflatedLock(rttiLock)) {
        Marker_markField(heap, stack, Field_allignedLockRef(rttiLock));
    }

    Field_t objectLock = object->lockWord;
    if (Field_isInflatedLock(objectLock)) {
        Field_t field = Field_allignedLockRef(objectLock);
        Marker_markField(heap, stack, field);
    }
}

static inline void Marker_markField(Heap *heap, Stack *stack, Field_t field) {
    if (Heap_IsWordInHeap(heap, field)) {
        ObjectMeta *fieldMeta = Bytemap_Get(heap->bytemap, field);
        if (ObjectMeta_IsAllocated(fieldMeta)) {
            Marker_markObject(heap, stack, heap->bytemap, (Object *)field,
                              fieldMeta);
        }
    }
}

void Marker_markConservative(Heap *heap, Stack *stack, word_t *address) {
    assert(Heap_IsWordInHeap(heap, address));
    Object *object = Object_GetUnmarkedObject(heap, address);
    Bytemap *bytemap = heap->bytemap;
    if (object != NULL) {
        ObjectMeta *objectMeta = Bytemap_Get(bytemap, (word_t *)object);
        assert(ObjectMeta_IsAllocated(objectMeta));
        if (ObjectMeta_IsAllocated(objectMeta)) {
            Marker_markObject(heap, stack, bytemap, object, objectMeta);
        }
    }
}

void Marker_Mark(Heap *heap, Stack *stack) {
    Bytemap *bytemap = heap->bytemap;
    while (!Stack_IsEmpty(stack)) {
        Object *object = Stack_Pop(stack);
        if (Object_IsArray(object)) {
            if (object->rtti->rt.id == __object_array_id) {
                ArrayHeader *arrayHeader = (ArrayHeader *)object;
                size_t length = arrayHeader->length;
                word_t **fields = (word_t **)(arrayHeader + 1);
                for (int i = 0; i < length; i++) {
                    Marker_markField(heap, stack, fields[i]);
                }
            }
            // non-object arrays do not contain pointers
        } else {
            int64_t *ptr_map = object->rtti->refMapStruct;
            for (int i = 0; ptr_map[i] != LAST_FIELD_OFFSET; i++) {
                if (Object_IsReferantOfWeakReference(object, ptr_map[i]))
                    continue;
                Marker_markField(heap, stack, object->fields[ptr_map[i]]);
            }
        }
    }
}

void Marker_markProgramStack(Heap *heap, Stack *stack) {
    // Dumps registers into 'regs' which is on stack
    jmp_buf regs;
    setjmp(regs);
    word_t *dummy;
    currentMutatorThread->stackTop = &dummy;
    MutatorThreads_foreach(mutatorThreads, node) {
        MutatorThread *thread = node->value;
        word_t **stackBottom = thread->stackBottom;
        assert(thread->stackTop != NULL);
        word_t **current = thread->stackTop;
        assert(current != NULL);
        while (current <= stackBottom) {
            word_t *stackObject = *current;
            if (Heap_IsWordInHeap(heap, stackObject)) {
                Marker_markConservative(heap, stack, stackObject);
            }
            current += 1;
        }
    }
}

void Marker_markModules(Heap *heap, Stack *stack) {
    word_t **modules = &__modules;
    int nb_modules = __modules_size;
    Bytemap *bytemap = heap->bytemap;
    for (int i = 0; i < nb_modules; i++) {
        Object *object = (Object *)modules[i];
        Marker_markField(heap, stack, (Field_t)object);
        if (object != NULL) {
            Marker_markLockWords(heap, stack, object);
        }
    }
}

void Marker_MarkRoots(Heap *heap, Stack *stack) {
    atomic_thread_fence(memory_order_seq_cst);
    Marker_markProgramStack(heap, stack);

    Marker_markModules(heap, stack);

    Marker_Mark(heap, stack);
}
