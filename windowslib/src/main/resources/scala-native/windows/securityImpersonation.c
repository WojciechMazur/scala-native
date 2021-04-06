#include "windows.h"

DWORD scalanative_win32_security_impersonation_anonymous() {
    return SecurityAnonymous;
}
DWORD scalanative_win32_security_impersonation_identification() {
    return SecurityIdentification;
}
DWORD scalanative_win32_security_impersonation_impersonation() {
    return SecurityImpersonation;
}
DWORD scalanative_win32_security_impersonation_delegation() {
    return SecurityDelegation;
}