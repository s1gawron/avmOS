package avmos.cpu;

import avmos.Fault;
import avmos.asm.Operand;
import avmos.memory.AddressSpace;

/**
 * Everything the CPU needs to resume a program: the registers, the instruction pointer, the
 * two flags and the address space. The address space is part of the context the way the page
 * table register is on a real CPU: switching to another process switches the view of memory
 * together with the registers. Later each process control block owns one context.
 */
public final class CpuContext {

    private final int pid;
    private final AddressSpace memory;
    private final int[] registers = new int[Operand.MAX_REGISTER + 1]; // index 0 is not used
    private int eip;
    private boolean sf;
    private boolean zf;

    public CpuContext(int pid, AddressSpace memory) {
        this.pid = pid;
        this.memory = memory;
    }

    public int pid() {
        return pid;
    }

    public AddressSpace memory() {
        return memory;
    }

    public int get(int register) {
        checkRegister(register);
        return registers[register];
    }

    public void set(int register, int value) {
        checkRegister(register);
        registers[register] = value;
    }

    public int eip() {
        return eip;
    }

    public void setEip(int eip) {
        this.eip = eip;
    }

    public boolean sf() {
        return sf;
    }

    public void setSf(boolean sf) {
        this.sf = sf;
    }

    public boolean zf() {
        return zf;
    }

    public void setZf(boolean zf) {
        this.zf = zf;
    }

    /** All registers, eip and flags on one line, for fault reports. */
    public String dump() {
        StringBuilder out = new StringBuilder();
        for (int r = Operand.MIN_REGISTER; r <= Operand.MAX_REGISTER; r++) {
            out.append('r').append(r).append('=').append(registers[r]).append(' ');
        }
        out.append(String.format("eip=0x%04x sf=%d zf=%d", eip, sf ? 1 : 0, zf ? 1 : 0));
        return out.toString();
    }

    // The register number comes from program bytes, so it can be anything.
    private static void checkRegister(int register) {
        if (register < Operand.MIN_REGISTER || register > Operand.MAX_REGISTER) {
            throw new Fault("invalid register r" + register);
        }
    }
}
