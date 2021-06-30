#ifndef SCALANATIVE_THREAD_H
#define SCALANATIVE_THREAD_H

#ifndef thread_local
#if __STDC_VERSION__ >= 201112 && !defined __STDC_NO_THREADS__
#define thread_local _Thread_local
#elif defined _WIN32 && (defined _MSC_VER || defined __ICL ||                  \
                         defined __DMC__ || defined __BORLANDC__)
#define thread_local __declspec(thread)
/* note that ICC (linux) and Clang are covered by __GNUC__ */
#elif defined __GNUC__ || defined __SUNPRO_C || defined __xlC__
#define thread_local __thread
#else
#error "Cannot define thread_local"
#endif
#endif

#ifdef _WIN32
#include <Winnt.h
// YieldProcessor already defined
#else // Unix
// Only clang defines __has_builtin, so we first test for a GCC define
// before using __has_builtin.

#if defined(__i386__) || defined(__x86_64__)
#if (__GNUC__ > 4 && __GNUC_MINOR > 7) || __has_builtin(__builtin_ia32_pause)
// clang added this intrinsic in 3.8
// gcc added this intrinsic by 4.7.1
#define YieldProcessor __builtin_ia32_pause
#endif // __has_builtin(__builtin_ia32_pause)

// If we don't have intrinsics, we can do some inline asm instead.
#ifndef YieldProcessor
#define YieldProcessor() asm volatile("pause")
#endif // YieldProcessor

#endif // defined(__i386__) || defined(__x86_64__)

#ifdef __aarch64__
#define YieldProcessor() asm volatile("yield")
#endif // __aarch64__

#ifdef __arm__
#define YieldProcessor()
#endif // __arm__

#endif // Unix
#endif // SCALANATIVE_THREAD_H
