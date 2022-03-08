#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <assert.h>
#include <string.h>
#include "libtest.hpp"

int main() {
    assert(ScalaNativeInit() == 0);

    sayHello();

    assert(strcmp(native_constant_string(), "ScalaNativeRocks!") == 0);

    assert(native_number() == 42);
    native_set_number(84);
    assert(native_number() == 84);

    assert(add_longs(123456789L, 876543210L) == 999999999L);

    struct Foo *p = retStructPtr();
    assert(p != NULL);
    assert(p->arg1 == 42);
    assert(p->arg2 == 2020);
    assert(p->arg3 == 27);
    assert(p->arg4 == 14.4556);
    assert(strcmp(p->arg5, "ScalaNativeRocks!") == 0);

    updateStruct(p);
    assert(p != NULL);
    assert(p->arg1 == 42);
    assert(p->arg2 == 2021);
    assert(p->arg3 == 27);
    assert(p->arg4 == 14.4556);
    assert(strcmp(p->arg5, "ScalaNativeRocks!") == 0);
    free(p);

    sn_runGC();

    bool exceptionCaught = false;
    try {
        fail();
    } catch (const scalanative::ExceptionWrapper &e) {
        exceptionCaught = true;
    }
    assert(exceptionCaught);

    exceptionCaught = false;
    try {
        fail();
    } catch (const std::exception &e) {
        exceptionCaught = true;
    }
    assert(exceptionCaught);

    return 0;
}
