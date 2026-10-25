package avmos.memory;

/** A range of bytes: {@code start} is the first byte and {@code end()} is the first byte after it. */
public record MemoryBlock(int start, int size) {

    public MemoryBlock {
        if (start < 0) {
            throw new IllegalArgumentException("block start must not be negative, got " + start);
        }
        if (size <= 0) {
            throw new IllegalArgumentException("block size must be positive, got " + size);
        }
        if ((long) start + size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("block " + start + "+" + size + " is beyond the 32-bit address range");
        }
    }

    public int end() {
        return start + size;
    }

    /** The range as the memory map prints it: {@code start..end}. The end is not part of the block. */
    @Override
    public String toString() {
        return start + ".." + end();
    }
}
