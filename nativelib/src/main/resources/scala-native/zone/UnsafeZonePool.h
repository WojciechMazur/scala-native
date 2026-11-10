#ifndef SCALANATIVE_UNSAFE_ZONE_POOL_H
#define SCALANATIVE_UNSAFE_ZONE_POOL_H

// Page pool backing `scala.scalanative.unsafe.Zone` (MemoryPoolZone).
//
// Memory handed out by a zone is visible to the garbage collector: the GC
// conservatively scans the used prefix [start, start + offset) of every page
// claimed by a currently open zone, plus every large allocation of an open
// zone. Free pages and the unused tail of a page are never scanned.
//
// Bookkeeping is designed so that the collector can walk it without taking
// the pool lock (mutators are stopped while roots are marked, and every
// mutation keeps the structures traversable at each step), and so that zone
// open/close cost O(1) under one short critical section.

#include <stddef.h>
#include <stdint.h>

#define SCALANATIVE_ZONE_PAGE_SIZE 4096
#define SCALANATIVE_ZONE_MIN_CHUNK_PAGES 8
#define SCALANATIVE_ZONE_MAX_CHUNK_PAGES 256

// Header prepended to each large (malloc'ed) zone allocation. Aligned to 16
// bytes so that the payload keeps malloc's alignment guarantees.
typedef struct __attribute__((aligned(16))) UnsafeZoneLarge {
    struct UnsafeZoneLarge *next;
    size_t size;
} UnsafeZoneLarge;

// Descriptor of a single page. Descriptors live in a side array allocated
// together with each chunk and are never freed while the pool is alive.
//
// The first page claimed by a zone doubles as the zone record: `head`,
// `large` and the `prevZone`/`nextZone` links are only meaningful there.
// Layout is mirrored by `UnsafeZonePool.Page` in Scala - keep in sync.
typedef struct UnsafeZonePage {
    uint8_t *start; // first byte of the page, immutable
    size_t offset;  // number of used bytes, scanned by the GC
    // zone page chain (newest first, ends at the zone record) or free list
    struct UnsafeZonePage *next;
    // -- zone record only --
    struct UnsafeZonePage *head; // newest page of the zone
    UnsafeZoneLarge *large;      // large allocations of the zone
    struct UnsafeZonePage *prevZone;
    struct UnsafeZonePage *nextZone;
} UnsafeZonePage;

// Open a zone: claim its first page and register it as a GC root.
UnsafeZonePage *scalanative_unsafe_zone_open(void);
// Claim a fresh page and make it the new head of the zone.
UnsafeZonePage *scalanative_unsafe_zone_add_page(UnsafeZonePage *zone);
// Allocate `size` bytes using the system allocator, owned by the zone.
void *scalanative_unsafe_zone_alloc_large(UnsafeZonePage *zone, size_t size);
// Unregister the zone, return its pages to the pool, free large allocations.
void scalanative_unsafe_zone_close(UnsafeZonePage *zone);
// Release all memory held by the pool (used at exit when ASan is enabled).
void scalanative_unsafe_zone_free_pool(void);

// Invoke `visit` for each memory range of open zones that may contain
// references. Must only be called while mutators are stopped.
typedef void (*UnsafeZone_RangeVisitor)(void *ctx, void *low, void *high);
void scalanative_unsafe_zone_foreach_root(UnsafeZone_RangeVisitor visit,
                                          void *ctx);

#endif
