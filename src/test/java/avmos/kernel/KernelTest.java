package avmos.kernel;

import avmos.BootConfig;
import avmos.asm.Assembler;
import avmos.asm.Program;
import avmos.io.FakeConsole;
import avmos.memory.PhysicalMemory;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KernelTest {

    /** Room for several small programs: each one needs its code plus 8192 bytes. */
    private static final int MEMORY = 65536;

    private final FakeConsole console = new FakeConsole();

    private static Program program(String name, String... lines) {
        return Assembler.assemble(name, List.of(lines));
    }

    private static BootConfig config(int memoryBytes, int maxCycles, boolean memoryMap) {
        return new BootConfig(memoryBytes, List.of(Path.of("unused.txt")), false, maxCycles, memoryMap);
    }

    private Kernel run(int memoryBytes, int maxCycles, boolean memoryMap, Program... programs) {
        Kernel kernel = new Kernel(config(memoryBytes, maxCycles, memoryMap), console);
        kernel.run(List.of(programs));
        return kernel;
    }

    private Kernel run(Program... programs) {
        return run(MEMORY, 0, false, programs);
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
        // 3 printr + exit = 16 bytes of code: data starts at 16, the stack ends at 16 + 4096 + 4096.
        // These are addresses of the process, not of the machine: the second process sees the same numbers.
        run(program("a", "11 r8", "11 r9", "11 r10", "27"),
                program("b", "11 r8", "11 r9", "11 r10", "27"));

        assertEquals(List.of(
                "[p1] r8 = 1",
                "[p1] r9 = 16",
                "[p1] r10 = 8208",
                "[p1] exited after 4 clock cycles",
                "[p2] r8 = 2",
                "[p2] r9 = 16",
                "[p2] r10 = 8208",
                "[p2] exited after 4 clock cycles"), console.lines());
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
    void aProcessCannotReadItsNeighbour() {
        // p1 has 18 bytes of code, so it owns 8210 bytes. Its virtual address 8210 would be the
        // first byte of p2 in the machine's memory.
        run(program("nosy", "6 r1, $8210", "8 r2, r1"),
                program("victim", "11 r8", "27"));

        assertEquals(List.of(
                "[p1] fault at eip=0x0009: invalid memory access at address 8210",
                "[p1] r1=8210 r2=0 r3=0 r4=0 r5=0 r6=0 r7=0 r8=1 r9=18 r10=8210 eip=0x0009 sf=0 zf=0",
                "[p1] terminated after 1 clock cycles",
                "[p2] r8 = 2",
                "[p2] exited after 2 clock cycles"), console.lines());
    }

    @Test
    void aProgramThatDoesNotFitIsReportedWithTheFreeMemory() {
        run(8200, 0, false,
                program("big", "6 r1, $1"),
                program("small", "27"));

        assertEquals(List.of(
                "avmOS: big does not fit in memory (needs 8201 bytes: 9 code + 4096 data + 4096 stack; "
                        + "free: 8200 bytes, largest block: 8200 bytes)",
                "[p2] exited after 1 clock cycles"), console.lines());
    }

    @Test
    void aProgramThatDoesNotFitIsSkippedAndTheOthersStillRun() {
        // p1 and p3 need 8193 bytes each. p2 has 4000 bytes of code and needs 12192.
        Program big = program("big", Collections.nCopies(800, "1 r1").toArray(String[]::new));

        Kernel kernel = run(20000, 0, false,
                program("first", "27"), big, program("third", "27"));

        assertEquals(List.of(
                "avmOS: big does not fit in memory (needs 12192 bytes: 4000 code + 4096 data + 4096 stack; "
                        + "free: 11807 bytes, largest block: 11807 bytes)",
                "[p1] exited after 1 clock cycles",
                "[p3] exited after 1 clock cycles"), console.lines()); // p3 keeps its place: pid = position
        assertEquals(0, kernel.processCount());
        assertEquals(20000, kernel.freeMemoryBytes());
    }

    @Test
    void aProgramThatDoesNotFitBecauseEarlierProgramsTookTheMemory() {
        run(16392, 0, false,
                program("first", "6 r1, $1", "27"), // 10 bytes of code: needs 8202 of the 16392 bytes
                program("second", "27"));           // needs 8193, but only 8190 are left

        assertEquals("avmOS: second does not fit in memory (needs 8193 bytes: 1 code + 4096 data + 4096 stack; "
                + "free: 8190 bytes, largest block: 8190 bytes)", console.lines().get(0));
    }

    @Test
    void theMemoryMapShowsWhereProcessesAreLoadedAndHowTheFreeMemoryChanges() {
        run(MEMORY, 0, true, program("a", "27"), program("b", "27"));

        assertEquals(List.of(
                "[p1] loaded at 0..8193 (1 code + 4096 data + 4096 stack)",
                "[p2] loaded at 8193..16386 (1 code + 4096 data + 4096 stack)",
                "memory: free 16386..65536 (49150 bytes)",
                "[p1] exited after 1 clock cycles",
                "[p1] memory freed 0..8193",
                "memory: free 0..8193 (8193 bytes), 16386..65536 (49150 bytes)",
                "[p2] exited after 1 clock cycles",
                "[p2] memory freed 8193..16386",
                "memory: free 0..65536 (65536 bytes)"), console.lines());
    }

    @Test
    void theMemoryMapSaysWhenNothingIsFree() {
        run(8193, 0, true, program("a", "27"));

        assertEquals(List.of(
                "[p1] loaded at 0..8193 (1 code + 4096 data + 4096 stack)",
                "memory: no free memory",
                "[p1] exited after 1 clock cycles",
                "[p1] memory freed 0..8193",
                "memory: free 0..8193 (8193 bytes)"), console.lines());
    }

    @Test
    void theMemoryAndThePcbAreReleasedWhenAProcessExits() {
        Kernel kernel = run(program("a", "27"), program("b", "27"));

        assertEquals(0, kernel.processCount());
        assertEquals(MEMORY, kernel.freeMemoryBytes());
    }

    @Test
    void theMemoryAndThePcbAreReleasedWhenAProcessFaults() {
        Kernel kernel = run(program("runs off the end", "6 r1, $1"));

        assertEquals(0, kernel.processCount());
        assertEquals(MEMORY, kernel.freeMemoryBytes());
    }

    @Test
    void aNewProcessStartsWithClearedMemoryEvenIfTheMemoryWasFullOfGarbage() {
        PhysicalMemory dirty = new PhysicalMemory(MEMORY);
        byte[] garbage = new byte[MEMORY];
        Arrays.fill(garbage, (byte) 0xff);
        dirty.write(0, garbage);
        Kernel kernel = new Kernel(config(MEMORY, 0, false), console, dirty);

        // prints the last word of the stack and the first word of the global data (code: 29 bytes)
        kernel.run(List.of(program("reader", "7 r2, r10", "2 r2, $-4", "12 r2", "12 r9", "27")));

        assertEquals(List.of(
                "[p1] [8217] = 0",
                "[p1] [29] = 0",
                "[p1] exited after 5 clock cycles"), console.lines());
    }

    @Test
    void stopsTheWholeRunWhenTheCycleLimitIsReached() {
        run(MEMORY, 3, false,
                program("a", "1 r1", "1 r1", "1 r1", "1 r1", "27"),
                program("b", "27"));

        assertEquals(List.of("avmOS: stopped after 3 clock cycles (--max-cycles)"), console.lines());
    }

    @Test
    void theCycleLimitCountsAllProgramsOnTheSameClock() {
        run(MEMORY, 3, false,
                program("a", "1 r1", "27"),
                program("b", "1 r1", "27"));

        assertEquals(List.of(
                "[p1] exited after 2 clock cycles",
                "avmOS: stopped after 3 clock cycles (--max-cycles)"), console.lines());
    }

    @Test
    void aStoppedRunLeavesTheProcessesAsTheyAre() {
        // 3 x incr + exit = 16 bytes of code, so the process owns 8208 bytes
        Kernel kernel = run(MEMORY, 2, false, program("a", "1 r1", "1 r1", "1 r1", "27"));

        assertEquals(1, kernel.processCount());
        assertEquals(MEMORY - 8208, kernel.freeMemoryBytes());
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
