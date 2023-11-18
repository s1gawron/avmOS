#include <stdio.h>
#include <stdlib.h>

int main(int argc, char *argv[]) {
  if (argc <= 2) {
    printf("Usage: avmOS <# of bytes> <program1.txt> <program2.txt> ...\n");
    return 1;
  }

  if (argv[1][0] == '-' || argv[1][0] == '0') {
    printf(
        "# of bytes for a system memory cannot be less than or equal to 0!\n");
    return 1;
  }

  int numberOfBytes = atoi(argv[1]);

  if (numberOfBytes == 0) {
    printf("Usage: avmOS <# of bytes> <program1.txt> <program2.txt> ...\n");
    return 1;
  }

  return 0;
}