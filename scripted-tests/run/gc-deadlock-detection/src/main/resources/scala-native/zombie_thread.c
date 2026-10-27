/**
 * Zombie Thread Helper Functions
 *
 * Provides functions to create "zombie" threads for testing GC liveness
 * detection. A zombie thread is one that was registered with GC but terminated
 * without cleanup.
 */

#ifndef _WIN32
#ifndef _POSIX_C_SOURCE
#define _POSIX_C_SOURCE 199309L
#endif
#ifdef __linux__
#define _DEFAULT_SOURCE // syscall
#endif
#endif

#ifdef _WIN32
#include <windows.h>
#else
#include <pthread.h>
#include <time.h>
#ifdef __linux__
#include <sys/syscall.h>
#include <unistd.h>
#endif
#endif

#include <stdio.h>
#include <stdlib.h>

/**
 * Leave the thread without returning to Scala code.
 *
 * Linux: exit only this OS thread with the raw syscall. No cleanup runs, so
 * the GC still lists the thread. glibc's pthread_exit is not used: its forced
 * unwind calls the libunwind bundled in the binary and crashes.
 *
 * Other POSIX: pthread_exit. The GC thread wrapper's cleanup handler removes
 * the mutator.
 *
 * Scala Native threads are already detached; do not detach again.
 */
void zombie_thread_exit_no_cleanup(void) {
    fprintf(stderr, "[zombie_thread] Exiting thread without cleanup...\n");
    fflush(stderr);

#if defined(_WIN32)
    ExitThread(0);
#elif defined(__linux__)
    syscall(SYS_exit, 0);
#else
    pthread_exit(NULL);
#endif
}

/**
 * Sleep in native code without triggering state transitions.
 */
void zombie_thread_native_sleep_ms(int ms) {
#ifdef _WIN32
    Sleep(ms);
#else
    struct timespec ts;
    ts.tv_sec = ms / 1000;
    ts.tv_nsec = (ms % 1000) * 1000000;
    nanosleep(&ts, NULL);
#endif
}
