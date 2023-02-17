
#include <stdlib.h>
#include <ucontext.h>
#include <stdio.h>

size_t scalanative_sizeof_ucontext_t() { return sizeof(ucontext_t); }

void scalantive_coroutine_setResumeContext(ucontext_t *ctx, size_t stackSize,
                                           void *stackPointer) {
    printf("set context %p, size=%lu, stack=%p\n", ctx, stackSize,stackPointer);
    ctx->uc_stack.ss_sp = stackPointer;
    ctx->uc_stack.ss_size = stackSize;
    ctx->uc_stack.ss_flags = 0;
    ctx->uc_link = NULL;
}