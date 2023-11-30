#ifndef VM_INSTANCE_H_
#define VM_INSTANCE_H_

#include <stdint.h>

#define STACK_POINTER 10
#define INSTRUCTION_POINTER 11
#define SIGN_FLAG 0
#define ZERO_FLAG 1
#define DEFAULT_PHYSICAL_MEM 128
#define DEFAULT_PAGE_MEM_SIZE 16

typedef struct VMInstance {
  // We have 10 registers that programmer is allowed to use R1...R10
  uint32_t r[12];

  // Stack pointer is R10
  uint32_t *sp;

  // Instruction pointer - R11 is not accessible for programmer
  uint32_t *_ip;

  // 2 bit flag registers - carry flag and zero flag
  uint8_t fr[2];
  uint8_t *sf;
  uint8_t *zf;

  unsigned int pageSize;
  unsigned int clock;

  uint8_t *physicalMem;

} VMInstance;

void initVM(VMInstance *vm, int physicalMemBytes);
void closeVm(VMInstance *vm);

#endif /* VM_INSTANCE_H */