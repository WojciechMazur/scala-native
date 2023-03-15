#ifndef IMMIX_STACKTRACE_H
#define IMMIX_STACKTRACE_H

#if !defined(__wasm)
#include "../../platform/unwind.h"
#endif

void StackTrace_PrintStackTrace();

#endif // IMMIX_STACKTRACE_H
