#include "windows.h"

DWORD scalanative_win32_console_std_in_handle() {
    return STD_INPUT_HANDLE;
}
DWORD scalanative_win32_console_std_out_handle() {
    return STD_OUTPUT_HANDLE;
}
DWORD scalanative_win32_console_std_err_handle() {
    return STD_ERROR_HANDLE;
}
