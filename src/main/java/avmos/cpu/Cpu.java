package avmos.cpu;

import avmos.Fault;
import avmos.asm.Opcode;
import avmos.io.Console;
import avmos.memory.PhysicalMemory;

/**
 * The processor. It reads the program from memory as raw bytes (it never sees the
 * {@code Instruction} objects of the assembler) and executes one instruction per call.
 */
public final class Cpu {

    private final PhysicalMemory memory;
    private final Console console;

    public Cpu(PhysicalMemory memory, Console console) {
        this.memory = memory;
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
        switch (opcode) {
            case INCR -> ctx.set(operands[0], ctx.get(operands[0]) + 1);
            case ADDI -> ctx.set(operands[0], ctx.get(operands[0]) + operands[1]);
            case ADDR -> ctx.set(operands[0], ctx.get(operands[0]) + ctx.get(operands[1]));
            case MOVI -> ctx.set(operands[0], operands[1]);
            case MOVR -> ctx.set(operands[0], ctx.get(operands[1]));
            case PRINTR -> console.println(
                    "[p" + ctx.pid() + "] r" + operands[0] + " = " + ctx.get(operands[0]));
            case INPUT -> ctx.set(operands[0],
                    console.readInt("[p" + ctx.pid() + "] input r" + operands[0] + ": "));
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
}
