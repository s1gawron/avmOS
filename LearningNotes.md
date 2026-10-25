# Learning notes: what each step teaches about how computers work

avmOS is built in 18 small steps. Each step adds one part of a computer or of an operating system. Each step also teaches one idea.

This file explains the idea of every step, shows where to find it in the code, and gives you something to run.

- ✅ means the step is done. Its section points at real files and real output.
- 🔜 means the step is planned. Its section is short and can still change.

## Before you start

Build the project once. Run all commands in the project folder. The `avmOS` function below is a short name for the jar.

```
mvn package
avmOS() { java -jar target/avmOS.jar "$@"; }
S=src/main/resources/sample_programs
T=src/test/resources/programs
```

`$S` holds the sample programs from the assignment. `$T` holds test programs written for these notes and for the tests. `avmOS` needs a memory size first, then the program files. The examples use `16384` bytes.

Every output in this file comes from a real run.

## The big picture

| Step | Status | The big idea |
| --- | --- | --- |
| 1. Boot | ✅ | A machine starts at one entry point. RAM is limited. |
| 2. Machine code | ✅ | A program is only numbers. The encoding gives them meaning. |
| 3. The CPU | ✅ | Fetch, decode, execute. The instruction pointer is just a register. |
| 4. Address space | ✅ | A process sees its own memory. Touching memory it does not own is a fault. |
| 5. Decisions | ✅ | `if` and loops are compare plus jump. |
| 6. The stack | ✅ | Calls, recursion and return addresses. |
| 7. Processes | 🔜 | The OS keeps one record per program and gives each its own memory. |
| 8. Switching | 🔜 | The OS stops a program on a timer and runs another one. |
| 9. Priorities | 🔜 | The most important program that can run goes first. |
| 10. Sleep | 🔜 | A program can wait. The OS runs something else meanwhile. |
| 11. Locks | 🔜 | Two programs must not change shared data at the same time. |
| 12. Priority inversion | 🔜 | A low-priority program can block a high-priority one. |
| 13. Events | 🔜 | One program tells another that something happened. |
| 14. Paging | 🔜 | Virtual memory is cut into pages, and a table maps them to RAM. |
| 15. The heap | 🔜 | Programs ask for memory while they run. |
| 16. Demand paging | 🔜 | Pages come into RAM only when needed, and go to disk when RAM is full. |
| 17. Shared memory | 🔜 | Two programs can use the same piece of RAM. |
| 18. Tracing | 🔜 | You can watch the whole machine work. |

---

## Step 1 — Boot ✅

### The idea

A computer starts in one fixed place. The first thing it needs is to know what it has to work with. For this machine, the most important number is the amount of RAM. The OS and every program must fit in it. RAM is limited and everyone shares it.

### How avmOS shows it

- `Main` is the entry point.
- `BootConfig` reads and checks the command line: the memory size, the program files and the options. It rejects wrong input at once, with a clear message.
- `BootConfigTest` checks each kind of wrong input. The tests are a safety net: you can change the code and know at once if something broke.

### Try it

```
$ avmOS
avmOS: expected a memory size and at least one program file
usage: avmOS [--dump] [--max-cycles N] <memory bytes> <program1.txt> <program2.txt> ...
```

### In real computers

The firmware (BIOS or UEFI) finds out how much RAM there is and tells the OS. Booting is a chain: each part starts the next one, from the firmware to the OS to your programs. Linux also accepts boot parameters, for example to use less RAM than the machine has.

### Questions to think about

1. Why does the OS check its input before it starts anything?
2. What should happen when a program is bigger than the RAM?

---

## Step 2 — Machine code ✅

### The idea

A CPU understands only numbers. **Assembly** gives these numbers readable names (`movi r1, $20`). An **assembler** turns the names back into numbers. The numbers are the **machine code**.

In avmOS, one instruction is one **opcode** byte plus four bytes for each **operand**. Because every operand has the same size, the CPU can find the next instruction easily.

Three facts about the bytes:

- **Little-endian.** A 32-bit number is stored with its smallest byte first. The number 20 is `14 00 00 00`.
- **Two's complement.** A negative number is stored as 2^32 minus its absolute value. The number -19 is 2^32 − 19, which is `ed ff ff ff`.
- **Nothing tags an operand.** Register `r4` and the constant `$4` are both `04 00 00 00`. Only the opcode tells the CPU which one it is.

Jump offsets count **bytes**. That is why every instruction has a known size.

### How avmOS shows it

- `Opcode` lists the 36 opcodes with the kind of each operand. `Operand` is a register or a constant.
- `Assembler` has two jobs: check every line, then write the bytes. A bad line gives an `AssemblyException` with the line number.
- `Program.listing()` makes the hex dump for `--dump`.
- `SampleProgramsTest` checks that the jump offsets in the sample programs fit this encoding exactly.

### Try it

```
$ avmOS --dump 16384 $S/idle.txt
== src/main/resources/sample_programs/idle.txt (42 bytes, 6 instructions)
0000  06 04 00 00 00 00 00 00 00  movi r4, $0
0009  1a 04 00 00 00              setpriority r4
000e  06 01 00 00 00 14 00 00 00  movi r1, $20
0017  0b 01 00 00 00              printr r1
001c  06 02 00 00 00 ed ff ff ff  movi r2, $-19
0025  0d 02 00 00 00              jmp r2
```

Read the line at `001c`: the opcode `06`, then `02 00 00 00` (register 2), then `ed ff ff ff` (-19). The `jmp` at `0025` is 5 bytes long, so the next instruction is at 42. Then 42 − 19 = 23, which is `0x17`. That is the `printr`. The program loops forever.

### In real computers

x86 instructions have different lengths (1 to 15 bytes). ARM64 and RISC-V use 4 bytes for most instructions. Fixed width is simpler for the hardware. Variable width saves space. x86, RISC-V and almost all ARM systems are little-endian. Network protocols use the other order (big-endian).

### Questions to think about

1. What are the four bytes of `$256`?
2. Why can the CPU not tell from the bytes if an operand is a register?
3. Why do jumps use a distance and not an absolute address?

---

## Step 3 — The CPU ✅

### The idea

A CPU repeats three things without end:

1. **Fetch** the byte at the address in `eip` (the instruction pointer).
2. **Decode** it: find the opcode and read its operands.
3. **Execute** the instruction.

`eip` is just a register that holds a number. The CPU moves it to the next instruction **before** it executes the current one. That is why a jump offset counts from the next instruction.

Registers hold 32-bit numbers, and arithmetic wraps around. Nothing in memory says where a program ends. After the last instruction there are zeros, and zero is not an opcode, so the CPU stops with a fault.

Every instruction takes one **clock cycle**. Some things the CPU cannot do alone, such as ending the program. It asks the OS for them with a **trap**.

### How avmOS shows it

- `Cpu.step` does fetch, decode and execute for one instruction. It reads the bytes from memory. It never sees the `Instruction` objects of the assembler.
- `CpuContext` holds everything needed to pause and resume a program: the registers, `eip` and the flags (from step 4, the address space too).
- `Trap` is the request to the OS. `Exit` is the first one. `Fault` is different: it says the program did something impossible.
- `Kernel` runs the CPU and counts the clock cycles.
- `GoldenOutputTest` runs programs and compares all output with a file in `src/test/resources/expected`. I traced those files by hand before the code existed.

### Try it

```
$ avmOS 16384 $S/scott01.txt
[p1] r2 = 3276834
[p1] r2 = 3276869
[p1] exited after 12 clock cycles
```

The program has 12 instructions, and it took 12 cycles.

Save this as `wrap.txt`. It adds 1 to the biggest 32-bit number:

```
6 r1, $2147483647
1 r1
11 r1
27
```

```
$ avmOS 16384 wrap.txt
[p1] r1 = -2147483648
[p1] exited after 4 clock cycles
```

Now save a program without `exit` as `offend.txt`. It has one line: `6 r1, $1`.

```
$ avmOS 16384 offend.txt
[p1] fault at eip=0x0009: unknown opcode 0
[p1] r1=1 r2=0 r3=0 r4=0 r5=0 r6=0 r7=0 r8=1 r9=9 r10=8201 eip=0x0009 sf=0 zf=0
[p1] terminated after 1 clock cycles
```

The CPU ran off the end of the code. At address 9 there is a zero.

### In real computers

x86 calls `eip` the instruction pointer (`rip` in 64-bit mode). Other CPUs call it the program counter. A real CPU also handles interrupts and system calls, and these are the same idea as a trap. Java integers wrap around like these registers do. In C, signed overflow is undefined. Real instructions need different numbers of cycles. The assignment uses one cycle each to keep it simple.

### Questions to think about

1. Why does `eip` move before the instruction runs?
2. What does the CPU do if a program has no `exit`? Why?
3. Why are `Trap` and `Fault` two different things?

---

## Step 4 — Address space ✅

### The idea

A program does not use real RAM addresses. Each process gets its own **address space**, and it starts at 0. In avmOS, the address space has three regions with no gaps: the code, then 4 KB of global data, then 4 KB of stack.

A process **owns** only these bytes. The machine can have more RAM, but touching an address outside the address space is a fault. This is a **segmentation fault**. The OS checks the address, refuses it, prints the registers, and ends only that process. The other programs keep running.

A new process starts with:

- `r8` = its process id
- `r9` = the address where the global data starts
- `r10` = the address just above the stack (`sp`)

The address space is part of the CPU context. When the OS switches to another process, it switches the memory view together with the registers.

### How avmOS shows it

- `ProcessLayout` computes where the regions are.
- `AddressSpace` is what a program sees. `FlatAddressSpace` is the first version. It checks every access.
- `Kernel.load` builds the address space and sets the first registers.
- Five new opcodes use memory: `movmr`, `movrm`, `movmm`, `printm` and `memoryclear`.
- Tests: `FlatAddressSpaceTest`, and the programs `layout.txt`, `memory.txt` and `segfault.txt`.

### Try it

```
$ avmOS 16384 $T/layout.txt
[p1] r8 = 1
[p1] r9 = 16
[p1] r10 = 8208
[p1] exited after 4 clock cycles
```

The code has 16 bytes, so the data starts at 16. The stack ends at 16 + 4096 + 4096 = 8208.

```
$ avmOS 16384 $T/segfault.txt
[p1] fault at eip=0x0012: invalid memory access at address 8220
[p1] r1=8220 r2=7 r3=0 r4=0 r5=0 r6=0 r7=0 r8=1 r9=28 r10=8220 eip=0x0012 sf=0 zf=0
[p1] terminated after 2 clock cycles
```

The program writes to the first address after its stack. That address is in RAM, but the process does not own it.

```
$ avmOS 1024 $S/scott01.txt
avmOS: src/main/resources/sample_programs/scott01.txt does not fit in memory (needs 8284 bytes: 92 code + 4096 data + 4096 stack; memory is 1024 bytes)
```

Now read `memory.txt` and its output:

```
$ avmOS 16384 $T/memory.txt
[p1] [121] = 255
[p1] r4 = 255
[p1] [122] = -16777216
[p1] [121] = 0
[p1] [125] = 255
[p1] exited after 17 clock cycles
```

The program stores 255 at address 121 and copies it to address 125. The line `[122] = -16777216` reads four bytes, from address 122 to 125. They are `00 00 00 ff`. The first byte is the smallest one, so the `ff` is the biggest byte of the number: `0xff000000`. As a signed 32-bit number, that is −16777216.

### In real computers

A Linux process also has regions for code, data, heap and stack. A bad access gives the process the signal `SIGSEGV`. On Windows it is an "access violation". On x86, the register `CR3` points to the page tables of the running process. A context switch changes `CR3`. avmOS keeps the address space in the CPU context for the same reason. Some CPUs refuse to read a number from an address that is not a multiple of 4. x86 allows it, and so does avmOS.

### Questions to think about

1. Why must a segfault end only one process and not the whole machine?
2. Why does the OS give the program `r9` and `r10`, and not use fixed addresses?
3. The stack region is right above the data region. What could happen to the data if the stack could grow without a limit? (Step 6 answers this.)

---

## Step 5 — Decisions ✅

### The idea

A CPU has no `if` and no `while`. It has two instructions that work together:

- **Compare** looks at two numbers and sets **flags**. In avmOS, `SF` means "the first number is smaller", and `ZF` means "the numbers are equal".
- **Conditional jump** reads the flags. It either changes `eip` or it does nothing.

A loop is a jump backward. The flags are the only link between the compare and the jump.

In avmOS a jump takes a register with a distance in bytes, counted from the next instruction. The program works at any address. This is **position-independent code**.

The CPU does **not** check where a jump goes. A bad target is a fault at the next fetch. A jump into the middle of an instruction runs whatever bytes it finds there.

### How avmOS shows it

- `Cpu` has `compare` and `jump`. Compare uses a true signed comparison and sets both flags every time.
- The assignment text has two mistakes. `je` says "jump if ZF is clear", but it must jump when the numbers are equal. `jgt` says "if SF is clear", which would also jump when equal. avmOS jumps only when the first number is strictly greater.
- `CpuTest` has tests for compare and jump, including the overflow case and the bad targets. The goldens `scott05`, `idle-n` and `idle` run whole programs.

### Try it

```
$ avmOS --max-cycles 12 16384 $S/idle.txt
[p1] r1 = 20
[p1] r1 = 20
[p1] r1 = 20
avmOS: stopped after 12 clock cycles (--max-cycles)
```

The idle program never ends. The option `--max-cycles` stops it. Three setup instructions run, then the loop of 3 instructions runs 3 times.

```
$ avmOS 16384 $S/scott05.txt
[p1] [244] = 255
[p1] [255] = 255
[p1] r3 = 255
[p1] r1 = 244
[p1] r1 = 244
[p1] r1 = 244
[p1] r1 = 244
[p1] exited after 30 clock cycles
```

Each `r1 = 244` comes after a jump that skips an `exit`: `jmp`, `jlt`, `jgt` and `je`.

`idle-n.txt` counts to 100. It prints `[p1] r1 = 20` 100 times, then `[p1] exited after 604 clock cycles`.

Now save this as `middle.txt`:

```
6 r1, $5
13 r1
6 r2, $27
```

```
$ avmOS 16384 middle.txt
[p1] exited after 3 clock cycles
```

The `jmp` goes 5 bytes into the next instruction. There, the constant `27` is read as an opcode. The opcode 27 is `exit`.

### In real computers

x86 has the flags `ZF`, `SF`, `CF` and `OF`. The flag `OF` (overflow) exists because a subtraction can overflow: the biggest negative number minus 1 wraps to a positive number. A signed "less than" test on x86 uses `SF` and `OF` together. avmOS compares the numbers directly instead. Relative jumps are normal on x86 and ARM. On x86, instructions have different lengths, so starting at another byte can give a different instruction. Some attacks use this.

### Questions to think about

1. How would you write a loop that runs 10 times with compare and jump?
2. Why must `jgt` look at `ZF` too?
3. In `idle-n.txt`, how many instructions run in one pass of the loop?

---

## Step 6 — The stack ✅

### The idea

A program needs a place for return addresses and for temporary values. This place is the **stack**. It is last-in, first-out. The register `sp` holds the address of the last word pushed. The stack grows towards lower addresses:

- **push**: `sp` goes down by 4, then the value is written there.
- **pop**: the value is read, then `sp` goes up by 4.

`call` pushes the address of the next instruction and jumps. `ret` pops that address into `eip`. A procedure can be called from anywhere and still return to the right place.

**Recursion** works because each call has its own return address on the stack.

The stack is finite. A procedure that calls itself forever fills it. This is a **stack overflow**. avmOS stops a push below the stack region, so the stack cannot write over the data under it.

`ret` trusts the stack. If the stack holds a wrong number, `ret` jumps there.

One more rule: an instruction that faults must change nothing. The CPU checks everything first and changes `sp` and the registers last. The instruction can then run again. Step 16 needs this.

### How avmOS shows it

- `Cpu` has `push`, `pop` and `call`. `CpuContext` holds the stack limits, and `Kernel.load` sets them from `ProcessLayout`.
- Faults: `stack overflow`, `stack underflow` and `invalid stack pointer`.
- `CpuTest` has tests for push, pop, call, overflow, underflow and faults that must change nothing. The goldens are `scott03`, `scott04`, `scott06`, `recursion`, `overflow` and `underflow`.

### Try it

`recursion.txt` counts down from 3 and then counts up again as the calls return:

```
$ avmOS 16384 $T/recursion.txt
[p1] r1 = 3
[p1] r1 = 2
[p1] r1 = 1
[p1] r1 = 1
[p1] r1 = 2
[p1] r1 = 3
[p1] exited after 41 clock cycles
```

`overflow.txt` calls itself forever:

```
$ avmOS 16384 $T/overflow.txt
[p1] fault at eip=0x0009: stack overflow
[p1] r1=0 r2=-14 r3=0 r4=0 r5=0 r6=0 r7=0 r8=1 r9=14 r10=4110 eip=0x0009 sf=0 zf=0
[p1] terminated after 2049 clock cycles
```

The stack has 1024 words. The 1024 calls work, and each takes 2 instructions. The `movi` of the 1025th round runs, and then its `call` faults. That makes 2049 cycles. `r10 = 4110` is the bottom of the stack.

`underflow.txt` is one `ret` with nothing on the stack:

```
$ avmOS 16384 $T/underflow.txt
[p1] fault at eip=0x0000: stack underflow
[p1] r1=0 r2=0 r3=0 r4=0 r5=0 r6=0 r7=0 r8=1 r9=1 r10=8193 eip=0x0000 sf=0 zf=0
[p1] terminated after 0 clock cycles
```

### In real computers

On x86, `call` pushes the return address and `ret` pops it. A **buffer overflow** attack writes over a return address on the stack, so `ret` jumps to the attacker's code. Operating systems often put an unmapped guard page next to the stack, so an overflow is a fault. Java throws `StackOverflowError` for the same problem. Real CPUs make a faulting instruction restartable: after the OS fixes the cause, the same instruction runs again.

### Questions to think about

1. Why does `ret` need a stack? Could the CPU keep the return address in a register instead? What would break?
2. Why must a stack overflow be a fault and not silent?
3. Draw the stack of `recursion.txt` at its deepest point. What is on it?

---

## Planned steps 🔜

These sections are short. They will be completed when each step is done.

### Step 7 — Processes
The OS keeps one record for each program: the **process control block** (PCB). It holds the registers, the state and the counters. Several programs share one RAM, so the OS gives each one a block of it (**base and limit**) and keeps a list of free blocks. You will see **external fragmentation**: there is enough free memory, but not in one piece.

### Step 8 — Switching
After 10 cycles the OS stops the running program, saves its context in the PCB, and runs another one. This is **preemption**. You will also see why an **idle process** must exist: the CPU always needs something to run. Real systems use a timer interrupt for this.

### Step 9 — Priorities
The scheduler always picks the highest-priority program that can run. Programs with the same priority take turns. You will see **starvation**: a low-priority program may never run.

### Step 10 — Sleep
A program can wait for some time. It leaves the ready queue, and a timer wakes it later. You will see the **process states** (ready, running, sleeping, blocked) and a **deadlock**: when every program sleeps forever, the OS must notice it.

### Step 11 — Locks
Two programs that change the same data at the same time give wrong results. This is a **race condition**. A **lock** lets only one program in at a time. The others wait in a queue and do not use the CPU while they wait.

### Step 12 — Priority inversion
A low-priority program holds a lock. A high-priority program waits for it. A medium-priority program keeps the CPU, so the low one cannot finish. This happened on the Mars Pathfinder probe in 1997. The fix is **priority inheritance**. You will also see how the OS cleans up everything a program held when it ends.

### Step 13 — Events
A lock keeps programs apart. An **event** lets one program tell another: "it is ready". You will see the difference between mutual exclusion and signaling.

### Step 14 — Paging
Memory is cut into equal **pages**. A **page table** maps each virtual page to a **frame** of real RAM. Pages of one process can be anywhere in RAM, so external fragmentation is gone. Every access goes through the table. Real CPUs keep a cache of recent translations (the TLB).

### Step 15 — The heap
A program asks for memory with `alloc` and gives it back with `free`. You will build the allocator yourself and see **fragmentation** and **coalescing** (joining free neighbors). This is what `malloc` and `free` do in C.

### Step 16 — Demand paging
A page comes into RAM only when the program first uses it. The CPU raises a **page fault**, and the OS loads the page. When RAM is full, the OS sends a page to a file (**swap**) to make room. You will compare ways to choose the page (FIFO, clock) and see **thrashing**. This is where the rule "a faulting instruction changes nothing" from step 6 pays off.

### Step 17 — Shared memory
Two page tables can point to the same frame. Two programs then share data without copying it. You will see why they still need locks or events.

### Step 18 — Tracing
A `--trace` option prints every instruction, every scheduling decision and every page fault. It works like a small `strace` for the whole machine.

---

## Words used

- **Address space**: the memory a process can use, numbered from 0.
- **Assembler**: a program that turns assembly text into machine code.
- **Clock cycle**: one step of the machine's clock. Here, every instruction takes one.
- **eip**: the instruction pointer. It holds the address of the next instruction.
- **Endianness**: the order of the bytes of a number in memory. Little-endian stores the smallest byte first.
- **Fault**: the program did something impossible. The OS ends it.
- **Flag**: one bit that records a result, such as "equal" (`ZF`).
- **Golden test**: a test that compares all output with a file written by hand.
- **Opcode**: the number that says which instruction this is.
- **Operand**: a value an instruction works on: a register or a constant.
- **PCB**: process control block. The OS record of one process.
- **Register**: a small, fast storage place inside the CPU.
- **Segmentation fault**: a fault caused by an access to memory the process does not own.
- **sp**: the stack pointer. It is register `r10`.
- **Trap**: a request from a program to the OS.
- **Two's complement**: the way to store negative whole numbers in binary.

## Further reading

- *Operating Systems: Three Easy Pieces* by Remzi and Andrea Arpaci-Dusseau. It is free online.
- *Computer Systems: A Programmer's Perspective* by Randal Bryant and David O'Hallaron.
- Gynvael Coldwind, *Zrozumieć programowanie* (in Polish). The `ReadMe.md` names it as the starting point of this project.
