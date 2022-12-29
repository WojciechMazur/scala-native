#include "Safepoint.h"
#include <stdio.h>
#include <stdlib.h>
#include <errno.h>
#include "MemoryMap.h"

#ifdef _WIN32
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#else // Unix
#include <sys/mman.h>
#endif

void Safepoint_init(safepoint_ref *ref) {
    void *addr =
#ifdef _WIN32
        VirtualAlloc(NULL, sizeof(safepoint_t), MEM_RESERVE | MEM_COMMIT,
                     PAGE_READONLY);
#else
        mmap(NULL, sizeof(safepoint_t), PROT_READ,
             MAP_NORESERVE | MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
#endif
    if (addr > (void *)0) {
        *ref = (safepoint_ref)addr;
    } else {
        perror("Failed to create GC safepoint trap\n");
        exit(errno);
    }
}

void Safepoint_arm(safepoint_ref ref) {
    bool success;
#ifdef _WIN32
    DWORD oldAccess;
    success = VirtualProtect((LPVOID)ref, sizeof(safepoint_t), PAGE_NOACCESS,
                             &oldAccess);
#else
    success = mprotect((void *)ref, sizeof(safepoint_t), PROT_NONE) == 0;
#endif
    if (!success) {
        perror("Failed to enable GC collect trap\n");
        exit(errno);
    }
}

void Safepoint_disarm(safepoint_ref ref) {
    bool success;
#ifdef _WIN32
    DWORD oldAccess;
    success = VirtualProtect((LPVOID)ref, sizeof(safepoint_t), PAGE_READONLY,
                             &oldAccess);
#else
    success = mprotect((void *)ref, sizeof(safepoint_t), PROT_READ) == 0;
#endif
    if (!success) {
        perror("Failed to disable GC collect trap\n");
        exit(errno);
    }
}