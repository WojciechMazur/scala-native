#include <Windows.h>

BOOL scalanative_win32_winnt_setupUsersGroupSid(PSID *ref) {
    SID_IDENTIFIER_AUTHORITY authNt = SECURITY_NT_AUTHORITY;
    return AllocateAndInitializeSid(&authNt, 2, SECURITY_BUILTIN_DOMAIN_RID,
                                    DOMAIN_ALIAS_RID_USERS, 0, 0, 0, 0, 0, 0,
                                    ref);
}