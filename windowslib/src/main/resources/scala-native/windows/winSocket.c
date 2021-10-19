#if defined(_WIN32) || defined(WIN32)
#define WIN32_LEAN_AND_MEAN
#include "Windows.h"
#include <winsock2.h>

#pragma comment(lib, "Ws2_32.lib")

SOCKET scalanative_winsock_invalid_socket() { return INVALID_SOCKET; }
DWORD scalanative_winsock_wsadata_size() { return sizeof(WSADATA); }
DWORD scalanative_winsock_fionbio() { return FIONBIO; }

SHORT scalanative_winsock_poll_pollin() { return POLLIN; }
SHORT scalanative_winsock_poll_pollout() { return POLLOUT; }

#endif // defined(_WIN32)
