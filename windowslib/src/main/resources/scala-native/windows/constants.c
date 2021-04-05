#include "Windows.h"

unsigned int scalanative_win32_file_max_path() { return MAX_PATH; }
HANDLE scalanative_win32_invalid_handle_value() { return INVALID_HANDLE_VALUE; }
DWORD scalanative_win32_infinite() { return INFINITE; }