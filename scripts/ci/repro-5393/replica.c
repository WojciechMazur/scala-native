/* #5393: replica of the trap yield point path on x86_64 macOS.
 * Uses nativelib's SafepointPollTrampoline-x86_64.S unchanged and a copy of
 * scalanative_gc_safepoint_prepare_redirect. Workers poll one trap page;
 * main arms/disarms it. -DUSE_ALTSTACK runs the handler on a sigaltstack
 * (control: kernel frame no longer below the interrupted rsp). */
#define _XOPEN_SOURCE 700
#define _DARWIN_C_SOURCE
#include <pthread.h>
#include <sched.h>
#include <signal.h>
#include <stdatomic.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <ucontext.h>
#include <unistd.h>

#define WORKERS 4
static void **trap;
static atomic_int armed;
static atomic_long redirects, polls, bad;
static atomic_bool done;

void scalanative_gc_safepoint_poll_trampoline(void);
static __thread uintptr_t resumePc;
uintptr_t scalanative_gc_safepoint_load_resume_pc(void) { return resumePc; }
void scalanative_gc_safepoint_poll_run_yield(void) {
    atomic_fetch_add(&redirects, 1);
    while (atomic_load(&armed))
        sched_yield();
}

static void handler(int sig, siginfo_t *si, void *uap) {
    uintptr_t page = (uintptr_t)trap & ~(uintptr_t)4095;
    if (((uintptr_t)si->si_addr & ~(uintptr_t)4095) != page) {
        fprintf(stderr, "unexpected fault sig=%d addr=%p\n", sig, si->si_addr);
        abort();
    }
    /* Copy of scalanative_gc_safepoint_prepare_redirect (x86_64, Apple) */
    ucontext_t *uc = (ucontext_t *)uap;
    uintptr_t pc = (uintptr_t)uc->uc_mcontext->__ss.__rip;
    uintptr_t sp =
        (uintptr_t)uc->uc_mcontext->__ss.__rsp - 128 - sizeof(uintptr_t);
#ifdef FIXED /* fixed trampoline writes the return slot itself */
    resumePc = pc;
#else
    *(uintptr_t *)sp = pc;
#endif
    uc->uc_mcontext->__ss.__rsp = (__uint64_t)sp;
    uc->uc_mcontext->__ss.__rip =
        (__uint64_t)(uintptr_t)scalanative_gc_safepoint_poll_trampoline;
}

static void *worker(void *arg) {
#ifdef USE_ALTSTACK
    stack_t ss = {.ss_sp = malloc(1 << 16), .ss_size = 1 << 16, .ss_flags = 0};
    sigaltstack(&ss, NULL);
#endif
    double acc = (double)(uintptr_t)arg;
    long n = 0;
    while (!atomic_load_explicit(&done, memory_order_relaxed)) {
        double before = acc;
        (void)*(void *volatile *)trap; /* the yield point */
        acc = before * 1.0000001 + 1.0;
        if (acc <= before)
            atomic_fetch_add(&bad, 1);
        if (acc > 1e12)
            acc = 1.0;
        n++;
    }
    atomic_fetch_add(&polls, n);
    return NULL;
}

int main(int argc, char **argv) {
    int rounds = argc > 1 ? atoi(argv[1]) : 20000;
    trap =
        mmap(NULL, 4096, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANON, -1, 0);
    struct sigaction sa;
    memset(&sa, 0, sizeof sa);
    sa.sa_sigaction = handler;
    sa.sa_flags = SA_SIGINFO | SA_RESTART;
#ifdef USE_ALTSTACK
    sa.sa_flags |= SA_ONSTACK;
#endif
    sigaction(SIGSEGV, &sa, NULL);
    sigaction(SIGBUS, &sa, NULL);
    pthread_t t[WORKERS];
    for (long i = 0; i < WORKERS; i++)
        pthread_create(&t[i], NULL, worker, (void *)(i + 1));
    for (int r = 0; r < rounds; r++) {
        atomic_store(&armed, 1);
        mprotect(trap, 4096, PROT_NONE);
        usleep(20);
        mprotect(trap, 4096, PROT_READ | PROT_WRITE);
        atomic_store(&armed, 0);
        usleep(20);
    }
    atomic_store(&done, true);
    for (int i = 0; i < WORKERS; i++)
        pthread_join(t[i], NULL);
    printf("OK rounds=%d redirects=%ld polls=%ld bad=%ld\n", rounds,
           atomic_load(&redirects), atomic_load(&polls), atomic_load(&bad));
    return atomic_load(&bad) != 0;
}
