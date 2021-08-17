#ifndef MULTITHREADING_SUPPORT_H
#define MULTITHREADING_SUPPORT_H

#include <stdlib.h>
#include <ThreadUtil.h>

#ifdef SCALANATIVE_MULTITHREADING_ENABLED
// Definitions for GC specific overrides, following Boehm GC convention
// used when multithreading support is enabled
typedef void *(*ThreadStartRoutine)(void*);
typedef void *RoutineArgs;
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
    return pthread_create(thread, attr, routine, args);
}
int GC_pthread_join(pthread_t thread, void **threadReturn) {
    return pthread_join(thread, threadReturn);
}
int GC_pthread_detach(pthread_t thread) { return pthread_detach(thread); }
int GC_pthread_cancel(pthread_t thread) { return pthread_cancel(thread); }
void GC_pthread_exit(void *returnValue) { return pthread_exit(returnValue); }
#endif // pthread bindings
#endif // defined SCALANATIVE_MULTITHREADING_ENABLED
#endif // MULTITHREADING_SUPPORT_H