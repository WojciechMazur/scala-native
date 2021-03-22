#ifndef PROCESS_MONITOR_H
#define PROCESS_MONITOR_H

typedef void timespec;

int scalanative_process_monitor_check_result(const int pid);
int scalanative_process_monitor_wait_for_pid(const int pid, timespec *ts, int *proc_res);
void scalanative_process_monitor_init();

#endif
