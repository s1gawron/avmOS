package avmos.kernel;

import avmos.BootConfig;
import avmos.asm.Assembler;
import avmos.asm.Program;
import avmos.io.FakeConsole;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Runs programs and compares everything the OS prints with a file in
 * {@code src/test/resources/expected}. These files were written by hand (by tracing the
 * program) before the code existed. When the OS changes inside (memory, scheduler), the output
 * of the old programs must stay the same.
 */
class GoldenOutputTest {

    private static final Path SAMPLES = Path.of("src/main/resources/sample_programs");
    private static final Path TEST_PROGRAMS = Path.of("src/test/resources/programs");
    private static final Path EXPECTED = Path.of("src/test/resources/expected");
    private static final int MEMORY = 16384;

    private static void assertGolden(Path programDirectory, String name) throws IOException {
        Path source = programDirectory.resolve(name + ".txt");
        Program program = Assembler.assemble(name, Files.readAllLines(source));
        FakeConsole console = new FakeConsole();
        BootConfig config = new BootConfig(MEMORY, List.of(source), false, 0);

        new Kernel(config, console).run(List.of(program));

        assertEquals(Files.readString(EXPECTED.resolve(name + ".out")), console.output());
    }

    @ParameterizedTest
    @ValueSource(strings = {"scott01", "scott02"})
    void sampleProgramsMatchTheExpectedFile(String name) throws IOException {
        assertGolden(SAMPLES, name);
    }

    @ParameterizedTest
    @ValueSource(strings = {"layout", "memory", "segfault"})
    void testProgramsMatchTheExpectedFile(String name) throws IOException {
        assertGolden(TEST_PROGRAMS, name);
    }
}
