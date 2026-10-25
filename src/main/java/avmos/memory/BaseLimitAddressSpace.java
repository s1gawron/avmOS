package avmos.memory;

/**
 * An address space made of one block of physical memory. Virtual address 0 is the first byte
 * of the block ({@code base}), and the process owns {@code size} bytes. The CPU adds the base to
 * every address and checks it against the size, so the same program works at any base and
 * cannot touch the memory of its neighbors.
 *
 * <p>Faults name the virtual address, the one the program used.
 */
public final class BaseLimitAddressSpace implements AddressSpace {

    private final PhysicalMemory memory;
    private final int base;
    private final int size;

    public BaseLimitAddressSpace(PhysicalMemory memory, int base, int size) {
        if (base < 0 || size < 0 || (long) base + size > memory.size()) {
            throw new IllegalArgumentException("block " + base + ".." + ((long) base + size)
                    + " does not fit in " + memory.size() + " bytes of memory");
        }
        this.memory = memory;
        this.base = base;
        this.size = size;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public int readByte(int address) {
        check(address, 1);
        return memory.readByte(base + address);
    }

    @Override
    public void writeByte(int address, int value) {
        check(address, 1);
        memory.writeByte(base + address, value);
    }

    @Override
    public int readInt(int address) {
        check(address, Integer.BYTES);
        return memory.readInt(base + address);
    }

    @Override
    public void writeInt(int address, int value) {
        check(address, Integer.BYTES);
        memory.writeInt(base + address, value);
    }

    private void check(int address, int length) {
        // long arithmetic: address + length must not wrap around for addresses near 2^31
        if (address < 0 || (long) address + length > size) {
            throw new MemoryFault("invalid memory access at address " + address);
        }
    }
}
