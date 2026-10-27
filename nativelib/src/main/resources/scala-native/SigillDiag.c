/* Records the faulting instruction on SIGILL.
 * The default action exits 132 with no PC. A libunwind handler can itself
 * execute an illegal instruction and hide the original site. Writes below
 * are async-signal-safe; the handler then _exit's so the fault is not rerun.
 */
#if defined(__APPLE__)
#ifndef _DARWIN_C_SOURCE
#define _DARWIN_C_SOURCE 1
#endif
#ifndef _XOPEN_SOURCE
#define _XOPEN_SOURCE 700
#endif
#endif

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

/* Same layout as nativeThreadTLS.h ThreadInfo. Do not include that header:
 * it pulls ThreadUtil.h, whose thread_t collides with <mach/mach.h>. */
typedef struct ThreadInfo {
    size_t stackSize;
    size_t maxStackSize;
    void *stackTop;
    void *stackBottom;
    void *stackGuardPage;
    bool isMainThread;
    bool isInitialized;
    bool pendingStackOverflowException;
#ifndef _WIN32
    void *signalHandlerStack;
    size_t signalHandlerStackSize;
#endif
} ThreadInfo;

#if defined(SCALANATIVE_MULTITHREADING_ENABLED)
#define DIAG_TLS _Thread_local
#else
#define DIAG_TLS
#endif

extern DIAG_TLS void *currentThread;
extern DIAG_TLS void *currentNativeThread;
extern DIAG_TLS ThreadInfo currentThreadInfo;

void scalanative_sigill_diag_install(void);

#if defined(__APPLE__) && defined(__aarch64__)
#include <dlfcn.h>
#include <fcntl.h>
#include <mach/mach.h>
#include <pthread.h>
#include <signal.h>
#include <string.h>
#include <sys/ucontext.h>
#include <unistd.h>
#if __has_feature(ptrauth_calls)
#include <ptrauth.h>
#endif

static int diag_log_fd = -1;

typedef struct {
    char b[512];
    int n;
} DiagBuf;

static void buf_reset(DiagBuf *b) { b->n = 0; }

static void buf_char(DiagBuf *b, char c) {
    if (b->n < (int)sizeof(b->b) - 2)
        b->b[b->n++] = c;
}

static void buf_str(DiagBuf *b, const char *s) {
    if (s == NULL)
        return;
    while (*s)
        buf_char(b, *s++);
}

static void buf_hex(DiagBuf *b, unsigned long long v, int digits) {
    static const char *hex = "0123456789abcdef";
    for (int i = digits - 1; i >= 0; i--)
        buf_char(b, hex[(v >> (i * 4)) & 0xf]);
}

static void buf_u(DiagBuf *b, unsigned v) {
    char tmp[16];
    int n = 0;
    if (v == 0) {
        buf_char(b, '0');
        return;
    }
    while (v && n < (int)sizeof(tmp)) {
        tmp[n++] = (char)('0' + (v % 10));
        v /= 10;
    }
    while (n > 0)
        buf_char(b, tmp[--n]);
}

static void buf_flush(DiagBuf *b) {
    if (b->n < (int)sizeof(b->b))
        b->b[b->n++] = '\n';
    if (b->n <= 0)
        return;
    (void)write(STDERR_FILENO, b->b, (size_t)b->n);
    if (diag_log_fd >= 0)
        (void)write(diag_log_fd, b->b, (size_t)b->n);
    b->n = 0;
}

static int diag_read(uintptr_t addr, void *dst, size_t n) {
    if (addr < 0x1000 || n == 0)
        return 0;
    vm_size_t out = 0;
    kern_return_t kr = vm_read_overwrite(mach_task_self(), (vm_address_t)addr,
                                         (vm_size_t)n, (vm_address_t)dst, &out);
    return kr == KERN_SUCCESS && out == n;
}

static uintptr_t diag_strip(uintptr_t addr) {
#if __has_feature(ptrauth_calls)
    return (uintptr_t)ptrauth_strip((void *)addr, ptrauth_key_return_address);
#else
    /* Top byte may hold a PAC signature on arm64e frames. */
    return addr & 0x00ffffffffffffffULL;
#endif
}

#ifdef SCALANATIVE_MULTITHREADING_ENABLED
#define DIAG_OBJ_HDR 16u
#define DIAG_RTTI_NAME 32u
#define DIAG_ARR_VALUES 24u
#else
#define DIAG_OBJ_HDR 8u
#define DIAG_RTTI_NAME 24u
#define DIAG_ARR_VALUES 16u
#endif

/* Class name via RTTI string. Layout matches ObjectHeader.h / java.lang.String.
 * Failure leaves "?"; this must not fault (vm_read, no libc string alloc). */
static void diag_class_name(uintptr_t obj, char *out, int cap) {
    if (cap < 2) {
        if (cap == 1)
            out[0] = 0;
        return;
    }
    out[0] = '?';
    out[1] = 0;
    uintptr_t rtti = 0;
    if (!diag_read(obj, &rtti, sizeof(rtti)))
        return;
    uintptr_t name = 0;
    if (!diag_read(rtti + DIAG_RTTI_NAME, &name, sizeof(name)))
        return;
    uintptr_t chars = 0;
    if (!diag_read(name + DIAG_OBJ_HDR, &chars, sizeof(chars)))
        return;
    int32_t arr_len = 0;
    int32_t str_off = 0;
    int32_t str_count = 0;
    if (!diag_read(chars + (DIAG_ARR_VALUES - 8u), &arr_len, sizeof(arr_len)))
        return;
    if (!diag_read(name + DIAG_OBJ_HDR + 8u, &str_off, sizeof(str_off)))
        return;
    if (!diag_read(name + DIAG_OBJ_HDR + 12u, &str_count, sizeof(str_count)))
        return;
    if (arr_len < 0 || arr_len > 100000 || str_off < 0 || str_count < 0)
        return;
    if (str_off > arr_len)
        return;
    if (str_count > arr_len - str_off)
        str_count = arr_len - str_off;
    if (str_count > 96)
        str_count = 96;
    if (str_count <= 0 || str_count >= cap)
        return;
    uint16_t tmp[96];
    if (!diag_read(chars + DIAG_ARR_VALUES + (uintptr_t)str_off * 2u, tmp,
                   (size_t)str_count * 2u))
        return;
    for (int i = 0; i < str_count; i++) {
        uint16_t c = tmp[i];
        out[i] = (c >= 32 && c < 127) ? (char)c : '?';
    }
    out[str_count] = 0;
}

static int diag_contains(const char *s, const char *needle) {
    if (s == NULL || needle == NULL)
        return 0;
    for (int i = 0; s[i]; i++) {
        int j = 0;
        while (needle[j] && s[i + j] == needle[j])
            j++;
        if (needle[j] == 0)
            return 1;
    }
    return 0;
}

static void buf_insn(DiagBuf *b, uint32_t w, int ok) {
    if (!ok) {
        buf_str(b, "unreadable");
        return;
    }
    buf_str(b, "0x");
    buf_hex(b, w, 8);
    buf_char(b, ' ');
    if (w == 0xdac1bffe)
        buf_str(b, "autib171615");
    else if (w == 0x04e0e3e0)
        buf_str(b, "cntd x0");
    else if (w == 0xD50321DFu)
        buf_str(b, "hint#0xe autib1716");
    else if (w == 0xD503219Fu)
        buf_str(b, "hint#0xc autia1716");
    else if (w == 0xD50324FFu)
        buf_str(b, "hint#0x27 pacm");
    else if ((w & 0xFFE0001Fu) == 0xD4200000u)
        buf_str(b, "brk");
    else if ((w & 0xFFFFFC1Fu) == 0xD65F0000u) {
        buf_str(b, "ret x");
        buf_u(b, (w >> 5) & 31u);
    } else if ((w & 0xFFFFFC1Fu) == 0xD61F0000u) {
        buf_str(b, "br x");
        buf_u(b, (w >> 5) & 31u);
    } else if ((w & 0xFFFFFC1Fu) == 0xD63F0000u) {
        buf_str(b, "blr x");
        buf_u(b, (w >> 5) & 31u);
    } else if ((w >> 16) == 0)
        buf_str(b, "udf");
    else if ((w & 0xFFFFF01Fu) == 0xD503201Fu)
        buf_str(b, "hint");
    else
        buf_str(b, "op");
}

static const char *diag_ec_name(unsigned ec) {
    switch (ec) {
    case 0x00:
        return "unknown";
    case 0x09:
        return "pauth";
    case 0x0D:
        return "bti";
    case 0x0E:
        return "illegal-state";
    case 0x20:
        return "iabort-lower";
    case 0x21:
        return "iabort";
    case 0x22:
        return "pc-align";
    case 0x26:
        return "sp-align";
    default:
        return "other";
    }
}

static void diag_image(DiagBuf *b, const char *label, uintptr_t addr) {
    buf_reset(b);
    buf_str(b, "SN SIGILL ");
    buf_str(b, label);
    buf_str(b, " 0x");
    buf_hex(b, addr, 16);
    Dl_info info;
    uintptr_t used = addr;
    int hit = dladdr((void *)addr, &info);
    if (!hit) {
        used = diag_strip(addr);
        if (used != addr)
            hit = dladdr((void *)used, &info);
    }
    if (!hit || info.dli_fname == NULL) {
        buf_str(b, " image=?");
        buf_flush(b);
        return;
    }
    buf_str(b, " image=");
    buf_str(b, info.dli_fname);
    buf_str(b, " off=0x");
    buf_hex(b, used - (uintptr_t)info.dli_fbase, 16);
    if (info.dli_sname != NULL) {
        buf_str(b, " sym=");
        buf_str(b, info.dli_sname);
        buf_str(b, "+0x");
        buf_hex(b, used - (uintptr_t)info.dli_saddr, 8);
    }
    buf_flush(b);
}

extern uintptr_t scalanative_sigill_diag_resume_pc(void);
extern uintptr_t scalanative_sigill_diag_trampoline(void);

static void sn_sigill_handler(int sig, siginfo_t *info, void *uap) {
    (void)sig;
    DiagBuf buf;
    buf_reset(&buf);

    uintptr_t pc = 0, lr = 0, fp = 0, sp = 0, x16 = 0;
    uint32_t cpsr = 0;
    uint64_t esr = 0, far = 0;
    if (uap != NULL) {
        ucontext_t *uc = (ucontext_t *)uap;
        _STRUCT_MCONTEXT64 *mc = uc->uc_mcontext;
        if (mc != NULL) {
            _STRUCT_ARM_THREAD_STATE64 ss = mc->__ss;
            pc = (uintptr_t)__darwin_arm_thread_state64_get_pc(ss);
            lr = (uintptr_t)__darwin_arm_thread_state64_get_lr(ss);
            fp = (uintptr_t)__darwin_arm_thread_state64_get_fp(ss);
            sp = (uintptr_t)__darwin_arm_thread_state64_get_sp(ss);
            x16 = (uintptr_t)ss.__x[16];
            cpsr = ss.__cpsr;
            esr = mc->__es.__esr;
            far = mc->__es.__far;
        }
    }

    uint32_t insn = 0;
    int insn_ok = diag_read(pc, &insn, sizeof(insn));
    unsigned ec = (unsigned)((esr >> 26) & 0x3f);

    buf_str(&buf, "SN SIGILL pid=");
    buf_u(&buf, (unsigned)getpid());
    buf_str(&buf, " sig=");
    buf_u(&buf, (unsigned)sig);
    buf_str(&buf, " si_code=");
    buf_u(&buf, info != NULL ? (unsigned)info->si_code : 0u);
    buf_str(&buf, " si_addr=0x");
    buf_hex(&buf, info != NULL ? (uintptr_t)info->si_addr : 0, 16);
    buf_str(&buf, " pc=0x");
    buf_hex(&buf, pc, 16);
    buf_str(&buf, " insn=");
    buf_insn(&buf, insn, insn_ok);
    buf_flush(&buf);

    buf_reset(&buf);
    buf_str(&buf, "SN SIGILL lr=0x");
    buf_hex(&buf, lr, 16);
    buf_str(&buf, " fp=0x");
    buf_hex(&buf, fp, 16);
    buf_str(&buf, " sp=0x");
    buf_hex(&buf, sp, 16);
    buf_str(&buf, " x16=0x");
    buf_hex(&buf, x16, 16);
    buf_str(&buf, " cpsr=0x");
    buf_hex(&buf, cpsr, 8);
    buf_str(&buf, " nzcv=");
    buf_u(&buf, (cpsr >> 28) & 0xfu);
    buf_str(&buf, " esr=0x");
    buf_hex(&buf, esr, 8);
    buf_str(&buf, " ec=");
    buf_u(&buf, ec);
    buf_char(&buf, ' ');
    buf_str(&buf, diag_ec_name(ec));
    buf_str(&buf, " far=0x");
    buf_hex(&buf, far, 16);
    buf_flush(&buf);

    uintptr_t resume = scalanative_sigill_diag_resume_pc();
    uintptr_t tramp = scalanative_sigill_diag_trampoline();
    buf_reset(&buf);
    buf_str(&buf, "SN SIGILL resume_pc=0x");
    buf_hex(&buf, resume, 16);
    buf_str(&buf, " trampoline=0x");
    buf_hex(&buf, tramp, 16);
    if (tramp != 0 && pc >= tramp) {
        buf_str(&buf, " pc_minus_tramp=0x");
        buf_hex(&buf, pc - tramp, 8);
    }
    buf_str(&buf, " pthread=0x");
    buf_hex(&buf, (uintptr_t)pthread_self(), 16);
    buf_flush(&buf);

    diag_image(&buf, "pc", pc);
    diag_image(&buf, "lr", lr);

    char java_name[128];
    char native_name[128];
    char carrier_name[128];
    java_name[0] = '?';
    java_name[1] = 0;
    native_name[0] = '?';
    native_name[1] = 0;
    carrier_name[0] = '?';
    carrier_name[1] = 0;
    uintptr_t java = (uintptr_t)currentThread;
    uintptr_t native = (uintptr_t)currentNativeThread;
    if (java >= 0x1000)
        diag_class_name(java, java_name, (int)sizeof(java_name));
    if (native >= 0x1000) {
        diag_class_name(native, native_name, (int)sizeof(native_name));
        uintptr_t carrier = 0;
        if (diag_read(native + DIAG_OBJ_HDR, &carrier, sizeof(carrier)) &&
            carrier >= 0x1000)
            diag_class_name(carrier, carrier_name, (int)sizeof(carrier_name));
    }
    const char *kind = "unknown";
    if (diag_contains(java_name, "VirtualThread"))
        kind = "virtual";
    else if (java_name[0] != '?')
        kind = "platform";

    buf_reset(&buf);
    buf_str(&buf, "SN SIGILL thread kind=");
    buf_str(&buf, kind);
    buf_str(&buf, " main=");
    buf_u(&buf, currentThreadInfo.isMainThread ? 1u : 0u);
    buf_str(&buf, " java=0x");
    buf_hex(&buf, java, 16);
    buf_str(&buf, " class=");
    buf_str(&buf, java_name);
    buf_str(&buf, " native=0x");
    buf_hex(&buf, native, 16);
    buf_str(&buf, " class=");
    buf_str(&buf, native_name);
    buf_str(&buf, " carrier=");
    buf_str(&buf, carrier_name);
    buf_flush(&buf);

    buf_reset(&buf);
    buf_str(&buf, "SN SIGILL stack top=0x");
    buf_hex(&buf, (uintptr_t)currentThreadInfo.stackTop, 16);
    buf_str(&buf, " bottom=0x");
    buf_hex(&buf, (uintptr_t)currentThreadInfo.stackBottom, 16);
    stack_t alt;
    if (sigaltstack(NULL, &alt) == 0) {
        buf_str(&buf, " alt_sp=0x");
        buf_hex(&buf, (uintptr_t)alt.ss_sp, 16);
        buf_str(&buf, " alt_flags=0x");
        buf_hex(&buf, (unsigned)alt.ss_flags, 8);
    }
    buf_flush(&buf);

    uintptr_t frame = fp;
    for (int i = 0; i < 24; i++) {
        uintptr_t pair[2];
        if (!diag_read(frame, pair, sizeof(pair)))
            break;
        buf_reset(&buf);
        buf_str(&buf, "SN SIGILL frame ");
        buf_u(&buf, (unsigned)i);
        buf_str(&buf, " fp=0x");
        buf_hex(&buf, frame, 16);
        buf_str(&buf, " lr=0x");
        buf_hex(&buf, pair[1], 16);
        buf_flush(&buf);
        diag_image(&buf, "frame", pair[1]);
        uintptr_t next = pair[0];
        if (next < 0x1000 || next == frame)
            break;
        frame = next;
    }

    _exit(128 + SIGILL);
}

__attribute__((constructor)) static void sn_sigill_diag_ctor(void) {
    scalanative_sigill_diag_install();
}
#endif /* Apple aarch64 */

void scalanative_sigill_diag_install(void) {
#if defined(__APPLE__) && defined(__aarch64__)
    if (diag_log_fd < 0) {
        diag_log_fd = open("/tmp/sn-sigill-diag.txt",
                           O_WRONLY | O_CREAT | O_APPEND, 0644);
    }
    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_sigaction = sn_sigill_handler;
    sigemptyset(&sa.sa_mask);
    /* SA_ONSTACK uses the alt stack installed by stack-overflow guards.
     * Delivery falls back to the faulting stack when none is set. */
    sa.sa_flags = SA_SIGINFO | SA_ONSTACK;
    (void)sigaction(SIGILL, &sa, NULL);
#endif
}

#ifdef SN_SIGILL_DIAG_SELFTEST
uintptr_t scalanative_sigill_diag_resume_pc(void) { return 0; }
uintptr_t scalanative_sigill_diag_trampoline(void) { return 0; }
DIAG_TLS void *currentThread = 0;
DIAG_TLS void *currentNativeThread = 0;
DIAG_TLS ThreadInfo currentThreadInfo = {0};

int main(void) {
    scalanative_sigill_diag_install();
    __asm__ volatile(".inst 0x00000000");
    return 0;
}
#endif
