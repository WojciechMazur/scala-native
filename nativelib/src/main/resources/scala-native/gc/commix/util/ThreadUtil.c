#include "ThreadUtil.h"
#include <stdio.h>

INLINE
void thread_create(thread_t *ref, routine_fn routine, void *data) {
#ifdef _WIN32
    *ref =
        CreateThread(NULL, 0, (LPTHREAD_START_ROUTINE)routine, data, 0, NULL);
#else
    pthread_create(&ref, NULL, routine, data);
#endif
}

INLINE
void thread_yield() {
#ifdef _WIN32
    SwitchToThread();
#else
    sched_yield();
#endif
}

INLINE
pid_t process_getid() {
#ifdef _WIN32
    return (pid_t)GetCurrentProcessId();
#else
    return (pid_t)getpid();
#endif
}

INLINE
void mutex_init(mutex_t *ref) {
#ifdef _WIN32
    *ref = CreateMutex(NULL, TRUE, NULL);
#else
    pthread_mutex_init(ref, NULL)
#endif
}

INLINE
void mutex_lock(mutex_t *ref) {
#ifdef _WIN32
    WaitForSingleObject(ref, INFINITE);
#else
    pthread_mutex_lock(ref);
#endif
}

INLINE
void mutex_unlock(mutex_t *ref) {
#ifdef _WIN32
    ReleaseMutex(ref);
#else
    pthread_mutex_unlock(ref);
#endif
}

INLINE
semaphore_t *semaphore_open(char *name, unsigned int initValue) {
#ifdef _WIN32
    semaphore_t *ret = CreateSemaphore(NULL, initValue, 128, NULL);
    if (ret == NULL) {
        printf("CreateSemaphore error: %lu\n", GetLastError());
    }
    return ret;
#else
    return sem_open(name, O_CREAT | O_EXCL, 0644, 0);
#endif
}

INLINE
void semaphore_wait(semaphore_t *ref) {
#ifdef _WIN32
    WaitForSingleObject(ref, INFINITE);
#else
    semaphore_wait(ref);
#endif
}

INLINE
void semaphore_unlock(semaphore_t *ref) {
#ifdef _WIN32
    ReleaseSemaphore(ref, 1, NULL);
#else
    sem_post(ref);
#endif
}
