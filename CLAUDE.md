# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

avmOS is a "virtual operating system" for an abstract 32-bit machine, inspired by Scott Hanselman's TinyOS (CST 352 final project). It is written in Java with Maven (`maven.compiler.source/target` = 25, no dependencies). The OS also acts as the hardware: it loads text programs and interprets their opcodes itself, the way a VM does.

`Scotts_OriginalAssignment.md` is the authoritative spec. It defines the machine (10 registers, r10 = sp, SF/ZF flags, eip), the opcode table, scheduling, memory, locks, events and the required process start and exit behavior. Read the relevant section before implementing machine behavior.

## Build / run

- Build and test: `mvn package` (JUnit 5) produces `target/avmOS.jar`
- Single test: `mvn test -Dtest=BootConfigTest`
- Run: `java -jar target/avmOS.jar [--dump] [--max-cycles N] <memory bytes> <program1.txt> <program2.txt> ...`
- `--dump` prints a hex listing (offset, bytes, instruction) of each assembled program and stops.
- `--max-cycles N` stops the whole run when the machine clock reaches N cycles (0 = no limit). Use it with programs that never exit, such as `idle.txt`.
- Entry point: `avmos.Main`. CLI arguments are parsed into the `avmos.BootConfig` record.

## Code layout

- `avmos.asm`: `Assembler` turns program text into a `Program` (a list of `Instruction`s plus the encoded `byte[]`). `Opcode` holds all 36 opcodes with their operand signature (register or constant). `Operand` is `Register` or `Immediate`. A bad line throws `AssemblyException` with the line number.
- Encoding: opcode byte, then each operand as a **little-endian** 32-bit int. The bytes do not tag an operand as register or constant. The opcode decides.
- `avmos.memory`: `PhysicalMemory` is a flat little-endian `byte[]`. An access outside it throws `MemoryFault`, a subclass of `avmos.Fault` (anything that terminates a process).
- `avmos.cpu`: `Cpu.step(CpuContext)` does fetch, decode, execute on the raw bytes in memory. eip moves to the next instruction *before* execution. If the instruction faults, eip is restored to it. `step` returns a `Trap` for requests to the OS (only `Exit` so far) or `null`. `CpuContext` holds pid, r1–r10, eip, SF and ZF. r1–r7 start at 0. Opcodes that are not implemented yet throw a `Fault`.
- `avmos.io`: `Console` is the only I/O device. `SystemConsole` is stdout/stdin. Tests use `FakeConsole`.
- `avmos.kernel.Kernel`: runs the programs one after another, each alone in a fresh memory, with code at address 0 and pid = position (1, 2, ...). Only completed instructions count as clock cycles (a faulting instruction does not).
- Output format: `[pN] rX = value` (printr), `[pN] exited after C clock cycles`, and on a fault `[pN] fault at eip=0x....: reason`, a register dump and `[pN] terminated after C clock cycles`.
- Golden tests (`GoldenOutputTest`): `src/test/resources/expected/<sample>.out` holds the exact output of a sample program. Write the expected file by tracing the program by hand, not by copying what the code printed. Output of old samples must not change when the OS changes inside.
- `SampleProgramsTest` checks the jump offsets in the sample programs against this encoding. Use it as the reference when you change the encoding.

## Spec essentials

- Base feature set: priority scheduling (32 levels, higher number = higher priority), Sleep (0 = forever), print, locks (10 OS locks, priority inheritance), and an idle process (`idle.txt`, 5-cycle quantum, never exits). On exit the OS frees all of the process's memory and its PCB, releases any locks it holds and wakes waiters, and prints the counts of page faults, context switches and clock cycles.
- Extended: Alloc/FreeMemory/MemoryClear, per-process page tables with page faults and file-backed paging, 10 shared memory regions (MapSharedMem), and 10 events.
- Default time quantum is 10 cycles. Context switches happen only on instruction boundaries.
- Initial registers: r8 = process id, r9 = virtual address of the 4 KB global data region, r10 = top of the 4 KB stack, eip = 0.
- An invalid memory access prints the process's registers, terminates the process and schedules another one.

## Program file format

The files in `src/main/resources/sample_programs/` have one instruction per line: `<decimal opcode> <operands> ;comment`. Operands are `rN` registers or `$N` constants, which may be negative. The comma between operands is optional (`6 r1 $10` and `6 r1, $10` both appear), and whitespace mixes tabs and spaces.

Byte encoding, inferred from the jump offsets in the samples: each instruction is a **1-byte opcode plus 4 bytes per operand**, whether the operand is a register or a constant. Jump and call offsets are relative to the **next** instruction (eip after the jump has been decoded). Checks: in `idle.txt`, `-19` jumps back to `printr`; in `idle-n.txt`, `-38` and `14` match the same encoding; in `scott05.txt`, `$1` skips a 1-byte `exit`.

`idle*.txt` are idle loops, `scott*.txt` are instructor-style test programs and `prog*.txt` are the author's own programs.
