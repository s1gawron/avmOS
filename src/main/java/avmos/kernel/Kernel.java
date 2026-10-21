package avmos.kernel;

import avmos.BootConfig;
import avmos.Fault;
import avmos.asm.Operand;
import avmos.asm.Program;
import avmos.cpu.Cpu;
import avmos.cpu.CpuContext;
import avmos.cpu.Trap;
import avmos.io.Console;
import avmos.memory.FlatAddressSpace;
import avmos.memory.PhysicalMemory;
import avmos.memory.ProcessLayout;

import java.util.List;

/**
 * The operating system. For now it runs the programs one after another, each alone in a fresh
 * memory. Processes, scheduling and a shared memory come in the next steps.
 */
public final class Kernel {

    private final BootConfig config;
    private final Console console;
    private final Cpu cpu;

    /** The machine clock: instructions executed so far by all programs. */
    private long clock;

    public Kernel(BootConfig config, Console console) {
        this.config = config;
        this.console = console;
        this.cpu = new Cpu(console);
    }

    public void run(List<Program> programs) {
        int pid = 1;
        for (Program program : programs) {
            if (!runProgram(pid++, program)) {
                return;
            }
        }
    }

    /** @return false if the whole run must stop (the cycle limit was reached) */
    private boolean runProgram(int pid, Program program) {
        CpuContext context = load(pid, program);
        if (context == null) {
            return true;
        }

        String tag = "[p" + pid + "]";
        long cycles = 0;

        while (true) {
            if (config.maxCycles() > 0 && clock >= config.maxCycles()) {
                console.println("avmOS: stopped after " + clock + " clock cycles (--max-cycles)");
                return false;
            }

            try {
                Trap trap = cpu.step(context);
                clock++;
                cycles++;
                if (trap instanceof Trap.Exit) {
                    console.println(tag + " exited after " + cycles + " clock cycles");
                    return true;
                }
            } catch (Fault fault) {
                // The instruction that faulted did not finish, so it does not count as a cycle.
                console.println(String.format("%s fault at eip=0x%04x: %s", tag, context.eip(), fault.getMessage()));
                console.println(tag + " " + context.dump());
                console.println(tag + " terminated after " + cycles + " clock cycles");
                return true;
            }
        }
    }

    /**
     * The loader: builds the address space of the program (code, global data, stack) in a fresh
     * memory and sets the registers a new process starts with.
     *
     * @return the new process, or {@code null} if it does not fit in memory
     */
    private CpuContext load(int pid, Program program) {
        ProcessLayout layout = new ProcessLayout(program.code().length);
        if (layout.size() > config.memoryBytes()) {
            console.println("avmOS: " + program.name() + " does not fit in memory (needs " + layout.size()
                    + " bytes: " + layout.codeSize() + " code + " + ProcessLayout.DATA_SIZE + " data + "
                    + ProcessLayout.STACK_SIZE + " stack; memory is " + config.memoryBytes() + " bytes)");
            return null;
        }

        PhysicalMemory memory = new PhysicalMemory(config.memoryBytes());
        memory.write(0, program.code());

        CpuContext context = new CpuContext(pid, new FlatAddressSpace(memory, layout.size()));
        context.set(8, pid);
        context.set(9, layout.dataStart());
        context.set(Operand.SP, layout.stackTop());
        context.setEip(0);
        return context;
    }
}
