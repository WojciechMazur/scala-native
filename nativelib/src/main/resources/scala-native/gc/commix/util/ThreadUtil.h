#ifndef COMMIX_THREAD_UTIL_H
#define COMMIX_THREAD_UTIL_H

#include "GCTypes.h"

#ifdef _WIN32
#include "../../platform/windows/ScalaWindows.h"
#else
#include <pthread.h>
#include <semaphore.h>
#include <sched.h>
#include <unistd.h>
#endif

typedef int pid_t;
typedef void *(*routine_fn)(void *);
#ifdef _WIN32
typedef HANDLE thread_t;
typedef HANDLE mutex_t;
typedef HANDLE semaphore_t;
#else
typedef pthread_t thread_t;
typedef pthread_mutex_t mutex_t;
typedef sem_t semaphore_t;
typedef pid pid_t;
#endif

void thread_create(thread_t *ref, routine_fn routine, void *data);
void thread_yield();

pid_t process_getid();

void mutex_init(mutex_t *ref);
void mutex_lock(mutex_t *ref);
void mutex_unlock(mutex_t *ref);

semaphore_t *semaphore_open(char *name, unsigned int initValue);
void semaphore_wait(semaphore_t *ref);
void semaphore_unlock(semaphore_t *ref);

#endif // COMMIX_THREAD_UTIL_H
