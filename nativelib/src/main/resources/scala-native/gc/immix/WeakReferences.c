#include "immix/Heap.h"
#include <stdio.h>
#if defined(SCALANATIVE_GC_IMMIX)

#include "WeakReferences.h"
#include "datastructures/Stack.h"
#include "metadata/ObjectMeta.h"
#include "immix_commix/headers/ObjectHeader.h"
#include "State.h"
#include <stdbool.h>

static bool collectedWeakReferences = false;
static void (*gcFinishedCallback)() = NULL;

// A collection of marked WeakReferences.
// Used to correctly set "NULL" values in place of cleaned objects
// and to call other handler functions with WeakRefStack_CallHandlers.

void WeakReferences_Nullify(void) {
    Bytemap *bytemap = heap.bytemap;
    while (!Stack_IsEmpty(&weakRefStack)) {
        Object *object = Stack_Pop(&weakRefStack);
        Object **weakRefReferantField =
            (Object **)((int8_t *)object + __weak_ref_field_offset);
        word_t *weakRefReferant = (word_t *)*weakRefReferantField;
        printf("Try nullify %p->%p - inHeap=%d", object, weakRefReferant,
               Heap_IsWordInHeap(&heap, weakRefReferant));
        if (Heap_IsWordInHeap(&heap, weakRefReferant)) {
            ObjectMeta *objectMeta = Bytemap_Get(bytemap, weakRefReferant);
            printf(", allocated=%d, marked=%d",
                   ObjectMeta_IsAllocated(objectMeta),
                   ObjectMeta_IsMarked(objectMeta));
            if (ObjectMeta_IsAllocated(objectMeta) &&
                !ObjectMeta_IsMarked(objectMeta)) {
                // WeakReferences should have the held referent
                // field set to null if collected
                *weakRefReferantField = NULL;
                collectedWeakReferences = true;
            }
        }
        printf("\n");
    }
}

void WeakReferences_SetGCFinishedCallback(void *handler) {
    gcFinishedCallback = handler;
}

void WeakReferences_InvokeGCFinishedCallback(void) {
    if (collectedWeakReferences && gcFinishedCallback != NULL) {
        gcFinishedCallback();
    }
}

#endif
