package avmos.kernel;

import avmos.BootConfig;
import avmos.Fault;
import avmos.asm.Program;
import avmos.cpu.Cpu;
import avmos.cpu.CpuContext;
import avmos.cpu.Trap;
import avmos.io.Console;
import avmos.memory.PhysicalMemory;

import java.util.List;

/**
 * The operating system. For now it runs the programs one after another, each alone in a fresh
 * memory. Processes, scheduling and a shared memory come in the next steps.
 */
public final class Kernel {

    private final BootConfig config;
    private final Console console;

    /** The machine clock: instructions executed so far by all programs. */
    private long clock;

    public Kernel(BootConfig config, Console console) {
        this.config = config;
        this.console = console;
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
        String tag = "[p" + pid + "]";

        if (program.code().length > config.memoryBytes()) {
            console.println("avmOS: " + program.name() + " does not fit in memory ("
                    + program.code().length + " bytes of code, " + config.memoryBytes() + " bytes of memory)");
            return true;
        }

        PhysicalMemory memory = new PhysicalMemory(config.memoryBytes());
        memory.write(0, program.code());
        CpuContext context = new CpuContext(pid);
        Cpu cpu = new Cpu(memory, console);
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
}
