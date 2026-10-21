package avmos.asm;

/** A program line that cannot be assembled. */
public class AssemblyException extends RuntimeException {

    private final int line;

    public AssemblyException(int line, String message) {
        super("line " + line + ": " + message);
        this.line = line;
    }

    /** 1-based source line number. */
    public int line() {
        return line;
    }
}
