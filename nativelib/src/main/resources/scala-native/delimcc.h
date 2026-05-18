#ifndef DELIMCC_H
#define DELIMCC_H
#include <stdlib.h>
#include <setjmp.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef unsigned long ContinuationBoundaryLabel;

typedef struct Continuation Continuation;

// ContinuationBody = ContBoundaryLabel -> any -> any
typedef void *ContinuationBody(ContinuationBoundaryLabel, void *);

// SuspendFn = Continuation -> any -> any
typedef void *SuspendFn(Continuation *, void *);

// Initializes the continuation helpers,
// set the allocation function for Continuations and stack fragments.
// without calling this, malloc is the default allocation function.
// The allocation function may take another parameter, as given in
// `scalanative_continuation_suspend`.
void scalanative_continuation_init(void *(*alloc_f)(unsigned long, void *));

// cont_boundary : ContinuationBody -> any -> any
// Installs a boundary handler and passes the boundary label associated with
// the handler to the ContinuationBody. Returns the return value of
// ContinuationBody (or the `scalanative_continuation_suspend` result
// corresponding to this handler).
void *scalanative_continuation_boundary(ContinuationBody *, void *);

// cont_suspend[T, R] : BoundaryLabel[T] -> (Continuation[T, R] -> T) -> R
// Suspends to the boundary handler corresponding to the given boundary label,
// reifying the suspended computation up to (and including) the handler as a
// Continuation struct, and passing it to the SuspendFn (alongside with `arg`),
// returning its result to the caller of scalanative_continuation_boundary.
//
// The reified computation is stored into memory allocated with `alloc_f(size,
// alloc_arg)`, the function set up by `scalanative_continuation_init`.
void *scalanative_continuation_suspend(ContinuationBoundaryLabel b,
                                       SuspendFn *f, void *arg,
                                       void *alloc_arg);

// resume[T, R] : Continuation[T, R] -> R -> Result
// Resumes the given Continuation under the resume call, passing back the
// argument into the suspended computation and returns its result.
void *scalanative_continuation_resume(Continuation *continuation, void *arg);

// Reset the thread-local handler chain to NULL. Call before entering a new
// boundary (e.g. before dispatching a virtual thread) so the carrier does not
// inherit stale handlers from a previous VT that ran on the same carrier.
void scalanative_continuation_handlers_reset(void);

/* Exception type compatible with eh.c (void* = Scala object). */
typedef void *Exception;

/*
 * Exception escape from resumed continuations: when a resumed body throws and
 * no handler in the continuation catches it, we longjmp back to the resumer
 * instead of aborting (eh.c) or terminating (eh.cpp). The resumer can then
 * return Failure(exception) to the Scala side. Local try/catch inside the
 * continuation body is unaffected (unwinding finds those first).
 *
 * Use scalanative_continuation_exception_handler_set() immediately before
 * calling scalanative_continuation_resume(); resume clears the handler on both
 * normal return and exception escape.
 */

/* Thread-local state for exception escape; shared by eh.c and eh.cpp to handle
 * throwing exceptions from resumed continuations.
 *
 * `env` is treated as an opaque pointer: on POSIX it is the address of a
 * `jmp_buf`/`lh_jmp_buf` saved by `_lh_setjmp` inside resume; on Windows it
 * is the address of an `lh_jmp_buf` (the libhandler-style register-only
 * jmpbuf, NOT the MSVC `jmp_buf`, because MSVC's `setjmp`/`longjmp` are
 * EH-aware via `RtlUnwindEx` and refuse to unwind out of a relocated
 * continuation fragment with STATUS_BAD_STACK / 0xC0000028).
 *
 * Callers in eh.c / eh.cpp MUST NOT call POSIX `longjmp(*env, 1)` directly;
 * use `scalanative_continuation_exception_jump` below, which performs the
 * register-only jump that works from both heap and OS-stack fragments. */
typedef struct ContinuationExceptionHandler {
    void *env;
    Exception *exception_slot;
} ContinuationExceptionHandler;

/* Set the exception escape handler for the next resume. env is the opaque
 * jmpbuf pointer captured by resume's `_lh_setjmp`; exception_slot is where
 * eh.c / eh.cpp will store the exception object when it jumps. Pass NULL for
 * both to clear the handler. */
void scalanative_continuation_exception_handler_set(
    ContinuationExceptionHandler handler);
ContinuationExceptionHandler scalanative_continuation_exception_handler(void);

/* Fallback escape route used by eh.c when _Unwind reaches end-of-stack and no
 * continuation exception handler is installed. Returns non-zero if escape was
 * handled (function does not return in that case), zero otherwise.
 */
int scalanative_continuation_exception_escape(Exception exception);

/* Clear the exception escape handler. Called by resume on both return paths. */
inline static void scalanative_continuation_exception_handler_clear(void) {
    ContinuationExceptionHandler handler = {NULL, NULL};
    scalanative_continuation_exception_handler_set(handler);
}

/* Perform the exception escape jump from eh.c / eh.cpp:
 *   *handler.exception_slot = exception;
 *   <clear handler>;
 *   <jump to handler.env with arg=1>;
 *
 * Uses libhandler's register-only `_lh_longjmp` so it works across the
 * relocated continuation fragment on Windows MSVC (no SEH unwind, no
 * RtlUnwindEx stack-range validation). On POSIX it behaves like `longjmp`.
 *
 * This function does not return. */
#if defined(__GNUC__) || defined(__clang__)
__attribute__((noreturn))
#endif
void scalanative_continuation_exception_jump(
    ContinuationExceptionHandler handler, Exception exception);

#ifdef SCALANATIVE_DELIMCC_DEBUG // Debug flag for delimcc

// Frees a continuation. Used only if malloc is used as the implementation of
// alloc function.
void scalanative_continuation_free(Continuation *continuation);

#endif // SCALANATIVE_DELIMCC_DEBUG

#ifdef __cplusplus
}
#endif
#endif // DELIMCC_H
