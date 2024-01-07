#include <stdio.h>
#include <stdlib.h>

#include "vm_instance.h"

#define OK_CODE 0
#define INVALID_RUN_COMMAND_ERROR 1
#define INVALID_MEMORY_AMOUNT_ERROR 2

int main(int argc, char *argv[]) {
  if (argc <= 2) {
    printf("Usage: avmOS <# of bytes> <program1.txt> <program2.txt> ...\n");
    return INVALID_RUN_COMMAND_ERROR;
  }

  if (argv[1][0] == '-') {
    printf("# of bytes for a system memory cannot be less than 0!\n");
    return INVALID_MEMORY_AMOUNT_ERROR;
  }

  int virutalMemBytes = atoi(argv[1]);

  if (virutalMemBytes == 0) {
    printf("# of bytes for a system memory cannot be equal to 0!\n");
    return INVALID_MEMORY_AMOUNT_ERROR;
  }

  VMInstance *pVm = malloc(sizeof(VMInstance));
  initVM(pVm, DEFAULT_PHYSICAL_MEM);

  closeVm(pVm);

  return OK_CODE;
}