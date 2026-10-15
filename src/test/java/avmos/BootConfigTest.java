package avmos;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
