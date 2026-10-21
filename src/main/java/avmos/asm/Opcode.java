package avmos.asm;

import java.util.List;

/** All opcodes of the abstract machine, with their decimal value and operand signature. */
public enum Opcode {
    INCR(1, Kind.REG),
    ADDI(2, Kind.REG, Kind.IMM),
    ADDR(3, Kind.REG, Kind.REG),
    PUSHR(4, Kind.REG),
    PUSHI(5, Kind.IMM),
    MOVI(6, Kind.REG, Kind.IMM),
    MOVR(7, Kind.REG, Kind.REG),
    MOVMR(8, Kind.REG, Kind.REG),
    MOVRM(9, Kind.REG, Kind.REG),
    MOVMM(10, Kind.REG, Kind.REG),
    PRINTR(11, Kind.REG),
    PRINTM(12, Kind.REG),
    JMP(13, Kind.REG),
    CMPI(14, Kind.REG, Kind.IMM),
    CMPR(15, Kind.REG, Kind.REG),
    JLT(16, Kind.REG),
    JGT(17, Kind.REG),
    JE(18, Kind.REG),
    CALL(19, Kind.REG),
    CALLM(20, Kind.REG),
    RET(21),
    ALLOC(22, Kind.REG, Kind.REG),
    ACQUIRELOCK(23, Kind.REG),
    RELEASELOCK(24, Kind.REG),
    SLEEP(25, Kind.REG),
    SETPRIORITY(26, Kind.REG),
    EXIT(27),
    FREEMEMORY(28, Kind.REG),
    MAPSHAREDMEM(29, Kind.REG, Kind.REG),
    SIGNALEVENT(30, Kind.REG),
    WAITEVENT(31, Kind.REG),
    INPUT(32, Kind.REG),
    MEMORYCLEAR(33, Kind.REG, Kind.REG),
    TERMINATEPROCESS(34, Kind.REG),
    POPR(35, Kind.REG),
    POPM(36, Kind.REG);

    /** What an operand slot accepts: a register or a constant. */
    public enum Kind {
        REG, IMM
    }

    /** Every operand takes 4 bytes, whatever its kind. */
    public static final int OPERAND_BYTES = 4;

    private final int value;
    private final List<Kind> signature;

    Opcode(int value, Kind... signature) {
        this.value = value;
        this.signature = List.of(signature);
    }

    public int value() {
        return value;
    }

    public List<Kind> signature() {
        return signature;
    }

    public String mnemonic() {
        return name().toLowerCase();
    }

    /** 1 byte for the opcode plus 4 bytes for each operand. */
    public int sizeInBytes() {
        return 1 + OPERAND_BYTES * signature.size();
    }

    /** Returns the opcode with this decimal value, or {@code null} if there is none. */
    public static Opcode fromValue(int value) {
        for (Opcode opcode : values()) {
            if (opcode.value == value) {
                return opcode;
            }
        }
        return null;
    }
}
