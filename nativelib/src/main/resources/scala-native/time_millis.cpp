#if defined(_WIN32)
#include <windows.h>
#else
#include <stdio.h>
#include <sys/time.h>
#endif

extern "C" {
long long scalanative_current_time_millis() {
    long long current_time_millis;

#define MILLIS_PER_SEC 1000LL
#define MICROS_PER_MILLI 1000LL

#if defined(_WIN32)
    SYSTEMTIME time;
    GetSystemTime(&time);
    current_time_millis = (time.wSecond * MILLIS_PER_SEC) + time.wMilliseconds;
#else
    struct timeval tv;
    gettimeofday(&tv, NULL);
    current_time_millis =
        tv.tv_sec * MILLIS_PER_SEC + tv.tv_usec / MICROS_PER_MILLI;
#endif

#undef MILLIS_PER_SEC
#undef MICROS_PER_MILLI

    return current_time_millis;
}
}