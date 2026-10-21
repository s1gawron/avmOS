package avmos.asm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The sample programs are the ground truth for the encoding: their jump offsets only land on
 * the right instruction if every instruction is 1 byte plus 4 bytes per operand.
 */
class SampleProgramsTest {

    private static final Path SAMPLES = Path.of("src/main/resources/sample_programs");

    static Stream<Path> samplePrograms() throws IOException {
        try (Stream<Path> files = Files.list(SAMPLES)) {
            return files.filter(path -> path.toString().endsWith(".txt")).sorted().toList().stream();
        }
    }

    private static Program load(String fileName) throws IOException {
        Path path = SAMPLES.resolve(fileName);
        return Assembler.assemble(fileName, Files.readAllLines(path));
    }

    /** The instruction that a jump or call at {@code index} lands on, given its offset operand. */
    private static Instruction target(Program program, int index, int relativeOffset) {
        Instruction jump = program.instructions().get(index);
        return program.instructionAt(jump.nextOffset() + relativeOffset);
    }

    @ParameterizedTest
    @MethodSource("samplePrograms")
    void everySampleAssembles(Path path) throws IOException {
        Program program = Assembler.assemble(path.getFileName().toString(), Files.readAllLines(path));

        assertEquals(program.instructions().stream().mapToInt(Instruction::sizeInBytes).sum(), program.code().length);
    }

    @Test
    void idleLoopJumpsBackToPrintr() throws IOException {
        Program idle = load("idle.txt");

        assertEquals(42, idle.code().length);
        Instruction landing = target(idle, 5, -19);
        assertNotNull(landing);
        assertEquals(Opcode.PRINTR, landing.opcode());
        assertEquals(23, landing.offset());
    }

    @Test
    void idleNLoopJumpsBackAndExitJumpLandsOnExit() throws IOException {
        Program idleN = load("idle-n.txt");

        Instruction back = target(idleN, 10, -38);
        assertNotNull(back);
        assertEquals(Opcode.PRINTR, back.opcode());

        Instruction exit = target(idleN, 8, 14);
        assertNotNull(exit);
        assertEquals(Opcode.EXIT, exit.opcode());
    }

    @Test
    void scott05HasExpectedSizeAndSkipsOverExit() throws IOException {
        Program scott05 = load("scott05.txt");

        assertEquals(222, scott05.code().length);

        // "6 r7, $1" then "13 r7": the jump skips the 1-byte exit that follows it.
        int jmpIndex = indexOfFirst(scott05, Opcode.JMP);
        Instruction skipped = scott05.instructions().get(jmpIndex + 1);
        assertEquals(Opcode.EXIT, skipped.opcode());
        Instruction landing = target(scott05, jmpIndex, 1);
        assertNotNull(landing);
        assertEquals(Opcode.PRINTR, landing.opcode());
    }

    @Test
    void scott06CallLandsOnTheSubroutine() throws IOException {
        Program scott06 = load("scott06.txt");

        Instruction landing = target(scott06, indexOfFirst(scott06, Opcode.CALL), 43);

        assertNotNull(landing);
        assertEquals("movi r1, $55", landing.toString());
        assertEquals(18, landing.line());
    }

    private static int indexOfFirst(Program program, Opcode opcode) {
        List<Instruction> instructions = program.instructions();
        for (int i = 0; i < instructions.size(); i++) {
            if (instructions.get(i).opcode() == opcode) {
                return i;
            }
        }
        throw new AssertionError("no " + opcode + " in " + program.name());
    }
}
