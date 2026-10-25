package avmos.kernel;

import avmos.BootConfig;
import avmos.asm.Assembler;
import avmos.asm.Program;
import avmos.io.FakeConsole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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

    private static void assertGolden(Path programDirectory, String name, int maxCycles) throws IOException {
        Path source = programDirectory.resolve(name + ".txt");
        Program program = Assembler.assemble(name, Files.readAllLines(source));
        FakeConsole console = new FakeConsole();
        BootConfig config = new BootConfig(MEMORY, List.of(source), false, maxCycles, false);

        new Kernel(config, console).run(List.of(program));

        assertEquals(Files.readString(EXPECTED.resolve(name + ".out")), console.output());
    }

    @ParameterizedTest
    @ValueSource(strings = {"scott01", "scott02", "scott03", "scott04", "scott05", "scott06", "idle-n"})
    void sampleProgramsMatchTheExpectedFile(String name) throws IOException {
        assertGolden(SAMPLES, name, 0);
    }

    @Test
    void theIdleLoopRunsUntilTheCycleLimit() throws IOException {
        assertGolden(SAMPLES, "idle", 12);
    }

    @Test
    void twoProgramsShareTheMemoryAndTheMemoryMapShowsIt() throws IOException {
        List<Path> sources = List.of(SAMPLES.resolve("scott01.txt"), SAMPLES.resolve("scott02.txt"));
        List<Program> programs = new ArrayList<>();
        for (Path source : sources) {
            programs.add(Assembler.assemble(source.getFileName().toString(), Files.readAllLines(source)));
        }
        FakeConsole console = new FakeConsole();

        new Kernel(new BootConfig(65536, sources, false, 0, true), console).run(programs);

        assertEquals(Files.readString(EXPECTED.resolve("memory-map.out")), console.output());
    }

    @ParameterizedTest
    @ValueSource(strings = {"layout", "memory", "segfault", "recursion", "overflow", "underflow"})
    void testProgramsMatchTheExpectedFile(String name) throws IOException {
        assertGolden(TEST_PROGRAMS, name, 0);
    }
}
