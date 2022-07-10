// #include "nativeThread.h"
// #include <sys/types.h>
// #include <stdint.h>
// #ifdef _WIN32
// #define _WIN32_MEAN_AND_LEAN
// #include <Windows.h>
// #else
// #include <pthread.h>
// #include <sched.h>

// pthread_attr_t PTHREAD_DEFAULT_ATTR;
// struct sched_param PTHREAD_DEFAULT_SCHED_PARAM;
// int PTHREAD_DEFAULT_POLICY;
// size_t PTHREAD_DEFAULT_STACK_SIZE;
// #endif

// // TLS support
// static thread_local NativeThread currentThread = NULL;
// void scalanative_set_currentThread(NativeThread thread) {
//     currentThread = thread;
// }
// NativeThread scalanative_currentThread() { return currentThread; }
// // Macros interface
// void scalanative_yieldProcessor() { YieldProcessor(); }

// #ifndef _WIN32
// static int initializedDefaultPthreadAttrs = 0;
// static void initDefaultPthreadAttrs() {
//     pthread_attr_init(&PTHREAD_DEFAULT_ATTR);
//     pthread_attr_getschedparam(&PTHREAD_DEFAULT_ATTR,
//                                &PTHREAD_DEFAULT_SCHED_PARAM);
//     pthread_attr_getschedpolicy(&PTHREAD_DEFAULT_ATTR, &PTHREAD_DEFAULT_POLICY);
//     pthread_attr_getstacksize(&PTHREAD_DEFAULT_ATTR,
//                               &PTHREAD_DEFAULT_STACK_SIZE);
//     initializedDefaultPthreadAttrs = 1;
// }
// #endif

// int scalanative_get_max_priority() {
// #ifdef _WIN32
//     return MAX_PRIORITY;
// #else
//     if (!initializedDefaultPthreadAttrs)
//         initDefaultPthreadAttrs();
//     return sched_get_priority_max(PTHREAD_DEFAULT_POLICY);
// #endif
// }

// int scalanative_get_min_priority() {
// #ifdef _WIN32
//     return MIN_PRIORITY;
// #else
//     if (!initializedDefaultPthreadAttrs)
//         initDefaultPthreadAttrs();
//     return sched_get_priority_min(PTHREAD_DEFAULT_POLICY);
// #endif
// }

// int scalanative_get_norm_priority() {
// #ifdef _WIN32
//     return DEF_PRIORITY;
// #else

//     if (!initializedDefaultPthreadAttrs)
//         initDefaultPthreadAttrs();
//     return PTHREAD_DEFAULT_SCHED_PARAM.sched_priority;
// #endif
// }

// size_t scalanative_get_stack_size() {
// #ifdef _WIN32
//     return 1 << 20; // 1MB
// #else
//     if (!initializedDefaultPthreadAttrs)
//         initDefaultPthreadAttrs();
//     return PTHREAD_DEFAULT_STACK_SIZE;
// #endif
// }
