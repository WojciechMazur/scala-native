#include "windows.h"

DWORD scalanative_win32_access_token_adjust_default() {
    return TOKEN_ADJUST_DEFAULT;
}
DWORD scalanative_win32_access_token_adjust_groups() {
    return TOKEN_ADJUST_GROUPS;
}
DWORD scalanative_win32_access_token_adjust_privileges() {
    return TOKEN_ADJUST_PRIVILEGES;
}
DWORD scalanative_win32_access_token_adjust_sessionid() {
    return TOKEN_ADJUST_SESSIONID;
}
DWORD scalanative_win32_access_token_assign_primary() {
    return TOKEN_ASSIGN_PRIMARY;
}
DWORD scalanative_win32_access_token_duplicate() { return TOKEN_DUPLICATE; }
DWORD scalanative_win32_access_token_execute() { return TOKEN_EXECUTE; }
DWORD scalanative_win32_access_token_impersonate() { return TOKEN_IMPERSONATE; }
DWORD scalanative_win32_access_token_query() { return TOKEN_QUERY; }
DWORD scalanative_win32_access_token_query_source() {
    return TOKEN_QUERY_SOURCE;
}
DWORD scalanative_win32_access_token_read() { return TOKEN_READ; }
DWORD scalanative_win32_access_token_write() { return TOKEN_WRITE; }
DWORD scalanative_win32_access_token_all_access() { return TOKEN_ALL_ACCESS; }
