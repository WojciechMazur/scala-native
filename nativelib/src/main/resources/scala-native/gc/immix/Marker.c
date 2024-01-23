#include <stdbool.h>
#if defined(SCALANATIVE_GC_IMMIX)
#include <stdint.h>
#include <stdio.h>
#include <setjmp.h>
#include "Marker.h"
#include "Object.h"
#include "immix_commix/Log.h"
#include "State.h"
#include "datastructures/Stack.h"
#include "immix_commix/headers/ObjectHeader.h"
#include "Block.h"
#include "WeakRefStack.h"
#include "shared/GCTypes.h"
#include <stdatomic.h>
#include "shared/ThreadUtil.h"

extern word_t *__modules;
extern int __modules_size;

#define LAST_FIELD_OFFSET -1

static atomic_bool lockInitialized = false;
static mutex_t lock;

static inline void Marker_markLockWords(Heap *heap, Stack *stack,
                                        Object *object);
static void Marker_markRange(Heap *heap, Stack *stack, word_t **from,
                             word_t **to, const char *context);

void Marker_markObject(Heap *heap, Stack *stack, Bytemap *bytemap,
                       Object *object, ObjectMeta *objectMeta) {
    assert(ObjectMeta_IsAllocated(objectMeta));
    assert(object->rtti != NULL);

    Marker_markLockWords(heap, stack, object);
    if (Object_IsWeakReference(object)) {
        // Added to the WeakReference stack for additional later visit
        Stack_Push(&weakRefStack, object);
    }

    assert(Object_Size(object) != 0);
    Object_Mark(heap, object, objectMeta);
    Stack_Push(stack, object);
}

static inline void Marker_markField(Heap *heap, Stack *stack, Field_t field) {
    if (Heap_IsWordInHeap(heap, field)) {
        ObjectMeta *fieldMeta = Bytemap_Get(heap->bytemap, field);
        if (ObjectMeta_IsAllocated(fieldMeta)) {
            Object *object = (Object *)field;
            Marker_markObject(heap, stack, heap->bytemap, object, fieldMeta);
        }
    }
}

/* If compiling with enabled lock words check if object monitor is inflated and
 * can be marked. Otherwise, in singlethreaded mode this funciton is no-op
 */
static inline void Marker_markLockWords(Heap *heap, Stack *stack,
                                        Object *object) {
#ifdef USES_LOCKWORD
    if (object != NULL) {
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
#endif
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
            ArrayHeader *arrayHeader = (ArrayHeader *)object;
            const int arrayId = object->rtti->rt.id;

            if (arrayId == __object_array_id) {
                const size_t length = arrayHeader->length;
                word_t **fields = (word_t **)(arrayHeader + 1);
                for (int i = 0; i < length; i++) {
                    Marker_markField(heap, stack, fields[i]);
                }
            } else if (arrayId == __blob_array_id) {
                int8_t *start = (int8_t *)(arrayHeader + 1);
                int8_t *end = start + BlobArray_ScannableLimit(arrayHeader);
                Marker_markRange(heap, stack, (word_t **)start, (word_t **)end,
                                 "blobArray");
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

void Marker_MarkRange_Eager(word_t **from, word_t **to) {
    if (!lockInitialized) {
        mutex_init(&lock);
        lockInitialized = true;
    }
    mutex_lock(&lock);
    Marker_markRange(&heap, &stack, from, to, "eager-custom");
    mutex_unlock(&lock);
}

NO_SANITIZE static void Marker_markRange(Heap *heap, Stack *stack,
                                         word_t **from, word_t **to,
                                         const char *context) {
    assert(from != NULL);
    assert(to != NULL);
    for (word_t **current = from; current <= to; current += 1) {
        word_t *addr = *current;
        if (Heap_IsWordInHeap(heap, addr)) {
            Marker_markConservative(heap, stack, addr);
        }
    }
}

NO_SANITIZE void Marker_markProgramStack(MutatorThread *thread, Heap *heap,
                                         Stack *stack) {
    word_t **stackBottom = thread->stackBottom;
    word_t **stackTop = NULL;
    do {
        // Can spuriously fail, very rare, yet deadly
        stackTop = (word_t **)atomic_load_explicit(&thread->stackTop,
                                                   memory_order_acquire);
    } while (stackTop == NULL);
    Marker_markRange(heap, stack, stackTop, stackBottom, "stack");

    // Mark last context of execution
    assert(thread->executionContext != NULL);
    word_t **regs = (word_t **)thread->executionContext;
    size_t regsSize = sizeof(jmp_buf) / sizeof(word_t *);
    Marker_markRange(heap, stack, regs, regs + regsSize, "regs");
}

void Marker_markModules(Heap *heap, Stack *stack) {
    word_t **modules = &__modules;
    int nb_modules = __modules_size;
    Bytemap *bytemap = heap->bytemap;
    for (int i = 0; i < nb_modules; i++) {
        Object *object = (Object *)modules[i];
        Marker_markField(heap, stack, (Field_t)object);
    }
}

void Marker_markCustomRoots(Heap *heap, Stack *stack, GC_Roots *roots) {
    mutex_lock(&roots->modificationLock);
    for (GC_Root *it = roots->head; it != NULL; it = it->next) {
        Marker_markRange(heap, stack, (word_t **)it->range.address_low,
                         (word_t **)it->range.address_high, "customRoots");
    }
    mutex_unlock(&roots->modificationLock);
}

void Marker_MarkRoots(Heap *heap, Stack *stack) {
    atomic_thread_fence(memory_order_seq_cst);
    if (!lockInitialized) {
        mutex_init(&lock);
        lockInitialized = true;
    }
    mutex_lock(&lock);

    MutatorThreadNode *head = mutatorThreads;
    MutatorThreads_foreach(mutatorThreads, node) {
        MutatorThread *thread = node->value;
        Marker_markProgramStack(thread, heap, stack);
    }
    Marker_markModules(heap, stack);
    Marker_markCustomRoots(heap, stack, customRoots);
    Marker_Mark(heap, stack);
    mutex_unlock(&lock);
}

#endif
