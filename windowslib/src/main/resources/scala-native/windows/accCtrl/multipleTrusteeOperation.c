#if defined(_WIN32) || defined(WIN32)
#define WIN32_LEAN_AND_MEAN
#include <AccCtrl.h>

int scalanative_win32_accctrl_no_multiple_trustee() {
    return NO_MULTIPLE_TRUSTEE;
}
int scalanative_win32_accctrl_trustee_is_impersonate() {
    return TRUSTEE_IS_IMPERSONATE;
}

#endif // defined(_WIN32)
