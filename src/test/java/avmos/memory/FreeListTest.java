package avmos.memory;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FreeListTest {

    private final FreeList list = new FreeList(0, 100);

    private static MemoryBlock block(int start, int size) {
        return new MemoryBlock(start, size);
    }

    @Test
    void startsAsOneBlockOfTheWholeRange() {
        assertEquals(List.of(block(0, 100)), list.blocks());
        assertEquals(100, list.freeBytes());
        assertEquals(100, list.largestBlock());
    }

    @Test
    void worksOnARangeThatDoesNotStartAtZero() {
        FreeList heap = new FreeList(1000, 50);

        assertEquals(OptionalInt.of(1000), heap.allocate(20));
        assertEquals(OptionalInt.of(1020), heap.allocate(30));
        assertEquals(OptionalInt.empty(), heap.allocate(1));
    }

    @Test
    void takesTheNewBlockFromTheFrontOfTheFreeBlock() {
        assertEquals(OptionalInt.of(0), list.allocate(30));
        assertEquals(OptionalInt.of(30), list.allocate(30));

        assertEquals(List.of(block(60, 40)), list.blocks());
    }

    @Test
    void anExactFitRemovesTheBlock() {
        list.allocate(100);

        assertEquals(List.of(), list.blocks());
        assertEquals(0, list.freeBytes());
        assertEquals(0, list.largestBlock());
    }

    @Test
    void firstFitTakesTheLowestBlockThatIsBigEnough() {
        list.allocate(30); // 0..30
        list.allocate(30); // 30..60
        list.allocate(30); // 60..90
        list.free(0, 30);  // free: 0..30 and 90..100
        list.free(60, 30); // free: 0..30 and 60..100

        assertEquals(OptionalInt.of(60), list.allocate(35)); // 0..30 is too small
        assertEquals(OptionalInt.of(0), list.allocate(20));  // the lowest block that fits
    }

    @Test
    void whenNothingFitsItReturnsEmptyAndChangesNothing() {
        list.allocate(60);
        List<MemoryBlock> before = list.blocks();

        assertEquals(OptionalInt.empty(), list.allocate(41));

        assertEquals(before, list.blocks());
    }

    @Test
    void freeingNextToNothingKeepsTheBlockSeparate() {
        list.allocate(100);

        list.free(30, 10);

        assertEquals(List.of(block(30, 10)), list.blocks());
    }

    @Test
    void aFreedBlockJoinsTheFreeBlockBeforeIt() {
        list.allocate(100);
        list.free(0, 20);

        list.free(20, 30);

        assertEquals(List.of(block(0, 50)), list.blocks());
    }

    @Test
    void aFreedBlockJoinsTheFreeBlockAfterIt() {
        list.allocate(100);
        list.free(50, 20);

        list.free(20, 30);

        assertEquals(List.of(block(20, 50)), list.blocks());
    }

    @Test
    void aFreedBlockBetweenTwoFreeBlocksJoinsBoth() {
        list.allocate(100);
        list.free(0, 20);
        list.free(50, 20);

        list.free(20, 30);

        assertEquals(List.of(block(0, 70)), list.blocks());
    }

    @Test
    void everythingComesBackToOneBlockWhateverTheOrderOfFrees() {
        for (int i = 0; i < 4; i++) {
            list.allocate(25);
        }

        list.free(50, 25);
        list.free(0, 25);
        list.free(75, 25);
        list.free(25, 25);

        assertEquals(List.of(block(0, 100)), list.blocks());
    }

    @Test
    void externalFragmentationFreeMemoryInTheWrongShape() {
        list.allocate(30); // A 0..30
        list.allocate(30); // B 30..60
        list.allocate(30); // C 60..90
        list.free(30, 30); // B goes away: a hole of 30, and a tail of 10

        assertEquals(40, list.freeBytes());
        assertEquals(30, list.largestBlock());
        assertEquals(OptionalInt.empty(), list.allocate(35)); // 40 bytes are free, but not in one piece

        list.free(0, 30); // A goes away: A and the hole join
        assertEquals(60, list.largestBlock());
        assertEquals(OptionalInt.of(0), list.allocate(35));
    }

    @Test
    void freeingAnythingTwiceIsAnError() {
        list.allocate(100);
        list.free(10, 20);

        assertThrows(IllegalArgumentException.class, () -> list.free(10, 20));
    }

    @Test
    void freeingABlockThatOverlapsFreeMemoryIsAnError() {
        list.allocate(100);
        list.free(10, 20);
        list.free(50, 20);

        assertThrows(IllegalArgumentException.class, () -> list.free(25, 10)); // overlaps the end of 10..30
        assertThrows(IllegalArgumentException.class, () -> list.free(5, 10));  // overlaps the start of 10..30
        assertThrows(IllegalArgumentException.class, () -> list.free(40, 20)); // overlaps the start of 50..70
        assertThrows(IllegalArgumentException.class, () -> list.free(0, 100)); // overlaps both
        assertEquals(List.of(block(10, 20), block(50, 20)), list.blocks());
    }

    @Test
    void freeingABlockOutsideTheRangeIsAnError() {
        FreeList heap = new FreeList(100, 50);
        heap.allocate(50);

        assertThrows(IllegalArgumentException.class, () -> heap.free(90, 20));
        assertThrows(IllegalArgumentException.class, () -> heap.free(140, 20));
        assertThrows(IllegalArgumentException.class, () -> heap.free(0, 10));
    }

    @Test
    void sizesOfZeroOrLessAreErrors() {
        assertThrows(IllegalArgumentException.class, () -> list.allocate(0));
        assertThrows(IllegalArgumentException.class, () -> list.allocate(-1));
        assertThrows(IllegalArgumentException.class, () -> list.free(10, 0));
        assertThrows(IllegalArgumentException.class, () -> list.free(10, -5));
    }

    @Test
    void blocksIsACopy() {
        List<MemoryBlock> before = list.blocks();

        list.allocate(40);

        assertEquals(List.of(block(0, 100)), before);
    }
}
