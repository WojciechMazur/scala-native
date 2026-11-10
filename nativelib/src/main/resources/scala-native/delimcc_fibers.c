#if defined(_WIN32) &&                                                         \
    (defined(SCALANATIVE_COMPILE_ALWAYS) || defined(__SCALANATIVE_DELIMCC))

/*
 * Delimited continuations for Windows, built on OS fibers.
 *
 * The POSIX implementation (delimcc.c) copies stack segments and moves them
 * between stacks. Windows unwinds exceptions with tables and checks frames
 * against the stack limits stored in the TEB, so moved frames cannot be
 * unwound there. Here each `boundary` runs its body on its own fiber, and
 * suspend/resume only switch fibers. Frames never move, so exceptions,
 * stack guard pages, stack probes and stack walks work as the OS expects, on
 * both x86_64 and ARM64.
 *
 * Differences from delimcc.c:
 * - A continuation can be resumed once. A second resume returns NULL, and the
 *   Scala side throws IllegalStateException.
 * - The suspend callback runs on the stack of the context that receives
 *   control (the boundary caller or the resumer), after the suspending fiber
 *   has fully switched out. Running it on the suspending fiber would let
 *   another thread resume that fiber while it still runs.
 * - The stacks of a suspended continuation are not GC roots. On suspend they
 *   are copied into a blob owned by the Scala Continuation, which the GC
 *   scans conservatively. A continuation that is never resumed is collected
 *   like any other object, and the Scala side then releases its fibers
 *   through a release token (see `scalanative_continuation_release`).
 *
 * The chain of enclosing boundaries is followed through `returnTo`. A fiber's
 * `returnTo` is the context that receives control when that fiber finishes
 * or suspends to its own label: the boundary caller first, then each
 * resumer. This matches the handler chain of delimcc.c, where a resumed
 * segment sits on top of the resumer's stack.
 *
 * GC: switching away from a stack registers [saved SP, stack base) as a GC
 * root range, and switching back removes it. The thread's ThreadInfo bounds
 * always describe the stack that currently runs, so the GC scans the right
 * range for the running stack.
 *
 * When a fiber suspends, the context that receives control copies every
 * stack of the suspended segment (from the suspending fiber up to the label
 * fiber) into one GC blob, and only then drops their root ranges. The parked
 * stacks do not change until a resume, and Immix and Commix never move
 * objects, so the copy holds exactly the references of the real stacks.
 * Resuming registers the root ranges again before switching.
 */

#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "delimcc.h"
#include "nativeThreadTLS.h"
#include "gc/shared/ThreadUtil.h"

#if !defined(_WIN32_WINNT) || _WIN32_WINNT < 0x0602
WINBASEAPI VOID WINAPI GetCurrentThreadStackLimits(PULONG_PTR LowLimit,
                                                   PULONG_PTR HighLimit);
#endif

extern void scalanative_GC_add_roots(void *addr_low, void *addr_high);
extern void scalanative_GC_remove_roots(void *addr_low, void *addr_high);
extern void scalanative_StackOverflowGuards_setup(bool isMainThread);

#define SN_FIBER_NOINLINE __attribute__((noinline))

// Saves every callee-saved register in the calling function's frame, so the
// GC finds the callers' values when it scans the parked stack. SwitchToFiber
// keeps them in the fiber context, which the GC never scans.
#if defined(__GNUC__) || defined(__clang__)
#define SN_FIBER_SPILL_CALLEE_SAVED() __builtin_unwind_init()
#else
#define SN_FIBER_SPILL_CALLEE_SAVED()
#endif

#define SN_FIBER_ABORT(...)                                                    \
    do {                                                                       \
        fprintf(stderr, "[delimcc-fibers] " __VA_ARGS__);                      \
        fflush(stderr);                                                        \
        abort();                                                               \
    } while (0)

static int fiberTrace = 0;
#define SN_FIBER_TRACE(...)                                                    \
    do {                                                                       \
        if (fiberTrace) {                                                      \
            fprintf(stderr, "[delimcc-fibers] " __VA_ARGS__);                  \
            fflush(stderr);                                                    \
        }                                                                      \
    } while (0)

typedef enum {
    FIBER_PRIMARY = 0, // a thread converted to a fiber; never finishes
    FIBER_CREATED,
    FIBER_RUNNING,
    FIBER_SUSPENDED,
    FIBER_FINISHED
} FiberState;

typedef struct Fiber Fiber;
struct Fiber {
    LPVOID handle;
    // Receives control when this fiber finishes or suspends to its label.
    Fiber *returnTo;
    // The fiber that last switched to this one.
    Fiber *from;
    volatile FiberState state;
    DWORD ownerThreadId; // thread of a FIBER_PRIMARY fiber

    // Stack bounds, set by the fiber itself once it runs.
    void *stackLo; // lowest reserved address
    void *stackHi; // stack base (highest address)
    void *stackGuardPage;
    size_t stackSize;

    // Lowest address of the parked part of the stack, valid while the fiber
    // does not run. `rooted` is set while [parkedSp, stackHi) is registered
    // as a GC root range.
    void *parkedSp;
    bool rooted;

    // Values handed over on a switch. They hold GC references only while no
    // safepoint can run: from the store until the receiver passes them on.
    ContinuationBody *body;
    void *bodyArg;
    void *result;
    SuspendFn *suspendFn;
    void *suspendArg;
    void *allocArg;
    Continuation *pending;
    void *resumeArg;
};

/* Lets the Scala side release the fibers of a continuation after the GC
 * collected it. Allocated with malloc on request, freed only by
 * scalanative_continuation_release. */
typedef struct ReleaseToken {
    volatile LONG claimed; // set by resume or release, whichever comes first
    Fiber *top;
    Fiber *label;
} ReleaseToken;

// Allocated through continuation_alloc_fn, owned by the Scala Continuation.
struct Continuation {
    Fiber *top;   // fiber that called suspend
    Fiber *label; // boundary fiber suspended to
    volatile LONG resumed;
    ReleaseToken *token; // NULL until requested
};

static void *(*continuation_alloc_fn)(size_t, void *) = NULL;
static SIZE_T fiberStackReserve = 1024 * 1024;
static SIZE_T fiberStackCommit = 0; // 0: the executable's default

static SN_ThreadLocal Fiber *currentFiber = NULL;

// The thread-local address must be computed again after every switch,
// because the fiber may continue on another thread. Calls to these
// non-inlined accessors cannot reuse an address from before the switch.
SN_FIBER_NOINLINE static Fiber *current_fiber_load(void) {
    return currentFiber;
}
SN_FIBER_NOINLINE static void current_fiber_store(Fiber *fiber) {
    currentFiber = fiber;
}

// Frame address of a callee: lower than every slot of the caller's frame,
// including the callee-saved registers spilled there.
SN_FIBER_NOINLINE static void *approximate_stack_pointer(void) {
    return __builtin_frame_address(0);
}

static void thread_info_apply(Fiber *fiber) {
    ThreadInfo *info = scalanative_currentThreadInfo();
    info->stackTop = fiber->stackLo;
    info->stackBottom = fiber->stackHi;
    info->stackGuardPage = fiber->stackGuardPage;
    info->stackSize = fiber->stackSize;
}

static Fiber *current_fiber(void) {
    Fiber *self = current_fiber_load();
    if (self != NULL)
        return self;

    self = (Fiber *)calloc(1, sizeof(Fiber));
    if (self == NULL)
        SN_FIBER_ABORT("out of memory allocating the thread fiber\n");
    if (IsThreadAFiber()) {
        self->handle = GetCurrentFiber();
    } else {
        self->handle = ConvertThreadToFiberEx(NULL, FIBER_FLAG_FLOAT_SWITCH);
        if (self->handle == NULL)
            SN_FIBER_ABORT("ConvertThreadToFiberEx failed: %lu\n",
                           (unsigned long)GetLastError());
    }
    self->state = FIBER_PRIMARY;
    self->ownerThreadId = GetCurrentThreadId();

    ThreadInfo *info = scalanative_currentThreadInfo();
    if (info->stackTop != NULL && info->stackBottom != NULL) {
        self->stackLo = info->stackTop;
        self->stackHi = info->stackBottom;
        self->stackGuardPage = info->stackGuardPage;
        self->stackSize = info->stackSize;
    } else {
        ULONG_PTR lo = 0, hi = 0;
        GetCurrentThreadStackLimits(&lo, &hi);
        self->stackLo = (void *)lo;
        self->stackHi = (void *)hi;
        self->stackSize = (size_t)(hi - lo);
    }
    current_fiber_store(self);
    SN_FIBER_TRACE("thread fiber %p stack [%p, %p)\n", (void *)self,
                   self->stackLo, self->stackHi);
    return self;
}

static void fiber_root(Fiber *fiber) {
    if (!fiber->rooted) {
        scalanative_GC_add_roots(fiber->parkedSp, fiber->stackHi);
        fiber->rooted = true;
    }
}

static void fiber_unroot(Fiber *fiber) {
    if (fiber->rooted) {
        fiber->rooted = false;
        scalanative_GC_remove_roots(fiber->parkedSp, fiber->stackHi);
    }
}

/* Switches from `self` (the running fiber) to `target`. Returns when some
 * fiber switches back to `self`, possibly on another thread. When
 * `keepSelfRooted` is set, the parked part of the stack of `self` is a GC
 * root while `self` does not run, until a suspend moves it into a blob. */
SN_FIBER_NOINLINE static void fiber_switch(Fiber *self, Fiber *target,
                                           bool keepSelfRooted) {
    SN_FIBER_SPILL_CALLEE_SAVED();
    // Word aligned, so the copies in a snapshot keep pointers aligned.
    self->parkedSp =
        (void *)((uintptr_t)approximate_stack_pointer() & ~(uintptr_t)15);
    if (keepSelfRooted)
        fiber_root(self);
    if (target->state == FIBER_PRIMARY &&
        target->ownerThreadId != GetCurrentThreadId())
        SN_FIBER_ABORT("switch to the thread fiber of thread %lu from "
                       "thread %lu\n",
                       (unsigned long)target->ownerThreadId,
                       (unsigned long)GetCurrentThreadId());
    target->from = self;
    SN_FIBER_TRACE("switch %p -> %p (thread %lu)\n", (void *)self,
                   (void *)target, (unsigned long)GetCurrentThreadId());
    current_fiber_store(target);
    SwitchToFiber(target->handle);

    // Running `self` again, possibly on another thread.
    current_fiber_store(self);
    thread_info_apply(self);
    fiber_unroot(self);
}

static void fiber_destroy(Fiber *fiber) {
    SN_FIBER_TRACE("delete %p\n", (void *)fiber);
    DeleteFiber(fiber->handle);
    free(fiber);
}

/* Thread start routines pass unhandled SEH exceptions to the filter set with
 * SetUnhandledExceptionFilter. The GC relies on that filter for safepoint
 * traps, so the fiber base does the same. */
static void *fiber_run_body(Fiber *self) {
    void *result = NULL;
    __try {
        result = self->body((ContinuationBoundaryLabel)self, self->bodyArg);
    } __except (UnhandledExceptionFilter(GetExceptionInformation())) {
        TerminateProcess(GetCurrentProcess(), (UINT)GetExceptionCode());
    }
    return result;
}

static VOID CALLBACK fiber_main(LPVOID param) {
    Fiber *self = (Fiber *)param;
    ULONG_PTR lo = 0, hi = 0;
    GetCurrentThreadStackLimits(&lo, &hi);
    self->stackLo = (void *)lo;
    self->stackHi = (void *)hi;
    self->stackSize = (size_t)(hi - lo);
    self->stackGuardPage = NULL;
    current_fiber_store(self);
    thread_info_apply(self);
    // Sets the guard page bound and the stack guarantee of this fiber; the
    // guarantee is stored per fiber by the OS.
    scalanative_StackOverflowGuards_setup(false);
    self->stackGuardPage = scalanative_currentThreadInfo()->stackGuardPage;
    self->state = FIBER_RUNNING;
    SN_FIBER_TRACE("start %p stack [%p, %p)\n", (void *)self, self->stackLo,
                   self->stackHi);

    // The Scala body catches every Throwable and returns a Try, so no
    // exception unwinds past this frame.
    void *result = fiber_run_body(self);
    self->bodyArg = NULL;
    self->result = result;
    self->state = FIBER_FINISHED;
    fiber_switch(self, self->returnTo, false);
    SN_FIBER_ABORT("finished fiber %p was resumed\n", (void *)self);
}

#define SEGMENT_FOREACH(fiber, top, label)                                     \
    for (Fiber *fiber = (top), *fiber##End = NULL; fiber##End != (label);      \
         fiber##End = fiber, fiber = fiber->returnTo)

/* Moves the stacks of a suspended segment out of the GC roots: copies them
 * into one blob owned by the Scala Continuation `allocArg`, then drops their
 * root ranges. The fibers of the segment are parked, so their stacks do not
 * change until the continuation is resumed. */
static void segment_snapshot(Fiber *top, Fiber *label, void *allocArg) {
    size_t total = 0;
    SEGMENT_FOREACH(fiber, top, label) {
        total += (size_t)((char *)fiber->stackHi - (char *)fiber->parkedSp);
    }
    // May run the GC: the stacks are still rooted.
    char *blob = (char *)continuation_alloc_fn(total, allocArg);
    if (blob == NULL)
        SN_FIBER_ABORT("suspend: stack snapshot allocation failed\n");
    char *out = blob;
    SEGMENT_FOREACH(fiber, top, label) {
        size_t size =
            (size_t)((char *)fiber->stackHi - (char *)fiber->parkedSp);
        memcpy(out, fiber->parkedSp, size);
        out += size;
    }
    SEGMENT_FOREACH(fiber, top, label) { fiber_unroot(fiber); }
    SN_FIBER_TRACE("snapshot %p..%p: %zu bytes\n", (void *)top, (void *)label,
                   total);
}

/* Runs in the context that just received control in boundary or resume. */
static void *fiber_dispatch(Fiber *self) {
    Fiber *source = self->from;
    switch (source->state) {
    case FIBER_FINISHED: {
        void *result = source->result;
        fiber_destroy(source);
        return result;
    }
    case FIBER_SUSPENDED: {
        // Copy before running the callback: once it publishes the
        // continuation, another thread may resume and re-suspend `source`.
        Continuation *continuation = source->pending;
        SuspendFn *fn = source->suspendFn;
        void *arg = source->suspendArg;
        void *allocArg = source->allocArg;
        source->pending = NULL;
        source->suspendFn = NULL;
        source->suspendArg = NULL;
        source->allocArg = NULL;
        segment_snapshot(continuation->top, continuation->label, allocArg);
        return fn(continuation, arg);
    }
    default:
        SN_FIBER_ABORT("unexpected switch from fiber %p in state %d\n",
                       (void *)source, (int)source->state);
    }
}

static SIZE_T parse_size(const char *value, SIZE_T fallback) {
    if (value == NULL || *value == '\0')
        return fallback;
    char *end = NULL;
    unsigned long long parsed = strtoull(value, &end, 10);
    if (end == value)
        return fallback;
    switch (*end) {
    case 'k':
    case 'K':
        parsed *= 1024ULL;
        break;
    case 'm':
    case 'M':
        parsed *= 1024ULL * 1024ULL;
        break;
    case 'g':
    case 'G':
        parsed *= 1024ULL * 1024ULL * 1024ULL;
        break;
    default:
        break;
    }
    if (parsed < 64 * 1024)
        parsed = 64 * 1024;
    return (SIZE_T)parsed;
}

void scalanative_continuation_init(void *(*alloc_f)(size_t, void *)) {
    if (alloc_f == NULL)
        SN_FIBER_ABORT("scalanative_continuation_init: alloc_f is NULL\n");
    continuation_alloc_fn = alloc_f;
    fiberStackReserve = parse_size(
        getenv("SCALANATIVE_CONTINUATION_STACK_SIZE"), fiberStackReserve);
    const char *trace = getenv("SCALANATIVE_DELIMCC_TRACE");
    fiberTrace = trace != NULL && trace[0] == '1';
}

// The chain of boundaries lives in the fibers, so a carrier thread never
// inherits stale state from a previous virtual thread.
void scalanative_continuation_handlers_reset(void) {}

void *scalanative_continuation_boundary(ContinuationBody *body, void *arg) {
    Fiber *self = current_fiber();
    Fiber *fiber = (Fiber *)calloc(1, sizeof(Fiber));
    if (fiber == NULL)
        SN_FIBER_ABORT("out of memory allocating a fiber\n");
    fiber->body = body;
    fiber->bodyArg = arg;
    fiber->returnTo = self;
    fiber->state = FIBER_CREATED;
    fiber->handle =
        CreateFiberEx(fiberStackCommit, fiberStackReserve,
                      FIBER_FLAG_FLOAT_SWITCH, fiber_main, (LPVOID)fiber);
    if (fiber->handle == NULL)
        SN_FIBER_ABORT("CreateFiberEx failed: %lu\n",
                       (unsigned long)GetLastError());
    fiber_switch(self, fiber, true);
    return fiber_dispatch(self);
}

void *scalanative_continuation_suspend(ContinuationBoundaryLabel label,
                                       SuspendFn *f, void *arg,
                                       void *alloc_arg) {
    Fiber *self = current_fiber();
    Fiber *target = (Fiber *)label;

    // The label must belong to an enclosing boundary of the running fiber.
    Fiber *it = self;
    while (it != NULL && it != target && it->state != FIBER_PRIMARY)
        it = it->returnTo;
    if (it != target || target->state == FIBER_PRIMARY)
        SN_FIBER_ABORT("suspend: label %p is not an enclosing boundary of "
                       "fiber %p\n",
                       (void *)target, (void *)self);

    // May run the GC: `arg` stays live in this frame until it is stored.
    Continuation *continuation =
        (Continuation *)continuation_alloc_fn(sizeof(Continuation), alloc_arg);
    if (continuation == NULL)
        SN_FIBER_ABORT("suspend: continuation allocation failed\n");
    continuation->top = self;
    continuation->label = target;
    continuation->resumed = 0;
    continuation->token = NULL;

    self->pending = continuation;
    self->suspendFn = f;
    self->suspendArg = arg;
    self->allocArg = alloc_arg;
    self->state = FIBER_SUSPENDED;
    fiber_switch(self, target->returnTo, true);

    // Resumed.
    self->state = FIBER_RUNNING;
    void *resumeArg = self->resumeArg;
    self->resumeArg = NULL;
    return resumeArg;
}

void *scalanative_continuation_resume(Continuation *continuation, void *arg) {
    if (InterlockedExchange(&continuation->resumed, 1) != 0)
        return NULL; // resumed twice: reported by the Scala side
    if (continuation->token != NULL)
        InterlockedExchange(&continuation->token->claimed, 1);
    Fiber *self = current_fiber();
    // The stacks become roots again before anything can change them. The
    // snapshot stays in the Scala Continuation until it is collected.
    SEGMENT_FOREACH(fiber, continuation->top, continuation->label) {
        fiber_root(fiber);
    }
    continuation->label->returnTo = self;
    continuation->top->resumeArg = arg;
    fiber_switch(self, continuation->top, true);
    return fiber_dispatch(self);
}

void *scalanative_continuation_release_token(Continuation *continuation) {
    if (continuation->token == NULL) {
        ReleaseToken *token = (ReleaseToken *)malloc(sizeof(ReleaseToken));
        if (token == NULL)
            SN_FIBER_ABORT("out of memory allocating a release token\n");
        token->claimed = continuation->resumed;
        token->top = continuation->top;
        token->label = continuation->label;
        continuation->token = token;
    }
    return continuation->token;
}

/* Called once the GC collected the Scala Continuation that owns `token`. A
 * continuation that was never resumed can no longer be resumed, so its fibers
 * are deleted. A resumed one gave its fibers back to the resumer, which
 * deletes them when they finish. */
void scalanative_continuation_release(void *opaque) {
    ReleaseToken *token = (ReleaseToken *)opaque;
    if (InterlockedExchange(&token->claimed, 1) == 0) {
        Fiber *fiber = token->top;
        for (;;) {
            Fiber *next = fiber->returnTo;
            bool last = fiber == token->label;
            if (fiber->rooted || fiber->state == FIBER_PRIMARY)
                SN_FIBER_ABORT("release: fiber %p is still in use\n",
                               (void *)fiber);
            SN_FIBER_TRACE("release %p\n", (void *)fiber);
            fiber_destroy(fiber);
            if (last)
                break;
            fiber = next;
        }
    }
    free(token);
}

/* Exception escape helpers used by the POSIX implementation. Exceptions never
 * leave a fiber here, so these only keep eh.c/eh.cpp linkable. */
void scalanative_continuation_exception_handler_set(
    ContinuationExceptionHandler handler) {
    (void)handler;
}

ContinuationExceptionHandler scalanative_continuation_exception_handler(void) {
    ContinuationExceptionHandler none = {NULL, NULL};
    return none;
}

int scalanative_continuation_exception_escape(Exception exception) {
    (void)exception;
    return 0;
}

#endif // _WIN32 && (SCALANATIVE_COMPILE_ALWAYS || __SCALANATIVE_DELIMCC)
