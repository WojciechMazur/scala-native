#if (defined(SCALANATIVE_COMPILE_ALWAYS) ||                                    \
     defined(__SCALANATIVE_DELIMCC_ONESHOT)) &&                                \
    (!defined(_WIN32) || defined(__x86_64__) || defined(__aarch64__))

/*
 * Entry of a new stack of a one-shot continuation (delimcc_oneshot.c).
 *
 * The first switch to a coroutine jumps here with the stack pointer at the
 * coroutine itself, which lives at the high end of its stack. Passes it to
 * scalanative_continuation_main, which never returns. The frame pointer is
 * null, so stack walks end here.
 *
 * On Windows walks follow the unwind info instead. It unwinds this frame to
 * a null return address at the coroutine, which is also the StackBase of the
 * stack in the TEB: unwinding, exception dispatch and stack traces end here.
 * Its exception handler calls the unhandled exception filter, as the base of
 * the stack of a thread does.
 */

#if defined(__APPLE__)
#define SYMBOL(name) _##name
#define HIDDEN(name) .private_extern SYMBOL(name)
#elif defined(_WIN32)
#define SYMBOL(name) name
#define HIDDEN(name)
#else
#define SYMBOL(name) name
#define HIDDEN(name) .hidden SYMBOL(name)
#endif

    .text
    .globl SYMBOL(scalanative_continuation_entry)
    HIDDEN(scalanative_continuation_entry)

#if defined(_WIN32)
    .def scalanative_continuation_entry
    .scl 2
    .type 32
    .endef
#endif

#if defined(__aarch64__) && defined(_WIN32)
    .balign 4
    /* Entered with sp at the coroutine and x29 null. */
    .seh_proc scalanative_continuation_entry
scalanative_continuation_entry:
    mov   x30, xzr
    .seh_nop
    stp   x29, x30, [sp, #-16]!
    .seh_save_fplr_x 16
    mov   x29, sp
    .seh_set_fp
    .seh_endprologue
    .seh_handler scalanative_continuation_exception_handler, @except
    add   x0, sp, #16
    bl    scalanative_continuation_main
    brk   #1
    .seh_handlerdata
    .long 0
    .text
    .seh_endproc

#elif defined(__x86_64__) && defined(_WIN32)
    .balign 16
    /* Entered as if called, with a null return address below the
     * coroutine, rsp at it and rbp null. */
    .seh_proc scalanative_continuation_entry
scalanative_continuation_entry:
    subq  $40, %rsp
    .seh_stackalloc 40
    .seh_endprologue
    .seh_handler scalanative_continuation_exception_handler, @except
    leaq  48(%rsp), %rcx
    callq scalanative_continuation_main
    ud2
    .seh_handlerdata
    .long 0
    .text
    .seh_endproc

#elif defined(__aarch64__)
    .balign 4
SYMBOL(scalanative_continuation_entry):
    hint  #36 /* bti j */
    mov   x0, sp
    bl    SYMBOL(scalanative_continuation_main)
    brk   #1

#elif defined(__x86_64__)
    .balign 16
SYMBOL(scalanative_continuation_entry):
    movq  %rsp, %rdi
    callq SYMBOL(scalanative_continuation_main)
    ud2

#endif

#endif // one-shot continuations

#if defined(__linux__) && defined(__ELF__)
    .section .note.GNU-stack,"",%progbits
#endif
