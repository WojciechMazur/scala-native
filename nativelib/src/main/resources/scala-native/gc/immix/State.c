#include "State.h"

Heap heap;
Stack stack;
Stack weakRefStack;
BlockAllocator blockAllocator;
MutatorThreads mutatorThreads;
thread_local MutatorThread *currentMutatorThread;