#ifndef IMMIX_LOG_H
#define IMMIX_LOG_H

#define GC_ASSERTIONS 1
#ifdef GC_ASSERTIONS
#undef NDEBUG
#else
#ifndef NDEBUG
#define NDEBUG
#endif // NDEBUG
#endif // GC_ASSERTIONS

#include <assert.h>
#include <inttypes.h>

#define DEBUG_PRINT 1

#endif // IMMIX_LOG_H
