package avmos.memory;

/**
 * The memory as one process sees it. Programs use only these addresses, never physical ones.
 * An access to an address the process does not own throws {@link MemoryFault}.
 */
public interface AddressSpace {

    /** Number of bytes the process owns. */
    int size();

    /** Returns the byte at this address as a number from 0 to 255. */
    int readByte(int address);

    void writeByte(int address, int value);

    int readInt(int address);

    void writeInt(int address, int value);

    /**
     * Sets {@code length} bytes starting at {@code address} to zero. If the range leaves the
     * address space, the bytes before the end are already cleared when the fault is thrown.
     */
    default void clear(int address, int length) {
        if (length < 0) {
            throw new MemoryFault("invalid length " + length + " for memory clear");
        }
        for (int i = 0; i < length; i++) {
            writeByte(address + i, 0);
        }
    }
}
