#include "vm_instance.h"

void initVM(VMInstance *vm, int physicalMemBytes) {
  vm->sp = &vm->r[STACK_POINTER];
  vm->_ip = &vm->r[INSTRUCTION_POINTER];

  vm->sf = &vm->fr[SIGN_FLAG];
  vm->zf = &vm->fr[ZERO_FLAG];
  *vm->sf = 0;
  *vm->zf = 0;

  vm->pageSize = DEFAULT_PAGE_MEM_SIZE;
  vm->clock = 0;
  vm->physicalMem = malloc(physicalMemBytes * sizeof(uint8_t));
}

void closeVm(VMInstance *vm) {
  free(vm->physicalMem);
  free(vm);
}