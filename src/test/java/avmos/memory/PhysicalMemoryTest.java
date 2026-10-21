package avmos.memory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PhysicalMemoryTest {

    @Test
    void startsFilledWithZeros() {
        PhysicalMemory memory = new PhysicalMemory(8);

        assertEquals(0, memory.readInt(0));
        assertEquals(0, memory.readByte(7));
    }

    @Test
    void storesIntegersLittleEndian() {
        PhysicalMemory memory = new PhysicalMemory(8);

        memory.writeInt(0, 0x04030201);

        assertEquals(0x01, memory.readByte(0));
        assertEquals(0x02, memory.readByte(1));
        assertEquals(0x03, memory.readByte(2));
        assertEquals(0x04, memory.readByte(3));
        assertEquals(0x04030201, memory.readInt(0));
    }

    @Test
    void roundTripsNegativeNumbersAndUnalignedAddresses() {
        PhysicalMemory memory = new PhysicalMemory(16);

        memory.writeInt(3, -19);
        memory.writeInt(7, Integer.MIN_VALUE);

        assertEquals(-19, memory.readInt(3));
        assertEquals(Integer.MIN_VALUE, memory.readInt(7));
        assertEquals(0xff, memory.readByte(3 + 3));
    }

    @Test
    void writesAByteArray() {
        PhysicalMemory memory = new PhysicalMemory(8);

        memory.write(2, new byte[]{1, 2, 3});

        assertEquals(3, memory.readByte(4));
        assertEquals(0, memory.readByte(5));
    }

    @Test
    void faultsOutsideMemory() {
        PhysicalMemory memory = new PhysicalMemory(8);

        assertThrows(MemoryFault.class, () -> memory.readByte(-1));
        assertThrows(MemoryFault.class, () -> memory.readByte(8));
        assertThrows(MemoryFault.class, () -> memory.readInt(-4));
        assertThrows(MemoryFault.class, () -> memory.writeInt(8, 1));
        assertThrows(MemoryFault.class, () -> memory.write(6, new byte[3]));
    }

    @Test
    void faultsWhenAnIntegerCrossesTheEnd() {
        PhysicalMemory memory = new PhysicalMemory(8);

        memory.readInt(4); // the last 4 bytes are fine
        assertThrows(MemoryFault.class, () -> memory.readInt(5));
        assertThrows(MemoryFault.class, () -> memory.writeInt(7, 1));
    }

    @Test
    void addressesNearTheIntegerLimitDoNotWrapAround() {
        PhysicalMemory memory = new PhysicalMemory(8);

        assertThrows(MemoryFault.class, () -> memory.readInt(Integer.MAX_VALUE));
        assertThrows(MemoryFault.class, () -> memory.readInt(Integer.MAX_VALUE - 2));
    }
}
