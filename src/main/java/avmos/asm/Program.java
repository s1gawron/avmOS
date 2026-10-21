package avmos.asm;

import java.util.List;
import java.util.stream.Collectors;

/**
 * An assembled program: the instructions and the machine code bytes they were encoded into.
 */
public record Program(String name, List<Instruction> instructions, byte[] code) {

    public Program {
        instructions = List.copyOf(instructions);
    }

    /** Returns the instruction that starts at this byte offset, or {@code null} if none does. */
    public Instruction instructionAt(int offset) {
        for (Instruction instruction : instructions) {
            if (instruction.offset() == offset) {
                return instruction;
            }
        }
        return null;
    }

    /** A hex dump: byte offset, the encoded bytes and the instruction they stand for. */
    public String listing() {
        int bytesColumn = instructions.stream().mapToInt(Instruction::sizeInBytes).max().orElse(1) * 3 - 1;
        StringBuilder out = new StringBuilder();
        for (Instruction instruction : instructions) {
            String bytes = hex(instruction.offset(), instruction.sizeInBytes());
            out.append(String.format("%04x  %-" + bytesColumn + "s  %s%n", instruction.offset(), bytes, instruction));
        }
        return out.toString();
    }

    private String hex(int offset, int length) {
        StringBuilder bytes = new StringBuilder();
        for (int i = 0; i < length; i++) {
            if (i > 0) {
                bytes.append(' ');
            }
            bytes.append(String.format("%02x", code[offset + i]));
        }
        return bytes.toString();
    }

    @Override
    public String toString() {
        return name + " (" + code.length + " bytes, " + instructions.size() + " instructions)";
    }
}
