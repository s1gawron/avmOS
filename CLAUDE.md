# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

avmOS is a "virtual operating system" for an abstract 32-bit machine, inspired by Scott Hanselman's TinyOS (CST 352 final project). It is written in Java with Maven (`maven.compiler.source/target` = 25, no dependencies). The OS also acts as the hardware: it loads text programs and interprets their opcodes itself, the way a VM does.

`Scotts_OriginalAssignment.md` is the authoritative spec. It defines the machine (10 registers, r10 = sp, SF/ZF flags, eip), the opcode table, scheduling, memory, locks, events and the required process start and exit behavior. Read the relevant section before implementing machine behavior.

## Build / run

- Build and test: `mvn package` (JUnit 5) produces `target/avmOS.jar`
- Single test: `mvn test -Dtest=BootConfigTest`
- Run: `java -jar target/avmOS.jar <memory bytes> <program1.txt> <program2.txt> ...`
- Entry point: `avmos.Main`. CLI arguments are parsed into the `avmos.BootConfig` record.

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
