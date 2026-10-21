package avmos.cpu;

import avmos.Fault;
import avmos.asm.Assembler;
import avmos.io.FakeConsole;
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
    private final Cpu cpu = new Cpu(memory, console);
    private final CpuContext ctx = new CpuContext(7);

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
    void fetchingFromOutsideMemoryFaults() {
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
        ctx.setEip(5);

        assertThrows(Fault.class, () -> new Cpu(small, console).step(ctx));
    }

    @Test
    void dumpShowsRegistersEipAndFlags() {
        ctx.set(1, 5);
        ctx.setEip(0xde);
        ctx.setZf(true);

        assertEquals("r1=5 r2=0 r3=0 r4=0 r5=0 r6=0 r7=0 r8=0 r9=0 r10=0 eip=0x00de sf=0 zf=1", ctx.dump());
    }
}
