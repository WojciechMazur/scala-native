#include "UnsafeZonePool.h"

#include <stdatomic.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>

#ifdef _WIN32
#define WIN32_LEAN_AND_MEAN
#define NOMINMAX
#include <windows.h>
#else
#include <sched.h>
#endif

typedef struct UnsafeZoneChunk {
    uint8_t *memory;
    UnsafeZonePage *pages;
    size_t pageCount;
    size_t used; // number of pages already handed out to the free list
    struct UnsafeZoneChunk *next;
} UnsafeZoneChunk;

// All state is guarded by `poolLock`, except for the read-only traversal
// performed by the GC in `scalanative_unsafe_zone_foreach_root`.
static atomic_flag poolLock = ATOMIC_FLAG_INIT;
static UnsafeZoneChunk *chunks = NULL;
static UnsafeZonePage *freePages = NULL;
static size_t nextChunkPages = SCALANATIVE_ZONE_MIN_CHUNK_PAGES;
// Sentinel of the list of open zones, linked through nextZone/prevZone.
static UnsafeZonePage openZones = {0};

static inline void pool_lock(void) {
    while (atomic_flag_test_and_set_explicit(&poolLock, memory_order_acquire)) {
#ifdef _WIN32
        SwitchToThread();
#else
        sched_yield();
#endif
    }
}

static inline void pool_unlock(void) {
    atomic_flag_clear_explicit(&poolLock, memory_order_release);
}

static void out_of_memory(void) {
    fprintf(stderr, "Out of memory: unable to allocate Zone memory\n");
    abort();
}

// Requires poolLock
static UnsafeZonePage *pool_take_page(void) {
    UnsafeZonePage *page = freePages;
    if (page != NULL) {
        freePages = page->next;
    } else {
        UnsafeZoneChunk *chunk = chunks;
        if (chunk == NULL || chunk->used == chunk->pageCount) {
            size_t count = nextChunkPages;
            if (nextChunkPages < SCALANATIVE_ZONE_MAX_CHUNK_PAGES)
                nextChunkPages *= 2;
            chunk = malloc(sizeof(UnsafeZoneChunk));
            if (chunk == NULL)
                out_of_memory();
            chunk->memory = malloc(count * SCALANATIVE_ZONE_PAGE_SIZE);
            chunk->pages = malloc(count * sizeof(UnsafeZonePage));
            if (chunk->memory == NULL || chunk->pages == NULL)
                out_of_memory();
            chunk->pageCount = count;
            chunk->used = 0;
            chunk->next = chunks;
            chunks = chunk;
        }
        page = &chunk->pages[chunk->used];
        page->start = chunk->memory + chunk->used * SCALANATIVE_ZONE_PAGE_SIZE;
        chunk->used++;
    }
    page->offset = 0;
    page->next = NULL;
    return page;
}

UnsafeZonePage *scalanative_unsafe_zone_open(void) {
    pool_lock();
    UnsafeZonePage *zone = pool_take_page();
    zone->head = zone;
    zone->large = NULL;
    UnsafeZonePage *first = openZones.nextZone;
    zone->prevZone = &openZones;
    zone->nextZone = first;
    if (first != NULL)
        first->prevZone = zone;
    // Publish only once the record is fully initialized.
    __atomic_store_n(&openZones.nextZone, zone, __ATOMIC_RELEASE);
    pool_unlock();
    return zone;
}

UnsafeZonePage *scalanative_unsafe_zone_add_page(UnsafeZonePage *zone) {
    pool_lock();
    UnsafeZonePage *page = pool_take_page();
    pool_unlock();
    page->next = zone->head;
    __atomic_store_n(&zone->head, page, __ATOMIC_RELEASE);
    return page;
}

void *scalanative_unsafe_zone_alloc_large(UnsafeZonePage *zone, size_t size) {
    UnsafeZoneLarge *large = malloc(sizeof(UnsafeZoneLarge) + size);
    if (large == NULL)
        out_of_memory();
    large->size = size;
    large->next = zone->large;
    __atomic_store_n(&zone->large, large, __ATOMIC_RELEASE);
    return (void *)(large + 1);
}

void scalanative_unsafe_zone_close(UnsafeZonePage *zone) {
    UnsafeZonePage *head = zone->head;
    UnsafeZoneLarge *large = zone->large;

    pool_lock();
    // Unregister first, the GC no longer scans this zone afterwards.
    UnsafeZonePage *prev = zone->prevZone, *next = zone->nextZone;
    __atomic_store_n(&prev->nextZone, next, __ATOMIC_RELEASE);
    if (next != NULL)
        next->prevZone = prev;
    // The zone record is the last page of the chain [head, ..., zone].
    zone->next = freePages;
    freePages = head;
    pool_unlock();

    while (large != NULL) {
        UnsafeZoneLarge *nextLarge = large->next;
        free(large);
        large = nextLarge;
    }
}

void scalanative_unsafe_zone_free_pool(void) {
    pool_lock();
    UnsafeZoneChunk *chunk = chunks;
    while (chunk != NULL) {
        UnsafeZoneChunk *next = chunk->next;
        free(chunk->memory);
        free(chunk->pages);
        free(chunk);
        chunk = next;
    }
    chunks = NULL;
    freePages = NULL;
    // Pages of zones still open are gone, stop scanning them.
    openZones.nextZone = NULL;
    pool_unlock();
}

void scalanative_unsafe_zone_foreach_root(UnsafeZone_RangeVisitor visit,
                                          void *ctx) {
    UnsafeZonePage *zone =
        __atomic_load_n(&openZones.nextZone, __ATOMIC_ACQUIRE);
    for (; zone != NULL;
         zone = __atomic_load_n(&zone->nextZone, __ATOMIC_ACQUIRE)) {
        UnsafeZonePage *page = __atomic_load_n(&zone->head, __ATOMIC_ACQUIRE);
        while (page != NULL) {
            size_t used = page->offset;
            if (used > SCALANATIVE_ZONE_PAGE_SIZE)
                used = SCALANATIVE_ZONE_PAGE_SIZE;
            if (used > 0)
                visit(ctx, page->start, page->start + used);
            if (page == zone)
                break;
            page = page->next;
        }
        UnsafeZoneLarge *large =
            __atomic_load_n(&zone->large, __ATOMIC_ACQUIRE);
        for (; large != NULL; large = large->next) {
            uint8_t *start = (uint8_t *)(large + 1);
            visit(ctx, start, start + large->size);
        }
    }
}
