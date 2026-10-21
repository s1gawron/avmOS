package avmos.memory;

/**
 * The simplest address space: virtual address = physical address. The process owns the bytes
 * {@code [0, size)}. Memory behind that may exist in the machine, but it does not belong to the
 * process, so touching it is a fault.
 */
public final class FlatAddressSpace implements AddressSpace {

    private final PhysicalMemory memory;
    private final int size;

    public FlatAddressSpace(PhysicalMemory memory, int size) {
        if (size < 0 || size > memory.size()) {
            throw new IllegalArgumentException(
                    "address space of " + size + " bytes does not fit in " + memory.size() + " bytes of memory");
        }
        this.memory = memory;
        this.size = size;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public int readByte(int address) {
        check(address, 1);
        return memory.readByte(address);
    }

    @Override
    public void writeByte(int address, int value) {
        check(address, 1);
        memory.writeByte(address, value);
    }

    @Override
    public int readInt(int address) {
        check(address, Integer.BYTES);
        return memory.readInt(address);
    }

    @Override
    public void writeInt(int address, int value) {
        check(address, Integer.BYTES);
        memory.writeInt(address, value);
    }

    private void check(int address, int length) {
        // long arithmetic: address + length must not wrap around for addresses near 2^31
        if (address < 0 || (long) address + length > size) {
            throw new MemoryFault("invalid memory access at address " + address);
        }
    }
}
