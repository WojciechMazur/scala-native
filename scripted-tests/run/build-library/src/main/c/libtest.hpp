#include <stdio.h>
#include <stdlib.h>
#include <exception>

extern "C" int ScalaNativeInit(); // needs to be called before first SN heap allocation (GC)

namespace scalanative{
	class ExceptionWrapper : std::exception {};
}

struct Foo {
	short arg1;
	int arg2;
	long arg3;
	double arg4;
	char* arg5;
};

int counter;

extern "C" {
	void sayHello(void);
	void sayHello(void);
	long addLongs(long l, long r);
	struct Foo* retStructPtr(void);
	void updateStruct(struct Foo* p);
	void fail();
	void sn_runGC(void);
}
