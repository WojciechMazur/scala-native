#if !defined(_WIN32) && (defined(__aarch64__) || defined(__x86_64__))
#include <stddef.h>

// 0.5.x: scalanative_GC_collect
// 0.4.x: compile with -DSN_COLLECT=scalanative_collect
#ifndef SN_COLLECT
#define SN_COLLECT scalanative_GC_collect
#endif
extern void SN_COLLECT(void);

#define STRINGIFY(x) #x
#define SYMBOL_(prefix, name) STRINGIFY(prefix) #name
#define SYMBOL(name) SYMBOL_(__USER_LABEL_PREFIX__, name)
#if defined(__x86_64__) && !defined(__APPLE__)
#define CALL(name) "call " SYMBOL(name) "@PLT\n"
#elif defined(__x86_64__)
#define CALL(name) "call " SYMBOL(name) "\n"
#else
#define CALL(name) "bl " SYMBOL(name) "\n"
#endif

__attribute__((noinline, used)) void sn3172_collect_in_deep_frame(void) {
    volatile char pad[16 * 1024];
    for (size_t i = 0; i < sizeof(pad); i++)
        pad[i] = 0;
    SN_COLLECT();
    __asm__ volatile("");
}

int sn3172_supported(void) { return 1; }

// clang-format off
// Moves *slot into a callee-saved register only (r13 / x28), clears *slot,
// runs a GC on this thread and stores the register back into *slot.
__attribute__((naked)) void sn3172_hold_in_callee_saved(void **slot) {
#if defined(__aarch64__)
    __asm__ volatile("stp x29, x30, [sp, #-32]!\n"
                     "mov x29, sp\n"
                     "stp x27, x28, [sp, #16]\n"
                     "mov x27, x0\n"
                     "ldr x28, [x0]\n"
                     "str xzr, [x0]\n" CALL(sn3172_collect_in_deep_frame)
                     "str x28, [x27]\n"
                     "ldp x27, x28, [sp, #16]\n"
                     "ldp x29, x30, [sp], #32\n"
                     "ret\n");
#else
    __asm__ volatile("pushq %rbp\n"
                     "movq %rsp, %rbp\n"
                     "pushq %r13\n"
                     "pushq %rbx\n"
                     "movq %rdi, %rbx\n"
                     "movq (%rdi), %r13\n"
                     "movq $0, (%rdi)\n" CALL(sn3172_collect_in_deep_frame)
                     "movq %r13, (%rbx)\n"
                     "popq %rbx\n"
                     "popq %r13\n"
                     "popq %rbp\n"
                     "ret\n");
#endif
}
// clang-format on
#else
int sn3172_supported(void) { return 0; }
void sn3172_hold_in_callee_saved(void **slot) { (void)slot; }
#endif
