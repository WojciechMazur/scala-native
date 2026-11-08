#ifdef _WIN32
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <malloc.h>
#else // Unix
#include <sys/resource.h>
#include <sys/mman.h>
#include <signal.h>
#include <unistd.h>
#endif

#include "string_constants.h"
#include "stackOverflowGuards.h"
#include "nativeThreadTLS.h"
#include "StackTrace.h"
#include <stdio.h>
#include <stdlib.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <assert.h>
#include <string.h>

__attribute((noreturn)) extern void scalanative_throwStackOverflowError();

size_t scalanative_StackOverflowGuards_size() {
    return stackGuardPages() * resolvePageSize();
}

#ifdef _WIN32
static LONG WINAPI stackOverflowHandler(PEXCEPTION_POINTERS ex) {
    switch (ex->ExceptionRecord->ExceptionCode) {
    case EXCEPTION_STACK_OVERFLOW:
        /* Do not throw from VEH: C++ unwind from this context skips
         * synchronized/finally cleanup (nested PrintStream monitors). The OS
         * has already committed the guarantee pages, so CONTINUE_EXECUTION
         * lets the faulting access succeed; StackOverflowGuards_check throws
         * on the next polled method entry with a normal call stack. */
        currentThreadInfo.pendingStackOverflowException = true;
        return EXCEPTION_CONTINUE_EXECUTION;
    default:
        return EXCEPTION_CONTINUE_SEARCH;
    }
}
void scalanative_StackOverflowGuards_setup(bool isMainThread) {
    static bool isHandlerConfigured = false;
    if (isMainThread && !isHandlerConfigured) {
        AddVectoredExceptionHandler(0, &stackOverflowHandler);
        isHandlerConfigured = true;
    }
    ULONG stackOverflowStackSize = scalanative_StackOverflowGuards_size();
    currentThreadInfo.stackGuardPage =
        (void *)((uintptr_t)currentThreadInfo.stackTop +
                 stackOverflowStackSize);
    currentThreadInfo.pendingStackOverflowException = false;

    if (!SetThreadStackGuarantee(&stackOverflowStackSize)) {
        fprintf(stderr,
                "%s Failed to set thread stack guarantee, "
                "stack overflow detection might not work correctly\n",
                SN_FATAL_ERROR_PREFIX);
    }
}

void scalanative_StackOverflowGuards_reset() {
    int dummy;
    void *curStackTop = &dummy;
    ThreadInfo info = currentThreadInfo;
    currentThreadInfo.pendingStackOverflowException = false;
    if (inStackPageBound(info.stackGuardPage, curStackTop))
        return; // still unwinding
    _resetstkoflw();
}

#else // Unix
// DEBUG(bsd-spurious-so): async-signal-safe tracing of guard hits.
#if defined(__FreeBSD__) || defined(__NetBSD__) || defined(__linux__)
#include <ucontext.h>
#endif
#if defined(__FreeBSD__) && defined(__x86_64__)
#define SN_DBG_UC_SP(uc) ((void *)((ucontext_t *)(uc))->uc_mcontext.mc_rsp)
#define SN_DBG_UC_PC(uc) ((void *)((ucontext_t *)(uc))->uc_mcontext.mc_rip)
#elif defined(__NetBSD__)
#define SN_DBG_UC_SP(uc) ((void *)_UC_MACHINE_SP((ucontext_t *)(uc)))
#define SN_DBG_UC_PC(uc) ((void *)_UC_MACHINE_PC((ucontext_t *)(uc)))
#elif defined(__linux__) && defined(__x86_64__)
#define SN_DBG_UC_SP(uc)                                                       \
    ((void *)((ucontext_t *)(uc))->uc_mcontext.gregs[REG_RSP])
#define SN_DBG_UC_PC(uc)                                                       \
    ((void *)((ucontext_t *)(uc))->uc_mcontext.gregs[REG_RIP])
#else
#define SN_DBG_UC_SP(uc) ((void *)0)
#define SN_DBG_UC_PC(uc) ((void *)0)
#endif

static SN_ThreadLocal char dbgLastEvent[1024];

static size_t dbgAppend(char *buf, size_t pos, size_t cap, const char *s) {
    while (*s && pos + 1 < cap)
        buf[pos++] = *s++;
    buf[pos] = 0;
    return pos;
}
static size_t dbgAppendHex(char *buf, size_t pos, size_t cap, uintptr_t v) {
    char tmp[2 + 2 * sizeof(uintptr_t) + 1];
    const char *digits = "0123456789abcdef";
    int n = 0;
    char rev[2 * sizeof(uintptr_t)];
    do {
        rev[n++] = digits[v & 0xf];
        v >>= 4;
    } while (v != 0);
    int i = 0;
    tmp[i++] = '0';
    tmp[i++] = 'x';
    while (n > 0)
        tmp[i++] = rev[--n];
    tmp[i] = 0;
    return dbgAppend(buf, pos, cap, tmp);
}
static size_t dbgField(char *buf, size_t pos, size_t cap, const char *name,
                       uintptr_t v) {
    pos = dbgAppend(buf, pos, cap, " ");
    pos = dbgAppend(buf, pos, cap, name);
    pos = dbgAppend(buf, pos, cap, "=");
    return dbgAppendHex(buf, pos, cap, v);
}

static void dbgTrace(const char *what, int sig, siginfo_t *info, void *context,
                     ThreadInfo *ti) {
    char *buf = dbgLastEvent;
    size_t cap = sizeof(dbgLastEvent);
    size_t pos = 0;
    int dummy;
    stack_t ss;
    sigaltstack(NULL, &ss);
    pos = dbgAppend(buf, pos, cap, "[SO-DEBUG] ");
    pos = dbgAppend(buf, pos, cap, what);
    pos = dbgField(buf, pos, cap, "pid", (uintptr_t)getpid());
    pos = dbgField(buf, pos, cap, "self", (uintptr_t)pthread_self());
    pos = dbgField(buf, pos, cap, "sig", (uintptr_t)sig);
    pos = dbgField(buf, pos, cap, "code", (uintptr_t)info->si_code);
    pos = dbgField(buf, pos, cap, "addr", (uintptr_t)info->si_addr);
    pos = dbgField(buf, pos, cap, "uc_sp", (uintptr_t)SN_DBG_UC_SP(context));
    pos = dbgField(buf, pos, cap, "uc_pc", (uintptr_t)SN_DBG_UC_PC(context));
    pos = dbgField(buf, pos, cap, "handler_sp", (uintptr_t)&dummy);
    pos = dbgField(buf, pos, cap, "altstack", (uintptr_t)ss.ss_sp);
    pos = dbgField(buf, pos, cap, "altsize", (uintptr_t)ss.ss_size);
    pos = dbgField(buf, pos, cap, "altflags", (uintptr_t)ss.ss_flags);
    pos = dbgField(buf, pos, cap, "ti_altstack",
                   (uintptr_t)ti->signalHandlerStack);
    pos = dbgField(buf, pos, cap, "guard", (uintptr_t)ti->stackGuardPage);
    pos = dbgField(buf, pos, cap, "top", (uintptr_t)ti->stackTop);
    pos = dbgField(buf, pos, cap, "bottom", (uintptr_t)ti->stackBottom);
    pos = dbgField(buf, pos, cap, "size", (uintptr_t)ti->stackSize);
    pos = dbgField(buf, pos, cap, "max", (uintptr_t)ti->maxStackSize);
    pos = dbgField(buf, pos, cap, "init", (uintptr_t)ti->isInitialized);
    pos = dbgField(buf, pos, cap, "pending",
                   (uintptr_t)ti->pendingStackOverflowException);
    pos = dbgAppend(buf, pos, cap, "\n");
    write(STDERR_FILENO, buf, pos);
}

static struct sigaction *resolvePreviousSignalHandler(int sig) {
    static struct sigaction previousSignalHandlers[2];
    switch (sig) {
    case SIGSEGV:
        return &previousSignalHandlers[0];
    case SIGBUS:
        return &previousSignalHandlers[1];
    default:
        fprintf(stderr,
                "%s StackOverflowHandler does not define handler "
                "for %d signal\n",
                SN_ERROR_PREFIX, sig);
        _exit(sig);
    }
}

static void protectStackGuardPage() {
    if (mprotect(currentThreadInfo.stackGuardPage, resolvePageSize(),
                 PROT_NONE) == -1) {
        perror(SN_FATAL_ERROR_MSG("StackOverflowHandler guard "
                                  "protection failed"));
        // _exit rather than exit: reachable from the signal handler.
        _exit(EXIT_FAILURE);
    }
}
static void unprotectStackGuardPage() {
    if (currentThreadInfo.stackGuardPage == NULL)
        return;
    if (mprotect(currentThreadInfo.stackGuardPage, resolvePageSize(),
                 PROT_READ | PROT_WRITE) == -1) {
        perror(SN_FATAL_ERROR_MSG("StackOverflowHandler guard "
                                  "unprotection failed"));
        // _exit rather than exit: reachable from the signal handler.
        _exit(EXIT_FAILURE);
    }
}

static bool tryGrowStack() {
    size_t curStackSize = currentThreadInfo.stackSize;
    size_t maxStackSize = currentThreadInfo.maxStackSize;
    if (curStackSize < maxStackSize) {
        size_t newSize = curStackSize + 1024 * 1024; // +512 KB
        if (newSize > maxStackSize)
            newSize = maxStackSize;
        void *newStackTop = (char *)(currentThreadInfo.stackBottom) - newSize;

        volatile char *cursor = currentThreadInfo.stackTop;
        while ((void *)cursor > newStackTop) {
            char value = *cursor;
            *cursor = value; // Write to the memory to force allocation
            cursor -= resolvePageSize();
        }
        currentThreadInfo.stackSize = newSize;
        currentThreadInfo.stackTop = newStackTop;
        return true;
    }
    return false;
}

static void setupStackOverflowGuards() {
    assert(currentThreadInfo.stackSize > 0);
    assert(currentThreadInfo.stackTop != NULL);
    assert(currentThreadInfo.stackBottom != NULL);

    currentThreadInfo.stackGuardPage =
        (void *)((uintptr_t)currentThreadInfo.stackTop +
                 scalanative_StackOverflowGuards_size());

    if (currentThreadInfo.stackGuardPage >= currentThreadInfo.stackBottom) {
        if (tryGrowStack()) {
            return setupStackOverflowGuards();
        } else {
            fprintf(stderr, "%s Cannot setup StackOverflowGuards handler\n",
                    SN_ERROR_PREFIX);
            // _exit rather than exit: reachable from the signal handler.
            _exit(EXIT_FAILURE);
        }
    }
    assert(currentThreadInfo.stackGuardPage > currentThreadInfo.stackTop);
    assert(currentThreadInfo.stackGuardPage < currentThreadInfo.stackBottom);

    protectStackGuardPage();
}

static void stackOverflowHandler(int sig, siginfo_t *info, void *context) {
    ThreadInfo threadInfo = currentThreadInfo;
    {
        stack_t dbgSs;
        if (sigaltstack(NULL, &dbgSs) == 0 && (dbgSs.ss_flags & SS_ONSTACK) &&
            currentThreadInfo.signalHandlerStack == NULL) {
            dbgTrace("freed-altstack", sig, info, context, &threadInfo);
        }
    }
    bool threadInfoInitialized = threadInfo.stackSize != 0 &&
                                 threadInfo.stackBottom && threadInfo.stackTop;
    if (!threadInfoInitialized) {
        goto dispatchDefaultSignal;
    }
    switch (sig) {
    case SIGSEGV:
    case SIGBUS:;
        void *faultAddr = info->si_addr;
        /* Fault outside this thread's stack (e.g. in delimcc heap fragment or
         * allocator) must not be treated as stack overflow. Otherwise we
         * mis-report "Unrecoverable StackOverflow" and exit.
         * Keep a pending overflow: GC safepoint traps also land here. If one
         * clears it before the next check, the guard stays unprotected and
         * the thread runs past it. */
        if (threadInfo.stackBottom != NULL && threadInfo.stackTop != NULL &&
            !isInRange(faultAddr, threadInfo.stackBottom,
                       threadInfo.stackTop)) {
            goto dispatchDefaultSignal;
        }
        /* We cannot throw exception directly from signal handler - libunwind
         * would not be able to locate catch handler.
         * In the past we've tried to workaround it with:
         * 1. Creating synthetic stack frame by modify the ucontext_t passed to
         * signal handler as `context` and continue execution in selected
         * function but throwing exceptions would not be reliable - under
         * -fomit-frame-pointer or non zero -O optimizer settings libunwind
         * might not be able to construct correct function call chain thus
         * failing to throw. In such case we would not be able to recover.
         * Without frame pointers it was needed to use alternative information
         * like DWARF for which we cannot reliably create synthetic data.
         * 2. Using signal handler context to create a synthetic unw_context /
         * unw_cursor. This was was stored in the thread local storage and used
         * on deamand instead of resolved one at the moment of invocation. These
         * allowed to successfully and mostly correctly collect stack trace
         * outside the signal handler. Also with minimal modification it could
         * have been used in a variant of _Unwind_RaiseException. Unfortunetly,
         * even though solution allowed in most cases to correctly unwind the
         * stack/execution on return it polutted the execution state (registers)
         * with incorrect data. This lead to logic errors and undefined
         * behaviours.
         * Becouse of these we introduced soft stack overflow limit - we
         * continue execution and notify runtime to throw StackOverflowError
         * lazily. Based on closed world assumptions we identify functions that
         * are recursive, only these would check if throwing StackOverflowError
         * is needed when entering the function.
         */
        if (inStackPageBound(threadInfo.stackGuardPage, faultAddr)) {
            unprotectStackGuardPage();
            if (threadInfo.stackSize < threadInfo.maxStackSize) {
                dbgTrace("grow", sig, info, context, &threadInfo);
                if (tryGrowStack()) {
                    setupStackOverflowGuards();
                    return;
                }
            }
            dbgTrace("pending", sig, info, context, &threadInfo);
            // Let the exception be thrown after polling
            currentThreadInfo.pendingStackOverflowException = true;
            return;
        } else if (isInRange(faultAddr, threadInfo.stackGuardPage,
                             (void *)(char *)threadInfo.stackTop -
                                 (resolvePageSize() + 64 * 1024)) ||
                   currentThreadInfo.pendingStackOverflowException) {
            // Unrecoverable if stack overflow is detected somewhere above
            // current stack top
            fprintf(stderr,
                    "%s Unrecoverable StackOverflow error in %s "
                    "thread, stack size = %zuKB\n",
                    SN_ERROR_PREFIX, threadInfo.isMainThread ? "main" : "user",
                    threadInfo.stackSize / 1024);
            StackTrace_PrintStackTrace();
            _exit(sig);
        } else if (faultAddr == NULL) {
            fprintf(stderr,
                    "%s Unrecoverable NullPointerException in %s "
                    "thread\n",
                    SN_ERROR_PREFIX, threadInfo.isMainThread ? "main" : "user");
            StackTrace_PrintStackTrace();
            _exit(sig);
        }
    default:
    dispatchDefaultSignal:;
        struct sigaction *previousSignalHandler;
        previousSignalHandler = resolvePreviousSignalHandler(sig);
        if (previousSignalHandler != NULL) {
            bool isSigAction = previousSignalHandler->sa_flags & SA_SIGINFO;
            void *handler = isSigAction
                                ? (void *)previousSignalHandler->sa_sigaction
                                : (void *)previousSignalHandler->sa_handler;
            if (handler != SIG_DFL && handler != SIG_IGN &&
                handler != SIG_ERR && handler != stackOverflowHandler) {
                if (isSigAction) {
                    return previousSignalHandler->sa_sigaction(sig, info,
                                                               context);
                } else {
                    return previousSignalHandler->sa_handler(sig);
                }
            }
        }
        fprintf(stderr, "%s Unhandled signal %d, si_addr=%p\n", SN_ERROR_PREFIX,
                sig, faultAddr);
        StackTrace_PrintStackTrace();
        // _exit rather than exit: exit() is not async-signal-safe, and would
        // run shutdown hooks on a spawned thread that can deadlock on a GC lock
        // the faulting thread holds.
        _exit(sig);
    }
}

static size_t sigHandlerStackSize() {
    // `SIGSTKSZ` is a compile-time constant on some libcs (e.g. musl
    // 8192). On Linux 5.14+ the kernel enforces a runtime minimum
    // (`AT_MINSIGSTKSZ`) that can be larger than that constant to
    // accommodate XSAVE-class CPU state; modern libcs expose this via
    // `sysconf(_SC_SIGSTKSZ)`. Use whichever is larger so `sigaltstack`
    // does not return `ENOMEM`.
    size_t size = (size_t)SIGSTKSZ;
#ifdef _SC_SIGSTKSZ
    long runtimeSize = sysconf(_SC_SIGSTKSZ);
    if (runtimeSize > 0 && (size_t)runtimeSize > size) {
        size = (size_t)runtimeSize;
    }
#endif
    return size;
}
static void setupSignalHandlerAltstack() {
    stack_t handlerStack = {};
    handlerStack.ss_size =
        (size_t)alignToNextPage((void *)sigHandlerStackSize());
    handlerStack.ss_sp = malloc(handlerStack.ss_size);
    if (handlerStack.ss_sp == NULL) {
        perror(SN_ERROR_MSG("StackOverflowGuards failed to allocate alternate "
                            "signal stack"));
        exit(EXIT_FAILURE);
    }
    handlerStack.ss_flags = 0;
    if (sigaltstack(&handlerStack, NULL) == -1) {
        perror(SN_ERROR_MSG("StackOverflowHandler failed to set alt stack"));
        exit(EXIT_FAILURE);
    }
    currentThreadInfo.signalHandlerStack = handlerStack.ss_sp;
    currentThreadInfo.signalHandlerStackSize = handlerStack.ss_size;
}
static void setupSignalHandler(int signal) {
    struct sigaction sa = {};
    sa.sa_sigaction = stackOverflowHandler;
    sa.sa_flags = SA_SIGINFO | SA_RESTART | SA_ONSTACK;
    sigemptyset(&sa.sa_mask);
    if (sigaddset(&sa.sa_mask, signal) == -1) {
        perror(SN_ERROR_MSG("StackOverflowHandler failed to set signal mask"));
        exit(EXIT_FAILURE);
    }
    if (sigaction(signal, &sa, resolvePreviousSignalHandler(signal)) == -1) {
        perror(SN_ERROR_MSG("StackOverflowHandler failed to set signal "
                            "handler"));

        exit(EXIT_FAILURE);
    }
}

void scalanative_StackOverflowGuards_setup(bool isMainThread) {
    setupStackOverflowGuards();

    static bool isHandlerConfigured = false;
    if (isMainThread && isHandlerConfigured)
        return;

    setupSignalHandlerAltstack();
    if (isMainThread) {
        setupSignalHandler(SIGSEGV);
#if (defined(__APPLE__) && defined(__MACH__))
        setupSignalHandler(SIGBUS);
#endif // Apple
        isHandlerConfigured = true;
    }
}

void scalanative_StackOverflowGuards_reset() {
    int dummy;
    void *curStackTop = &dummy;
    ThreadInfo info = currentThreadInfo;
    currentThreadInfo.pendingStackOverflowException = false;
    if (inStackPageBound(info.stackGuardPage, curStackTop))
        return; // still unwinding
    protectStackGuardPage();
}

#endif // Unix

void scalanative_StackOverflowGuards_check() {
    if (currentThreadInfo.pendingStackOverflowException) {
#ifndef _WIN32
        {
            int dummy;
            char buf[256];
            snprintf(buf, sizeof(buf),
                     "[SO-DEBUG] throwing self=%p sp=%p guard=%p top=%p "
                     "bottom=%p\n",
                     (void *)pthread_self(), (void *)&dummy,
                     currentThreadInfo.stackGuardPage,
                     currentThreadInfo.stackTop, currentThreadInfo.stackBottom);
            fputs(buf, stderr);
            fputs(dbgLastEvent, stderr);
            StackTrace_PrintStackTrace();
            char cmd[128];
#if defined(__FreeBSD__)
            snprintf(cmd, sizeof(cmd), "procstat -v %d 1>&2", (int)getpid());
#elif defined(__NetBSD__)
            snprintf(cmd, sizeof(cmd), "pmap -l -p %d 1>&2", (int)getpid());
#else
            snprintf(cmd, sizeof(cmd), "cat /proc/%d/maps 1>&2", (int)getpid());
#endif
            fflush(stderr);
            system(cmd);
        }
#endif
        currentThreadInfo.pendingStackOverflowException = false;
        scalanative_throwStackOverflowError();
    }
}

void scalanative_StackOverflowGuards_close() {
#ifdef _WIN32
// noop
#else
    unprotectStackGuardPage();
    currentThreadInfo.stackGuardPage = NULL;
    if (currentThreadInfo.signalHandlerStack) {
        free(currentThreadInfo.signalHandlerStack);
        currentThreadInfo.signalHandlerStack = NULL;
    }
#endif
}
