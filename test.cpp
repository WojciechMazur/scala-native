
#include <cstdio>

int main(int argc, char** argv){
  printf("Hello\n");

  try {
    throw 42;
  } catch (int e) {
    printf("Catched %d\n", e);
    // return e;
  }
  printf("Done\n");
}