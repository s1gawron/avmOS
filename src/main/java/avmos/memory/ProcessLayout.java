package avmos.memory;

/**
 * Where the regions of a process's address space are. The regions follow each other with no
 * gaps:
 * <pre>
 * 0                code
 * codeSize         global data (4 KB, zeroed)
 * codeSize + 4096  stack (4 KB, grows towards lower addresses)
 * codeSize + 8192  first address that the process does not own
 * </pre>
 */
public record ProcessLayout(int codeSize) {

    public static final int DATA_SIZE = 4096;
    public static final int STACK_SIZE = 4096;

    public ProcessLayout {
        if (codeSize < 0) {
            throw new IllegalArgumentException("code size must not be negative, got " + codeSize);
        }
    }

    /** First address of the global data region. A new process gets it in r9. */
    public int dataStart() {
        return codeSize;
    }

    /** Lowest address of the stack region. */
    public int stackBottom() {
        return codeSize + DATA_SIZE;
    }

    /**
     * The address just above the stack. A new process gets it in r10. It is not part of the
     * stack: a push first lowers sp by 4 and then writes.
     */
    public int stackTop() {
        return codeSize + DATA_SIZE + STACK_SIZE;
    }

    /** Number of bytes the process owns. */
    public int size() {
        return stackTop();
    }
}
