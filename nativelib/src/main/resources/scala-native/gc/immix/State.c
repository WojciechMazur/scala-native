#include "State.h"

Heap heap = {};
Stack stack = {};
Stack weakRefStack = {};
BlockAllocator blockAllocator = {};
MutatorThreads mutatorThreads = NULL;
thread_local MutatorThread *currentMutatorThread = NULL;