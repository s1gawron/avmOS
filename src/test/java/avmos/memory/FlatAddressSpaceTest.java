package avmos.memory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FlatAddressSpaceTest {

    // The machine has 100 bytes of RAM, but the process owns only the first 40.
    private final PhysicalMemory physical = new PhysicalMemory(100);
    private final AddressSpace space = new FlatAddressSpace(physical, 40);

    @Test
    void accessesReachPhysicalMemory() {
        space.writeInt(8, 0x0a0b0c0d);
        space.writeByte(20, 0xfe);

        assertEquals(0x0a0b0c0d, physical.readInt(8));
        assertEquals(0xfe, physical.readByte(20));
        assertEquals(0x0a0b0c0d, space.readInt(8));
        assertEquals(0xfe, space.readByte(20));
    }

    @Test
    void theLastBytesOfTheSpaceAreUsable() {
        space.writeInt(36, 7);
        space.writeByte(39, 1);

        assertEquals(1, space.readByte(39));
    }

    @Test
    void memoryThatExistsButIsNotOwnedIsAFault() {
        assertThrows(MemoryFault.class, () -> space.readByte(40));
        assertThrows(MemoryFault.class, () -> space.writeByte(40, 1));
        assertThrows(MemoryFault.class, () -> space.readInt(40));
        assertThrows(MemoryFault.class, () -> space.writeInt(40, 1));
        assertThrows(MemoryFault.class, () -> space.readInt(99));
    }

    @Test
    void anIntegerThatCrossesTheEndIsAFault() {
        assertThrows(MemoryFault.class, () -> space.readInt(37));
        assertThrows(MemoryFault.class, () -> space.writeInt(39, 1));
    }

    @Test
    void negativeAddressesAreAFault() {
        assertThrows(MemoryFault.class, () -> space.readByte(-1));
        assertThrows(MemoryFault.class, () -> space.readInt(-4));
        assertThrows(MemoryFault.class, () -> space.writeInt(Integer.MIN_VALUE, 1));
    }

    @Test
    void addressesNearTheIntegerLimitDoNotWrapAround() {
        assertThrows(MemoryFault.class, () -> space.readInt(Integer.MAX_VALUE));
        assertThrows(MemoryFault.class, () -> space.readInt(Integer.MAX_VALUE - 2));
    }

    @Test
    void theFaultMessageHasTheAddressInDecimal() {
        MemoryFault fault = assertThrows(MemoryFault.class, () -> space.readInt(1000));

        assertEquals("invalid memory access at address 1000", fault.getMessage());
    }

    @Test
    void clearSetsBytesToZero() {
        for (int i = 0; i < 12; i++) {
            space.writeByte(i, 0xff);
        }

        space.clear(4, 4);

        assertEquals(0xff, space.readByte(3));
        assertEquals(0, space.readByte(4));
        assertEquals(0, space.readByte(7));
        assertEquals(0xff, space.readByte(8));
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
    void clearThatRunsPastTheEndFirstClearsWhatIsInside() {
        space.writeByte(38, 5);

        assertThrows(MemoryFault.class, () -> space.clear(38, 10));

        assertEquals(0, space.readByte(38));
    }

    @Test
    void theSpaceCannotBeBiggerThanTheMemory() {
        assertThrows(IllegalArgumentException.class, () -> new FlatAddressSpace(physical, 101));
        assertThrows(IllegalArgumentException.class, () -> new FlatAddressSpace(physical, -1));
        assertEquals(100, new FlatAddressSpace(physical, 100).size());
    }
}
