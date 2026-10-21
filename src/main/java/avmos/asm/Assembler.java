package avmos.asm;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns program text into machine code.
 *
 * <p>Each line is {@code <decimal opcode> <operands> ;comment}. The comma between operands is
 * optional and any mix of spaces and tabs may separate the parts. The assembler has two jobs:
 * check that every line is valid, then lay the instructions out as bytes.
 */
public final class Assembler {

    private Assembler() {
    }

    public static Program assemble(String name, List<String> lines) {
        List<Instruction> instructions = new ArrayList<>();
        int offset = 0;

        for (int i = 0; i < lines.size(); i++) {
            int lineNumber = i + 1;
            String text = stripComment(lines.get(i)).trim();
            if (text.isEmpty()) {
                continue;
            }

            Instruction instruction = parseLine(text, offset, lineNumber);
            instructions.add(instruction);
            offset += instruction.sizeInBytes();
        }

        return new Program(name, instructions, encode(instructions, offset));
    }

    private static String stripComment(String line) {
        int comment = line.indexOf(';');
        return comment < 0 ? line : line.substring(0, comment);
    }

    private static Instruction parseLine(String text, int offset, int lineNumber) {
        String[] tokens = text.split("[\\s,]+");

        Opcode opcode = parseOpcode(tokens[0], lineNumber);
        int operandCount = tokens.length - 1;
        if (operandCount != opcode.signature().size()) {
            throw new AssemblyException(lineNumber, opcode.mnemonic() + " takes "
                    + opcode.signature().size() + " operand(s), found " + operandCount);
        }

        List<Operand> operands = new ArrayList<>();
        for (int i = 0; i < operandCount; i++) {
            Operand operand = parseOperand(tokens[i + 1], lineNumber);
            checkKind(opcode, i, operand, lineNumber);
            operands.add(operand);
        }
        return new Instruction(opcode, operands, offset, lineNumber);
    }

    private static Opcode parseOpcode(String token, int lineNumber) {
        int value;
        try {
            value = Integer.parseInt(token);
        } catch (NumberFormatException e) {
            throw new AssemblyException(lineNumber, "opcode must be a decimal number, found '" + token + "'");
        }
        Opcode opcode = Opcode.fromValue(value);
        if (opcode == null) {
            throw new AssemblyException(lineNumber, "unknown opcode " + value);
        }
        return opcode;
    }

    private static Operand parseOperand(String token, int lineNumber) {
        String text = token.toLowerCase();

        if (text.equals("sp")) {
            return new Operand.Register(Operand.SP);
        }
        if (text.startsWith("$")) {
            try {
                return new Operand.Immediate(Integer.parseInt(text.substring(1)));
            } catch (NumberFormatException e) {
                throw new AssemblyException(lineNumber, "bad constant '" + token + "' (must fit in 32 bits)");
            }
        }
        if (text.startsWith("r")) {
            try {
                return new Operand.Register(Integer.parseInt(text.substring(1)));
            } catch (IllegalArgumentException e) {
                throw new AssemblyException(lineNumber, "bad register '" + token + "' (use r1..r10 or sp)");
            }
        }
        throw new AssemblyException(lineNumber, "bad operand '" + token + "'");
    }

    private static void checkKind(Opcode opcode, int index, Operand operand, int lineNumber) {
        Opcode.Kind expected = opcode.signature().get(index);
        boolean isRegister = operand instanceof Operand.Register;
        if (isRegister != (expected == Opcode.Kind.REG)) {
            String wanted = expected == Opcode.Kind.REG ? "a register" : "a constant";
            throw new AssemblyException(lineNumber, opcode.mnemonic() + " operand " + (index + 1)
                    + " must be " + wanted + ", found '" + operand + "'");
        }
    }

    /** One opcode byte, then each operand as a little-endian 32-bit integer. */
    private static byte[] encode(List<Instruction> instructions, int totalSize) {
        ByteBuffer buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN);
        for (Instruction instruction : instructions) {
            buffer.put((byte) instruction.opcode().value());
            for (Operand operand : instruction.operands()) {
                buffer.putInt(operand.encoded());
            }
        }
        return buffer.array();
    }
}
