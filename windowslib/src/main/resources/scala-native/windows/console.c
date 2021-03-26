#include "windows.h"

unsigned long scalanative_win32_console_std_in_handle() {
    return STD_INPUT_HANDLE;
}
unsigned long scalanative_win32_console_std_out_handle() {
    return STD_OUTPUT_HANDLE;
}
unsigned long scalanative_win32_console_std_err_handle() {
    return STD_ERROR_HANDLE;
}
