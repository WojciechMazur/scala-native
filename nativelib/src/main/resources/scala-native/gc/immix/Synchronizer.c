#include <stdio.h>
#include <stdatomic.h>
#include <stdlib.h>
#include <signal.h>
#include <errno.h>
#include <pthread.h>
#include <sys/mman.h>
#include <sys/time.h>
#include <time.h>
#include <setjmp.h>
#include <unistd.h>

#include "State.h"
#include "ThreadUtil.h"
#include "Safepoint.h"
#include "MutatorThread.h"

#define THREAD_WAKUP_SIGNAL (SIGCONT)
static sigset_t threadWakupSignals;

static struct sigaction defaultAction;
static volatile bool isCollecting = false;
static mutex_t synchronizerLock;

static void Synchronizer_SafepointTrapHandler(int signal, siginfo_t *siginfo,
                                              void *uap);

extern safepoint_t *scalanative_gc_safepoint;
#define SafepointInstance (scalanative_gc_safepoint)

void Synchronizer_init() {
    Safepoint_init(&SafepointInstance);
    mutex_init(&synchronizerLock);

    sigemptyset(&threadWakupSignals);
    sigaddset(&threadWakupSignals, THREAD_WAKUP_SIGNAL);
    sigprocmask(SIG_BLOCK, &threadWakupSignals, NULL);
    assert(sigismember(&threadWakupSignals, THREAD_WAKUP_SIGNAL));

    struct sigaction sa;
    memset(&sa, 0, sizeof(struct sigaction));
    sigemptyset(&sa.sa_mask);
    sa.sa_sigaction = &Synchronizer_SafepointTrapHandler;
    sa.sa_flags = SA_SIGINFO | SA_RESTART;
    if (sigaction(SIGSEGV, &sa, &defaultAction) == -1) {
        perror("Error: cannot setup synchronization handler SIGSEGV");
        exit(errno);
    }
}

void Synchronizer_wait() {
    MutatorThread *self = currentMutatorThread;
    MutatorThread_switchState(self, MutatorThreadState_Unmanaged);
    atomic_thread_fence(memory_order_release);
    atomic_signal_fence(memory_order_seq_cst);

    atomic_store_explicit(&self->isWaiting, true, memory_order_release);
    int signum;
    if (0 != sigwait(&threadWakupSignals, &signum)) {
        perror("Error: sig wait");
        exit(errno);
    }
    assert(signum == THREAD_WAKUP_SIGNAL);
    if (signum == THREAD_WAKUP_SIGNAL) {
        atomic_store_explicit(&self->isWaiting, false, memory_order_release);
    }
    MutatorThread_switchState(self, MutatorThreadState_Managed);
}

void onSegFault(void *addr) {
    MutatorThread *self = currentMutatorThread;
    printf("On segfault %p in %p\n", addr, currentMutatorThread);
#include <execinfo.h>
#include <stdio.h>
    void *callstack[128];
    int i, frames = backtrace(callstack, 128);
    char **strs = backtrace_symbols(callstack, frames);
    for (i = 0; i < frames; ++i) {
        printf("%s\n", strs[i]);
    }
    free(strs);
}
static void Synchronizer_SafepointTrapHandler(int signal, siginfo_t *siginfo,
                                              void *uap) {
    if (siginfo->si_addr == SafepointInstance) {
        Synchronizer_wait();
    } else {
        fprintf(stderr, "Unexpected SIGSEGV signal at address %p\n",
                siginfo->si_addr);
        onSegFault(siginfo->si_addr);
        defaultAction.sa_handler(signal);
    }
}

bool Synchronizer_acquire() {
    if (!mutex_tryLock(&synchronizerLock)) {
        if (isCollecting)
            Synchronizer_wait();
        return false;
    }

    isCollecting = true;
    MutatorThread *self = currentMutatorThread;
    MutatorThread_switchState(self, MutatorThreadState_Unmanaged);

    // Don't allow for registration of any new threads;
    MutatorThreads_lock();
    Safepoint_arm(SafepointInstance);

    int iteration = 0;
    int activeThreads;
    do {
        atomic_thread_fence(memory_order_seq_cst);
        iteration++;
        activeThreads = 0;
        MutatorThreads_foreach(mutatorThreads, node) {
            MutatorThread *it = node->value;
            if (it->stackTop == NULL) {
                activeThreads++;
            }
        }
        if (activeThreads > 0) {
            usleep(4);
        }
    } while (activeThreads > 0);
    return true;
}

void Synchronizer_release() {
    Safepoint_disarm(SafepointInstance);
    isCollecting = false;
    int stoppedThreads;
    do {
        MutatorThreads_foreach(mutatorThreads, node) {
            MutatorThread *thread = node->value;
            if (atomic_load_explicit(&thread->isWaiting,
                                     memory_order_acquire)) {
                int status = pthread_kill(thread->thread, THREAD_WAKUP_SIGNAL);
                if (status != 0) {
                    fprintf(stderr,
                            "Failed to resume thread %lu after GC, errno: %d\n",
                            thread->thread, status);
                }
            }
        }
        usleep(4);
        stoppedThreads = 0;
        MutatorThreads_foreach(mutatorThreads, node) {
            MutatorThread *thread = node->value;
            if (atomic_load_explicit(&thread->isWaiting,
                                     memory_order_acquire)) {
                stoppedThreads++;
            }
        }
    } while (stoppedThreads > 0);
    MutatorThread_switchState(currentMutatorThread, MutatorThreadState_Managed);
    MutatorThreads_unlock();
    mutex_unlock(&synchronizerLock);
}