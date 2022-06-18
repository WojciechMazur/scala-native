#include "Safepoint.h"
#include <stdio.h>
#include <stdlib.h>
#include <errno.h>
#include <sys/mman.h>

void Safepoint_init(safepoint_ref *ref) {
    *ref =
        (safepoint_ref)mmap(NULL, sizeof(safepoint_t), PROT_READ,
                            MAP_NORESERVE | MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    if (*ref == (safepoint_t *)-1) {
        perror("Failed to create GC safepoint trap\n");
        exit(errno);
    }
}

void Safepoint_arm(safepoint_ref ref) {
    if (mprotect((void *)ref, sizeof(safepoint_t), PROT_NONE)) {
        perror("Failed to enable GC collect trap\n");
        exit(errno);
    }
}

void Safepoint_disarm(safepoint_ref ref) {
    if (mprotect((void *)ref, sizeof(safepoint_t), PROT_READ)) {
        perror("Failed to disable GC safepoint trap\n");
        exit(errno);
    }
}