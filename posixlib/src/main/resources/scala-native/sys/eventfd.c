#ifndef _WIN32
#include <sys/eventfd.h>

int scalanative_efd_cloexec() { return EFD_CLOEXEC; }
int scalanative_efd_nonblock() { return EFD_NONBLOCK; }
int scalanative_efd_semaphore() { return EFD_SEMAPHORE; }

#endif