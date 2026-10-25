package avmos.cpu;

import avmos.Fault;
import avmos.asm.Opcode;
import avmos.io.Console;
import avmos.memory.AddressSpace;

/**
 * The processor. It reads the program from memory as raw bytes (it never sees the
 * {@code Instruction} objects of the assembler) and executes one instruction per call. All
 * memory access goes through the address space of the process that is running.
 */
public final class Cpu {

    private final Console console;

    public Cpu(Console console) {
        this.console = console;
    }

    /**
     * Fetches, decodes and executes the instruction at eip.
     *
     * @return the request for the OS if the instruction needs one, or {@code null} if the
     * program just continues
     * @throws Fault if the instruction cannot be executed. eip then points at the faulting
     *               instruction, as on a real CPU.
     */
    public Trap step(CpuContext ctx) {
        AddressSpace memory = ctx.memory();
        int start = ctx.eip();

        // fetch
        int opcodeByte = memory.readByte(start);
        Opcode opcode = Opcode.fromValue(opcodeByte);
        if (opcode == null) {
            throw new Fault("unknown opcode " + opcodeByte);
        }

        // decode: one 4-byte number per operand. The CPU does not know yet whether
        // a number is a register or a constant. The opcode decides in execute().
        int[] operands = new int[opcode.signature().size()];
        for (int i = 0; i < operands.length; i++) {
            operands[i] = memory.readInt(start + 1 + Opcode.OPERAND_BYTES * i);
        }

        // eip moves to the next instruction before execution. This is why jumps are
        // relative to the next instruction.
        ctx.setEip(start + opcode.sizeInBytes());

        try {
            return execute(ctx, opcode, operands);
        } catch (Fault fault) {
            ctx.setEip(start);
            throw fault;
        }
    }

    private Trap execute(CpuContext ctx, Opcode opcode, int[] operands) {
        AddressSpace memory = ctx.memory();
        switch (opcode) {
            case INCR -> ctx.set(operands[0], ctx.get(operands[0]) + 1);
            case ADDI -> ctx.set(operands[0], ctx.get(operands[0]) + operands[1]);
            case ADDR -> ctx.set(operands[0], ctx.get(operands[0]) + ctx.get(operands[1]));
            case MOVI -> ctx.set(operands[0], operands[1]);
            case MOVR -> ctx.set(operands[0], ctx.get(operands[1]));
            // The "m" in the names marks the operand that is a memory address held in a register.
            case MOVMR -> ctx.set(operands[0], memory.readInt(ctx.get(operands[1])));
            case MOVRM -> memory.writeInt(ctx.get(operands[0]), ctx.get(operands[1]));
            case MOVMM -> memory.writeInt(ctx.get(operands[0]), memory.readInt(ctx.get(operands[1])));
            case PRINTR -> console.println(
                    "[p" + ctx.pid() + "] r" + operands[0] + " = " + ctx.get(operands[0]));
            case PRINTM -> {
                int address = ctx.get(operands[0]);
                console.println("[p" + ctx.pid() + "] [" + address + "] = " + memory.readInt(address));
            }
            case MEMORYCLEAR -> memory.clear(ctx.get(operands[0]), ctx.get(operands[1]));
            case INPUT -> ctx.set(operands[0],
                    console.readInt("[p" + ctx.pid() + "] input r" + operands[0] + ": "));
            case CMPI -> compare(ctx, ctx.get(operands[0]), operands[1]);
            case CMPR -> compare(ctx, ctx.get(operands[0]), ctx.get(operands[1]));
            // The offset register is read even when the jump is not taken, so a bad register
            // number always faults.
            case JMP -> jump(ctx, ctx.get(operands[0]), true);
            case JLT -> jump(ctx, ctx.get(operands[0]), ctx.sf());
            case JGT -> jump(ctx, ctx.get(operands[0]), !ctx.sf() && !ctx.zf());
            case JE -> jump(ctx, ctx.get(operands[0]), ctx.zf());
            case SETPRIORITY -> {
                // The scheduler does not use priorities yet.
            }
            case EXIT -> {
                return new Trap.Exit();
            }
            default -> throw new Fault("opcode " + opcode.mnemonic() + " is not implemented yet");
        }
        return null;
    }

    /**
     * Sets both flags from a signed comparison. Comparing is not the same as subtracting: the
     * difference of two ints can overflow and then has the wrong sign (MIN_VALUE - 1 is
     * positive). A real CPU has an overflow flag for this. This machine has none.
     */
    private static void compare(CpuContext ctx, int a, int b) {
        int order = Integer.compare(a, b);
        ctx.setSf(order < 0);
        ctx.setZf(order == 0);
    }

    /**
     * eip already points at the next instruction, so the offset is relative to it. The CPU does
     * not check the target: a bad one shows up as a fault when the next instruction is fetched.
     */
    private static void jump(CpuContext ctx, int offset, boolean taken) {
        if (taken) {
            ctx.setEip(ctx.eip() + offset);
        }
    }
}
