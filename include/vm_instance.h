#ifndef VM_INSTANCE_H_
#define VM_INSTANCE_H_

#include <stdint.h>

#define STACK_POINTER 10
#define INSTRUCTION_POINTER 11
#define SIGN_FLAG 0
#define ZERO_FLAG 1
#define PHYSICAL_MEM 128
#define PAGE_MEM_SIZE 16

typedef struct VMInstance {
  // We have 10 registers that programmer is allowed to use R1...R10
  // Stack pointer is R10
  uint32_t r[12];
  uint32_t *sp;

  // Instruction pointer is not accessible for programmer
  // Ip is R11
  uint32_t *_ip;

  // 2 bit flag registers - carry flag and zero flag
  uint8_t fr[2];
  uint8_t *sf;
  uint8_t *zf;

  unsigned int pageSize;
  unsigned int clock;

  uint8_t *physicalMem;

} VMInstance;

#endif /* VM_INSTANCE_H */