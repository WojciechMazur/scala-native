#ifndef IMMIX_STACKTRACE_H
#define IMMIX_STACKTRACE_H

#if !defined(_WIN32) && !defined(__wasm)
#include "../../platform/posix/libunwind/libunwind.h"
#endif

void StackTrace_PrintStackTrace();

#endif // IMMIX_STACKTRACE_H
