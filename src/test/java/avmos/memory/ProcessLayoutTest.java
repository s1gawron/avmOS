package avmos.memory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProcessLayoutTest {

    @Test
    void regionsFollowEachOtherWithoutGaps() {
        ProcessLayout layout = new ProcessLayout(100);

        assertEquals(100, layout.dataStart());
        assertEquals(4196, layout.stackBottom());
        assertEquals(8292, layout.stackTop());
        assertEquals(8292, layout.size());
    }

    @Test
    void anEmptyProgramStillGetsDataAndStack() {
        ProcessLayout layout = new ProcessLayout(0);

        assertEquals(0, layout.dataStart());
        assertEquals(8192, layout.size());
    }

    @Test
    void rejectsNegativeCodeSize() {
        assertThrows(IllegalArgumentException.class, () -> new ProcessLayout(-1));
    }
}
