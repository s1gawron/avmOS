package avmos;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BootConfigTest {

    private static final String IDLE = "src/main/resources/sample_programs/idle.txt";
    private static final String SCOTT01 = "src/main/resources/sample_programs/scott01.txt";

    @Test
    void parsesMemorySizeAndPrograms() {
        BootConfig config = BootConfig.parse(new String[]{"1024", IDLE, SCOTT01});

        assertEquals(1024, config.memoryBytes());
        assertEquals(List.of(Path.of(IDLE), Path.of(SCOTT01)), config.programs());
    }

    @Test
    void dumpIsOffByDefaultAndOnWithTheOption() {
        assertFalse(BootConfig.parse(new String[]{"1024", IDLE}).dump());
        assertTrue(BootConfig.parse(new String[]{"--dump", "1024", IDLE}).dump());
    }

    @Test
    void rejectsUnknownOption() {
        assertThrows(IllegalArgumentException.class, () -> BootConfig.parse(new String[]{"--bogus", "1024", IDLE}));
    }

    @Test
    void rejectsOptionWithoutMemoryAndPrograms() {
        assertThrows(IllegalArgumentException.class, () -> BootConfig.parse(new String[]{"--dump"}));
        assertThrows(IllegalArgumentException.class, () -> BootConfig.parse(new String[]{"--dump", "1024"}));
    }

    @Test
    void rejectsMissingPrograms() {
        assertThrows(IllegalArgumentException.class, () -> BootConfig.parse(new String[]{"1024"}));
        assertThrows(IllegalArgumentException.class, () -> BootConfig.parse(new String[]{}));
    }

    @Test
    void rejectsBadMemorySize() {
        assertThrows(IllegalArgumentException.class, () -> BootConfig.parse(new String[]{"lots", IDLE}));
        assertThrows(IllegalArgumentException.class, () -> BootConfig.parse(new String[]{"0", IDLE}));
        assertThrows(IllegalArgumentException.class, () -> BootConfig.parse(new String[]{"-5", IDLE}));
    }

    @Test
    void rejectsUnreadableProgram() {
        assertThrows(IllegalArgumentException.class,
                () -> BootConfig.parse(new String[]{"1024", "does/not/exist.txt"}));
    }
}
