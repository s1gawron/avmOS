#include <stdio.h>
#include <stdlib.h>

#include "vm_instance.h"

#define OK_CODE 0
#define INVALID_RUN_COMMAND_ERROR 1
#define INVALID_MEMORY_AMOUNT_ERROR 2

void initVM(VMInstance *vm, int physicalMemBytes) {
  vm->sp = &vm->r[STACK_POINTER];
  vm->_ip = &vm->r[INSTRUCTION_POINTER];

  vm->sf = &vm->fr[SIGN_FLAG];
  vm->zf = &vm->fr[ZERO_FLAG];
  *vm->sf = 0;
  *vm->zf = 0;

  vm->pageSize = PAGE_MEM_SIZE;
  vm->clock = 0;
  vm->physicalMem = malloc(physicalMemBytes * sizeof(uint8_t));
}

void closeVm(VMInstance *vm) {
  free(vm->physicalMem);
  free(vm);
}

int main(int argc, char *argv[]) {
  if (argc <= 2) {
    printf("Usage: avmOS <# of bytes> <program1.txt> <program2.txt> ...\n");
    return INVALID_RUN_COMMAND_ERROR;
  }

  if (argv[1][0] == '-' || argv[1][0] == '0') {
    printf(
        "# of bytes for a system memory cannot be less than or equal to 0!\n");
    return INVALID_MEMORY_AMOUNT_ERROR;
  }

  int virutalMemBytes = atoi(argv[1]);

  if (virutalMemBytes == 0) {
    printf("Usage: avmOS <# of bytes> <program1.txt> <program2.txt> ...\n");
    return INVALID_RUN_COMMAND_ERROR;
  }

  VMInstance *pVm = malloc(sizeof(VMInstance));
  initVM(pVm, PHYSICAL_MEM);

  closeVm(pVm);

  return OK_CODE;
}