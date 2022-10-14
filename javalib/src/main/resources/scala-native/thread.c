#include "thread.h"

#include <stdlib.h>

typedef void *JavaThread;

thread_local JavaThread currentThread = NULL;

void scalanative_set_currentThread(JavaThread thread) {
    currentThread = thread;
}

JavaThread scalanative_currentThread() { return currentThread; }
