package avmos.asm;

/**
 * An instruction operand. Both kinds are encoded as a 4-byte integer, so the bytes alone
 * do not say whether an operand is a register or a constant: the opcode decides that.
 */
public sealed interface Operand {

    /** Register numbers go from 1 to 10. Register 10 is also the stack pointer (sp). */
    int MIN_REGISTER = 1;
    int MAX_REGISTER = 10;
    int SP = 10;

    /** The 32-bit value written into the program bytes. */
    int encoded();

    record Register(int number) implements Operand {

        public Register {
            if (number < MIN_REGISTER || number > MAX_REGISTER) {
                throw new IllegalArgumentException("register must be r1..r10, got r" + number);
            }
        }

        @Override
        public int encoded() {
            return number;
        }

        @Override
        public String toString() {
            return "r" + number;
        }
    }

    record Immediate(int value) implements Operand {

        @Override
        public int encoded() {
            return value;
        }

        @Override
        public String toString() {
            return "$" + value;
        }
    }
}
