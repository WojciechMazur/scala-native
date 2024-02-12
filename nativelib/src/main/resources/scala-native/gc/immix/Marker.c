#include "immix/datastructures/Bytemap.h"
#include <string.h>
#include <wchar.h>
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

static inline void Marker_markLockWords(Heap *heap, Stack *stack,
                                        Object *object);
static void Marker_markRange(Heap *heap, Stack *stack, word_t **from,
                             word_t **to);

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
    if (field != NULL && Heap_IsWordInHeap(heap, field)) {
        ObjectMeta *fieldMeta = Bytemap_Get(heap->bytemap, field);
        if (ObjectMeta_IsAllocated(fieldMeta)) {
            Object *object = (Object *)field;
            wchar_t *objectName = Object_nameWString((Object *)object);
            printf("MarkField of %ls - field %p, obj %p\n", objectName,
                   field, object);
            free(objectName);
            Marker_markObject(heap, stack, heap->bytemap, object, fieldMeta);
        } else {
            // word_t *pointee = *(word_t **)field;
            // if (Bytemap_addressInBytemap(heap->bytemap, pointee)) {
            //     wchar_t *objectName = Object_nameWString((Object *)pointee);
            //     printf("Try markField %ls - unboxed pointer %p to %p\n",
            //            objectName, field, pointee);
            //     free(objectName);
            //     Marker_markField(heap, stack, pointee);
            // }
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
    if (address != NULL && Bytemap_isPtrAligned(address)) {
        Object *object = Object_GetUnmarkedObject(heap, address);
        if (object != NULL) {
            // wchar_t *objectName = Object_nameWString(object);
            // printf("MarkConservative %ls - addr=%p, obj=%p\n", objectName,
            //        address, object);
            // free(objectName);
            Bytemap *bytemap = heap->bytemap;
            ObjectMeta *objectMeta = Bytemap_Get(bytemap, (word_t *)object);
            if (ObjectMeta_IsAllocated(objectMeta)) {
                Marker_markObject(heap, stack, bytemap, object, objectMeta);
            }
        } else {
            // word_t *pointee = *(word_t **)address;
            // if (Heap_IsWordInHeap(heap, pointee) &&
            //     Bytemap_addressInBytemap(heap->bytemap, pointee)) {
            //     wchar_t *objectName = Object_nameWString((Object *)pointee);
            //     printf("Try markConservative %ls - unboxed pointer %p to
            //     %p\n", objectName,
            //            address, pointee);
            //     free(objectName);
            //     Marker_markConservative(heap, stack, pointee);
            // }
        }
    }
}

void Marker_Mark(Heap *heap, Stack *stack) {
    Bytemap *bytemap = heap->bytemap;
    int idx = 0;
    printf("MarkerMark\n");
    while (!Stack_IsEmpty(stack)) {
        Object *object = Stack_Pop(stack);
        const int objectId = object->rtti->rt.id;
        wchar_t *objectName = Object_nameWString(object);
        printf("Marker mark[%d]: %ls - %p\n", idx++, objectName, object);
        free(objectName);
        if (Object_IsArray(object)) {
            ArrayHeader *arrayHeader = (ArrayHeader *)object;
            if (objectId == __object_array_id) {
                const size_t length = arrayHeader->length;
                word_t **fields = (word_t **)(arrayHeader + 1);
                for (int i = 0; i < length; i++) {
                    Marker_markField(heap, stack, fields[i]);
                }
            } else if (objectId == __blob_array_id) {
                int8_t *start = (int8_t *)(arrayHeader + 1);
                int8_t *end = start + BlobArray_ScannableLimit(arrayHeader);
                printf("In blob array:\n");
                Marker_markRange(heap, stack, (word_t **)start, (word_t **)end);
            }
            // non-object arrays do not contain pointers
        } else {
            int32_t *refFieldOffsets = object->rtti->refFieldOffsets;
            for (int i = 0; refFieldOffsets[i] != LAST_FIELD_OFFSET; i++) {
                size_t fieldOffset = (size_t)refFieldOffsets[i];
                Field_t *fieldRef = (Field_t *)((int8_t *)object + fieldOffset);
                if (Object_IsReferantOfWeakReference(object, fieldOffset))
                    continue;
                Marker_markField(heap, stack, *fieldRef);
            }
            if (objectId == __boxed_ptr_id) {
                // Boxed ptr always has a single field
                word_t *rawPtr = object->fields[0];
                if (Heap_IsWordInHeap(heap, rawPtr)) {
                    Marker_markConservative(heap, stack, rawPtr);
                }
            }
        }
    }
}

NO_SANITIZE static void Marker_markRange(Heap *heap, Stack *stack,
                                         word_t **from, word_t **to) {
    assert(from != NULL);
    assert(to != NULL);
    int idx = 0;
    for (word_t **current = from; current <= to; current += 1, idx += 1) {
        word_t *addr = *current;
        if (Heap_IsWordInHeap(heap, addr)) {
            // Object *object = Object_GetObject(heap, addr);
            // Object *unmarkedObject = Object_GetUnmarkedObject(heap, addr);
            // bool isUnmarked = unmarkedObject == object;
            // if (object != NULL) {
            //     wchar_t *objectName = Object_nameWString(object);
            //     printf("%p: [%d] Mark %ls: addr=%p, obj=%p, off=%ld,
            //     val=%p\n",
            //            current, idx, objectName, addr, object,
            //            (addr - (word_t *)object), *(word_t **)addr);
            //     free(objectName);
            // } else {
            //     word_t *inner = *(word_t **)addr;
            //     printf("%p: [%d] Not an object %p, val=%p, valInHeap=%d\n",
            //            current, idx, addr, inner,
            //            Heap_IsWordInHeap(heap, inner));
            //     fflush(stdout);
            //     // Object *object = NULL;
            //     // if (Bytemap_addressInBytemap(heap->bytemap, inner)) {
            //     //     object = Object_GetObject(heap, inner);
            //     // } else {
            //     //     bool afterStart = inner >=
            //     heap->bytemap->firstAddress;
            //     //     size_t index = Bytemap_indexUnsafe(heap->bytemap,
            //     inner);
            //     //     printf("Not in bytemap bounds: inner=%p,
            //     afterStart=%d, "
            //     //            "start=%p, idx=%lu, limit=%lu, end=%p\n",
            //     //            inner, afterStart, heap->bytemap->firstAddress,
            //     //            index, heap->bytemap->size,
            //     heap->bytemap->end);
            //     // }
            //     // printf("Inner object: %p\n", object);

            //     // if (object != NULL && Heap_IsWordInHeap(heap, inner)) {
            //     //     CharArray *strChars = object->rtti->rt.name->value;
            //     //     int nameLength = strChars->header.length;
            //     //     wchar_t buf[nameLength + 1] = {};
            //     //     for (int i = 0; i < nameLength; i++) {
            //     //         buf[i] = (wchar_t)strChars->values[i];
            //     //     }
            //     //     buf[nameLength] = 0;
            //     //     printf("Unboxed pointer to: %ls\n", buf);
            //     //     Marker_markConservative(heap, stack, inner);
            //     // }
            // }
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
    printf("Mark stack of %p, from=%p, to=%p, size=%zu\n", thread, stackTop,
           stackBottom, (stackBottom - stackTop) * sizeof(word_t));
    Marker_markRange(heap, stack, stackTop, stackBottom);

    // printf("Outside stack of %p\n", thread);
    // Marker_markRange(heap, stack, stackTop - 128, stackTop);

    // Mark registers buffer
    printf("Mark regs of %p, from=%p, to=%p, size=%zu\n", thread,
           (word_t **)&thread->registersBuffer,
           (word_t **)(&thread->registersBuffer + 1),
           sizeof(thread->registersBuffer));
    Marker_markRange(heap, stack, (word_t **)&thread->registersBuffer,
                     (word_t **)(&thread->registersBuffer + 1));
}

void Marker_markModules(Heap *heap, Stack *stack) {
    word_t **modules = &__modules;
    int nb_modules = __modules_size;
    Bytemap *bytemap = heap->bytemap;
    int initialized = 0;
    for (int i = 0; i < nb_modules; i++) {
        Object *object = (Object *)modules[i];
        if (object == NULL) {
            object = atomic_load((_Atomic(Object *) *)&modules[i]);
            if (object != NULL) {
                printf("DATA_RACE!!!\n");
                atomic_thread_fence(memory_order_seq_cst);
            }
        }
        if (object != NULL) {
            // CharArray *strChars = object->rtti->rt.name->value;
            // int nameLength = strChars->header.length;
            // wchar_t buf[nameLength + 1] = {};
            // for (int i = 0; i < nameLength; i++) {
            //     buf[i] = (wchar_t)strChars->values[i];
            // }
            // buf[nameLength] = 0;
            // printf("%d: Mark module of %ls allocated at %p\n", i, buf,
            // object);
            initialized++;
            Marker_markField(heap, stack, (Field_t)object);
        }
    }
    // printf("Initialized %d/%d modules\n", initialized, __modules_size);
}

void Marker_markCustomRoots(Heap *heap, Stack *stack, GC_Roots *roots) {
    mutex_lock(&roots->modificationLock);
    printf("In custom root:\n");
    for (GC_Root *it = roots->head; it != NULL; it = it->next) {
        Marker_markRange(heap, stack, (word_t **)it->range.address_low,
                         (word_t **)it->range.address_high);
    }
    mutex_unlock(&roots->modificationLock);
}

void Marker_MarkRoots(Heap *heap, Stack *stack) {
    atomic_thread_fence(memory_order_seq_cst);

    MutatorThreadNode *head = mutatorThreads;
    MutatorThreads_foreach(mutatorThreads, node) {
        MutatorThread *thread = node->value;
        Marker_markProgramStack(thread, heap, stack);
    }
    Marker_markModules(heap, stack);
    Marker_markCustomRoots(heap, stack, customRoots);
    Marker_Mark(heap, stack);
}

#endif
