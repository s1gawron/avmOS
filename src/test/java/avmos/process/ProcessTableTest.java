package avmos.process;

import avmos.cpu.CpuContext;
import avmos.memory.BaseLimitAddressSpace;
import avmos.memory.MemoryBlock;
import avmos.memory.PhysicalMemory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProcessTableTest {

    private final PhysicalMemory memory = new PhysicalMemory(64);
    private final ProcessTable table = new ProcessTable();

    private Pcb pcb(int pid) {
        CpuContext context = new CpuContext(pid, new BaseLimitAddressSpace(memory, 0, 64));
        return new Pcb("program" + pid, context, new MemoryBlock(0, 64));
    }

    @Test
    void aNewPcbIsReadyAndHasUsedNoCycles() {
        Pcb pcb = pcb(3);

        assertEquals(3, pcb.pid());
        assertEquals("program3", pcb.name());
        assertEquals(ProcessState.READY, pcb.state());
        assertEquals(0, pcb.cycles());
        assertEquals(new MemoryBlock(0, 64), pcb.memory());
    }

    @Test
    void aPcbCountsCyclesAndChangesState() {
        Pcb pcb = pcb(1);

        pcb.countCycle();
        pcb.countCycle();
        pcb.setState(ProcessState.RUNNING);

        assertEquals(2, pcb.cycles());
        assertEquals(ProcessState.RUNNING, pcb.state());
    }

    @Test
    void findsAProcessByPid() {
        Pcb second = pcb(2);
        table.add(pcb(1));
        table.add(second);

        assertSame(second, table.find(2));
        assertNull(table.find(3));
        assertEquals(2, table.size());
    }

    @Test
    void keepsTheOrderInWhichProcessesWereAdded() {
        table.add(pcb(3));
        table.add(pcb(1));
        table.add(pcb(2));

        assertEquals(List.of(3, 1, 2), table.all().stream().map(Pcb::pid).toList());
    }

    @Test
    void removeTakesAProcessOut() {
        table.add(pcb(1));
        table.add(pcb(2));

        table.remove(1);

        assertNull(table.find(1));
        assertEquals(List.of(2), table.all().stream().map(Pcb::pid).toList());
        assertEquals(1, table.size());
    }

    @Test
    void removingAProcessThatIsNotThereDoesNothing() {
        table.add(pcb(1));

        table.remove(9);

        assertEquals(1, table.size());
    }

    @Test
    void aPidCanOnlyBeUsedOnce() {
        table.add(pcb(1));

        assertThrows(IllegalArgumentException.class, () -> table.add(pcb(1)));
        assertEquals(1, table.size());
    }

    @Test
    void allIsACopySoProcessesCanBeRemovedWhileLooping() {
        table.add(pcb(1));
        table.add(pcb(2));

        for (Pcb process : table.all()) {
            table.remove(process.pid());
        }

        assertEquals(0, table.size());
    }
}
