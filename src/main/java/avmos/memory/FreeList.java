package avmos.memory;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * Keeps track of the free bytes of one address range as a list of free blocks, sorted by
 * address. Blocks are handed out first-fit: the lowest block that is big enough. A freed block
 * is joined with the free blocks next to it, so neighbors do not stay split.
 *
 * <p>The list knows nothing about what the range is. It manages the physical memory of the
 * machine now, and can manage the heap of a process later.
 */
public final class FreeList {

    private final int rangeStart;
    private final int rangeEnd;
    private final List<MemoryBlock> free = new ArrayList<>();

    public FreeList(int start, int size) {
        MemoryBlock whole = new MemoryBlock(start, size);
        this.rangeStart = whole.start();
        this.rangeEnd = whole.end();
        free.add(whole);
    }

    /**
     * Takes {@code size} bytes from the first free block that has room.
     *
     * @return the address of the new block, or empty if no free block is big enough. The list is
     * not changed then.
     */
    public OptionalInt allocate(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive, got " + size);
        }
        for (int i = 0; i < free.size(); i++) {
            MemoryBlock block = free.get(i);
            if (block.size() < size) {
                continue;
            }
            if (block.size() == size) {
                free.remove(i);
            } else {
                free.set(i, new MemoryBlock(block.start() + size, block.size() - size));
            }
            return OptionalInt.of(block.start());
        }
        return OptionalInt.empty();
    }

    /**
     * Gives a block back. It is joined with the free block just before it and just after it, if
     * there are such blocks.
     *
     * @throws IllegalArgumentException if the block is outside the range or overlaps free
     *                                  memory (freeing something twice)
     */
    public void free(int start, int size) {
        MemoryBlock block = new MemoryBlock(start, size);
        if (block.start() < rangeStart || block.end() > rangeEnd) {
            throw new IllegalArgumentException("block " + block + " is outside " + rangeStart + ".." + rangeEnd);
        }

        int index = 0;
        while (index < free.size() && free.get(index).start() < block.start()) {
            index++;
        }
        MemoryBlock before = index > 0 ? free.get(index - 1) : null;
        MemoryBlock after = index < free.size() ? free.get(index) : null;
        if ((before != null && before.end() > block.start()) || (after != null && block.end() > after.start())) {
            throw new IllegalArgumentException("block " + block + " overlaps free memory (freed twice?)");
        }

        int newStart = block.start();
        int newEnd = block.end();
        if (after != null && after.start() == newEnd) {
            newEnd = after.end();
            free.remove(index);
        }
        if (before != null && before.end() == newStart) {
            newStart = before.start();
            free.remove(index - 1);
            index--;
        }
        free.add(index, new MemoryBlock(newStart, newEnd - newStart));
    }

    public int freeBytes() {
        int total = 0;
        for (MemoryBlock block : free) {
            total += block.size();
        }
        return total;
    }

    /** Size of the biggest free block, or 0 if nothing is free. */
    public int largestBlock() {
        int largest = 0;
        for (MemoryBlock block : free) {
            largest = Math.max(largest, block.size());
        }
        return largest;
    }

    /** A copy of the free blocks, lowest address first. */
    public List<MemoryBlock> blocks() {
        return List.copyOf(free);
    }
}
