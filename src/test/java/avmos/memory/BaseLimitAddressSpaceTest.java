package avmos.memory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BaseLimitAddressSpaceTest {

    // The machine has 100 bytes of RAM. The process owns the 40 bytes at 30..70.
    private final PhysicalMemory physical = new PhysicalMemory(100);
    private final AddressSpace space = new BaseLimitAddressSpace(physical, 30, 40);

    @Test
    void virtualAddressZeroIsTheFirstByteOfTheBlock() {
        space.writeByte(0, 7);

        assertEquals(7, physical.readByte(30));
        assertEquals(7, space.readByte(0));
    }

    @Test
    void accessesReachPhysicalMemoryAtBasePlusAddress() {
        space.writeInt(8, 0x0a0b0c0d);
        space.writeByte(20, 0xfe);

        assertEquals(0x0a0b0c0d, physical.readInt(38));
        assertEquals(0xfe, physical.readByte(50));
        assertEquals(0x0a0b0c0d, space.readInt(8));
        assertEquals(0xfe, space.readByte(20));
    }

    @Test
    void theLastBytesOfTheSpaceAreUsable() {
        space.writeInt(36, 7); // the last 4 bytes of the block: physical 66..69
        assertEquals(7, physical.readInt(66));

        space.writeByte(39, 1); // the very last byte: physical 69
        assertEquals(1, physical.readByte(69));
    }

    @Test
    void memoryThatExistsButIsNotOwnedIsAFault() {
        // virtual 40 is physical 70: it exists, but it belongs to somebody else
        assertThrows(MemoryFault.class, () -> space.readByte(40));
        assertThrows(MemoryFault.class, () -> space.writeByte(40, 1));
        assertThrows(MemoryFault.class, () -> space.readInt(40));
        assertThrows(MemoryFault.class, () -> space.writeInt(40, 1));
        assertThrows(MemoryFault.class, () -> space.readInt(69));
    }

    @Test
    void anIntegerThatCrossesTheEndIsAFault() {
        assertThrows(MemoryFault.class, () -> space.readInt(37));
        assertThrows(MemoryFault.class, () -> space.writeInt(39, 1));
    }

    @Test
    void negativeAddressesAreAFaultAndDoNotReachTheBytesBeforeTheBlock() {
        physical.writeByte(29, 0x55); // the byte just before the block

        assertThrows(MemoryFault.class, () -> space.readByte(-1));
        assertThrows(MemoryFault.class, () -> space.readInt(-4));
        assertThrows(MemoryFault.class, () -> space.writeInt(Integer.MIN_VALUE, 1));
        assertThrows(MemoryFault.class, () -> space.writeByte(-1, 0));

        assertEquals(0x55, physical.readByte(29));
    }

    @Test
    void addressesNearTheIntegerLimitDoNotWrapAround() {
        assertThrows(MemoryFault.class, () -> space.readInt(Integer.MAX_VALUE));
        assertThrows(MemoryFault.class, () -> space.readInt(Integer.MAX_VALUE - 2));
    }

    @Test
    void theFaultNamesTheVirtualAddressInDecimal() {
        MemoryFault fault = assertThrows(MemoryFault.class, () -> space.readInt(1000));

        assertEquals("invalid memory access at address 1000", fault.getMessage());
    }

    @Test
    void aFaultNeverChangesTheMemoryOfTheNeighbors() {
        physical.writeByte(29, 0x55);
        physical.writeByte(70, 0x66);

        assertThrows(MemoryFault.class, () -> space.writeInt(-4, 1));
        assertThrows(MemoryFault.class, () -> space.writeInt(40, 1));
        assertThrows(MemoryFault.class, () -> space.writeInt(38, 1)); // crosses the end

        assertEquals(0x55, physical.readByte(29));
        assertEquals(0x66, physical.readByte(70));
        assertEquals(0, physical.readByte(68)); // not even the part that was inside
    }

    @Test
    void twoSpacesOverTheSameMemoryDoNotSeeEachOther() {
        AddressSpace first = new BaseLimitAddressSpace(physical, 0, 50);
        AddressSpace second = new BaseLimitAddressSpace(physical, 50, 50);

        first.writeInt(0, 111);
        assertEquals(0, second.readInt(0));
        second.writeInt(0, 222);

        assertEquals(111, first.readInt(0));
        assertEquals(222, physical.readInt(50));
    }

    @Test
    void clearSetsBytesToZeroInsideTheBlockOnly() {
        for (int i = 0; i < 100; i++) {
            physical.writeByte(i, 0xff);
        }

        space.clear(4, 4);

        assertEquals(0xff, space.readByte(3));
        assertEquals(0, space.readByte(4));
        assertEquals(0, space.readByte(7));
        assertEquals(0xff, space.readByte(8));
        assertEquals(0, physical.readByte(34)); // virtual 4 is physical 34
        assertEquals(0xff, physical.readByte(29));
    }

    @Test
    void clearOfZeroBytesDoesNothing() {
        space.writeByte(5, 9);

        space.clear(5, 0);
        space.clear(1000, 0); // nothing is touched, so the address does not matter

        assertEquals(9, space.readByte(5));
    }

    @Test
    void clearOutsideTheSpaceIsAFault() {
        assertThrows(MemoryFault.class, () -> space.clear(36, 5));
        assertThrows(MemoryFault.class, () -> space.clear(-1, 2));
        assertThrows(MemoryFault.class, () -> space.clear(0, -1));
    }

    @Test
    void clearThatRunsPastTheEndFirstClearsWhatIsInsideButNeverTheNeighbor() {
        space.writeByte(38, 5);
        physical.writeByte(70, 0x66);

        assertThrows(MemoryFault.class, () -> space.clear(38, 10));

        assertEquals(0, space.readByte(38));
        assertEquals(0x66, physical.readByte(70));
    }

    @Test
    void theBlockMustBeInsideTheMemory() {
        assertThrows(IllegalArgumentException.class, () -> new BaseLimitAddressSpace(physical, 80, 21));
        assertThrows(IllegalArgumentException.class, () -> new BaseLimitAddressSpace(physical, -1, 5));
        assertThrows(IllegalArgumentException.class, () -> new BaseLimitAddressSpace(physical, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> new BaseLimitAddressSpace(physical, Integer.MAX_VALUE, 10));
        assertEquals(100, new BaseLimitAddressSpace(physical, 0, 100).size());
        assertEquals(20, new BaseLimitAddressSpace(physical, 80, 20).size());
    }
}
