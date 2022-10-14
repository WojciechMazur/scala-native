#include <stdlib.h>
#include <stdbool.h>
#include "GCTypes.h"

typedef bool (*IsThreadSuspendedFn)(void *);

IsThreadSuspendedFn scalanative_isThreadSuspended;

void scalanative_init();
void *scalanative_alloc(void *info, size_t size);
void *scalanative_alloc_small(void *info, size_t size);
void *scalanative_alloc_large(void *info, size_t size);
void *scalanative_alloc_atomic(void *info, size_t size);
void scalanative_collect();
void scalanative_register_weak_reference_handler(void *handler);

void scalanative_register_thread(word_t **stackBottom, void *jlThread);
void scalanative_unregister_thread(void);
