package avmos.memory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MemoryBlockTest {

    @Test
    void endIsTheFirstByteAfterTheBlock() {
        MemoryBlock block = new MemoryBlock(100, 20);

        assertEquals(120, block.end());
    }

    @Test
    void isPrintedAsAHalfOpenRange() {
        assertEquals("0..8284", new MemoryBlock(0, 8284).toString());
    }

    @Test
    void rejectsAnEmptyOrNegativeBlock() {
        assertThrows(IllegalArgumentException.class, () -> new MemoryBlock(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new MemoryBlock(0, -5));
        assertThrows(IllegalArgumentException.class, () -> new MemoryBlock(-1, 5));
    }

    @Test
    void rejectsABlockBeyondThe32BitRange() {
        assertThrows(IllegalArgumentException.class, () -> new MemoryBlock(Integer.MAX_VALUE, 1));
        assertThrows(IllegalArgumentException.class, () -> new MemoryBlock(10, Integer.MAX_VALUE));
    }
}
