#include "MultithreadingSupport.h"
#include <stdio.h>
#include <stdatomic.h>
#include <stdlib.h>
#include <signal.h>
#include <errno.h>
#include <pthread.h>
#include <sys/mman.h>
#include <sys/time.h>
#include <time.h>

#include "State.h"
#include "ThreadUtil.h"
#include "Safepoint.h"
#include <unistd.h>

atomic_bool scalanative_gc_wantsToCollect = ATOMIC_VAR_INIT(false);

struct sigaction defaultAction;
pthread_cond_t canContinueExecution;
thread_local mutex_t selfLock;
volatile MutatorThread *collectingThread;

void MutatorThread_synchronizationHandler(int signal);

void scalanative_init_yieldpoint() {
    struct sigaction sa;
    sa.sa_handler = &MutatorThread_synchronizationHandler;
    sa.sa_flags = SA_RESTART;
    sigfillset(&sa.sa_mask); // Block all during the handler
    // Intercept SIGSEGV
    if (sigaction(SIGSEGV, &sa, &defaultAction) == -1) {
        perror("Error: cannot handle SIGSEGV");
        exit(errno);
    }

    pthread_cond_init(&canContinueExecution, NULL);
    mutex_init(&selfLock);
}

void onDefaultAction(int signal) {
    printf("Using default sig handler %p, wantsToCollect %d\n", &defaultAction,
           atomic_load(&scalanative_gc_wantsToCollect));
    return defaultAction.sa_handler(signal);
}

thread_local bool inSafepointPollRetry = false;

void onRetry(){
    int x = 42;
}

void MutatorThread_synchronizationHandler(int signal) {
    MutatorThread *currentThread = currentMutatorThread;
    switch (signal) {
    case SIGSEGV:
        // printf("Got seg fault,  wantsToCollect=%d thread=%lu\n",
        //        atomic_load(&scalanative_gc_wantsToCollect), pthread_self());
        if(collectingThread == currentThread) break;
        if (!atomic_load(&scalanative_gc_wantsToCollect)) {
            // Retry once safepoint on unexpected signal
            // If we manage to get access safepoint this time we're already
            // after the GC
            if (!inSafepointPollRetry) {
                inSafepointPollRetry = true;
                printf("--Retry safepoint\n");
                onRetry();
                scalanative_yieldpoint();
                inSafepointPollRetry = false;
                return;
            }
            return onDefaultAction(signal);
        }

        scalanative_gc_waitUntilCollected();
        break;

    default:
        fprintf(stderr, "Caught wrong signal: %d, thread=%lu\n", signal,
                pthread_self());
        defaultAction.sa_handler(signal);
    }
}

void scalanative_yieldpoint() { 
    int8_t pollResult = *currentMutatorThread->safepoint; 
}

void scalanative_gc_waitUntilCollected() {
    word_t *dummy;
    currentMutatorThread->stackTop = &dummy;
    MutatorThreadState prevState = MutatorThread_switchState(
        currentMutatorThread, MutatorThreadState_Synchronized);
    while (atomic_load(&scalanative_gc_wantsToCollect)) {
        pthread_cond_wait(&canContinueExecution, &selfLock);
    }
    MutatorThread_switchState(currentMutatorThread, prevState);
    // printf("Switched state after sync of %lu to %d\n", pthread_self(),
    // prevState);
}

bool scalanative_gc_onCollectStart() {
    // printf("Set wantsToCollect: true\n");
    bool alreadyCollects =
        atomic_exchange(&scalanative_gc_wantsToCollect, true);
    // printf("Set wantsToCollect: true - done, prev %d:\n", alreadyCollects);

    if (alreadyCollects) {
        // printf("Skipping collect in %lu\n", pthread_self());
        return true;
    }
    // printf("Perform collect in %lu\n", pthread_self());
    collectingThread = currentMutatorThread;
    MutatorThreadState prevState = MutatorThread_switchState(
        currentMutatorThread, MutatorThreadState_Synchronized);
    struct timeval start, end;
    gettimeofday(&start, NULL);
    MutatorThreads_foreach(mutatorThreads, node){
        Safepoint_arm(node->value->safepoint);
    }
    // printf("poolTrap armed\n");

    int activeThreads;
    int logged = 0;
    do {
        activeThreads = 0;
        MutatorThreads_foreach(mutatorThreads, node) {
            if (node->value->state == MutatorThreadState_Working) {
                printf("\tActive thread %p, state: %d\n", node->value, node->value->state);
                activeThreads++;
            }
        }
        // if (logged == 0) {
        printf("Active threads %d\n", activeThreads);
        //     logged = 1;
        // }
        if (activeThreads > 0) {
            usleep(1000);
        }
    } while (activeThreads > 0);
    gettimeofday(&end, NULL);
    // printf("Stoping threads took %ld microsecons\n",
    //        end.tv_usec - start.tv_usec);
    return false;
}

void scalanative_gc_onCollectEnd() {
    MutatorThreads_foreach(mutatorThreads, node){
        if(node->value != collectingThread){
            if(node->value->safepoint != NULL){
                Safepoint_disarm(node->value->safepoint);
            }
        }
    }
    collectingThread = NULL;
    printf("Pool traps disarmed\n");
    // printf("Set wantsToCollect: false\n");
    atomic_store(&scalanative_gc_wantsToCollect, false);
    // printf("Set wantsToCollect: false - done\n");
    // atomic_signal_fence(memory_order_seq_cst);

    MutatorThread_switchState(currentMutatorThread, MutatorThreadState_Working);

    pthread_cond_broadcast(&canContinueExecution);
}

typedef struct {
    ThreadStartRoutine fn;
    RoutineArgs args;
} WrappedFunctionCallArgs;

static void ProxyThreadStartRoutine(void *args) {
    WrappedFunctionCallArgs *wrapped = (WrappedFunctionCallArgs *)args;
    ThreadStartRoutine originalFn = wrapped->fn;
    RoutineArgs originalArgs = wrapped->args;
    int dummy = 0;

    GC_register_my_thread(&dummy);
    originalFn(originalArgs);
    free(args);
}

#ifdef _WIN32 // windows bindings
Handle GC_CreateThread(SecurityAttributes *threadAttributes, UWORD stackSize,
                       ThreadStartRoutine routine, RoutineArgs args, DWORD,
                       creationFlags, DWORD *threadId){
    return CreateThread(threadAttributes, stackSize, routine, args,
                        creationFlags, threadId)};
void GC_ExitThread(DWORD exitCode) { return ExitThread(exitCode); }
#else  // pthread bindings
int GC_pthread_create(pthread_t *thread, pthread_attr_t *attr,
                      ThreadStartRoutine routine, RoutineArgs args) {

    WrappedFunctionCallArgs *proxyArgs =
        (WrappedFunctionCallArgs *)malloc(sizeof(WrappedFunctionCallArgs));
    proxyArgs->fn = routine;
    proxyArgs->args = args;
    return pthread_create(thread, attr,
                          (ThreadStartRoutine)&ProxyThreadStartRoutine,
                          (RoutineArgs)proxyArgs);
}
int GC_pthread_join(pthread_t thread, void **threadReturn) {
    return pthread_join(thread, threadReturn);
}
int GC_pthread_detach(pthread_t thread) { return pthread_detach(thread); }
int GC_pthread_cancel(pthread_t thread) { return pthread_cancel(thread); }
void GC_pthread_exit(void *returnValue) { return pthread_exit(returnValue); }
#endif // pthread bindings

void GC_allow_register_threads(void) {
    printf("GC: allow register threads\n");
    return;
}
int GC_register_my_thread(void *stackbase) {
    MutatorThread_init(stackbase);
    printf("GC: register thread %p\n", currentMutatorThread);
    return 0;
}
int GC_unregister_my_thread(void) {
    printf("GC: unregister thread %p\n", currentMutatorThread);
    MutatorThread_delete(currentMutatorThread);
    return 0;
}
