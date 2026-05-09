/* Optional cross-DSO instanceof slow path (cached-library mode).
 * When useCrossDsoRtti is enabled, LLVM may reference this symbol.
 * Returns 1 if obj's RTTI ancestry contains typeId, else 0.
 * PoC: conservative stub — extend with real ancestor walk when split linking is active.
 */
#ifdef _WIN32
#include <windows.h>
#else
#include <stddef.h>
#endif

int scalanative_cross_dso_instanceof(void *obj, int type_id) {
  (void)obj;
  (void)type_id;
  return 0;
}
