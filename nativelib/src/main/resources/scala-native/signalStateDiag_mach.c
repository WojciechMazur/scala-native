// Repro-only diagnostics: page protection query without ThreadUtil.h typedefs.
#if defined(__APPLE__)
#include <mach/mach.h>
#include <mach/mach_vm.h>

// Returns protection of the page at addr, -1 if unmapped/unknown.
int scalanative_diag_page_protection(void *addr) {
    mach_vm_address_t a = (mach_vm_address_t)addr;
    mach_vm_size_t size = 0;
    vm_region_basic_info_data_64_t info;
    mach_msg_type_number_t count = VM_REGION_BASIC_INFO_COUNT_64;
    mach_port_t obj = MACH_PORT_NULL;
    kern_return_t kr =
        mach_vm_region(mach_task_self(), &a, &size, VM_REGION_BASIC_INFO_64,
                       (vm_region_info_t)&info, &count, &obj);
    if (kr != KERN_SUCCESS || a > (mach_vm_address_t)addr)
        return -1;
    return info.protection;
}
#else
int scalanative_diag_page_protection(void *addr) {
    (void)addr;
    return -2;
}
#endif
