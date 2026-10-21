package avmos.cpu;

/**
 * A request from the running program to the operating system. The CPU cannot handle these
 * itself, so {@link Cpu#step} hands them back to the kernel.
 */
public sealed interface Trap {

    /** The program executed Exit. */
    record Exit() implements Trap {
    }
}
