package avmos.process;

import avmos.cpu.CpuContext;
import avmos.memory.MemoryBlock;

/**
 * The process control block: everything the OS knows about one process. The registers, eip,
 * flags and address space are in the {@link CpuContext}. Switching to another process means
 * running the CPU on the context of another PCB.
 */
public final class Pcb {

    private final String name;
    private final CpuContext context;
    private final MemoryBlock memory;
    private ProcessState state = ProcessState.READY;
    private long cycles;

    /**
     * @param name   the program this process runs
     * @param memory the block of physical memory that holds the process (its address space)
     */
    public Pcb(String name, CpuContext context, MemoryBlock memory) {
        this.name = name;
        this.context = context;
        this.memory = memory;
    }

    public int pid() {
        return context.pid();
    }

    public String name() {
        return name;
    }

    public CpuContext context() {
        return context;
    }

    /** The physical memory of the process. The process itself only sees addresses from 0. */
    public MemoryBlock memory() {
        return memory;
    }

    public ProcessState state() {
        return state;
    }

    public void setState(ProcessState state) {
        this.state = state;
    }

    /** Clock cycles used by this process so far. */
    public long cycles() {
        return cycles;
    }

    public void countCycle() {
        cycles++;
    }
}
