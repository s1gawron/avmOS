package avmos.asm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AssemblerTest {

    private static Program assemble(String... lines) {
        return Assembler.assemble("test", List.of(lines));
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }

    @Test
    void encodesOpcodeThenLittleEndianOperands() {
        Program program = assemble("6 r1, $20");

        assertArrayEquals(bytes(0x06, 0x01, 0x00, 0x00, 0x00, 0x14, 0x00, 0x00, 0x00), program.code());
    }

    @Test
    void encodesNegativeConstantsAsTwosComplement() {
        Program program = assemble("6 r2, $-19");

        assertArrayEquals(bytes(0x06, 0x02, 0x00, 0x00, 0x00, 0xed, 0xff, 0xff, 0xff), program.code());
    }

    @Test
    void encodesBigConstants() {
        Program program = assemble("6 r1, $3276834");

        assertArrayEquals(bytes(0x06, 0x01, 0x00, 0x00, 0x00, 0x22, 0x00, 0x32, 0x00), program.code());
    }

    @Test
    void commaIsOptionalAndTabsAreAllowed() {
        byte[] withComma = assemble("6 r1, $10").code();

        assertArrayEquals(withComma, assemble("6 r1 $10").code());
        assertArrayEquals(withComma, assemble("6\tr1,\t$10").code());
        assertArrayEquals(withComma, assemble("  6   r1 ,  $10   ").code());
    }

    @Test
    void spIsRegisterTen() {
        Program program = assemble("4 sp");

        assertEquals(new Operand.Register(10), program.instructions().get(0).operands().get(0));
        assertArrayEquals(bytes(0x04, 0x0a, 0x00, 0x00, 0x00), program.code());
    }

    @Test
    void instructionsWithoutOperandsAreOneByte() {
        Program program = assemble("27", "21");

        assertArrayEquals(bytes(27, 21), program.code());
    }

    @Test
    void skipsBlankAndCommentLinesButKeepsLineNumbers() {
        Program program = assemble(
                "; a comment",
                "",
                "1 r1 ;incr",
                "   \t",
                "27");

        assertEquals(2, program.instructions().size());
        assertEquals(3, program.instructions().get(0).line());
        assertEquals(5, program.instructions().get(1).line());
    }

    @Test
    void assignsByteOffsets() {
        Program program = assemble("6 r1, $1", "11 r1", "27");

        assertEquals(0, program.instructions().get(0).offset());
        assertEquals(9, program.instructions().get(1).offset());
        assertEquals(14, program.instructions().get(2).offset());
        assertEquals(15, program.code().length);
        assertEquals(program.instructions().get(1), program.instructionAt(9));
    }

    @Test
    void instructionAtReturnsNullInTheMiddleOfAnInstruction() {
        Program program = assemble("6 r1, $1", "27");

        assertEquals(null, program.instructionAt(3));
    }

    @Test
    void printsInstructionsAsText() {
        Program program = assemble("6 r1, $-5", "27", "3 r2 r3");

        assertEquals("movi r1, $-5", program.instructions().get(0).toString());
        assertEquals("exit", program.instructions().get(1).toString());
        assertEquals("addr r2, r3", program.instructions().get(2).toString());
    }

    @Test
    void listingShowsOffsetBytesAndInstruction() {
        Program program = assemble("6 r4, $0", "27");

        String[] rows = program.listing().split("\n");
        assertEquals("0000  06 04 00 00 00 00 00 00 00  movi r4, $0", rows[0]);
        // the bytes column is as wide as the longest instruction (9 bytes = 26 characters)
        assertEquals("0009  1b" + " ".repeat(24) + "  exit", rows[1]);
    }

    @Test
    void rejectsUnknownOpcodeWithLineNumber() {
        AssemblyException e = assertThrows(AssemblyException.class, () -> assemble("27", "99 r1"));

        assertEquals(2, e.line());
    }

    @Test
    void rejectsNonNumericOpcode() {
        assertThrows(AssemblyException.class, () -> assemble("movi r1, $1"));
    }

    @Test
    void rejectsWrongOperandCount() {
        assertThrows(AssemblyException.class, () -> assemble("6 r1"));
        assertThrows(AssemblyException.class, () -> assemble("27 r1"));
        assertThrows(AssemblyException.class, () -> assemble("1 r1 r2"));
    }

    @Test
    void rejectsConstantWhereRegisterIsExpected() {
        assertThrows(AssemblyException.class, () -> assemble("1 $5"));
        assertThrows(AssemblyException.class, () -> assemble("6 $1, $5"));
    }

    @Test
    void rejectsRegisterWhereConstantIsExpected() {
        assertThrows(AssemblyException.class, () -> assemble("6 r1, r2"));
        assertThrows(AssemblyException.class, () -> assemble("5 r1"));
    }

    @Test
    void rejectsRegistersOutOfRange() {
        assertThrows(AssemblyException.class, () -> assemble("1 r0"));
        assertThrows(AssemblyException.class, () -> assemble("1 r11"));
        assertThrows(AssemblyException.class, () -> assemble("1 r"));
    }

    @Test
    void rejectsConstantsThatDoNotFitIn32Bits() {
        assertThrows(AssemblyException.class, () -> assemble("6 r1, $2147483648"));
        assertThrows(AssemblyException.class, () -> assemble("6 r1, $"));
        assertThrows(AssemblyException.class, () -> assemble("6 r1, $abc"));
    }

    @Test
    void acceptsTheExtremeConstants() {
        assertArrayEquals(bytes(0x05, 0xff, 0xff, 0xff, 0x7f), assemble("5 $2147483647").code());
        assertArrayEquals(bytes(0x05, 0x00, 0x00, 0x00, 0x80), assemble("5 $-2147483648").code());
    }

    @Test
    void rejectsGarbageOperand() {
        assertThrows(AssemblyException.class, () -> assemble("1 banana"));
    }
}
