package avmos.cpu;

import avmos.Fault;
import avmos.asm.Assembler;
import avmos.io.FakeConsole;
import avmos.memory.FlatAddressSpace;
import avmos.memory.PhysicalMemory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CpuTest {

    private final FakeConsole console = new FakeConsole();
    private final PhysicalMemory memory = new PhysicalMemory(256);
    private final Cpu cpu = new Cpu(console);
    private final CpuContext ctx = new CpuContext(7, new FlatAddressSpace(memory, 256));

    private void load(String... lines) {
        memory.write(0, Assembler.assemble("test", List.of(lines)).code());
    }

    private void steps(int count) {
        for (int i = 0; i < count; i++) {
            cpu.step(ctx);
        }
    }

    @Test
    void movAndArithmetic() {
        load("6 r1, $10", "7 r2, r1", "1 r2", "2 r2, $5", "3 r1, r2");

        steps(5);

        assertEquals(26, ctx.get(1)); // 10 + (10 + 1 + 5)
        assertEquals(16, ctx.get(2));
    }

    @Test
    void eipAdvancesByTheSizeOfTheInstruction() {
        load("6 r1, $1", "1 r1", "27");

        assertNull(cpu.step(ctx));
        assertEquals(9, ctx.eip());
        assertNull(cpu.step(ctx));
        assertEquals(14, ctx.eip());
        cpu.step(ctx);
        assertEquals(15, ctx.eip());
    }

    @Test
    void additionWrapsAroundLike32BitHardware() {
        load("6 r1, $2147483647", "1 r1", "2 r2, $-1");

        steps(2);
        assertEquals(Integer.MIN_VALUE, ctx.get(1));

        cpu.step(ctx);
        assertEquals(-1, ctx.get(2));
    }

    @Test
    void spIsRegisterTen() {
        load("6 sp, $100", "1 r10");

        steps(2);

        assertEquals(101, ctx.get(10));
    }

    @Test
    void printrShowsTheProcessAndTheRegister() {
        load("6 r2, $42", "11 r2");

        steps(2);

        assertEquals(List.of("[p7] r2 = 42"), console.lines());
    }

    @Test
    void inputReadsANumberFromTheConsole() {
        console.withInput(123);
        load("32 r3");

        cpu.step(ctx);

        assertEquals(123, ctx.get(3));
        assertEquals(List.of("[p7] input r3: 123"), console.lines());
    }

    @Test
    void exitIsHandedBackToTheOs() {
        load("27");

        assertInstanceOf(Trap.Exit.class, cpu.step(ctx));
    }

    @Test
    void setPriorityDoesNothingYet() {
        load("6 r1, $5", "26 r1");

        steps(2);

        assertEquals(5, ctx.get(1));
        assertEquals(14, ctx.eip());
    }

    @Test
    void runningOffTheEndOfTheCodeFaults() {
        load("6 r1, $1");

        cpu.step(ctx);
        Fault fault = assertThrows(Fault.class, () -> cpu.step(ctx));

        assertEquals("unknown opcode 0", fault.getMessage());
        assertEquals(9, ctx.eip()); // still points at the instruction that faulted
    }

    @Test
    void fetchingFromOutsideTheAddressSpaceFaults() {
        ctx.setEip(1000);

        assertThrows(Fault.class, () -> cpu.step(ctx));
    }

    @Test
    void anInstructionThatIsNotImplementedYetFaults() {
        load("22 r1, r2"); // alloc: comes with the heap

        Fault fault = assertThrows(Fault.class, () -> cpu.step(ctx));

        assertEquals("opcode alloc is not implemented yet", fault.getMessage());
        assertEquals(0, ctx.eip()); // a fault during execution also reports the faulting instruction
    }

    @Test
    void theOpcodeDecidesWhatAnOperandMeans() {
        // The assembler would refuse "incr 11", but the CPU only sees bytes.
        // The operand 11 is a register number here, and r11 does not exist.
        memory.write(0, new byte[]{1, 11, 0, 0, 0});

        Fault fault = assertThrows(Fault.class, () -> cpu.step(ctx));

        assertEquals("invalid register r11", fault.getMessage());
        assertEquals(0, ctx.eip());
    }

    @Test
    void anInstructionCutOffByTheEndOfMemoryFaults() {
        PhysicalMemory small = new PhysicalMemory(10);
        small.write(5, new byte[]{6, 1, 0, 0, 0}); // movi r1, <4 bytes missing>
        CpuContext smallCtx = new CpuContext(7, new FlatAddressSpace(small, 10));
        smallCtx.setEip(5);

        assertThrows(Fault.class, () -> cpu.step(smallCtx));
    }

    @Test
    void dumpShowsRegistersEipAndFlags() {
        ctx.set(1, 5);
        ctx.setEip(0xde);
        ctx.setZf(true);

        assertEquals("r1=5 r2=0 r3=0 r4=0 r5=0 r6=0 r7=0 r8=0 r9=0 r10=0 eip=0x00de sf=0 zf=1", ctx.dump());
    }

    // --- memory instructions ---

    @Test
    void movrmStoresARegisterAndMovmrLoadsItBack() {
        load("6 r1, $100", "6 r2, $-5", "9 r1, r2", "8 r3, r1");

        steps(4);

        assertEquals(-5, memory.readInt(100));
        assertEquals(-5, ctx.get(3));
    }

    @Test
    void movmmCopiesAllFourBytes() {
        memory.writeInt(100, 0x01020304);
        load("6 r1, $200", "6 r2, $100", "10 r1, r2");

        steps(3);

        assertEquals(0x01020304, memory.readInt(200));
    }

    @Test
    void printmShowsTheAddressAndTheNumberStoredThere() {
        memory.writeInt(100, 42);
        load("6 r1, $100", "12 r1");

        steps(2);

        assertEquals(List.of("[p7] [100] = 42"), console.lines());
    }

    @Test
    void memoryAccessNeedsNoAlignment() {
        memory.writeInt(100, 255); // bytes 100..103 are ff 00 00 00
        memory.writeByte(104, 1);
        load("6 r1, $101", "12 r1");

        steps(2);

        // bytes 101..104 are 00 00 00 01, and the byte at the highest address is the most significant one
        assertEquals(List.of("[p7] [101] = 16777216"), console.lines());
    }

    @Test
    void memoryclearZeroesTheGivenBytes() {
        for (int i = 98; i < 110; i++) {
            memory.writeByte(i, 0xff);
        }
        load("6 r1, $100", "6 r2, $8", "33 r1, r2");

        steps(3);

        assertEquals(0xff, memory.readByte(99));
        assertEquals(0, memory.readByte(100));
        assertEquals(0, memory.readByte(107));
        assertEquals(0xff, memory.readByte(108));
    }

    @Test
    void anAddressOutsideTheAddressSpaceFaultsAndEipPointsAtTheInstruction() {
        load("6 r1, $256", "8 r2, r1");

        cpu.step(ctx);
        Fault fault = assertThrows(Fault.class, () -> cpu.step(ctx));

        assertEquals("invalid memory access at address 256", fault.getMessage());
        assertEquals(9, ctx.eip());
        assertEquals(0, ctx.get(2));
    }

    @Test
    void everyMemoryInstructionFaultsOnABadAddress() {
        for (String instruction : List.of("8 r2, r1", "9 r1, r2", "10 r1, r2", "10 r2, r1", "12 r1")) {
            CpuContext bad = new CpuContext(7, new FlatAddressSpace(memory, 256));
            bad.set(1, -4);
            memory.write(0, Assembler.assemble("test", List.of(instruction)).code());

            assertThrows(Fault.class, () -> cpu.step(bad), instruction);
        }
    }

    @Test
    void movmmFaultsWhenTheDestinationIsOutsideAndLeavesTheSourceAlone() {
        memory.writeInt(100, 7);
        load("6 r1, $256", "6 r2, $100", "10 r1, r2");

        steps(2);
        assertThrows(Fault.class, () -> cpu.step(ctx));

        assertEquals(7, memory.readInt(100));
    }

    // --- compare and jump ---

    private void assertFlags(boolean sf, boolean zf) {
        assertEquals(sf, ctx.sf(), "sign flag");
        assertEquals(zf, ctx.zf(), "zero flag");
    }

    @Test
    void cmpiSetsTheSignFlagForLessAndTheZeroFlagForEqual() {
        load("6 r1, $12", "14 r1, $14", "14 r1, $11", "14 r1, $12");

        steps(2); // 12 against 14
        assertFlags(true, false);
        cpu.step(ctx); // 12 against 11
        assertFlags(false, false);
        cpu.step(ctx); // 12 against 12
        assertFlags(false, true);
    }

    @Test
    void everyCompareSetsBothFlagsSoOldResultsDoNotLinger() {
        load("6 r1, $12", "14 r1, $12", "14 r1, $14");

        steps(2); // equal
        assertFlags(false, true);
        cpu.step(ctx); // less: ZF must be cleared again
        assertFlags(true, false);
    }

    @Test
    void cmprComparesTwoRegisters() {
        load("6 r1, $3", "6 r2, $5", "15 r1, r2", "15 r2, r1", "15 r1, r1");

        steps(3);
        assertFlags(true, false);
        cpu.step(ctx);
        assertFlags(false, false);
        cpu.step(ctx);
        assertFlags(false, true);
    }

    @Test
    void compareIsSignedAndDoesNotOverflow() {
        // MIN_VALUE - 1 wraps around to a positive number, but MIN_VALUE is still less than 1
        load("6 r1, $-2147483648", "14 r1, $1", "6 r2, $2147483647", "14 r2, $-1");

        steps(2);
        assertFlags(true, false);
        steps(2);
        assertFlags(false, false);
    }

    @Test
    void jmpJumpsRelativeToTheNextInstruction() {
        load("6 r1, $5", "13 r1");

        steps(2);

        assertEquals(14 + 5, ctx.eip()); // jmp ends at 14
    }

    @Test
    void jmpWithANegativeOffsetJumpsBack() {
        load("6 r1, $-14", "13 r1");

        steps(2);

        assertEquals(0, ctx.eip());
    }

    @ParameterizedTest(name = "opcode {0} with r1={1} (compared with 10) jumps: {2}")
    @CsvSource({
            "16, 5, true", "16, 10, false", "16, 20, false",   // jlt
            "17, 20, true", "17, 10, false", "17, 5, false",   // jgt: not when equal
            "18, 10, true", "18, 5, false", "18, 20, false"    // je
    })
    void conditionalJumps(int opcode, int left, boolean taken) {
        // 3 x 9 bytes + the 5-byte jump: the next instruction is at 32
        load("6 r1, $" + left, "6 r2, $100", "14 r1, $10", opcode + " r2");

        steps(4);

        assertEquals(taken ? 32 + 100 : 32, ctx.eip());
    }

    @Test
    void aJumpThatIsNotTakenStillChecksItsRegister() {
        memory.write(0, new byte[]{16, 11, 0, 0, 0}); // jlt r11, with SF clear

        Fault fault = assertThrows(Fault.class, () -> cpu.step(ctx));

        assertEquals("invalid register r11", fault.getMessage());
    }

    @Test
    void theCpuDoesNotCheckTheTargetOfAJumpButTheNextFetchFaults() {
        load("6 r1, $-100", "13 r1");

        steps(2);
        assertEquals(14 - 100, ctx.eip()); // the jump itself succeeded

        assertThrows(Fault.class, () -> cpu.step(ctx));
        assertEquals(14 - 100, ctx.eip());
    }

    @Test
    void jumpingIntoTheEmptyMemoryAfterTheCodeFaultsWithUnknownOpcode() {
        load("6 r1, $50", "13 r1");

        steps(2);
        Fault fault = assertThrows(Fault.class, () -> cpu.step(ctx));

        assertEquals("unknown opcode 0", fault.getMessage());
    }

    @Test
    void aJumpIntoTheMiddleOfAnInstructionRunsWhateverBytesAreThere() {
        // movi r2, $27 is 06 | 02 00 00 00 | 1b 00 00 00. Five bytes in, the constant 27
        // looks like the opcode of exit.
        load("6 r1, $5", "13 r1", "6 r2, $27");

        steps(2);

        assertEquals(14 + 5, ctx.eip());
        assertInstanceOf(Trap.Exit.class, cpu.step(ctx));
    }

    // --- stack and procedures ---

    /** An empty stack: sp starts at the top of the 256-byte address space. */
    private void emptyStack() {
        ctx.set(10, 256);
    }

    /** A process with a 14-word stack at [200, 256), where the code (at 0) cannot reach it. */
    private CpuContext contextWithSmallStack() {
        CpuContext small = new CpuContext(7, new FlatAddressSpace(memory, 256), 200, 256);
        small.set(10, 256);
        return small;
    }

    @Test
    void pushStoresTheValueBelowSpAndLowersSp() {
        emptyStack();
        load("6 r1, $77", "4 r1");

        steps(2);

        assertEquals(252, ctx.get(10));
        assertEquals(77, memory.readInt(252));
    }

    @Test
    void pushiPushesAConstant() {
        emptyStack();
        load("5 $-9");

        steps(1);

        assertEquals(252, ctx.get(10));
        assertEquals(-9, memory.readInt(252));
    }

    @Test
    void popReturnsTheValuesInReverseOrderAndRaisesSp() {
        emptyStack();
        load("5 $1", "5 $2", "5 $3", "35 r1", "35 r2", "35 r3");

        steps(6);

        assertEquals(3, ctx.get(1));
        assertEquals(2, ctx.get(2));
        assertEquals(1, ctx.get(3));
        assertEquals(256, ctx.get(10)); // a pop raises sp, the spec text says "decremented"
    }

    @Test
    void pushrSpPushesTheValueSpHadBeforeThePush() {
        emptyStack();
        load("4 r10");

        steps(1);

        assertEquals(252, ctx.get(10));
        assertEquals(256, memory.readInt(252));
    }

    @Test
    void poprSpEndsWithTheValueThatWasPopped() {
        emptyStack();
        load("5 $100", "35 r10");

        steps(2);

        assertEquals(100, ctx.get(10));
    }

    @Test
    void popmStoresTheValueInTheMemoryAtTheAddressInTheRegister() {
        emptyStack();
        load("5 $42", "6 r1, $100", "36 r1");

        steps(3);

        assertEquals(42, memory.readInt(100));
        assertEquals(256, ctx.get(10));
    }

    @Test
    void callPushesTheReturnAddressAndJumpsRelativeToTheNextInstruction() {
        emptyStack();
        load("6 r1, $20", "19 r1"); // the call is at 9..14

        steps(2);

        assertEquals(14 + 20, ctx.eip());
        assertEquals(14, memory.readInt(252)); // the return address is the next instruction
        assertEquals(252, ctx.get(10));
    }

    @Test
    void callmTakesTheOffsetFromMemory() {
        emptyStack();
        memory.writeInt(100, 20);
        load("6 r1, $100", "20 r1");

        steps(2);

        assertEquals(14 + 20, ctx.eip());
        assertEquals(14, memory.readInt(252));
    }

    @Test
    void retJumpsToTheAddressOnTheStack() {
        emptyStack();
        load("5 $200", "21");

        steps(2);

        assertEquals(200, ctx.eip());
        assertEquals(256, ctx.get(10));
    }

    @Test
    void aFullStackFaultsOnThePushAndNothingBelowItIsOverwritten() {
        CpuContext small = contextWithSmallStack();
        memory.writeInt(196, 0x1234); // just below the stack
        load("5 $1", "5 $1", "5 $1", "5 $1", "5 $1", "5 $1", "5 $1",
                "5 $1", "5 $1", "5 $1", "5 $1", "5 $1", "5 $1", "5 $1", "5 $1"); // 15 pushes

        for (int i = 0; i < 14; i++) {
            cpu.step(small);
        }
        assertEquals(200, small.get(10));
        Fault fault = assertThrows(Fault.class, () -> cpu.step(small));

        assertEquals("stack overflow", fault.getMessage());
        assertEquals(200, small.get(10));
        assertEquals(14 * 5, small.eip()); // at the push that failed
        assertEquals(0x1234, memory.readInt(196));
    }

    @Test
    void aCallOnAFullStackFaultsAndChangesNothing() {
        CpuContext small = contextWithSmallStack();
        small.set(10, 200); // full
        load("6 r1, $5", "19 r1");

        cpu.step(small);
        assertThrows(Fault.class, () -> cpu.step(small));

        assertEquals(200, small.get(10));
        assertEquals(9, small.eip());
    }

    @Test
    void popAndRetOnAnEmptyStackFaultAndChangeNothing() {
        for (String instruction : List.of("35 r1", "36 r1", "21")) {
            CpuContext empty = new CpuContext(7, new FlatAddressSpace(memory, 256));
            empty.set(10, 256);
            empty.set(1, 100);
            load(instruction);

            Fault fault = assertThrows(Fault.class, () -> cpu.step(empty), instruction);

            assertEquals("stack underflow", fault.getMessage(), instruction);
            assertEquals(256, empty.get(10), instruction);
            assertEquals(100, empty.get(1), instruction);
            assertEquals(0, empty.eip(), instruction);
        }
    }

    @Test
    void anSpOutsideTheStackIsRefusedByPushPopAndRet() {
        for (int sp : new int[]{1000, -8}) {
            for (String instruction : List.of("5 $1", "4 r1", "35 r1", "36 r1", "21", "19 r1")) {
                CpuContext bad = new CpuContext(7, new FlatAddressSpace(memory, 256));
                bad.set(10, sp);
                load(instruction);

                Fault fault = assertThrows(Fault.class, () -> cpu.step(bad), sp + " " + instruction);

                assertEquals("invalid stack pointer " + sp, fault.getMessage());
                assertEquals(sp, bad.get(10));
            }
        }
    }

    @Test
    void popmToABadAddressFaultsAndLeavesSpAlone() {
        emptyStack();
        load("5 $42", "6 r1, $1000", "36 r1");

        steps(2);
        assertThrows(Fault.class, () -> cpu.step(ctx));

        assertEquals(252, ctx.get(10));
        assertEquals(14, ctx.eip()); // the popm
    }

    @Test
    void poprIntoABadRegisterNumberFaultsAndLeavesSpAlone() {
        memory.write(0, new byte[]{35, 11, 0, 0, 0}); // popr r11
        memory.writeInt(252, 5);
        ctx.set(10, 252);

        Fault fault = assertThrows(Fault.class, () -> cpu.step(ctx));

        assertEquals("invalid register r11", fault.getMessage());
        assertEquals(252, ctx.get(10));
    }

    @Test
    void memoryclearOutsideTheAddressSpaceOrWithANegativeLengthFaults() {
        load("6 r1, $250", "6 r2, $10", "33 r1, r2");
        steps(2);
        assertThrows(Fault.class, () -> cpu.step(ctx));

        CpuContext negative = new CpuContext(7, new FlatAddressSpace(memory, 256));
        negative.set(1, 10);
        negative.set(2, -1);
        memory.write(0, Assembler.assemble("test", List.of("33 r1, r2")).code());
        assertThrows(Fault.class, () -> cpu.step(negative));
    }
}
