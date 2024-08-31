#if defined(SCALANATIVE_GC_IMMIX) || defined(SCALANATIVE_GC_COMMIX)

#include "immix_commix/GCRoots.h"

#include <stdio.h>
#include <assert.h>
#include <stdlib.h>
#include <stdatomic.h>
#ifdef SCALANATIVE_MULTITHREADING_ENABLED
#include "shared/ThreadUtil.h"
#endif

GC_Roots *GC_Roots_Init() {
    GC_Roots *roots = (GC_Roots *)malloc(sizeof(GC_Roots));
    roots->head = ATOMIC_VAR_INIT(NULL);
#ifdef SCALANATIVE_CAN_USE_THREADS
    mutex_init(&roots->modificationLock);
#endif
    return roots;
}

void GC_Roots_Add(GC_Roots *roots, AddressRange range) {
    // Prepend the node with given range to the head of linked list of GC roots
    GC_Root *node = (GC_Root *)malloc(sizeof(GC_Root));
    node->range = range;
#ifdef SCALANATIVE_CAN_USE_THREADS
    mutex_lock(&roots->modificationLock);
#endif
    node->next = roots->head;
    roots->head = node;
#ifdef SCALANATIVE_CAN_USE_THREADS
    mutex_unlock(&roots->modificationLock);
#endif
}

void GC_Roots_Add_Range_Except(GC_Roots *roots, AddressRange range,
                               AddressRange except) {
    assert(AddressRange_Contains(range, except));
    if (range.address_low < except.address_low) {
        GC_Roots_Add(roots,
                     (AddressRange){range.address_low, except.address_low});
    }
    if (range.address_high > except.address_high) {
        GC_Roots_Add(roots,
                     (AddressRange){except.address_high, range.address_high});
    }
}

void GC_Roots_RemoveByRange(GC_Roots *roots, AddressRange range) {
#ifdef SCALANATIVE_CAN_USE_THREADS
    mutex_lock(&roots->modificationLock);
#endif
    GC_Root *current = roots->head;
    GC_Root *prev = NULL;
    while (current != NULL) {
        if (AddressRange_Contains(range, current->range)) {
            AddressRange current_range = current->range;
            if (prev == NULL)
                roots->head = current->next;
            else
                prev->next = current->next;
            GC_Roots_Add_Range_Except(roots, current_range, range);

            prev = current;
            GC_Root *next = current->next;
            free(current);
            current = next;
        } else {
            prev = current;
            current = current->next;
        }
    }
#ifdef SCALANATIVE_CAN_USE_THREADS
    mutex_unlock(&roots->modificationLock);
#endif
}
#endif
