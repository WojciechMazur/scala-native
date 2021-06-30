// Binds pthread_* functions to scalanative_pthread_* functions.
// This allows GCs to hook into pthreads.
// Every GC must include this file.

#include <stdlib.h>
#include <stdio.h>

void unimplemented() {
    vfprintf(stderr, "Selected GC does not support concurrent execution yet");
    exit(1);
}

#ifdef _WIN32

#else 
#include <pthread.h>

int scalanative_gc_pthread_create(pthread_t *thread, const pthread_attr_t *attr,
                                  void *(*start_routine)(void *), void *arg) {
    unimplemented();
}

int scalanative_gc_pthread_join(pthread_t thread, void **value_ptr) {
    unimplemented();
}

int scalanative_gc_pthread_detach(pthread_t thread) { unimplemented(); }

int scalanative_gc_pthread_cancel(pthread_t thread) { unimplemented(); }

void scalanative_gc_pthread_exit(void *retval) { unimplemented(); }
#endif