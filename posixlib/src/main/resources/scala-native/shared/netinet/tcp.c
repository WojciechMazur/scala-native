#ifdef _WIN32
#include <WinSock2.h>
#else
#include <netinet/tcp.h>
#endif

int scalanative_tcp_nodelay() { return TCP_NODELAY; }
