package avmos.cpu;

import avmos.Fault;
import avmos.asm.Assembler;
import avmos.io.FakeConsole;
import avmos.memory.FlatAddressSpace;
import avmos.memory.PhysicalMemory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        load("13 r1");

        Fault fault = assertThrows(Fault.class, () -> cpu.step(ctx));

        assertEquals("opcode jmp is not implemented yet", fault.getMessage());
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
