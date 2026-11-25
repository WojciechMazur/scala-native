/* #5393: where does the kernel put the signal frame relative to the
 * interrupted rsp, and does prepare_redirect's slot (rsp - 128 - 8) hit it? */
#define _XOPEN_SOURCE 700
#define _DARWIN_C_SOURCE
#include <signal.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/mman.h>
#include <ucontext.h>

static volatile int *page;
static void handler(int sig, siginfo_t *si, void *uap) {
    ucontext_t *uc = uap;
    uintptr_t rsp = uc->uc_mcontext->__ss.__rsp;
    uintptr_t slot = rsp - 128 - 8;
    uintptr_t ucb = (uintptr_t)uc;
    uintptr_t mcb = (uintptr_t)uc->uc_mcontext;
    printf("sig=%d rsp=%#lx slot=%#lx\n", sig, rsp, slot);
    printf("uc=%#lx (rsp-uc=%ld) &uc->uc_mcontext=%#lx offsetof=%zu\n", ucb,
           (long)(rsp - ucb), (uintptr_t)&uc->uc_mcontext,
           offsetof(ucontext_t, uc_mcontext));
    printf("slot-uc=%ld  mcontext=%#lx (rsp-mc=%ld) mcsize=%zu siginfo=%#lx\n",
           (long)(slot - ucb), mcb, (long)(rsp - mcb), (size_t)uc->uc_mcsize,
           (uintptr_t)si);
    printf("RESULT slot==&uc->uc_mcontext: %s\n",
           slot == (uintptr_t)&uc->uc_mcontext ? "YES" : "no");
    mprotect((void *)page, 4096, PROT_READ | PROT_WRITE);
}
int main(void) {
    page = mmap(NULL, 4096, PROT_NONE, MAP_PRIVATE | MAP_ANON, -1, 0);
    struct sigaction sa;
    memset(&sa, 0, sizeof sa);
    sa.sa_sigaction = handler;
    sa.sa_flags = SA_SIGINFO | SA_RESTART;
    sigaction(SIGSEGV, &sa, NULL);
    sigaction(SIGBUS, &sa, NULL);
    return *page;
}
