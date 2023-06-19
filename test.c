#define _POSIX_C_SOURCE 200809L

#include <errno.h>
#include <netinet/in.h>
#include <stdio.h>
#include <string.h>
#include <sys/socket.h>
#include <time.h>
#include <unistd.h>

#define ACCEPT_FD 3

int main(void) {
    char buf[128] = {0};
    struct sockaddr_in addr;
    socklen_t addr_size = sizeof(struct sockaddr_in);
    size_t res;
    int fd = -1;
    struct timespec tv = {0, 500 * 1000 * 1000};

    printf("accept() on fd %i\n", ACCEPT_FD);
    while (fd == -1) {
        fd = accept(ACCEPT_FD, (struct sockaddr *)&addr, &addr_size);
        nanosleep(&tv, NULL); // hack
        printf("%d\n", errno);
        fflush(stdout);
    }
    printf("\naccept returned fd %i\n", fd);

    res = recv(fd, buf, sizeof(buf)-1, 0);
    printf("recv %zd\n", res);
    if (res == -1) {
        printf("recv(%i, ...) failed: %s (%i)\n", fd, strerror(errno), errno);
	    return 2;
    } else {
        printf("buf %.*s (%zd)", (int)res, buf, res);
        return 0;
    }
}