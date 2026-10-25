package avmos.process;

/** What a process is doing. Sleeping and blocked states come with sleep and locks. */
public enum ProcessState {
    /** Loaded and waiting for the CPU. */
    READY,
    /** On the CPU now. */
    RUNNING
}
