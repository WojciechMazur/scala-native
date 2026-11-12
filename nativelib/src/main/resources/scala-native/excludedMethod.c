#include <stdio.h>
#include <stdlib.h>

/* Fills the virtual table slots of methods left out of a library compiled
 * apart in granular builds, see build.units.UnitCompile. Reachability
 * analysis rejects programs reaching such methods, so no program calls it.
 */
void scalanative_excluded_method(void) {
    fprintf(stderr, "Scala Native: called a method left out of a library "
                    "compiled apart\n");
    abort();
}
