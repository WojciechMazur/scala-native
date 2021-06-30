// Binds pthread_* functions to scalanative_pthread_* functions.
// This allows GCs to hook into pthreads.
// Every GC must include this file.

#ifndef _WIN32

#include <pthread.h>
#define GC_THREADS // re-defines pthread functions
#include <gc.h>

int scalanative_gc_pthread_create(pthread_t *thread, const pthread_attr_t *attr,
                                  void *(*start_routine)(void *), void *arg) {
    return pthread_create(thread, attr, start_routine, arg);
}

int scalanative_gc_pthread_join(pthread_t thread, void **value_ptr) {
    return pthread_join(thread, value_ptr);
}

int scalanative_gc_pthread_detach(pthread_t thread) {
    return pthread_detach(thread);
}

int scalanative_gc_pthread_cancel(pthread_t thread) {
    return pthread_cancel(thread);
}

void scalanative_gc_pthread_exit(void *retval) { pthread_exit(retval); }
#endif