// #include <pthread.h>
// #include <signal.h>
// #include <stdio.h>
// #include <stdbool.h>
// #include <stdint.h>
// #include <sys/mman.h>
// #include <unistd.h>
// #include <stdlib.h>

// volatile bool shouldExit, stopTrapActivated = false;
// volatile int8_t *shouldStopTrap = NULL;
// struct sigaction *defaultSigAction = NULL;

// void handle_signal(int signal) {
//     switch (signal) {
//     case SIGSEGV:
//         printf("Got seg fault, stopTrap=%d thread=%lu\n", stopTrapActivated,
//                pthread_self());
//         if (stopTrapActivated) {
//             printf("Will suspend thread %lu\n", pthread_self());
//             while (stopTrapActivated) {
//                 usleep(1000);
//             }
//             printf("Thread %lu resumed\n", pthread_self());
//         } else {
//             printf("Using default signal handler\n");
//             defaultSigAction->sa_handler(signal);
//         }
//         break;
//     default:
//         fprintf(stderr, "Caught wrong signal: %d, thread=%lu\n", signal,
//                 pthread_self());
//         break;
//     }
// }

// void *thread_proc(void *x) {
//     printf("sub thread %lu\n", pthread_self());
//     unsigned int usecs = 100000;

//     while (!shouldExit) {
//         printf("trapValue=%d, sleepRes= %d, thread=%lu\n", *shouldStopTrap,
//                usleep(usecs), pthread_self());
//         // int8_t *invalid = (int8_t *)0x00;
//         // printf("should seg fault\n", *invalid);
//     }
//     pthread_exit(NULL);
// }

// int main(int argc, char **argv) {
//     pthread_t threads[10];
//     struct sigaction sa;
//     // Setup the sighub handler
//     sa.sa_handler = &handle_signal;
//     // Restart the system call, if at all possible
//     sa.sa_flags = SA_RESTART;
//     // Block every signal during the handler
//     sigfillset(&sa.sa_mask);
//     // Intercept SIGSEGV
//     if (sigaction(SIGSEGV, &sa, &defaultSigAction) == -1) {
//         perror("Error: cannot handle SIGSEGV"); // Should not happen
//     }

//     printf("Hello world\n");
//     printf("Trap address %p\n", shouldStopTrap);
//     shouldStopTrap =
//         (int8_t *)mmap(NULL, sizeof(int8_t), PROT_READ,
//                        MAP_NORESERVE | MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
//     printf("Trap address %p\n", shouldStopTrap);

//     for (int i = 0; i < 10; i++) {
//         pthread_t handle;
//         int res = pthread_create(&handle, NULL, thread_proc, NULL);
//         if (res) {
//             printf("error %d\n", res);
//         }
//         threads[i] = handle;
//     }

//     usleep(550000);

//     printf("Enabling trap res=%d\n",
//            stopTrapActivated = (0 == mprotect((void *)shouldStopTrap,
//                                               sizeof(int8_t), PROT_NONE)));
//     usleep(1000000);
//     printf("Disable trap res=%d\n",
//            stopTrapActivated = !(0 == mprotect((void *)shouldStopTrap,
//                                                sizeof(int8_t), PROT_READ)));
//     usleep(1000000);
//     shouldExit = true;
//     printf("main thread id=%lu\n", pthread_self());
//     for (int i = 0; i < 10; i++) {
//         printf("thread %d joined status: %d\n", i,
//                pthread_join(threads[i], NULL));
//     }
//     return 0;
// }
