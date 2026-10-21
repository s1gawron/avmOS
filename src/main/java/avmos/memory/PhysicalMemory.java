package avmos.memory;

/**
 * The machine's RAM: a flat array of bytes. Integers are stored little-endian, the same way
 * the assembler writes them.
 */
public final class PhysicalMemory {

    private final byte[] bytes;

    public PhysicalMemory(int size) {
        this.bytes = new byte[size];
    }

    public int size() {
        return bytes.length;
    }

    /** Returns the byte at this address as a number from 0 to 255. */
    public int readByte(int address) {
        check(address, 1);
        return bytes[address] & 0xff;
    }

    public int readInt(int address) {
        check(address, Integer.BYTES);
        return (bytes[address] & 0xff)
                | (bytes[address + 1] & 0xff) << 8
                | (bytes[address + 2] & 0xff) << 16
                | (bytes[address + 3] & 0xff) << 24;
    }

    public void writeByte(int address, int value) {
        check(address, 1);
        bytes[address] = (byte) value;
    }

    public void writeInt(int address, int value) {
        check(address, Integer.BYTES);
        bytes[address] = (byte) value;
        bytes[address + 1] = (byte) (value >> 8);
        bytes[address + 2] = (byte) (value >> 16);
        bytes[address + 3] = (byte) (value >> 24);
    }

    public void write(int address, byte[] data) {
        check(address, data.length);
        System.arraycopy(data, 0, bytes, address, data.length);
    }

    private void check(int address, int length) {
        // long arithmetic: address + length must not wrap around for addresses near 2^31
        if (address < 0 || (long) address + length > bytes.length) {
            throw new MemoryFault("invalid memory access at address 0x" + Integer.toHexString(address));
        }
    }
}
