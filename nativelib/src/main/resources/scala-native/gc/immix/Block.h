#ifndef IMMIX_BLOCK_H
#define IMMIX_BLOCK_H

#include "metadata/BlockMeta.h"
#include "Heap.h"

void Block_Recycle(BlockMeta *block, word_t *blockStart, LineMeta *lineMetas);
#endif // IMMIX_BLOCK_H
