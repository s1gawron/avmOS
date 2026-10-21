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

    private static final int MEMORY = 16384;

    private final FakeConsole console = new FakeConsole();

    private static Program program(String name, String... lines) {
        return Assembler.assemble(name, List.of(lines));
    }

    private void run(int memoryBytes, int maxCycles, Program... programs) {
        BootConfig config = new BootConfig(memoryBytes, List.of(Path.of("unused.txt")), false, maxCycles);
        new Kernel(config, console).run(List.of(programs));
    }

    private void run(Program... programs) {
        run(MEMORY, 0, programs);
    }

    @Test
    void runsProgramsOneAfterAnotherWithTheirOwnIds() {
        run(program("a", "6 r1, $1", "11 r1", "27"),
                program("b", "6 r1, $2", "11 r1", "27"));

        assertEquals(List.of(
                "[p1] r1 = 1",
                "[p1] exited after 3 clock cycles",
                "[p2] r1 = 2",
                "[p2] exited after 3 clock cycles"), console.lines());
    }

    @Test
    void aNewProcessStartsWithTheRegistersOfTheSpec() {
        // 3 printr + exit = 16 bytes of code: data starts at 16, the stack ends at 16 + 4096 + 4096
        run(program("a", "11 r8", "11 r9", "11 r10", "27"),
                program("b", "11 r8", "27"));

        assertEquals(List.of(
                "[p1] r8 = 1",
                "[p1] r9 = 16",
                "[p1] r10 = 8208",
                "[p1] exited after 4 clock cycles",
                "[p2] r8 = 2",
                "[p2] exited after 2 clock cycles"), console.lines());
    }

    @Test
    void theGlobalDataStartsZeroedAndEachProgramHasItsOwnMemory() {
        run(program("a", "6 r1, $9", "9 r9, r1", "27"),
                program("b", "12 r9", "27"));

        assertEquals("[p2] [6] = 0", console.lines().get(1)); // p2 has 6 bytes of code, so data starts at 6
    }

    @Test
    void eachProgramStartsWithFreshRegisters() {
        run(program("a", "6 r1, $9", "27"),
                program("b", "11 r1", "27"));

        assertEquals("[p2] r1 = 0", console.lines().get(1));
    }

    @Test
    void aFaultPrintsTheRegistersAndTerminatesTheProgram() {
        run(program("runs off the end", "6 r1, $1"),
                program("next", "27"));

        assertEquals(List.of(
                "[p1] fault at eip=0x0009: unknown opcode 0",
                "[p1] r1=1 r2=0 r3=0 r4=0 r5=0 r6=0 r7=0 r8=1 r9=9 r10=8201 eip=0x0009 sf=0 zf=0",
                "[p1] terminated after 1 clock cycles",
                "[p2] exited after 1 clock cycles"), console.lines());
    }

    @Test
    void aSegmentationFaultDoesNotStopTheNextProgram() {
        run(program("bad", "6 r1, $-1", "8 r2, r1"),
                program("good", "6 r1, $5", "11 r1", "27"));

        assertEquals(List.of(
                "[p1] fault at eip=0x0009: invalid memory access at address -1",
                "[p1] r1=-1 r2=0 r3=0 r4=0 r5=0 r6=0 r7=0 r8=1 r9=18 r10=8210 eip=0x0009 sf=0 zf=0",
                "[p1] terminated after 1 clock cycles",
                "[p2] r1 = 5",
                "[p2] exited after 3 clock cycles"), console.lines());
    }

    @Test
    void aProgramNeedsRoomForItsCodeItsDataAndItsStack() {
        run(8200, 0,
                program("big", "6 r1, $1"),
                program("small", "27"));

        assertEquals(List.of(
                "avmOS: big does not fit in memory (needs 8201 bytes: 9 code + 4096 data + 4096 stack; memory is 8200 bytes)",
                "[p2] exited after 1 clock cycles"), console.lines());
    }

    @Test
    void stopsTheWholeRunWhenTheCycleLimitIsReached() {
        run(MEMORY, 3,
                program("a", "1 r1", "1 r1", "1 r1", "1 r1", "27"),
                program("b", "27"));

        assertEquals(List.of("avmOS: stopped after 3 clock cycles (--max-cycles)"), console.lines());
    }

    @Test
    void theCycleLimitCountsAllProgramsOnTheSameClock() {
        run(MEMORY, 3,
                program("a", "1 r1", "27"),
                program("b", "1 r1", "27"));

        assertEquals(List.of(
                "[p1] exited after 2 clock cycles",
                "avmOS: stopped after 3 clock cycles (--max-cycles)"), console.lines());
    }

    @Test
    void inputIsReadFromTheConsole() {
        console.withInput(21);

        run(program("echo", "32 r1", "11 r1", "27"));

        assertEquals(List.of(
                "[p1] input r1: 21",
                "[p1] r1 = 21",
                "[p1] exited after 3 clock cycles"), console.lines());
    }
}
