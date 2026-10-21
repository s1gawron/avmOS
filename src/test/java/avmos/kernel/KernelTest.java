package avmos.kernel;

import avmos.BootConfig;
import avmos.asm.Assembler;
import avmos.asm.Program;
import avmos.io.FakeConsole;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KernelTest {

    private final FakeConsole console = new FakeConsole();

    private static Program program(String name, String... lines) {
        return Assembler.assemble(name, List.of(lines));
    }

    private void run(int memoryBytes, int maxCycles, Program... programs) {
        BootConfig config = new BootConfig(memoryBytes, List.of(Path.of("unused.txt")), false, maxCycles);
        new Kernel(config, console).run(List.of(programs));
    }

    @Test
    void runsProgramsOneAfterAnotherWithTheirOwnIds() {
        run(256, 0,
                program("a", "6 r1, $1", "11 r1", "27"),
                program("b", "6 r1, $2", "11 r1", "27"));

        assertEquals(List.of(
                "[p1] r1 = 1",
                "[p1] exited after 3 clock cycles",
                "[p2] r1 = 2",
                "[p2] exited after 3 clock cycles"), console.lines());
    }

    @Test
    void eachProgramStartsWithFreshRegistersAndMemory() {
        run(256, 0,
                program("a", "6 r1, $9", "27"),
                program("b", "11 r1", "27"));

        assertEquals("[p2] r1 = 0", console.lines().get(1));
    }

    @Test
    void aFaultPrintsTheRegistersAndTerminatesTheProgram() {
        run(256, 0,
                program("runs off the end", "6 r1, $1"),
                program("next", "27"));

        assertEquals(List.of(
                "[p1] fault at eip=0x0009: unknown opcode 0",
                "[p1] r1=1 r2=0 r3=0 r4=0 r5=0 r6=0 r7=0 r8=0 r9=0 r10=0 eip=0x0009 sf=0 zf=0",
                "[p1] terminated after 1 clock cycles",
                "[p2] exited after 1 clock cycles"), console.lines());
    }

    @Test
    void aProgramBiggerThanMemoryIsNotLoaded() {
        run(8, 0,
                program("big", "6 r1, $1"),
                program("small", "27"));

        assertEquals(List.of(
                "avmOS: big does not fit in memory (9 bytes of code, 8 bytes of memory)",
                "[p2] exited after 1 clock cycles"), console.lines());
    }

    @Test
    void stopsTheWholeRunWhenTheCycleLimitIsReached() {
        run(256, 3,
                program("a", "1 r1", "1 r1", "1 r1", "1 r1", "27"),
                program("b", "27"));

        assertEquals(List.of("avmOS: stopped after 3 clock cycles (--max-cycles)"), console.lines());
    }

    @Test
    void theCycleLimitCountsAllProgramsOnTheSameClock() {
        run(256, 3,
                program("a", "1 r1", "27"),
                program("b", "1 r1", "27"));

        assertEquals(List.of(
                "[p1] exited after 2 clock cycles",
                "avmOS: stopped after 3 clock cycles (--max-cycles)"), console.lines());
    }

    @Test
    void inputIsReadFromTheConsole() {
        console.withInput(21);

        run(256, 0, program("echo", "32 r1", "11 r1", "27"));

        assertEquals(List.of(
                "[p1] input r1: 21",
                "[p1] r1 = 21",
                "[p1] exited after 3 clock cycles"), console.lines());
    }
}
