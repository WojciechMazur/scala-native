#include "Synchronizer.h"
#include <stdio.h>
#include <stdatomic.h>
#include <stdlib.h>

#include "State.h"
#include "ThreadUtil.h"
#include "Safepoint.h"
#include "MutatorThread.h"

#ifdef _WIN32
#include <errhandlingapi.h>
#else
#include <signal.h>
#include <pthread.h>
#include <sys/mman.h>
#include <sys/time.h>
#include <unistd.h>
#endif

static volatile bool isCollecting = false;
static mutex_t synchronizerLock;

extern safepoint_t *scalanative_gc_safepoint;
#define SafepointInstance (scalanative_gc_safepoint)

#ifdef _WIN32
static LPTOP_LEVEL_EXCEPTION_FILTER defaultFilter;
thread_local static HANDLE wakeupEvent;
static long SafepointTrapHandler(EXCEPTION_POINTERS *ex) {
    switch (ex->ExceptionRecord->ExceptionCode) {
    case EXCEPTION_ACCESS_VIOLATION:
        ULONG_PTR addr = ex->ExceptionRecord->ExceptionInformation[1];
        if (SafepointInstance == (void *)addr) {
            Synchronizer_wait();
            return EXCEPTION_CONTINUE_EXECUTION;
        } else if (defaultFilter != NULL) {
            return defaultFilter(ex);
        }
        break;
    default:
        break;
    }
    return EXCEPTION_EXECUTE_HANDLER;
}
// Stub, Windows does not define usleep, on unix it's deprecated
void usleep(int usec) {
    HANDLE timer;
    LARGE_INTEGER ft;

    ft.QuadPart = -(10 * usec); // Convert to 100 nanosecond interval, negative
                                // value indicates relative time

    timer = CreateWaitableTimer(NULL, TRUE, NULL);
    SetWaitableTimer(timer, &ft, 0, NULL, NULL, 0);
    WaitForSingleObject(timer, INFINITE);
    CloseHandle(timer);
}
#else
#define THREAD_WAKUP_SIGNAL (SIGCONT)
static struct sigaction defaultAction;
static sigset_t threadWakupSignals;
static void SafepointTrapHandler(int signal, siginfo_t *siginfo, void *uap) {
    if (siginfo->si_addr == SafepointInstance) {
        Synchronizer_wait();
    } else {
        fprintf(stderr, "Unexpected SIGSEGV signal at address %p\n",
                siginfo->si_addr);
        defaultAction.sa_handler(signal);
    }
}
#endif

static void SetupPageFaultHandler() {
#ifdef _WIN32
    defaultFilter = SetUnhandledExceptionFilter(&SafepointTrapHandler);
#else
    sigemptyset(&threadWakupSignals);
    sigaddset(&threadWakupSignals, THREAD_WAKUP_SIGNAL);
    sigprocmask(SIG_BLOCK, &threadWakupSignals, NULL);
    assert(sigismember(&threadWakupSignals, THREAD_WAKUP_SIGNAL));

    struct sigaction sa;
    memset(&sa, 0, sizeof(struct sigaction));
    sigemptyset(&sa.sa_mask);
    sa.sa_sigaction = &SafepointTrapHandler;
    sa.sa_flags = SA_SIGINFO | SA_RESTART;
    if (sigaction(SIGSEGV, &sa, &defaultAction) == -1) {
        perror("Error: cannot setup synchronization handler SIGSEGV");
        exit(errno);
    }
#endif
}

static void Synchronizer_SuspendThread(MutatorThread *thread) {
#ifdef _WIN32
    assert(thread == currentMutatorThread);
    if (!ResetEvent(thread->wakeupEvent)) {
        fprintf(stderr, "Failed to reset event %lu\n", GetLastError());
    }
    while (WaitForSingleObject(thread->wakeupEvent, INFINITE) !=
           WAIT_OBJECT_0) {
    }
#else
    int signum;
    if (0 != sigwait(&threadWakupSignals, &signum)) {
        perror("Error: sig wait");
        exit(errno);
    }
    assert(signum == THREAD_WAKUP_SIGNAL);
#endif
}

static void Synchronizer_WakupThread(MutatorThread *thread) {
#ifdef _WIN32
    assert(thread != currentMutatorThread);
    if (!SetEvent(thread->wakeupEvent)) {
        fprintf(stderr, "Failed to set event %lu\n", GetLastError());
    }
#else
    int status = pthread_kill(thread->thread, THREAD_WAKUP_SIGNAL);
    if (status != 0) {
        fprintf(stderr, "Failed to resume thread %lu after GC, errno: %d\n",
                thread->thread, status);
    }
#endif
}

void Synchronizer_init() {
    Safepoint_init(&SafepointInstance);
    mutex_init(&synchronizerLock);
    SetupPageFaultHandler();
}

void Synchronizer_wait() {
    MutatorThread *self = currentMutatorThread;
    MutatorThread_switchState(self, MutatorThreadState_Unmanaged);
    atomic_thread_fence(memory_order_release);
    atomic_signal_fence(memory_order_seq_cst);

    atomic_store_explicit(&self->isWaiting, true, memory_order_release);
    Synchronizer_SuspendThread(self);
    atomic_store_explicit(&self->isWaiting, false, memory_order_release);
    MutatorThread_switchState(self, MutatorThreadState_Managed);
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
                Synchronizer_WakupThread(thread);
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
