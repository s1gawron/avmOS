package avmos.asm;

import java.util.List;
import java.util.stream.Collectors;

/**
 * One decoded instruction.
 *
 * @param offset byte offset of the instruction from the start of the program
 * @param line   1-based line number in the source file
 */
public record Instruction(Opcode opcode, List<Operand> operands, int offset, int line) {

    public Instruction {
        operands = List.copyOf(operands);
    }

    public int sizeInBytes() {
        return opcode.sizeInBytes();
    }

    /** Offset of the instruction after this one. Jumps and calls are relative to this address. */
    public int nextOffset() {
        return offset + sizeInBytes();
    }

    @Override
    public String toString() {
        if (operands.isEmpty()) {
            return opcode.mnemonic();
        }
        return opcode.mnemonic() + " "
                + operands.stream().map(Operand::toString).collect(Collectors.joining(", "));
    }
}
