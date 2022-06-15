#ifndef SAFEPOINT_H
#define SAFEPOINT_H

#include <stdint.h>

typedef int8_t safepoint_t;
typedef safepoint_t *safepoint_ref;

void Safepoint_init(safepoint_ref *ref);
void Safepoint_arm(safepoint_ref ref);
void Safepoint_disarm(safepoint_ref ref);

#endif