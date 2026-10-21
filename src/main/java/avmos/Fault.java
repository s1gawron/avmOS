package avmos;

/**
 * Anything that makes the running program impossible to continue: an unknown opcode, a bad
 * register number, an access outside memory. The OS catches it and terminates the process.
 */
public class Fault extends RuntimeException {

    public Fault(String message) {
        super(message);
    }
}
