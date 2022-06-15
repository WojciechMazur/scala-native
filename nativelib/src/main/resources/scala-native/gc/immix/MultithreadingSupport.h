#ifdef SCALANATIVE_MULTITHREADING_ENABLED
#ifndef MULTITHREADING_SUPPORT_H
#define MULTITHREADING_SUPPORT_H

#include <stdlib.h>
#include <stdbool.h>
#include "ThreadUtil.h"

// enum GcState{
//   Running = 0,
//   BeforeStopTheWorld,
//   AfterStopTheWorld
// }

void scalanative_gc_waitUntilCollected();
void scalanative_yieldpoint();
// Definitions for GC specific overrides, following Boehm GC convention
// used when multithreading support is enabled
typedef void *(*ThreadStartRoutine)(void *);
typedef void *RoutineArgs;

void GC_allow_register_threads(void);
int GC_register_my_thread(void *);
int GC_unregister_my_thread(void);

#ifdef _WIN32 // windows bindings
Handle GC_CreateThread(SecurityAttributes *threadAttributes, UWORD stackSize,
                       ThreadStartRoutine routine, RoutineArgs args, DWORD,
                       creationFlags, DWORD *threadId){
    return CreateThread(threadAttributes, stackSize, routine, args,
                        creationFlags, threadId)};
void GC_ExitThread(DWORD exitCode) { return ExitThread(exitCode); }
#else  // pthread bindings
int GC_pthread_create(pthread_t *thread, pthread_attr_t *attr,
                      ThreadStartRoutine routine, RoutineArgs args);
int GC_pthread_join(pthread_t thread, void **threadReturn);
int GC_pthread_detach(pthread_t thread);
int GC_pthread_cancel(pthread_t thread);
void GC_pthread_exit(void *returnValue);
#endif // pthread bindings

void scalanative_init_yieldpoint();
void scalanative_yieldpoint();
bool scalanative_gc_onCollectStart();
void scalanative_gc_onCollectEnd();

#endif // MULTITHREADING_SUPPORT_H
#endif // defined SCALANATIVE_MULTITHREADING_ENABLED