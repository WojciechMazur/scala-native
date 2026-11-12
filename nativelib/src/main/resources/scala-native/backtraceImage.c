#if defined(SCALANATIVE_COMPILE_ALWAYS) ||                                     \
    defined(__SCALANATIVE_BACKTRACE_IMAGE)

// Before any header: glibc declares dl_iterate_phdr only for GNU sources
#if defined(__linux__) && !defined(_GNU_SOURCE)
#define _GNU_SOURCE
#endif

#include <stdint.h>

#define SCALANATIVE_IMAGE_UNKNOWN 0
#define SCALANATIVE_IMAGE_EXECUTABLE 1
#define SCALANATIVE_IMAGE_LIBRARY 2

#if defined(__APPLE__) && defined(__MACH__)
#include <dlfcn.h>
#include <mach-o/dyld.h>

// Finds the image of `address`: its path, and the bias to subtract from
// addresses of the image to get the addresses of its debug information.
int scalanative_backtrace_image(int64_t address, const char **path,
                                int64_t *bias) {
    Dl_info info;
    if (dladdr((const void *)(uintptr_t)address, &info) == 0 ||
        info.dli_fbase == NULL)
        return SCALANATIVE_IMAGE_UNKNOWN;
    for (uint32_t i = 0; i < _dyld_image_count(); i++) {
        if ((const void *)_dyld_get_image_header(i) == info.dli_fbase) {
            *path = info.dli_fname;
            *bias = (int64_t)_dyld_get_image_vmaddr_slide(i);
            return info.dli_fbase == (const void *)_dyld_get_image_header(0)
                       ? SCALANATIVE_IMAGE_EXECUTABLE
                       : SCALANATIVE_IMAGE_LIBRARY;
        }
    }
    return SCALANATIVE_IMAGE_UNKNOWN;
}

#elif defined(__linux__)
#include <link.h>
#include <stddef.h>

struct ImageSearch {
    uintptr_t address;
    const char *path;
    int64_t bias;
    int kind;
};

static int find_image(struct dl_phdr_info *info, size_t size, void *data) {
    struct ImageSearch *search = (struct ImageSearch *)data;
    for (int i = 0; i < info->dlpi_phnum; i++) {
        const ElfW(Phdr) *header = &info->dlpi_phdr[i];
        uintptr_t start = info->dlpi_addr + header->p_vaddr;
        if (header->p_type == PT_LOAD && search->address >= start &&
            search->address < start + header->p_memsz) {
            search->path = info->dlpi_name;
            search->bias = (int64_t)info->dlpi_addr;
            // The executable comes first, without a name
            search->kind = info->dlpi_name == NULL || info->dlpi_name[0] == 0
                               ? SCALANATIVE_IMAGE_EXECUTABLE
                               : SCALANATIVE_IMAGE_LIBRARY;
            return 1;
        }
    }
    return 0;
}

// Finds the image of `address`: its path, and the bias to subtract from
// addresses of the image to get the addresses of its debug information.
int scalanative_backtrace_image(int64_t address, const char **path,
                                int64_t *bias) {
    struct ImageSearch search = {(uintptr_t)address, NULL, 0,
                                 SCALANATIVE_IMAGE_UNKNOWN};
    dl_iterate_phdr(find_image, &search);
    if (search.kind != SCALANATIVE_IMAGE_UNKNOWN) {
        *path = search.path;
        *bias = search.bias;
    }
    return search.kind;
}

#else

// Other platforms read the debug information of the executable only
int scalanative_backtrace_image(int64_t address, const char **path,
                                int64_t *bias) {
    return SCALANATIVE_IMAGE_UNKNOWN;
}

#endif
#endif
