#if defined(SCALANATIVE_USING_CPP_EXCEPTIONS)

#include <exception>
#include <mutex>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#if defined(_WIN32)
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#endif

#if defined(__SCALANATIVE_DELIMCC)
#include "delimcc.h"
#include "string_constants.h"
#include <stdio.h>
#endif

#if defined(__SCALANATIVE_DELIMCC)
#include "delimcc.h"
#include "string_constants.h"
#include <stdio.h>
#endif

namespace {
/* Mirror of `delimcc.c`'s SCALANATIVE_DELIMCC_TRACE switch so we can
 * follow the exception handling path together with the C-side traces. */
__attribute__((noinline)) bool eh_trace_enabled() {
    static int checked = 0;
    static int enabled = 0;
    if (!checked) {
        const char *e = std::getenv("SCALANATIVE_DELIMCC_TRACE");
        enabled = (e != NULL && e[0] != '\0' &&
                   !(e[0] == '0' && e[1] == '\0'));
        checked = 1;
    }
    return enabled != 0;
}
__attribute__((noinline, format(printf, 1, 2))) void
eh_trace(const char *fmt, ...) {
    if (!eh_trace_enabled())
        return;
    std::fprintf(stderr, "[ScalaNative eh] ");
    va_list ap;
    va_start(ap, fmt);
    std::vfprintf(stderr, fmt, ap);
    va_end(ap);
    std::fputc('\n', stderr);
    std::fflush(stderr);
}

#if defined(_WIN32)
/* Vectored Exception Handler installed first in the chain.
 * It logs every SEH exception that gets raised by the OS before any
 * frame-based handler runs. We want to know exactly which exception
 * (and exception code) precedes the `__fastfail` that terminates the
 * process when a continuation rethrows an escaping ControlThrowable. */
static LONG WINAPI sn_eh_veh(EXCEPTION_POINTERS *info) {
    if (!eh_trace_enabled())
        return EXCEPTION_CONTINUE_SEARCH;
    if (info && info->ExceptionRecord) {
        PEXCEPTION_RECORD r = info->ExceptionRecord;
        eh_trace("VEH: ExceptionCode=%08lx Flags=%08lx Address=%p Params=%lu "
                 "p0=%p p1=%p",
                 (unsigned long)r->ExceptionCode,
                 (unsigned long)r->ExceptionFlags, r->ExceptionAddress,
                 (unsigned long)r->NumberParameters,
                 r->NumberParameters > 0 ? (void *)r->ExceptionInformation[0]
                                         : NULL,
                 r->NumberParameters > 1 ? (void *)r->ExceptionInformation[1]
                                         : NULL);
    }
    return EXCEPTION_CONTINUE_SEARCH;
}
__attribute__((constructor)) static void sn_eh_install_veh() {
    AddVectoredExceptionHandler(/* FirstHandler = */ 1, sn_eh_veh);
}
#endif
} // namespace

// Scala Native compiles Scala's exception in C++-compatible
// manner under the hood. Every exception thrown on the Scala
// side is wrapped into ExceptionWrapper and only
// ExceptionWrapper-based exceptions can be caught by
// Scala code. We currently do not support catching arbitrary
// C++ exceptions.

typedef void *Exception;
typedef void (*OnCatchHandler)(Exception);
extern "C" OnCatchHandler
scalanative_Throwable_onCatchHandler(Exception exception);

namespace scalanative {
class ExceptionWrapper : public std::exception {
  public:
    ExceptionWrapper(Exception _obj) : obj(_obj) {}
    Exception obj;
};
} // namespace scalanative

extern "C" {
#if defined(__SCALANATIVE_DELIMCC)
/*
 * Continuation exception escape (C++): when a resumed body throws
 * scalanative::ExceptionWrapper and no handler in the continuation catches it,
 * the C++ unwinder cannot cross the longjmp boundary (resumed code runs on a
 * copied stack), so it runs out of frames and calls std::terminate(). We
 * install a custom terminate handler once at load time (process-wide). When
 * we're in a continuation-resume context
 * (scalanative_continuation_exception_handler set by delimcc.c), it extracts
 * the current exception (via std::current_exception) and longjmps to the
 * resumer instead of terminating.
 */
static std::terminate_handler default_terminate_handler = NULL;
static std::once_flag continuation_terminate_handler_once;

static void continuation_terminate_handler() {
    eh_trace("continuation_terminate_handler entered");
    ContinuationExceptionHandler ceh =
        scalanative_continuation_exception_handler();
    eh_trace("continuation_terminate_handler ceh.env=%p ceh.exception_slot=%p",
             (void *)ceh.env, (void *)ceh.exception_slot);
    if (ceh.env != NULL && ceh.exception_slot != NULL) {
        std::exception_ptr eptr = std::current_exception();
        eh_trace("continuation_terminate_handler current_exception=%s",
                 eptr ? "present" : "null");
        if (eptr != nullptr) {
            try {
                std::rethrow_exception(eptr);
            } catch (scalanative::ExceptionWrapper &e) {
                eh_trace("continuation_terminate_handler caught wrapper "
                         "obj=%p, jumping to resumer",
                         e.obj);
                scalanative_continuation_exception_jump(ceh, e.obj);
            } catch (...) {
                eh_trace("continuation_terminate_handler caught non-wrapper "
                         "exception, falling through");
                /* not our exception, fall through */
            }
        }
    }
    eh_trace("continuation_terminate_handler falling back to default/abort");
    if (default_terminate_handler)
        default_terminate_handler();
    fprintf(stderr,
            SN_FATAL_ERROR_MSG(
                "Failed to throw exception, not found a valid catch handler "
                "for exception when unwinding execution stack.\n"));
    fflush(stderr);
    std::abort();
}

void scalanative_continuation_exception_terminate_handler_install(void) {
    std::call_once(continuation_terminate_handler_once, []() {
        default_terminate_handler =
            std::set_terminate(continuation_terminate_handler);
    });
}
#endif

void scalanative_throw(void *obj) {
    eh_trace("scalanative_throw obj=%p", obj);
#if defined(_WIN32) && defined(__SCALANATIVE_DELIMCC)
    /* Windows MSVC WinEH cannot unwind through frames in a relocated
     * continuation fragment. The WinEH personality validates each frame's
     * /GS security cookie (__GSHandlerCheck_EH) and the establisher-frame
     * address against the thread's TIB stack range; both checks fail on a
     * fragment because the frame's RBP/RSP no longer match the values from
     * the original (pre-suspend) stack location at which the cookie was
     * stamped. The kernel then triggers __fastfail(7)
     * (FAST_FAIL_FATAL_APP_EXIT, status 0xC0000409) and the process dies
     * BEFORE std::terminate runs (so the set_terminate hook never fires).
     *
     * To work around this, when scalanative_throw is invoked from inside a
     * resumed continuation, bypass C++ EH entirely: stash the throwable in
     * the resumer's exception slot and longjmp to the resumer's
     * exception_env. The resumer turns it into Failure(throwable), and
     * Continuation.apply re-throws via .get on the caller's stack (which
     * is on the OS thread stack, NOT in a fragment) - that throw goes
     * through the normal MSVC C++ EH path and is caught by the user's
     * outer try/catch as expected.
     *
     * Trade-off: a try/catch placed inside the resumed body itself will
     * NOT catch this throw on Windows. That is unfortunate but acceptable:
     * the same try/catch would __fastfail if invoked normally on the
     * relocated stack, so this workaround does not eliminate
     * functionality - it merely substitutes a graceful escape for an
     * unrecoverable crash. Linux/macOS continue to use C++ EH, where
     * unwinding across the fragment works correctly. */
    ContinuationExceptionHandler ceh =
        scalanative_continuation_exception_handler();
    if (ceh.env != NULL && ceh.exception_slot != NULL) {
        eh_trace(
            "scalanative_throw: continuation context active, bypassing C++ EH "
            "and jumping to resumer obj=%p env=%p slot=%p",
            obj, ceh.env, (void *)ceh.exception_slot);
        scalanative_continuation_exception_jump(ceh, obj);
        __builtin_unreachable();
    }
#endif
    throw scalanative::ExceptionWrapper(obj);
}
size_t scalanative_Throwable_sizeOfExceptionWrapper() { return 0; }
void scalanative_Exception_onCatch(Exception self) {
    if (self) {
        OnCatchHandler handler = scalanative_Throwable_onCatchHandler(self);
        if (handler)
            handler(self);
    }
}
}
#endif
