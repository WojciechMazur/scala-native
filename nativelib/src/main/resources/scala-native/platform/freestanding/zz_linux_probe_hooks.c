#ifdef SCALANATIVE_FREESTANDING
// Measurement harness for PR 5187 only: the six platform hooks and _start,
// over raw x86_64 Linux syscalls ("static Linux process" scenario of #5188).

#include <stddef.h>
#include <stdint.h>

_Noreturn void scalanative_freestanding_start(int argc, char **argv);

#define HEAP_REGION ((size_t)1 << 31)
#define STACK_SIZE ((size_t)8 << 20)

static inline long sys6(long n, long a, long b, long c, long d, long e,
                        long f) {
    long ret;
    register long r10 __asm__("r10") = d;
    register long r8 __asm__("r8") = e;
    register long r9 __asm__("r9") = f;
    __asm__ volatile("syscall"
                     : "=a"(ret)
                     : "a"(n), "D"(a), "S"(b), "d"(c), "r"(r10), "r"(r8),
                       "r"(r9)
                     : "rcx", "r11", "memory");
    return ret;
}

static uintptr_t stack_top;

void scalanative_platform_console_write(int stream, const char *buf,
                                        size_t len) {
    sys6(1 /* write */, stream, (long)buf, (long)len, 0, 0, 0);
}

static int64_t clock_ns(int clk) {
    long ts[2];
    sys6(228 /* clock_gettime */, clk, (long)ts, 0, 0, 0, 0);
    return ts[0] * 1000000000 + ts[1];
}

int64_t scalanative_platform_monotonic_nanos(void) { return clock_ns(1); }
int64_t scalanative_platform_realtime_nanos(void) { return clock_ns(0); }

void scalanative_platform_memory(void **base, size_t *size) {
    static void *region;
    if (!region)
        region = (void *)sys6(9 /* mmap */, 0, HEAP_REGION, 3 /* RW */,
                              0x22 /* PRIVATE|ANON */, -1, 0);
    *base = region;
    *size = HEAP_REGION;
}

void scalanative_platform_stack(void **base, size_t *size) {
    *base = (void *)(stack_top - STACK_SIZE);
    *size = STACK_SIZE;
}

_Noreturn void scalanative_platform_exit(int status) {
    for (;;)
        sys6(231 /* exit_group */, status, 0, 0, 0, 0, 0);
}

__attribute__((used)) _Noreturn void probe_cstart(long *sp) {
    stack_top = ((uintptr_t)sp + 4095) & ~(uintptr_t)4095;
    scalanative_freestanding_start((int)sp[0], (char **)(sp + 1));
}

__asm__(".globl _start\n"
        ".type _start,@function\n"
        "_start:\n"
        "  xor %ebp,%ebp\n"
        "  mov %rsp,%rdi\n"
        "  and $-16,%rsp\n"
        "  call probe_cstart\n"
        "  hlt\n");

#endif
