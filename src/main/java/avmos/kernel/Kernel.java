package avmos.kernel;

import avmos.BootConfig;
import avmos.Fault;
import avmos.asm.Operand;
import avmos.asm.Program;
import avmos.cpu.Cpu;
import avmos.cpu.CpuContext;
import avmos.cpu.Trap;
import avmos.io.Console;
import avmos.memory.BaseLimitAddressSpace;
import avmos.memory.FreeList;
import avmos.memory.MemoryBlock;
import avmos.memory.PhysicalMemory;
import avmos.memory.ProcessLayout;
import avmos.process.Pcb;
import avmos.process.ProcessState;
import avmos.process.ProcessTable;

import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Collectors;

/**
 * The operating system. It loads all programs into the one memory of the machine, then runs the
 * processes one after another until each ends. The scheduler (processes that take turns) comes
 * in the next steps.
 */
public final class Kernel {

    private final BootConfig config;
    private final Console console;
    private final Cpu cpu;
    private final PhysicalMemory memory;
    private final FreeList freeMemory;
    private final ProcessTable processes = new ProcessTable();

    /** The machine clock: instructions executed so far by all processes. */
    private long clock;

    public Kernel(BootConfig config, Console console) {
        this(config, console, new PhysicalMemory(config.memoryBytes()));
    }

    /** For tests: they can prepare the memory first, for example fill it with garbage. */
    Kernel(BootConfig config, Console console, PhysicalMemory memory) {
        if (memory.size() != config.memoryBytes()) {
            throw new IllegalArgumentException("memory has " + memory.size() + " bytes, the boot configuration says "
                    + config.memoryBytes());
        }
        this.config = config;
        this.console = console;
        this.cpu = new Cpu(console);
        this.memory = memory;
        this.freeMemory = new FreeList(0, memory.size());
    }

    public void run(List<Program> programs) {
        loadAll(programs);
        for (Pcb process : processes.all()) {
            if (!runProcess(process)) {
                return;
            }
        }
    }

    /** Bytes of memory that no process uses. */
    public int freeMemoryBytes() {
        return freeMemory.freeBytes();
    }

    /** Number of processes that exist (loaded and not ended). */
    public int processCount() {
        return processes.size();
    }

    /**
     * The process id is the position of the program on the command line, also when an earlier
     * program could not be loaded.
     */
    private void loadAll(List<Program> programs) {
        int pid = 1;
        for (Program program : programs) {
            load(pid++, program);
        }
        if (config.memoryMap()) {
            printFreeMemory();
        }
    }

    /** @return false if the whole run must stop (the cycle limit was reached) */
    private boolean runProcess(Pcb process) {
        CpuContext context = process.context();
        String tag = "[p" + process.pid() + "]";
        process.setState(ProcessState.RUNNING);

        while (true) {
            if (config.maxCycles() > 0 && clock >= config.maxCycles()) {
                console.println("avmOS: stopped after " + clock + " clock cycles (--max-cycles)");
                return false;
            }

            try {
                Trap trap = cpu.step(context);
                clock++;
                process.countCycle();
                if (trap instanceof Trap.Exit) {
                    console.println(tag + " exited after " + process.cycles() + " clock cycles");
                    release(process);
                    return true;
                }
            } catch (Fault fault) {
                // The instruction that faulted did not finish, so it does not count as a cycle.
                console.println(String.format("%s fault at eip=0x%04x: %s", tag, context.eip(), fault.getMessage()));
                console.println(tag + " " + context.dump());
                console.println(tag + " terminated after " + process.cycles() + " clock cycles");
                release(process);
                return true;
            }
        }
    }

    /**
     * The loader: finds room for the program in the memory, builds its address space (code,
     * global data, stack) and creates the process with the registers a new process starts with.
     * A program that does not fit is reported and skipped.
     */
    private void load(int pid, Program program) {
        ProcessLayout layout = new ProcessLayout(program.code().length);
        OptionalInt start = freeMemory.allocate(layout.size());
        if (start.isEmpty()) {
            console.println("avmOS: " + program.name() + " does not fit in memory (needs " + layout.size()
                    + " bytes: " + layout.codeSize() + " code + " + ProcessLayout.DATA_SIZE + " data + "
                    + ProcessLayout.STACK_SIZE + " stack; free: " + freeMemory.freeBytes()
                    + " bytes, largest block: " + freeMemory.largestBlock() + " bytes)");
            return;
        }
        MemoryBlock block = new MemoryBlock(start.getAsInt(), layout.size());

        // Freed memory still holds the bytes of its last owner. A new process must start clean.
        memory.clear(block.start(), block.size());
        memory.write(block.start(), program.code());

        CpuContext context = new CpuContext(pid, new BaseLimitAddressSpace(memory, block.start(), block.size()),
                layout.stackBottom(), layout.stackTop());
        context.set(8, pid);
        context.set(9, layout.dataStart());
        context.set(Operand.SP, layout.stackTop());
        context.setEip(0);

        processes.add(new Pcb(program.name(), context, block));
        if (config.memoryMap()) {
            console.println("[p" + pid + "] loaded at " + block + " (" + layout.codeSize() + " code + "
                    + ProcessLayout.DATA_SIZE + " data + " + ProcessLayout.STACK_SIZE + " stack)");
        }
    }

    /** A process ended: its memory goes back to the free list and its PCB is removed. */
    private void release(Pcb process) {
        freeMemory.free(process.memory().start(), process.memory().size());
        processes.remove(process.pid());
        if (config.memoryMap()) {
            console.println("[p" + process.pid() + "] memory freed " + process.memory());
            printFreeMemory();
        }
    }

    private void printFreeMemory() {
        List<MemoryBlock> blocks = freeMemory.blocks();
        if (blocks.isEmpty()) {
            console.println("memory: no free memory");
            return;
        }
        console.println("memory: free " + blocks.stream()
                .map(block -> block + " (" + block.size() + " bytes)")
                .collect(Collectors.joining(", ")));
    }
}
