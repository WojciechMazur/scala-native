#include <exception>
#include <stdio.h>

// Scala Native compiles Scala's exception in C++-compatible
// manner under the hood. Every exception thrown on the Scala
// side is wrapped into ExceptionWrapper and only
// ExceptionWrapper-based exceptions can be caught by
// Scala code. We currently do not support catching arbitrary
// C++ exceptions.

namespace scalanative {
class ExceptionWrapper : public std::exception {
  public:
    ExceptionWrapper(void *_obj) : obj(_obj) {}
    void *obj;
};
} // namespace scalanative

extern "C" {
void *scalanative_throw(void *obj) {
#if defined(__wasi__)
    printf("Tried to throw exception %p, it's not supported by WASI\n", obj);
    return NULL;
#else
    throw scalanative::ExceptionWrapper(obj);
#endif
}
}
