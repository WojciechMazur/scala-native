#ifndef IMMIX_MARKER_H
#define IMMIX_MARKER_H

#include "Heap.h"
#include "datastructures/Stack.h"

void Marker_MarkRoots(Heap *heap, Stack *stack);
void Marker_Mark(Heap *heap, Stack *stack);
void Marker_MarkRange_Eager(word_t **from, word_t **to);

#endif // IMMIX_MARKER_H
