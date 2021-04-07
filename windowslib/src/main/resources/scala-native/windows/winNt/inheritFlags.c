#include <AccCtrl.h>

int scalanative_win32_accctrl_object_inherit_ace() {
    return OBJECT_INHERIT_ACE;
}
int scalanative_win32_accctrl_container_inherit_ace() {
    return CONTAINER_INHERIT_ACE;
}
int scalanative_win32_accctrl_no_propagate_inherit_ace() {
    return NO_PROPAGATE_INHERIT_ACE;
}
int scalanative_win32_accctrl_inherit_only_ace() { return INHERIT_ONLY_ACE; }
int scalanative_win32_accctrl_inherited_ace() { return INHERITED_ACE; }
int scalanative_win32_accctrl_valid_inherit_flags() {
    return VALID_INHERIT_FLAGS;
}
